package com.fateh7.hiderootapps;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);

        bindCard(R.id.card_templates, R.drawable.ic_layers,
                R.string.manage_templates, R.string.manage_templates_desc,
                TemplatesActivity.class);

        bindCard(R.id.card_apps, R.drawable.ic_apps,
                R.string.manage_apps, R.string.manage_apps_desc,
                AppsActivity.class);

        bindCard(R.id.card_account, R.drawable.ic_person,
                R.string.account, R.string.account_desc,
                AccountActivity.class);
    }

    private void bindCard(int rootId, int icon, int title, int desc, Class<?> target) {
        View root = findViewById(rootId);
        ((ImageView) root.findViewById(R.id.card_icon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.card_title)).setText(title);
        ((TextView) root.findViewById(R.id.card_desc)).setText(desc);
        root.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }
}
