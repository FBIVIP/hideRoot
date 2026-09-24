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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * RootHider — LSPosed (Java) layer. Ships inside the single manager APK.
 *
 *  - Scope this module ONLY to your target app(s) in the LSPosed manager.
 *  - The "apps to hide" list is read from the manager UI (XSharedPreferences).
 *  - Pair with the RootHider Zygisk module (native layer) + an unmounter.
 */
public class RootHiderHook implements IXposedHookLoadPackage {

    private static final List<String> ROOT_PATHS = Arrays.asList(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/adb/magisk", "/data/adb/ksu",
            "/data/adb/ap", "/data/adb/modules", "/system/bin/magisk",
            "/dev/.magisk", "/cache/.disable_magisk", "/system/xbin/daemonsu",
            "/system/etc/init.d", "/data/local/xbin", "/data/local/bin",
            "/system/sd/xbin", "/system/bin/.ext", "/vendor/bin/su",
            "/system/usr/we-need-root"
    );

    private static final List<String> ROOT_PKGS = Arrays.asList(
            "com.topjohnwu.magisk", "io.github.huskydg.magisk",
            "io.github.vvb2060.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
            "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "de.robv.android.xposed.installer", "org.lsposed.manager",
            "com.zachspong.temprootremovejb", "com.ramdroid.appquarantine"
    );

    /** Extra apps to hide, loaded from the manager UI at load time. */
    private static Set<String> HIDE_PKGS = new HashSet<>();

    private static final String[] KEYS = {
            "magisk", "ksu", "kernelsu", "apatch", "supersu", "superuser",
            "xposed", "lsposed", "riru", "zygisk", "shamiko", "busybox",
            "/data/adb", "/su/", "test-keys", "frida", "substrate"
    };

    /** Class-name fragments that betray a hooking framework (anti-detection). */
    private static final String[] HOOK_CLASS_KEYS = {
            "de.robv.android.xposed", "org.lsposed", "xposed", "lsposed",
            "riru", "roothider", "frida", "substrate"
    };

    @Override
    public void handleLoadPackage(final LoadPackageParam lp) {

        loadConfig();

        /* ---- anti-hook-detection first (so it protects everything below) ---- */
        installAntiDetection(lp);

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

        /* 2) Runtime.exec */
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

        /* 4) Build fields */
        XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");
        Object fpObj = XposedHelpers.getStaticObjectField(Build.class, "FINGERPRINT");
        if (fpObj instanceof String && ((String) fpObj).contains("test-keys"))
            XposedHelpers.setStaticObjectField(Build.class, "FINGERPRINT",
                    ((String) fpObj).replace("test-keys", "release-keys"));

        /* 5) SystemProperties.get — root hide + locked-bootloader spoof */
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
        } catch (Throwable ignored) {}

