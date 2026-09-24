package com.fateh7.roothider;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Single home page for the RootHider manager.
 *
 * From here the user drives BOTH layers:
 *   - LSPosed (Java) hooks read the "hide" list via XSharedPreferences.
 *   - The Zygisk (native) module reads the target list from
 *     /data/adb/roothider/target.txt, which we write with root.
 */
public class MainActivity extends AppCompatActivity {

    private TextView status;
    private EditText targetsBox;   // Zygisk targets (protected apps) — one per line
    private EditText hideBox;      // apps to hide from the target — one per line
    private Switch masterSwitch;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status       = findViewById(R.id.status);
        targetsBox   = findViewById(R.id.targets);
        hideBox      = findViewById(R.id.hide);
        masterSwitch = findViewById(R.id.master);
        Button save  = findViewById(R.id.save);

        // Load saved config into the UI.
        SharedPreferences prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        targetsBox.setText(prefs.getString(Config.KEY_TARGETS, ""));
        hideBox.setText(prefs.getString(Config.KEY_HIDE, ""));
        masterSwitch.setChecked(prefs.getBoolean(Config.KEY_ENABLED, true));

        refreshStatus();

        save.setOnClickListener(v -> applyConfig());
    }

    private void refreshStatus() {
        boolean root = Config.isRootAvailable();
        StringBuilder sb = new StringBuilder();
        sb.append("Root: ").append(root ? "available ✓" : "NOT available ✗").append('\n');
        sb.append("LSPosed layer: enable + scope this app in the LSPosed manager\n");
        sb.append("Zygisk layer: flash the RootHider Zygisk module once");
        status.setText(sb.toString());
    }

    @SuppressWarnings("deprecation")
    private void applyConfig() {
        String targets = targetsBox.getText().toString();
        String hide    = hideBox.getText().toString();

        // 1) Persist config for the LSPosed layer (read via XSharedPreferences).
        SharedPreferences prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        prefs.edit()
                .putString(Config.KEY_TARGETS, targets)
                .putString(Config.KEY_HIDE, hide)
                .putBoolean(Config.KEY_ENABLED, masterSwitch.isChecked())
                .apply();
        // Make the prefs file readable by the LSPosed module inside the target.
        makePrefsWorldReadable();

        // 2) Write the Zygisk target list with root.
        boolean ok = Config.writeTargetsAsRoot(targets);

        Toast.makeText(this,
                ok ? "Saved. Force-stop the target app to apply."
                   : "Saved LSPosed config, but writing Zygisk targets needs root.",
                Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    /** LSPosed reads the module's prefs; ensure the file is world-readable. */
    private void makePrefsWorldReadable() {
        try {
            java.io.File f = new java.io.File(getFilesDir().getParentFile(),
                    "shared_prefs/" + Config.PREFS + ".xml");
            if (f.exists()) {
                f.setReadable(true, false);
                f.getParentFile().setReadable(true, false);
                f.getParentFile().setExecutable(true, false);
            }
        } catch (Throwable ignored) {}
    }
}
