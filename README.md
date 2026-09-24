# RootHider Suite

A complete, layered root-hiding setup for a device **you own and control**.
Two components, built by one GitHub Actions pipeline:

| Layer | Path | What it covers |
|-------|------|----------------|
| **LSPosed (Java)** | `lsposed/` | `java.io.File`, `Runtime.exec`, `ProcessBuilder`, `PackageManager` (root + app hiding), `SystemProperties`, `Settings`, `/proc` line filtering, `Debug`/`SELinux`, build tags |
| **Zygisk (native)** | `zygisk/` | libc: `access`, `faccessat`, `stat`/`lstat`/`fstatat`, `open`/`openat`, `fopen`, `readlinkat`, `statfs`, `__system_property_get`; `/proc/{mounts,self/maps}` scrubbing |

The native layer closes the gap the Java layer cannot reach (native/JNI
detectors, direct libc calls). Together they are strong; neither alone is.

## Build (GitHub Actions)

Push to GitHub. The workflow runs two jobs and uploads two artifacts:

- **RootHider-LSPosed-APK** — the LSPosed module APK (debug-signed).
- **RootHider-Zygisk** — the flashable Magisk/Zygisk module zip.

Trigger on push, or **Actions → Build RootHider Suite → Run workflow**.

> For a signed **release** APK, add a keystore + `signingConfigs` in
> `lsposed/build.gradle` and switch the build step to `assembleRelease`.

## Install

1. **Magisk → Settings → enable Zygisk → reboot.**
2. Flash **RootHider-Zygisk** in Magisk → reboot.
3. Install **RootHider-LSPosed-APK**, open the **LSPosed manager**, enable the
   module, and **scope it only to your target app(s)**.
4. Set targets for the native layer (no rebuild):
   ```
   /data/adb/roothider/target.txt      # one package per line
   ```
5. Force-stop and reopen the target app.

## What the hardened native layer does

For target processes only:
- Root paths → `ENOENT` (`access`/`stat`/`open`/`openat`/`fopen`/`readlinkat`/`statfs`).
- Scrubbed `/proc/self/{maps,mounts,mountinfo,status}` — root lines dropped,
  `TracerPid` forced to `0` (anti-debug).
- **Locked-bootloader / stock-secure spoof** via system properties:
  `ro.boot.verifiedbootstate=green`, `ro.boot.vbmeta.device_state=locked`,
  `ro.boot.flash.locked=1`, `ro.boot.veritymode=enforcing`,
  `sys.oem_unlock_allowed=0`, `ro.secure=1`, `ro.debuggable=0`,
  `ro.build.tags=release-keys`, `ro.build.type=user`, warranty bits `0`.
- **Self-hide**: the module's own traces (`roothider`, `libroothider`) are
  filtered from `/proc` and libc lookups too.

App-hiding (hide specific apps from the target) lives in the **Java layer**
(`HIDE_PKGS`) because package queries go through `system_server`, not libc.

## Complete the setup (strongly recommended)

- **One unmount/hider module** — this handles the memory-based Zygisk traces
  and mount cleanup that a libc-hooking module cannot. As of 2026:
  **ReZygisk + NoHello** (recommended), or **Zygisk Next / Zygisk Assistant**.
  Do **not** mix Shamiko with ReZygisk.
- **PlayIntegrityFix (PIF)** — for Play Integrity / attestation verdicts.
- Add the target to the **Magisk/KernelSU DenyList**.

## Configure

- `lsposed/.../RootHiderHook.java` → `TARGET_PACKAGE` and `HIDE_PKGS`.
- `lsposed/src/main/res/values/arrays.xml` → scope suggestion.
- `/data/adb/roothider/target.txt` → native-layer targets at runtime.

## Honest limits (read this)

- **Raw syscalls bypass this.** PLT-hooking libc does not catch a detector
  that calls `syscall(SYS_openat, ...)` directly. Catching those needs inline
  or syscall hooking — much more invasive and fragile.
- **Memory-based Zygisk detection** is the harder problem and is handled by a
  proper unmount/hider (ReZygisk/Zygisk Next), not by libc hooks.
- **Play Integrity STRONG** is hardware-backed; no user-space hook spoofs a
  hardware keystore. That needs PIF / a valid keybox, and even then STRONG is
  often unreachable.
- **Server-side checks** run off-device; you can't hook them.

Net: this raises the bar a lot against most on-device checks. It does **not**
make detection impossible, and it needs ongoing maintenance as apps update.
