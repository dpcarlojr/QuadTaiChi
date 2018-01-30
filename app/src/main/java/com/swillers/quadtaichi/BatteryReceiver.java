package com.swillers.quadtaichi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by dcarlo on 1/23/18.
 */

public class BatteryReceiver extends BroadcastReceiver {
    // Logging
    String logTag = "BatteryReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                Log.d(logTag, String.format("Hooked up - Action: %s", intent.getAction()));
                MainActivity.isChargerConnected = true;
            } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
                Log.d(logTag, String.format("Discoed - Action: %s", intent.getAction()));
                MainActivity.isChargerConnected = false;
            }
            Log.d(logTag, "intent.getAction is null sucka");
        }
        Log.d(logTag, "onReceive");

    }
}
