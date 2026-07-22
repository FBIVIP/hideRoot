package com.fateh7.hiderootapps;

import com.fateh7.hiderootapps.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Reads the config written by the app UI. Runs inside the target process,
 * so it uses XSharedPreferences (LSPosed exposes the world-readable file).
 */
class HookPrefs {

    private final XSharedPreferences sp;

    HookPrefs() {
        sp = new XSharedPreferences(Prefs.PKG, Prefs.FILE);
        sp.makeWorldReadable();
    }

    void reload() {
        try { sp.reload(); } catch (Throwable ignored) {}
    }

    Set<String> protectedApps() {
        return arraySet(sp.getString(Prefs.PROTECTED, "[]"));
    }

    Set<String> hidden() {
        return arraySet(sp.getString(Prefs.HIDDEN, "[]"));
    }

    boolean flag(String pkg, String key, boolean def) {
        try {
            JSONObject flags = new JSONObject(sp.getString(Prefs.FLAGS, "{}"));
            JSONObject app = flags.optJSONObject(pkg);
            if (app == null || !app.has(key)) return def;
            return app.optBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static Set<String> arraySet(String json) {
        Set<String> out = new HashSet<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, null);
                if (s != null) out.add(s);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
