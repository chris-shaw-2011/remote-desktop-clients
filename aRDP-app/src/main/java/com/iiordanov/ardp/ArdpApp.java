package com.iiordanov.ardp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.iiordanov.bVNC.App;

public class ArdpApp extends App {
    private static final String HELPER_PACKAGE = "com.iiordanov.questadb";
    private static final String HELPER_RECEIVER = HELPER_PACKAGE
            + ".ArdpAccessibilityReceiver";
    private static final String ACTION_ENABLE = HELPER_PACKAGE + ".ENABLE_ARDP_KEY_CAPTURE";

    @Override
    public void onCreate() {
        super.onCreate();
        requestKeyCapture(this);
    }

    static void requestKeyCapture(Context context) {
        context.sendBroadcast(new Intent(ACTION_ENABLE)
                .setComponent(new ComponentName(HELPER_PACKAGE, HELPER_RECEIVER))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES));
    }
}
