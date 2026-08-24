package com.iiordanov.bVNC;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.iiordanov.bVNC.input.SystemKeyCaptureService;
import com.undatech.remoteClientUi.R;

public class GlobalPreferencesFragment extends PreferenceFragmentCompat {
    private static final String ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setSharedPreferencesName(Constants.generalSettingsTag);
        setPreferencesFromResource(R.xml.global_preferences, s);
        if (Utils.isVnc(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_vnc);
        } else if (Utils.isRdp(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_rdp);
        } else if (Utils.isSpice(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_spice);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if ("openDefaultConnectionSettings".equals(preference.getKey())) {
            Utils.openDefaultConnectionSettings(requireContext());
            return true;
        }
        if ("openSystemKeyCaptureSettings".equals(preference.getKey())) {
            openSystemKeyCaptureSettings();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void openSystemKeyCaptureSettings() {
        ComponentName service = new ComponentName(requireContext(), SystemKeyCaptureService.class);
        Intent details = new Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToString());

        Intent androidSettingsDetails = new Intent(details).setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.Settings$AccessibilityDetailsSettingsActivity"));
        if (!tryStartActivity(androidSettingsDetails) && !tryStartActivity(details)) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private boolean tryStartActivity(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return false;
        }
    }
}
