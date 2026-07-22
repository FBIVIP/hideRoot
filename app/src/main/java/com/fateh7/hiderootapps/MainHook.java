package com.fateh7.hiderootapps;

import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.pm.PackageManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Per scoped app, hides tamper/root traces on four layers:
 *   1) PACKAGES  - root manager / tool apps look "not installed".
 *   2) PATHS     - root files, dirs and shell commands look absent.
 *   3) SETTINGS  - Settings.* keys return "clean" values (toggle per app).
 *   4) PROPS     - bootloader looks locked + verified boot (toggle per app).
 *
 * The active apps, hide list and per-app toggles come from the app UI
 * (read via XSharedPreferences). Java layer only; native (JNI) checks
 * still need a Zygisk native module.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String SELF = "com.fateh7.hiderootapps";
    private static final String NOOP = "/system/bin/false";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (SELF.equals(lpparam.packageName)) return;

        HookPrefs prefs = new HookPrefs();
        prefs.reload();

        Set<String> protectedApps = prefs.protectedApps();
        boolean bootloaderOn, settingsOn;

        if (protectedApps.isEmpty()) {
            // Not configured yet: protect every scoped app with defaults on.
            bootloaderOn = true;
            settingsOn = true;
        } else if (protectedApps.contains(lpparam.packageName)) {
            bootloaderOn = prefs.flag(lpparam.packageName,
                    com.fateh7.hiderootapps.data.Prefs.FLAG_BOOTLOADER, true);
            settingsOn = prefs.flag(lpparam.packageName,
                    com.fateh7.hiderootapps.data.Prefs.FLAG_SETTINGS, true);
        } else {
            return; // this app is not protected -> do nothing
        }

        // Effective hide list = built-in defaults + user templates.
        final Set<String> hidden = new HashSet<>(Config.defaults());
        hidden.addAll(prefs.hidden());

        hookPackages(lpparam, hidden);
        hookPaths(lpparam);
        if (settingsOn) hookSettings(lpparam);
        if (bootloaderOn) hookProps(lpparam);
    }

    // ================================================================
    // LAYER 1: hide root PACKAGES
    // ================================================================
    private void hookPackages(LoadPackageParam lpparam, final Set<String> hidden) {
        final Class<?> apm;
        try {
            apm = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager", lpparam.classLoader);
        } catch (Throwable t) {
            return;
        }

        final XC_MethodHook listFilter = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                filterPackageList((List<?>) param.getResult(), hidden);
            }
        };
        hookByName(apm, "getInstalledPackages", listFilter);
        hookByName(apm, "getInstalledApplications", listFilter);

        final XC_MethodHook notFound = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (firstArgHidden(param, hidden)) {
                    throw new PackageManager.NameNotFoundException((String) param.args[0]);
                }
            }
        };
        hookByName(apm, "getPackageInfo", notFound);
        hookByName(apm, "getPackageInfoAsUser", notFound);
        hookByName(apm, "getApplicationInfo", notFound);
        hookByName(apm, "getApplicationInfoAsUser", notFound);
        hookByName(apm, "getPackageUid", notFound);
        hookByName(apm, "getPackageUidAsUser", notFound);
        hookByName(apm, "getPackageGids", notFound);

        final XC_MethodHook nullResult = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (firstArgHidden(param, hidden)) param.setResult(null);
            }
        };
        hookByName(apm, "getLaunchIntentForPackage", nullResult);
        hookByName(apm, "getLeanbackLaunchIntentForPackage", nullResult);
    }

    private static boolean firstArgHidden(XC_MethodHook.MethodHookParam param, Set<String> hidden) {
        return param.args.length > 0
                && param.args[0] instanceof String
                && hidden.contains(param.args[0]);
    }

    private static void filterPackageList(List<?> list, Set<String> hidden) {
        if (list == null || list.isEmpty()) return;
        Iterator<?> it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            try {
                String pkg = (String) XposedHelpers.getObjectField(item, "packageName");
                if (hidden.contains(pkg)) it.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    // ================================================================
    // LAYER 2: hide root PATHS and shell commands
    // ================================================================
    private void hookPaths(LoadPackageParam lpparam) {
        final XC_MethodHook fileFalse = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File f = (File) param.thisObject;
                if (PathConfig.isRootPath(f.getAbsolutePath())) param.setResult(false);
            }
        };
        hookFile("exists", fileFalse);
        hookFile("isFile", fileFalse);
        hookFile("isDirectory", fileFalse);
        hookFile("canRead", fileFalse);
        hookFile("canWrite", fileFalse);
        hookFile("canExecute", fileFalse);

        hookFile("length", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File f = (File) param.thisObject;
                if (PathConfig.isRootPath(f.getAbsolutePath())) param.setResult(0L);
            }
        });

        hookFile("listFiles", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                File[] arr = (File[]) param.getResult();
                if (arr == null) return;
                ArrayList<File> keep = new ArrayList<>();
                for (File f : arr) {
                    if (!PathConfig.isRootPath(f.getAbsolutePath())) keep.add(f);
                }
                param.setResult(keep.toArray(new File[0]));
            }
        });
        hookFile("list", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String[] arr = (String[]) param.getResult();
                if (arr == null) return;
                File dir = (File) param.thisObject;
                ArrayList<String> keep = new ArrayList<>();
                for (String name : arr) {
                    if (!PathConfig.isRootPath(new File(dir, name).getAbsolutePath())) {
                        keep.add(name);
                    }
                }
                param.setResult(keep.toArray(new String[0]));
            }
        });

        final XC_MethodHook execHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0) return;
                Object first = param.args[0];
                String joined;
                if (first instanceof String) {
                    joined = (String) first;
                } else if (first instanceof String[]) {
                    joined = TextUtils.join(" ", (String[]) first);
                } else {
                    return;
                }
                if (PathConfig.isRootCommand(joined)) {
                    param.args[0] = (first instanceof String) ? NOOP : new String[]{ NOOP };
                }
            }
        };
        for (Method m : Runtime.class.getDeclaredMethods()) {
            if (m.getName().equals("exec")) {
                try { XposedBridge.hookMethod(m, execHook); } catch (Throwable ignored) {}
            }
        }

        try {
            XposedHelpers.findAndHookMethod(ProcessBuilder.class, "start",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                    String joined = TextUtils.join(" ", pb.command());
                    if (PathConfig.isRootCommand(joined)) pb.command(NOOP);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ================================================================
    // LAYER 3: spoof Settings.* values
    // ================================================================
    private void hookSettings(LoadPackageParam lpparam) {
        hookSettingsClass(lpparam, "android.provider.Settings$Global", SpoofConfig.GLOBAL);
        hookSettingsClass(lpparam, "android.provider.Settings$Secure", SpoofConfig.SECURE);
        hookSettingsClass(lpparam, "android.provider.Settings$System", SpoofConfig.SYSTEM);
    }

    private void hookSettingsClass(LoadPackageParam lpparam, String cls,
                                   final Map<String, String> map) {
        if (map.isEmpty()) return;
        final Class<?> c;
        try {
            c = XposedHelpers.findClass(cls, lpparam.classLoader);
        } catch (Throwable t) {
            return;
        }
        final XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 2 || !(param.args[1] instanceof String)) return;
                String val = map.get((String) param.args[1]);
                if (val != null) applyTyped(param, val);
            }
        };
        for (String name : new String[]{
                "getString", "getInt", "getLong", "getFloat",
                "getStringForUser", "getIntForUser", "getLongForUser", "getFloatForUser"}) {
            hookByName(c, name, h);
        }
    }

    // ================================================================
    // LAYER 4: spoof SystemProperties -> bootloader locked / verified boot
    // ================================================================
    private void hookProps(LoadPackageParam lpparam) {
        try {
            XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");
            XposedHelpers.setStaticObjectField(Build.class, "TYPE", "user");
        } catch (Throwable ignored) {
        }

        final Class<?> sp;
        try {
            sp = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader);
        } catch (Throwable t) {
            return;
        }
        final XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                String val = SpoofConfig.PROPS.get((String) param.args[0]);
                if (val != null) applyTyped(param, val);
            }
        };
        for (String name : new String[]{"get", "getInt", "getLong", "getBoolean"}) {
            hookByName(sp, name, h);
        }
    }

    private static void applyTyped(XC_MethodHook.MethodHookParam param, String val) {
        Class<?> ret = ((Method) param.method).getReturnType();
        try {
            if (ret == String.class)      param.setResult(val);
            else if (ret == int.class)    param.setResult(Integer.parseInt(val));
            else if (ret == long.class)   param.setResult(Long.parseLong(val));
            else if (ret == float.class)  param.setResult(Float.parseFloat(val));
            else if (ret == boolean.class) param.setResult(val.equals("1") || val.equalsIgnoreCase("true"));
        } catch (Throwable ignored) {
        }
    }

    // ================================================================
    // helpers
    // ================================================================
    private static void hookFile(String name, XC_MethodHook cb) {
        for (Method m : File.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
                try { XposedBridge.hookMethod(m, cb); } catch (Throwable ignored) {}
            }
        }
    }

    private static void hookByName(Class<?> clazz, String name, XC_MethodHook cb) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                try { XposedBridge.hookMethod(m, cb); } catch (Throwable ignored) {}
            }
        }
    }
}
