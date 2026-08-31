package com.iiordanov.questadb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ArdpAccessibilityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ArdpAccessibility.ACTION_ENABLE.equals(intent.getAction())) {
            ArdpAccessibility.enable(context);
        }
    }
}
