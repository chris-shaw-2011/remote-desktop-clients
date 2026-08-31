package com.iiordanov.questadb;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class WirelessAdbJobService extends JobService {
    private static final int JOB_ID = 0x51414442;
    private static final String TAG = "QuestWirelessAdb";

    static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, WirelessAdbJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        context.getSystemService(JobScheduler.class).schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", 0) == 1) {
            Log.i(TAG, "Wireless ADB is already enabled");
            return false;
        }
        boolean enabled = Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 1);
        Log.i(TAG, "Wireless ADB enable requested: " + enabled);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
