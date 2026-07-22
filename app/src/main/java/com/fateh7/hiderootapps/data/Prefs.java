package com.fateh7.hiderootapps.data;

/** Names and keys shared between the app UI and the Xposed hook. */
public final class Prefs {
    private Prefs() {}

    public static final String PKG = "com.fateh7.hiderootapps";
    public static final String FILE = "config";

    // JSONArray of templates: [{ "name": "...", "pkgs": ["a","b"] }, ...]
    public static final String TEMPLATES = "templates";
    // JSONArray of the union of all template pkgs (what to hide)
    public static final String HIDDEN = "hidden_packages";
    // JSONArray of protected (detector) app package names
    public static final String PROTECTED = "protected_apps";
    // JSONObject: { "pkg": { "bl": true, "st": true } }
    public static final String FLAGS = "flags";

    public static final String FLAG_BOOTLOADER = "bl";
    public static final String FLAG_SETTINGS = "st";
}