        /* 6) PackageManager — hide root apps + user-listed apps */
        XposedHelpers.findAndHookMethod(PackageManager.class, "getPackageInfo",
                String.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws PackageManager.NameNotFoundException {
                        if (isHidden((String) p.args[0]))
                            p.setThrowable(new PackageManager.NameNotFoundException());
                    }
                });
        XposedHelpers.findAndHookMethod(PackageManager.class, "getApplicationInfo",
                String.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws PackageManager.NameNotFoundException {
                        if (isHidden((String) p.args[0]))
                            p.setThrowable(new PackageManager.NameNotFoundException());
                    }
                });
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
        XposedHelpers.findAndHookMethod(PackageManager.class,
                "getLaunchIntentForPackage", String.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (isHidden((String) p.args[0])) p.setResult(null);
                    }
                });
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
        XposedHelpers.findAndHookMethod(PackageManager.class, "queryIntentActivities",
                Intent.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        filterResolveInfos(p);
                    }
                });
        XposedHelpers.findAndHookMethod(PackageManager.class, "resolveActivity",
                Intent.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object ri = p.getResult();
                        if (ri != null && isHidden(resolvePkg(ri))) p.setResult(null);
                    }
                });

        /* 7) Settings — adb & dev options */
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

        /* 8) /proc line filtering */
        XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (r instanceof String && containsKey((String) r)) {
                            BufferedReader br = (BufferedReader) p.thisObject;
                            try {
                                String next;
                                while ((next = br.readLine()) != null
                                        && containsKey(next)) { /* skip */ }
                                p.setResult(next);
                            } catch (Throwable t) { p.setResult(null); }
                        }
                    }
                });

        /* 9) Debugger / SELinux */
        try {
            XposedHelpers.findAndHookMethod("android.os.Debug", lp.classLoader,
                    "isDebuggerConnected", XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {}
        try {
            XposedHelpers.findAndHookMethod("android.os.SELinux", lp.classLoader,
                    "isSELinuxEnforced", XC_MethodReplacement.returnConstant(true));
        } catch (Throwable ignored) {}
    }

    /* ---------------- anti-hook-detection ---------------- */

    private void installAntiDetection(LoadPackageParam lp) {
        // a) Scrub stack traces so a thrown-exception scan can't spot Xposed/LSPosed.
        XposedHelpers.findAndHookMethod(Throwable.class, "getStackTrace",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        p.setResult(scrubTrace((StackTraceElement[]) p.getResult()));
                    }
                });
        XposedHelpers.findAndHookMethod(Thread.class, "getStackTrace",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        p.setResult(scrubTrace((StackTraceElement[]) p.getResult()));
                    }
                });

        // b) Class.forName(...) for known detector classes -> ClassNotFound.
        XposedHelpers.findAndHookMethod(Class.class, "forName", String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws ClassNotFoundException {
                        if (isHookClass((String) p.args[0]))
                            p.setThrowable(new ClassNotFoundException());
                    }
                });
        XposedHelpers.findAndHookMethod(Class.class, "forName", String.class,
                boolean.class, ClassLoader.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p)
                            throws ClassNotFoundException {
                        if (isHookClass((String) p.args[0]))
                            p.setThrowable(new ClassNotFoundException());
                    }
                });
    }

    private static StackTraceElement[] scrubTrace(StackTraceElement[] in) {
        if (in == null) return null;
        List<StackTraceElement> out = new ArrayList<>();
        for (StackTraceElement e : in) {
            String cn = e.getClassName();
            if (cn != null && isHookClass(cn)) continue;
            out.add(e);
        }
        return out.toArray(new StackTraceElement[0]);
    }

    private static boolean isHookClass(String n) {
        if (n == null) return false;
        String low = n.toLowerCase();
        for (String k : HOOK_CLASS_KEYS) if (low.contains(k)) return true;
        return false;
    }

    /* ---------------- config ---------------- */

    private void loadConfig() {
        try {
            XSharedPreferences prefs =
                    new XSharedPreferences(Config.MODULE_PKG, Config.PREFS);
            prefs.makeWorldReadable();
            if (prefs.getFile().canRead()) {
                String hide = prefs.getString(Config.KEY_HIDE, "");
                Set<String> s = new HashSet<>();
                for (String line : hide.split("\\n")) {
                    String p = line.trim();
                    if (!p.isEmpty()) s.add(p);
                }
                HIDE_PKGS = s;
            }
        } catch (Throwable ignored) {
            HIDE_PKGS = new HashSet<>();
        }
    }

    /* ---------------- helpers ---------------- */

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
            } catch (Throwable t2) { return null; }
        }
    }

    private static void filterResolveInfos(XC_MethodHook.MethodHookParam p) {
        Object res = p.getResult();
        if (!(res instanceof List)) return;
        List<?> list = (List<?>) res;
        List<Object> out = new ArrayList<>();
        for (Object ri : list) if (!isHidden(resolvePkg(ri))) out.add(ri);
        p.setResult(out);
    }

    private static String resolvePkg(Object resolveInfo) {
        try {
            Object ai = XposedHelpers.getObjectField(resolveInfo, "activityInfo");
            return (String) XposedHelpers.getObjectField(ai, "packageName");
        } catch (Throwable t) { return null; }
    }
}
