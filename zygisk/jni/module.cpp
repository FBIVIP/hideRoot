/*
 * RootHider — Zygisk native module (hardened, API-corrected).
 *
 * For TARGET apps only (read from /data/adb/roothider/target.txt), it:
 *   - hides root paths from libc (access/stat/open/fopen/readlinkat/...)
 *   - scrubs /proc/self/{maps,mounts,mountinfo,status}
 *   - spoofs a LOCKED-bootloader / stock-secure device via system props
 *   - hides its own artifacts (self-hide)
 *
 * LIMITS (honest):
 *   - PLT-hooking libc does NOT catch RAW syscalls (syscall(SYS_openat,...)).
 *   - Memory-based Zygisk detection needs a proper unmount/hider.
 *   - Play Integrity STRONG is hardware-backed: needs PIF / a valid keybox.
 */

#include <android/log.h>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/system_properties.h>
#include <linux/memfd.h>
#include <limits.h>
#include <unistd.h>
#include <string>
#include <vector>
#include <set>
#include <utility>
#include <mutex>
#include <dlfcn.h>

#include "zygisk.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

static Api *g_api = nullptr;                 // set in onLoad, used by dlopen re-hook
static std::set<std::pair<dev_t, ino_t>> g_seen;
static std::mutex g_mtx;
static void rehook_new_libs();               // fwd decl (defined after HOOKS)

#define LOG_TAG "RootHider"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

/* memfd_create is not declared for API < 30 — call it via syscall. */
static int rh_memfd(const char *name, unsigned int flags) {
    return (int) syscall(__NR_memfd_create, name, flags);
}

/* ---------------------------------------------------------------------- */
/* Taint matching (root traces + this module's own traces = self-hide)     */
/* ---------------------------------------------------------------------- */

static const char *TAINT_KEYS[] = {
        "magisk", "/data/adb", "ksu", "kernelsu", "apatch", "supersu",
        "/su/", "superuser", "zygisk", "rezygisk", "xposed", "lsposed",
        "riru", "shamiko", "/sbin/su", "busybox", "daemonsu",
        "roothider", "libroothider", "nohello", "tricky",
        "frida", "gum-js", "gadget", "linjector", "substrate", "hluda"
};

static bool is_tainted(const char *p) {
    if (!p) return false;
    for (auto k : TAINT_KEYS) if (strstr(p, k)) return true;
    return false;
}

static bool is_proc_sensitive(const char *p) {
    if (!p) return false;
    return strcmp(p, "/proc/self/maps") == 0 ||
           strcmp(p, "/proc/self/mounts") == 0 ||
           strcmp(p, "/proc/mounts") == 0 ||
           strcmp(p, "/proc/self/mountinfo") == 0 ||
           strcmp(p, "/proc/self/status") == 0;
}

/* ---------------------------------------------------------------------- */
/* Property spoofing table — locked bootloader + stock-secure state        */
/* ---------------------------------------------------------------------- */

struct PropSpoof { const char *name; const char *val; };
static const PropSpoof PROP_SPOOF[] = {
        {"ro.boot.verifiedbootstate",      "green"},
        {"vendor.boot.verifiedbootstate",  "green"},
        {"ro.boot.vbmeta.device_state",    "locked"},
        {"vendor.boot.vbmeta.device_state","locked"},
        {"ro.boot.flash.locked",           "1"},
        {"ro.boot.veritymode",             "enforcing"},
        {"ro.boot.warranty_bit",           "0"},
        {"ro.warranty_bit",                "0"},
        {"ro.vendor.warranty_bit",         "0"},
        {"sys.oem_unlock_allowed",         "0"},
        {"ro.oem_unlock_supported",        "0"},
        {"ro.secure",                      "1"},
        {"ro.adb.secure",                  "1"},
        {"ro.debuggable",                  "0"},
        {"service.adb.root",               "0"},
        {"ro.build.selinux",               "1"},
        {"ro.build.tags",                  "release-keys"},
        {"ro.build.type",                  "user"},
};

