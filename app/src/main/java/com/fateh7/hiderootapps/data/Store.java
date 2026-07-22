package com.fateh7.hiderootapps.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App-side config store. Uses MODE_WORLD_READABLE so the Xposed hook can read
 * it via XSharedPreferences (LSPosed intercepts this mode to make it work).
 */
public class Store {

    private final SharedPreferences sp;

    @SuppressWarnings("deprecation")
    public Store(Context ctx) {
        SharedPreferences p;
        try {
            p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            // Not running under LSPosed (or blocked): fall back to private.
            p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE);
        }
        sp = p;
    }

    // ---------------- Templates ----------------

    public JSONArray templates() {
        try {
            return new JSONArray(sp.getString(Prefs.TEMPLATES, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public void saveTemplate(String name, List<String> pkgs) {
        try {
            JSONArray arr = templates();
            int foundIndex = -1;
            for (int i = 0; i < arr.length(); i++) {
                if (name.equals(arr.getJSONObject(i).optString("name"))) {
                    foundIndex = i;
                    break;
                }
            }
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("pkgs", new JSONArray(pkgs));
            if (foundIndex >= 0) arr.put(foundIndex, obj);
            else arr.put(obj);
            sp.edit().putString(Prefs.TEMPLATES, arr.toString()).apply();
            recomputeHidden();
        } catch (JSONException ignored) {
        }
    }

    public void deleteTemplate(String name) {
        JSONArray arr = templates();
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && !name.equals(o.optString("name"))) out.put(o);
        }
        sp.edit().putString(Prefs.TEMPLATES, out.toString()).apply();
        recomputeHidden();
    }

    public List<String> templatePkgs(String name) {
        JSONArray arr = templates();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && name.equals(o.optString("name"))) {
                return toList(o.optJSONArray("pkgs"));
            }
        }
        return new ArrayList<>();
    }

    /** Union of every template's packages -> the effective hide list. */
    private void recomputeHidden() {
        Set<String> all = new HashSet<>();
        JSONArray arr = templates();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            all.addAll(toList(o.optJSONArray("pkgs")));
        }
        sp.edit().putString(Prefs.HIDDEN, new JSONArray(all).toString()).apply();
    }

    // ---------------- Protected apps + flags ----------------

    public Set<String> protectedApps() {
        try {
            return new HashSet<>(toList(new JSONArray(sp.getString(Prefs.PROTECTED, "[]"))));
        } catch (JSONException e) {
            return new HashSet<>();
        }
    }

    public boolean isProtected(String pkg) {
        return protectedApps().contains(pkg);
    }

    public void setProtected(String pkg, boolean on) {
        Set<String> s = protectedApps();
        if (on) s.add(pkg); else s.remove(pkg);
        sp.edit().putString(Prefs.PROTECTED, new JSONArray(s).toString()).apply();
    }

    public boolean getFlag(String pkg, String flag, boolean def) {
        try {
            JSONObject flags = new JSONObject(sp.getString(Prefs.FLAGS, "{}"));
            JSONObject app = flags.optJSONObject(pkg);
            if (app == null || !app.has(flag)) return def;
            return app.optBoolean(flag, def);
        } catch (JSONException e) {
            return def;
        }
    }

    public void setFlag(String pkg, String flag, boolean value) {
        try {
            JSONObject flags = new JSONObject(sp.getString(Prefs.FLAGS, "{}"));
            JSONObject app = flags.optJSONObject(pkg);
            if (app == null) app = new JSONObject();
            app.put(flag, value);
            flags.put(pkg, app);
            sp.edit().putString(Prefs.FLAGS, flags.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------------- helpers ----------------

    private static List<String> toList(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null) out.add(s);
        }
        return out;
    }
}
