package com.iiordanov.questadb;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

final class ArdpAccessibility {
    static final String ACTION_ENABLE = "com.iiordanov.questadb.ENABLE_ARDP_KEY_CAPTURE";
    static final String ARDP_PACKAGE = "com.iiordanov.aRDP";
    static final String SERVICE = ARDP_PACKAGE
            + "/com.iiordanov.bVNC.input.SystemKeyCaptureService";
    private static final String TAG = "QuestWirelessAdb";

    private ArdpAccessibility() {
    }

    static void enable(Context context) {
        try {
            String current = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String enabled = addEnabledService(current, SERVICE);
            boolean serviceEnabled = enabled.equals(current)
                    || Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled);
            boolean accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                    || Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(TAG, "aRDP key capture enable requested: "
                    + (serviceEnabled && accessibilityEnabled));
        } catch (SecurityException exception) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS has not been granted", exception);
        }
    }

    static String addEnabledService(String enabledServices, String service) {
        Set<String> services = new LinkedHashSet<>();
        if (enabledServices != null) {
            for (String enabledService : enabledServices.split(":")) {
                if (!enabledService.isEmpty()) {
                    services.add(enabledService);
                }
            }
        }
        services.add(service);
        return String.join(":", services);
    }
}
