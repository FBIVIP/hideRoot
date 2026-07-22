package com.fateh7.hiderootapps;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Root-related file paths and shell commands to hide from scoped apps.
 * Edit these lists and rebuild to tune coverage vs. false positives.
 */
public final class PathConfig {

    private PathConfig() {}

    /** Exact file/dir paths that indicate root. */
    private static final Set<String> PATHS = new HashSet<>(Arrays.asList(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/system/bin/.ext/.su", "/system/usr/we-need-root/su",
        "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
        "/system/bin/failsafe/su", "/system/xbin/daemonsu",
        "/system/app/Superuser.apk", "/system/app/SuperSU",
        "/system/etc/init.d", "/dev/com.koushikdutta.superuser.daemon/",
        "/system/bin/busybox", "/system/xbin/busybox",
        "/data/adb/magisk", "/data/adb/magisk.db", "/data/adb/modules",
        "/data/adb/ksu", "/data/adb/ap", "/cache/magisk.log",
        "/sbin/.magisk", "/init.magisk.rc"
    ));

    /** Any path CONTAINING one of these fragments is treated as a root trace. */
    private static final String[] KEYWORDS = {
        "magisk", "supersu", "superuser", "kernelsu", "/data/adb",
        "apatch", "/su/bin", "/sbin/.magisk", "zygisk", "riru",
        "lsposed", "xposed", "busybox", "daemonsu"
    };

    public static boolean isRootPath(String p) {
        if (p == null || p.isEmpty()) return false;
        if (PATHS.contains(p)) return true;
        String low = p.toLowerCase();
        for (String k : KEYWORDS) {
            if (low.contains(k)) return true;
        }
        return false;
    }

    /**
     * True if a shell command would reveal root (e.g. "which su", "su",
     * "magisk", "busybox", "mount", "getprop"). Such calls get redirected
     * to a harmless failing command.
     */
    public static boolean isRootCommand(String cmd) {
        if (cmd == null || cmd.isEmpty()) return false;
        String low = cmd.toLowerCase();
        if (low.contains("magisk") || low.contains("busybox")
                || low.contains("supersu") || low.contains("superuser")
                || low.contains("/data/adb")) {
            return true;
        }
        // Token-based check for short binaries to avoid over-matching.
        for (String tok : low.split("[\\s/]+")) {
            if (tok.equals("su") || tok.equals("mount")
                    || tok.equals("getprop") || tok.equals("which")) {
                return true;
            }
        }
        return false;
    }
}
