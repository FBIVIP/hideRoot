package com.fateh7.hiderootapps;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class AccountActivity extends AppCompatActivity {

    private static final String TELEGRAM_URL = "https://t.me/fateh7";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_account);

        ((TextView) findViewById(R.id.title)).setText(R.string.account);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((MaterialButton) findViewById(R.id.btn_telegram)).setOnClickListener(v -> openTelegram());
    }

    private void openTelegram() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            // no browser / telegram app; ignore
        }
    }
}