/* ---------------------------------------------------------------------- */
/* Scrub a /proc file into an in-memory fd                                 */
/* ---------------------------------------------------------------------- */

static FILE *(*orig_fopen)(const char *, const char *);

static int scrub_proc_to_fd(const char *path) {
    FILE *real = orig_fopen ? orig_fopen(path, "re") : fopen(path, "re");
    if (!real) return -1;

    int mfd = rh_memfd("rh", MFD_CLOEXEC);
    if (mfd < 0) { fclose(real); return -1; }

    bool is_status = (strcmp(path, "/proc/self/status") == 0);
    char *line = nullptr; size_t n = 0; ssize_t len;
    while ((len = getline(&line, &n, real)) != -1) {
        if (is_status && strncmp(line, "TracerPid:", 10) == 0) {
            const char *clean = "TracerPid:\t0\n";
            write(mfd, clean, strlen(clean));
            continue;
        }
        if (is_tainted(line)) continue;
        write(mfd, line, (size_t) len);
    }
    free(line);
    fclose(real);
    lseek(mfd, 0, SEEK_SET);
    return mfd;
}

/* ---------------------------------------------------------------------- */
/* Hooked libc functions                                                   */
/* ---------------------------------------------------------------------- */

#define DECL(ret, name, ...) \
    static ret (*orig_##name)(__VA_ARGS__); \
    static ret my_##name(__VA_ARGS__)

DECL(int, access, const char *path, int mode) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_access(path, mode);
}
DECL(int, faccessat, int dirfd, const char *path, int mode, int flags) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_faccessat(dirfd, path, mode, flags);
}
DECL(int, stat, const char *path, struct stat *buf) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_stat(path, buf);
}
DECL(int, lstat, const char *path, struct stat *buf) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_lstat(path, buf);
}
DECL(int, fstatat, int dirfd, const char *path, struct stat *buf, int flags) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_fstatat(dirfd, path, buf, flags);
}
DECL(int, statfs, const char *path, struct statfs *buf) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    return orig_statfs(path, buf);
}
DECL(ssize_t, readlinkat, int dirfd, const char *path, char *buf, size_t bufsiz) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    ssize_t r = orig_readlinkat(dirfd, path, buf, bufsiz);
    if (r > 0) {
        char tmp[PATH_MAX];
        size_t c = (r < (ssize_t) sizeof(tmp) - 1) ? (size_t) r : sizeof(tmp) - 1;
        memcpy(tmp, buf, c); tmp[c] = '\0';
        if (is_tainted(tmp)) { errno = ENOENT; return -1; }
    }
    return r;
}

DECL(int, open, const char *path, int flags, ...) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    if (is_proc_sensitive(path)) { int f = scrub_proc_to_fd(path); if (f >= 0) return f; }
    va_list ap; va_start(ap, flags);
    mode_t mode = static_cast<mode_t>(va_arg(ap, int));
    va_end(ap);
    return orig_open(path, flags, mode);
}
DECL(int, openat, int dirfd, const char *path, int flags, ...) {
    if (is_tainted(path)) { errno = ENOENT; return -1; }
    if (is_proc_sensitive(path)) { int f = scrub_proc_to_fd(path); if (f >= 0) return f; }
    va_list ap; va_start(ap, flags);
    mode_t mode = static_cast<mode_t>(va_arg(ap, int));
    va_end(ap);
    return orig_openat(dirfd, path, flags, mode);
}

static FILE *my_fopen(const char *path, const char *mode) {
    if (is_tainted(path)) { errno = ENOENT; return nullptr; }
    if (is_proc_sensitive(path)) {
        int f = scrub_proc_to_fd(path);
        if (f >= 0) { FILE *s = fdopen(f, "r"); if (s) return s; close(f); }
    }
    return orig_fopen(path, mode);
}

DECL(int, __system_property_get, const char *name, char *value) {
    if (name) {
        if (strstr(name, "magisk") || strstr(name, "ksu")) { value[0] = '\0'; return 0; }
        for (auto &s : PROP_SPOOF) {
            if (strcmp(name, s.name) == 0)
                return (int) strlcpy(value, s.val, PROP_VALUE_MAX);
        }
    }
    return orig___system_property_get(name, value);
}

