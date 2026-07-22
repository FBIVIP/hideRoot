package com.fateh7.hiderootapps;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fateh7.hiderootapps.data.AppItem;
import com.fateh7.hiderootapps.data.Store;
import com.fateh7.hiderootapps.ui.AppSelectAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class TemplateEditActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";

    private Store store;
    private AppSelectAdapter adapter;
    private EditText nameField;
    private final List<AppItem> all = new ArrayList<>();
    private String editingName;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_template_edit);
        store = new Store(this);

        ((TextView) findViewById(R.id.title)).setText(R.string.new_template);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        nameField = findViewById(R.id.name);
        editingName = getIntent().getStringExtra(EXTRA_NAME);
        if (editingName != null) nameField.setText(editingName);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppSelectAdapter();
        rv.setAdapter(adapter);

        if (editingName != null) {
            adapter.setSelected(new HashSet<>(store.templatePkgs(editingName)));
        }

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        ((MaterialButton) findViewById(R.id.btn_save)).setOnClickListener(v -> save());

        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            final List<AppItem> loaded = AppItem.loadAll(this);
            runOnUiThread(() -> {
                all.clear();
                all.addAll(loaded);
                adapter.setItems(all);
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

    private void save() {
        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.template_name, Toast.LENGTH_SHORT).show();
            return;
        }
        // If renamed, drop the old entry.
        if (editingName != null && !editingName.equals(name)) {
            store.deleteTemplate(editingName);
        }
        store.saveTemplate(name, new ArrayList<>(adapter.getSelected()));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
