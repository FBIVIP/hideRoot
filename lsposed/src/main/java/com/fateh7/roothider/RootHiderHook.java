package com.fateh7.roothider;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Comprehensive Java-layer root + app hider for LSPosed.
 *
 *  IMPORTANT
 *  ---------
 *  1) In the LSPosed manager, enable this module and scope it ONLY to the
 *     target app(s). Never enable it globally.
 *  2) Java hooks alone do NOT defeat native (JNI/libc) detectors, hardware
 *     attestation (Play Integrity STRONG), or server-side checks. Pair with:
 *        - Magisk DenyList + Shamiko (unmounts modules for the target)
 *        - a Zygisk native module (libc PLT hooks)
 *        - PlayIntegrityFix (for attestation)
 *  3) Set TARGET_PACKAGE, and add any apps you want to hide to HIDE_PKGS.
 */
public class RootHiderHook implements IXposedHookLoadPackage {

    /** Leave empty ("") to act on all apps this module is scoped to. */
    private static final String TARGET_PACKAGE = "";

    private static final List<String> ROOT_PATHS = Arrays.asList(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/adb/magisk", "/data/adb/ksu",
            "/data/adb/ap", "/data/adb/modules", "/system/bin/magisk",
            "/dev/.magisk", "/cache/.disable_magisk", "/system/xbin/daemonsu",
            "/system/etc/init.d", "/data/local/xbin", "/data/local/bin",
            "/system/sd/xbin", "/system/bin/.ext", "/vendor/bin/su",
            "/system/usr/we-need-root"
    );

    /** Root / hooking tool packages — always hidden. */
    private static final List<String> ROOT_PKGS = Arrays.asList(
            "com.topjohnwu.magisk", "io.github.huskydg.magisk",
            "io.github.vvb2060.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
            "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "de.robv.android.xposed.installer", "org.lsposed.manager",
            "com.zachspong.temprootremovejb", "com.ramdroid.appquarantine"
    );

    /** Extra (non-root) packages to hide from queries. Add your own here. */
    private static final List<String> HIDE_PKGS = Arrays.asList(
            // "com.example.someapp",
            // "com.another.tool"
    );

    /** Substrings that mark a path / command / proc line as root-related. */
    private static final String[] KEYS = {
            "magisk", "ksu", "kernelsu", "apatch", "supersu", "superuser",
            "xposed", "lsposed", "riru", "zygisk", "shamiko", "busybox",
            "/data/adb", "/su/", "test-keys"
    };