/*
 * Intercept the libc syscall() dispatcher. Catches detectors that bypass the
 * normal wrappers with syscall(__NR_openat, ...) etc. NOTE: this cannot catch
 * inline `svc #0` assembly that does not go through libc's syscall symbol.
 */
DECL(long, syscall, long number, ...) {
    va_list ap; va_start(ap, number);
    long a0 = va_arg(ap, long); long a1 = va_arg(ap, long);
    long a2 = va_arg(ap, long); long a3 = va_arg(ap, long);
    long a4 = va_arg(ap, long); long a5 = va_arg(ap, long);
    va_end(ap);

    const char *path = nullptr;
    switch (number) {
#ifdef __NR_openat
        case __NR_openat:      path = (const char *) a1; break;   // openat(dirfd, path, ...)
#endif
#ifdef __NR_faccessat
        case __NR_faccessat:   path = (const char *) a1; break;
#endif
#ifdef __NR_faccessat2
        case __NR_faccessat2:  path = (const char *) a1; break;
#endif
#ifdef __NR_newfstatat
        case __NR_newfstatat:  path = (const char *) a1; break;
#endif
#ifdef __NR_statx
        case __NR_statx:       path = (const char *) a1; break;
#endif
#ifdef __NR_readlinkat
        case __NR_readlinkat:  path = (const char *) a1; break;
#endif
        default: break;
    }

    if (path && is_tainted(path)) { errno = ENOENT; return -1; }
#ifdef __NR_openat
    if (number == __NR_openat && path && is_proc_sensitive(path)) {
        int f = scrub_proc_to_fd(path);
        if (f >= 0) return f;
    }
#endif
    return orig_syscall(number, a0, a1, a2, a3, a4, a5);
}

/*
 * Re-hook when a new library is dlopen'd, so detection code loaded AFTER
 * process start is covered too (a common way apps defeat startup-only hooks).
 */
DECL(void *, dlopen, const char *name, int flag) {
    void *h = orig_dlopen(name, flag);
    if (h) rehook_new_libs();
    return h;
}
DECL(void *, android_dlopen_ext, const char *name, int flag, const void *info) {
    void *h = orig_android_dlopen_ext(name, flag, info);
    if (h) rehook_new_libs();
    return h;
}

/* ---------------------------------------------------------------------- */
/* Hook table + target-library discovery                                   */
/* ---------------------------------------------------------------------- */

struct HookDef { const char *sym; void *repl; void **backup; };
static HookDef HOOKS[] = {
        {"access",                (void *) my_access,                (void **) &orig_access},
        {"faccessat",             (void *) my_faccessat,             (void **) &orig_faccessat},
        {"stat",                  (void *) my_stat,                  (void **) &orig_stat},
        {"lstat",                 (void *) my_lstat,                 (void **) &orig_lstat},
        {"fstatat",               (void *) my_fstatat,               (void **) &orig_fstatat},
        {"statfs",                (void *) my_statfs,                (void **) &orig_statfs},
        {"readlinkat",            (void *) my_readlinkat,            (void **) &orig_readlinkat},
        {"open",                  (void *) my_open,                  (void **) &orig_open},
        {"openat",                (void *) my_openat,                (void **) &orig_openat},
        {"fopen",                 (void *) my_fopen,                 (void **) &orig_fopen},
        {"syscall",               (void *) my_syscall,               (void **) &orig_syscall},
        {"__system_property_get", (void *) my___system_property_get, (void **) &orig___system_property_get},
        {"dlopen",                (void *) my_dlopen,                (void **) &orig_dlopen},
        {"android_dlopen_ext",    (void *) my_android_dlopen_ext,    (void **) &orig_android_dlopen_ext},
};

