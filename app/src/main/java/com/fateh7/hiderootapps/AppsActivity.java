package com.fateh7.hiderootapps;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fateh7.hiderootapps.data.AppItem;
import com.fateh7.hiderootapps.data.Store;
import com.fateh7.hiderootapps.ui.AppListAdapter;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppsActivity extends AppCompatActivity {

    private Store store;
    private AppListAdapter adapter;
    private final List<AppItem> all = new ArrayList<>();
    private boolean showSystem = false;
    private String query = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_apps);
        store = new Store(this);

        ((TextView) findViewById(R.id.title)).setText(R.string.protected_apps);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(item -> {
            Intent i = new Intent(this, AppConfigActivity.class);
            i.putExtra(AppConfigActivity.EXTRA_PKG, item.pkg);
            i.putExtra(AppConfigActivity.EXTRA_LABEL, item.label);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { query = s.toString(); applyFilter(); }
        });

        MaterialButtonToggleGroup fg = findViewById(R.id.filter_group);
        fg.check(R.id.btn_user);
        fg.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            showSystem = (checkedId == R.id.btn_all);
            applyFilter();
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setProtected(store.protectedApps());
    }

    private void loadApps() {
        new Thread(() -> {
            final List<AppItem> loaded = AppItem.loadAll(this);
            runOnUiThread(() -> {
                all.clear();
                all.addAll(loaded);
                applyFilter();
                adapter.setProtected(store.protectedApps());
            });
        }).start();
    }

    private void applyFilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<AppItem> out = new ArrayList<>();
        for (AppItem it : all) {
            if (!showSystem && it.isSystem) continue;
            if (!q.isEmpty()
                    && !it.label.toLowerCase(Locale.ROOT).contains(q)
                    && !it.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(it);
        }
        adapter.setItems(out);
        adapter.setProtected(store.protectedApps());
    }
}
