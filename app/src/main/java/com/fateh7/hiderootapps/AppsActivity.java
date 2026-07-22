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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppsActivity extends AppCompatActivity {

    private Store store;
    private AppListAdapter adapter;
    private final List<AppItem> all = new ArrayList<>();

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
            public void afterTextChanged(Editable s) { filter(s.toString()); }
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
                adapter.setItems(all);
                adapter.setProtected(store.protectedApps());
            });
        }).start();
    }

    private void filter(String q) {
        String query = q.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            adapter.setItems(all);
            return;
        }
        List<AppItem> out = new ArrayList<>();
        for (AppItem it : all) {
            if (it.label.toLowerCase(Locale.ROOT).contains(query)
                    || it.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(it);
            }
        }
        adapter.setItems(out);
    }
}