    @Override
    public void handleLoadPackage(final LoadPackageParam lp) {

        if (!TARGET_PACKAGE.isEmpty() && !TARGET_PACKAGE.equals(lp.packageName))
            return;

        /* 1) File existence / access checks */
        hookFileBool("exists");
        hookFileBool("canRead");
        hookFileBool("canExecute");
        hookFileBool("isFile");
        hookFileBool("isDirectory");

        XposedHelpers.findAndHookMethod(File.class, "getCanonicalPath",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (r instanceof String && containsKey((String) r))
                            p.setResult("/system/bin/false");
                    }
                });

        /* 2) Runtime.exec — neutralise su / which / magisk invocations */
        XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        String c = (String) p.args[0];
                        if (c != null && containsKey(c)) p.args[0] = "/system/bin/true";
                    }
                });
        XposedHelpers.findAndHookMethod(Runtime.class, "exec", String[].class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        String[] c = (String[]) p.args[0];
                        if (c != null && containsKey(String.join(" ", c)))
                            p.args[0] = new String[]{"/system/bin/true"};
                    }
                });

        /* 3) ProcessBuilder */
        XposedHelpers.findAndHookMethod(ProcessBuilder.class, "start",
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        ProcessBuilder pb = (ProcessBuilder) p.thisObject;
                        if (containsKey(String.join(" ", pb.command())))
                            pb.command("/system/bin/true");
                    }
                });

        /* 4) Build fields — spoof to stock release-keys */
        XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");
        Object fpObj = XposedHelpers.getStaticObjectField(Build.class, "FINGERPRINT");
        if (fpObj instanceof String && ((String) fpObj).contains("test-keys"))
            XposedHelpers.setStaticObjectField(Build.class, "FINGERPRINT",
                    ((String) fpObj).replace("test-keys", "release-keys"));

        /* 5) android.os.SystemProperties.get */
        try {
            Class<?> sp = XposedHelpers.findClass(
                    "android.os.SystemProperties", lp.classLoader);
            XposedHelpers.findAndHookMethod(sp, "get", String.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (k == null) return;
                            if (k.contains("magisk") || k.contains("ksu")) p.setResult("");
                            else if (k.equals("ro.build.selinux")) p.setResult("1");
                            else if (k.equals("ro.debuggable")) p.setResult("0");
                            else if (k.equals("ro.secure")) p.setResult("1");
                            else if (k.equals("service.adb.root")) p.setResult("0");
                            else if (k.equals("ro.build.tags")) p.setResult("release-keys");
                            else if (k.equals("ro.build.type")) p.setResult("user");
                            // Locked-bootloader / stock-secure spoof
                            else if (k.equals("ro.boot.verifiedbootstate")
                                    || k.equals("vendor.boot.verifiedbootstate")) p.setResult("green");
                            else if (k.equals("ro.boot.vbmeta.device_state")
                                    || k.equals("vendor.boot.vbmeta.device_state")) p.setResult("locked");
                            else if (k.equals("ro.boot.flash.locked")) p.setResult("1");
                            else if (k.equals("ro.boot.veritymode")) p.setResult("enforcing");
                            else if (k.equals("ro.boot.warranty_bit")
                                    || k.equals("ro.warranty_bit")) p.setResult("0");
                            else if (k.equals("sys.oem_unlock_allowed")) p.setResult("0");
                        }
                    });
            XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (k != null && (k.contains("magisk") || k.contains("ksu")))
                                p.setResult("");
                        }
                    });
        } catch (Throwable ignored) {}

        /* 6) PackageManager — hide root apps AND user-listed apps */

        // getPackageInfo -> NameNotFound for hidden packages
        XposedHelpers.findAndHookMethod(PackageManager.class, "getPackageInfo",
                String.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws PackageManager.NameNotFoundException {
                        if (isHidden((String) p.args[0]))
                            p.setThrowable(new PackageManager.NameNotFoundException());
                    }
                });

        // getApplicationInfo -> NameNotFound for hidden packages
        XposedHelpers.findAndHookMethod(PackageManager.class, "getApplicationInfo",
                String.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws PackageManager.NameNotFoundException {
                        if (isHidden((String) p.args[0]))
                            p.setThrowable(new PackageManager.NameNotFoundException());
                    }
                });

        // getPackageUid -> NameNotFound for hidden packages
        try {
            XposedHelpers.findAndHookMethod(PackageManager.class, "getPackageUid",
                    String.class, int.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p)
                                throws PackageManager.NameNotFoundException {
                            if (isHidden((String) p.args[0]))
                                p.setThrowable(new PackageManager.NameNotFoundException());
                        }
                    });
        } catch (Throwable ignored) {}

        // getLaunchIntentForPackage -> null for hidden packages
        XposedHelpers.findAndHookMethod(PackageManager.class,
                "getLaunchIntentForPackage", String.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (isHidden((String) p.args[0])) p.setResult(null);
                    }
                });

        // getInstalledApplications / getInstalledPackages -> filter list
        for (String m : new String[]{"getInstalledApplications",
                "getInstalledPackages"}) {
            XposedHelpers.findAndHookMethod(PackageManager.class, m, int.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Object res = p.getResult();
                            if (!(res instanceof List)) return;
                            List<?> list = (List<?>) res;
                            List<Object> out = new ArrayList<>();
                            for (Object o : list) {
                                String pkg = readPkgName(o);
                                if (!isHidden(pkg)) out.add(o);
                            }
                            p.setResult(out);
                        }
                    });
        }

        // queryIntentActivities -> filter apps discovered via intents
        XposedHelpers.findAndHookMethod(PackageManager.class, "queryIntentActivities",
                Intent.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        filterResolveInfos(p);
                    }
                });

        // resolveActivity -> null if it resolves to a hidden app
        XposedHelpers.findAndHookMethod(PackageManager.class, "resolveActivity",
                Intent.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object ri = p.getResult();
                        if (ri != null && isHidden(resolvePkg(ri))) p.setResult(null);
                    }
                });

        /* 7) Settings — hide adb & developer options state */
        try {
            Class<?> global = XposedHelpers.findClass(
                    "android.provider.Settings$Global", lp.classLoader);
            XposedHelpers.findAndHookMethod(global, "getInt",
                    ContentResolver.class, String.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            String k = (String) p.args[1];
                            if ("adb_enabled".equals(k)
                                    || "development_settings_enabled".equals(k))
                                p.setResult(0);
                        }
                    });
        } catch (Throwable ignored) {}

        /* 8) /proc reads — drop root-related lines from mounts/maps/status */
        XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (r instanceof String && containsKey((String) r)) {
                            BufferedReader br = (BufferedReader) p.thisObject;
                            try {
                                String next;
                                while ((next = br.readLine()) != null
                                        && containsKey(next)) { /* skip tainted */ }
                                p.setResult(next);
                            } catch (Throwable t) { p.setResult(null); }
                        }
                    }
                });

        /* 9) Debugger / SELinux state */
        try {
            XposedHelpers.findAndHookMethod("android.os.Debug", lp.classLoader,
                    "isDebuggerConnected",
                    XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {}
        try {
            XposedHelpers.findAndHookMethod("android.os.SELinux", lp.classLoader,
                    "isSELinuxEnforced",
                    XC_MethodReplacement.returnConstant(true));
        } catch (Throwable ignored) {}
    }

    /* ------------------------------- helpers ------------------------------- */

    private void hookFileBool(String method) {
        XposedHelpers.findAndHookMethod(File.class, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                File f = (File) p.thisObject;
                if (f == null) return;
                String path = f.getAbsolutePath();
                if (ROOT_PATHS.contains(path) || containsKey(path))
                    p.setResult(false);
            }
        });
    }

    /** True if a package should be hidden (root tool OR user-listed). */
    private static boolean isHidden(String pkg) {
        return pkg != null && (ROOT_PKGS.contains(pkg) || HIDE_PKGS.contains(pkg));
    }

    private static boolean containsKey(String s) {
        if (s == null) return false;
        String low = s.toLowerCase();
        for (String k : KEYS) if (low.contains(k)) return true;
        return false;
    }

    private static String readPkgName(Object o) {
        try {
            Field f = o.getClass().getField("packageName");
            return (String) f.get(o);
        } catch (Throwable t) {
            try {
                Object ai = XposedHelpers.getObjectField(o, "applicationInfo");
                return (String) XposedHelpers.getObjectField(ai, "packageName");
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /** Filter a List<ResolveInfo> result, dropping hidden packages. */
    private static void filterResolveInfos(XC_MethodHook.MethodHookParam p) {
        Object res = p.getResult();
        if (!(res instanceof List)) return;
        List<?> list = (List<?>) res;
        List<Object> out = new ArrayList<>();
        for (Object ri : list) {
            if (!isHidden(resolvePkg(ri))) out.add(ri);
        }
        p.setResult(out);
    }

    /** Extract packageName from a ResolveInfo object. */
    private static String resolvePkg(Object resolveInfo) {
        try {
            Object ai = XposedHelpers.getObjectField(resolveInfo, "activityInfo");
            return (String) XposedHelpers.getObjectField(ai, "packageName");
        } catch (Throwable t) {
            return null;
        }
    }
}
