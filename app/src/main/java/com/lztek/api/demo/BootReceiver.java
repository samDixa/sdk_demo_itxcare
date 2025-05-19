package com.lztek.api.demo;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Device Boot Completed - Starting SerialPortService and App");

            // Start SerialPortService
            Intent serviceIntent = new Intent(context, SerialPortService.class);
            context.startForegroundService(serviceIntent);

            // Start MainActivity (ya jo bhi teri UI activity hai)
            Intent activityIntent = new Intent(context, LoginActivity.class); // ya MainActivity.class
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
    }
}