/*
 * The new Zygisk API hooks the PLT/GOT of a target ELF identified by
 * (dev, inode). We enumerate file-backed executable mappings from
 * /proc/self/maps and hook each new one. Called at startup and again on
 * every dlopen, so libraries loaded later are covered automatically.
 */
static void rehook_new_libs() {
    if (!g_api) return;
    std::lock_guard<std::mutex> lk(g_mtx);

    FILE *f = orig_fopen ? orig_fopen("/proc/self/maps", "re")
                         : fopen("/proc/self/maps", "re");
    if (!f) return;

    bool added = false;
    char line[512];
    while (fgets(line, sizeof(line), f)) {
        char perms[8] = {0};
        unsigned int maj = 0, min = 0;
        unsigned long ino = 0;
        char path[256] = {0};
        if (sscanf(line, "%*s %7s %*s %x:%x %lu %255s",
                   perms, &maj, &min, &ino, path) < 5)
            continue;
        if (ino == 0) continue;
        if (!strchr(perms, 'x')) continue;
        if (path[0] != '/') continue;
        if (strstr(path, "libroothider")) continue;

        dev_t dev = makedev(maj, min);
        auto key = std::make_pair(dev, (ino_t) ino);
        if (!g_seen.insert(key).second) continue;   // already hooked

        for (auto &h : HOOKS)
            g_api->pltHookRegister(dev, (ino_t) ino, h.sym, h.repl, h.backup);
        added = true;
    }
    fclose(f);
    if (added) g_api->pltHookCommit();
}

/* ---------------------------------------------------------------------- */
/* Zygisk module                                                           */
/* ---------------------------------------------------------------------- */

class RootHider : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api; this->env = env; g_api = api;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        enabled = false;
        if (args && args->nice_name) {
            const char *proc = env->GetStringUTFChars(args->nice_name, nullptr);
            if (proc) { enabled = is_target(proc); env->ReleaseStringUTFChars(args->nice_name, proc); }
        }
        if (!enabled) api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

    void postAppSpecialize(const AppSpecializeArgs *) override {
        if (enabled) install_hooks();
    }

    void preServerSpecialize(ServerSpecializeArgs *) override {
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
    bool enabled = false;

    bool is_target(const char *process) {
        int fd = api->connectCompanion();
        if (fd < 0) return false;
        uint32_t len = strlen(process);
        write(fd, &len, sizeof(len));
        write(fd, process, len);
        uint8_t match = 0;
        read(fd, &match, sizeof(match));
        close(fd);
        return match == 1;
    }

    void install_hooks() {
        rehook_new_libs();   // hooks all currently-loaded libs; dlopen keeps it current
        LOGD("hardened hooks installed (with dlopen re-hook)");
    }
};

/* ---------------------------------------------------------------------- */
/* Companion (root context) — resolves target list at runtime              */
/* ---------------------------------------------------------------------- */

static const char *TARGET_FILE = "/data/adb/roothider/target.txt";

static bool file_has_target(const std::string &proc) {
    FILE *f = fopen(TARGET_FILE, "re");
    if (!f) return false;
    char *line = nullptr; size_t n = 0; ssize_t len;
    bool match = false;
    while ((len = getline(&line, &n, f)) != -1) {
        while (len > 0 && (line[len-1]=='\n'||line[len-1]=='\r'||line[len-1]==' '||line[len-1]=='\t'))
            line[--len] = '\0';
        if (len == 0 || line[0] == '#') continue;
        if (proc == line) { match = true; break; }
    }
    free(line);
    fclose(f);
    return match;
}

static void companion_handler(int fd) {
    uint32_t len = 0;
    if (read(fd, &len, sizeof(len)) != (ssize_t) sizeof(len)) return;
    if (len == 0 || len > 256) return;
    std::string proc(len, '\0');
    if (read(fd, &proc[0], len) != (ssize_t) len) return;
    uint8_t match = file_has_target(proc) ? 1 : 0;
    write(fd, &match, sizeof(match));
}

REGISTER_ZYGISK_MODULE(RootHider)
REGISTER_ZYGISK_COMPANION(companion_handler)
