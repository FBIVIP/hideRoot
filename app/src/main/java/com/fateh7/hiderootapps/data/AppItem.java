package com.fateh7.hiderootapps.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single installed application entry shown in the pickers. */
public class AppItem {
    public final String label;
    public final String pkg;
    public final Drawable icon;

    public AppItem(String label, String pkg, Drawable icon) {
        this.label = label;
        this.pkg = pkg;
        this.icon = icon;
    }

    /** Loads all launchable / user + system apps, sorted by label. */
    public static List<AppItem> loadAll(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<AppItem> list = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            try {
                String label = pm.getApplicationLabel(ai).toString();
                Drawable icon = pm.getApplicationIcon(ai);
                list.add(new AppItem(label, ai.packageName, icon));
            } catch (Throwable ignored) {
            }
        }
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }
}
