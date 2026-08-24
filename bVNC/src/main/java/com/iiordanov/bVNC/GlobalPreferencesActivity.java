package com.iiordanov.bVNC;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import com.undatech.opaque.NormalizedScrollActivity;

public class GlobalPreferencesActivity extends NormalizedScrollActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.hide();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new GlobalPreferencesFragment())
                .commit();
    }
}
