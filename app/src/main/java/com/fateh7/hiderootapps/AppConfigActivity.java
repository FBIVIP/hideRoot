package com.fateh7.hiderootapps;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fateh7.hiderootapps.data.Prefs;
import com.fateh7.hiderootapps.data.Store;
import com.google.android.material.materialswitch.MaterialSwitch;

public class AppConfigActivity extends AppCompatActivity {

    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_LABEL = "label";

    private Store store;
    private String pkg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_app_config);
        store = new Store(this);

        pkg = getIntent().getStringExtra(EXTRA_PKG);
        String label = getIntent().getStringExtra(EXTRA_LABEL);

        ((TextView) findViewById(R.id.title)).setText(R.string.app_settings_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.app_label)).setText(label);
        ((TextView) findViewById(R.id.app_pkg)).setText(pkg);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(pkg);
            ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        setupRow(R.id.row_protect, R.drawable.ic_shield, R.string.protect_switch,
                store.isProtected(pkg),
                (v, on) -> store.setProtected(pkg, on));

        setupRow(R.id.row_bootloader, R.drawable.ic_lock, R.string.bootloader_switch,
                store.getFlag(pkg, Prefs.FLAG_BOOTLOADER, true),
                (v, on) -> store.setFlag(pkg, Prefs.FLAG_BOOTLOADER, on));

        setupRow(R.id.row_settings, R.drawable.ic_apps, R.string.settings_switch,
                store.getFlag(pkg, Prefs.FLAG_SETTINGS, true),
                (v, on) -> store.setFlag(pkg, Prefs.FLAG_SETTINGS, on));
    }

    private interface Toggle {
        void onChange(View v, boolean on);
    }

    private void setupRow(int rootId, int icon, int titleRes, boolean checked, Toggle cb) {
        View root = findViewById(rootId);
        ((ImageView) root.findViewById(R.id.s_icon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.s_title)).setText(titleRes);
        MaterialSwitch sw = root.findViewById(R.id.s_switch);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((btn, on) -> cb.onChange(root, on));
    }
}
