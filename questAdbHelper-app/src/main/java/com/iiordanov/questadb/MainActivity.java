package com.iiordanov.questadb;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArdpAccessibility.enable(this);
        WirelessAdbJobService.schedule(this);
        finish();
    }
}
