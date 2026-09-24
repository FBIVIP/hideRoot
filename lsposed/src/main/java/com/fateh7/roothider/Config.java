package com.fateh7.roothider;

import java.io.DataOutputStream;

/** Shared config keys + a small root helper (uses the su binary directly). */
public final class Config {
    public static final String MODULE_PKG   = "com.fateh7.roothider";
    public static final String PREFS         = "config";

    // Preference keys
    public static final String KEY_TARGETS   = "targets";     // Zygisk + scope hint (one pkg per line)
    public static final String KEY_HIDE      = "hide_pkgs";   // apps hidden from target (one pkg per line)
    public static final String KEY_ENABLED   = "master_enabled";

    public static final String TARGET_FILE   = "/data/adb/roothider/target.txt";

    private Config() {}

    /** Write the Zygisk target list via root. Returns true on success. */
    public static boolean writeTargetsAsRoot(String targetsMultiline) {
        StringBuilder sb = new StringBuilder();
        sb.append("mkdir -p /data/adb/roothider\n");
        sb.append("cat > ").append(TARGET_FILE).append(" << 'RH_EOF'\n");
        sb.append("# Managed by RootHider Manager. One package per line.\n");
        for (String line : targetsMultiline.split("\\n")) {
            String p = line.trim();
            if (!p.isEmpty()) sb.append(p).append("\n");
        }
        sb.append("RH_EOF\n");
        sb.append("chmod 0600 ").append(TARGET_FILE).append("\n");
        return runAsRoot(sb.toString());
    }

    public static boolean isRootAvailable() {
        return runAsRoot("id");
    }

    private static boolean runAsRoot(String script) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(script);
            os.writeBytes("\nexit\n");
            os.flush();
            return p.waitFor() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
