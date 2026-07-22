package com.fateh7.hiderootapps;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fateh7.hiderootapps.data.Store;
import com.fateh7.hiderootapps.ui.TemplateAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class TemplatesActivity extends AppCompatActivity implements TemplateAdapter.Listener {

    private Store store;
    private TemplateAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_templates);
        store = new Store(this);

        ((TextView) findViewById(R.id.title)).setText(R.string.templates);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        empty = findViewById(R.id.empty);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TemplateAdapter(this);
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, TemplateEditActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.setData(store.templates());
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(String name) {
        Intent i = new Intent(this, TemplateEditActivity.class);
        i.putExtra(TemplateEditActivity.EXTRA_NAME, name);
        startActivity(i);
    }

    @Override
    public void onDelete(String name) {
        store.deleteTemplate(name);
        refresh();
    }
}
