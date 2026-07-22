package com.fateh7.hiderootapps;

import java.util.HashMap;
import java.util.Map;

/**
 * Values returned to scoped apps to make the device look untampered.
 *
 *   GLOBAL / SECURE / SYSTEM  ->  Settings.* key overrides
 *   PROPS                     ->  SystemProperties key overrides
 *
 * Edit any map (key -> value) and rebuild. This is the same idea as the
 * "Edit the list" screen: name + namespace + value.
 */
public final class SpoofConfig {

    private SpoofConfig() {}

    /**
     * Master switch for the "bootloader locked" spoof.
     * true  = report a locked bootloader + verified boot to scoped apps.
     * (This is the toggle; a real on/off button needs the config UI.)
     */
    public static final boolean SPOOF_BOOTLOADER_LOCKED = true;

    public static final Map<String, String> GLOBAL = new HashMap<>();
    public static final Map<String, String> SECURE = new HashMap<>();
    public static final Map<String, String> SYSTEM = new HashMap<>();
    public static final Map<String, String> PROPS  = new HashMap<>();

    static {
        // --- Settings that reveal a dev / debug / tampered device ---
        GLOBAL.put("adb_enabled", "0");
        GLOBAL.put("adb_wifi_enabled", "0");
        GLOBAL.put("airplane_mode_on", "0");

        SECURE.put("development_settings_enabled", "0");
        SECURE.put("adb_enabled", "0");
        SECURE.put("install_non_market_apps", "0");

        // --- Bootloader locked / verified boot (gated by the switch above) ---
        PROPS.put("ro.boot.verifiedbootstate", "green");
        PROPS.put("ro.boot.flash.locked", "1");
        PROPS.put("ro.boot.vbmeta.device_state", "locked");
        PROPS.put("ro.boot.veritymode", "enforcing");
        PROPS.put("vendor.boot.verifiedbootstate", "green");
        PROPS.put("ro.secure", "1");
        PROPS.put("ro.debuggable", "0");
        PROPS.put("service.adb.root", "0");
        PROPS.put("sys.oem_unlock_allowed", "0");
        PROPS.put("ro.build.tags", "release-keys");
        PROPS.put("ro.build.type", "user");
    }
}
