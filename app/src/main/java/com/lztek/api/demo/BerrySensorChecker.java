package com.lztek.api.demo;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class BerrySensorChecker {

    private static final String TAG = "BerrySensorChecker";
    private HandlerThread handlerThread;
    private Handler handler;
    private GlobalVars globalVars;
    private boolean isRunning = false;
    private Context context;
    private BerrySerialPort serialPort;

    private long lastSpO2Time = 0;
    private long lastECGTime = 0;
    private long lastNIBPTime = 0;
    private long lastTempTime = 0;
    private static final long TIMEOUT_MS = 5000;

    public BerrySensorChecker(Context context, BerrySerialPort serialPort) {
        this.context = context;
        this.serialPort = serialPort;
        globalVars = GlobalVars.getInstance();
        handlerThread = new HandlerThread("BerrySensorThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void startChecking() {
        if (isRunning) return;
        isRunning = true;
        handler.postDelayed(statusCheckerRunnable, 0);
    }

    public void stopChecking() {
        isRunning = false;
        handler.removeCallbacks(statusCheckerRunnable);
        handlerThread.quitSafely();
    }

    private final Runnable statusCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning || !serialPort.isConnected()) {
                globalVars.setSpO2Connected(false);
                globalVars.setECGConnected(false);
                globalVars.setNIBPConnected(false);
                globalVars.setTempConnected(false);
//                Log.d(TAG, "No connection or stopped - All sensors set to false");
            } else {
                checkSensorStatus();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void checkSensorStatus() {
        long currentTime = System.currentTimeMillis();
        boolean spo2Status = (currentTime - lastSpO2Time) < TIMEOUT_MS;
        boolean ecgStatus = (currentTime - lastECGTime) < TIMEOUT_MS;
        boolean nibpStatus = (currentTime - lastNIBPTime) < TIMEOUT_MS;
        boolean tempStatus = (currentTime - lastTempTime) < TIMEOUT_MS;

        globalVars.setSpO2Connected(spo2Status);
        globalVars.setECGConnected(ecgStatus);
        globalVars.setNIBPConnected(nibpStatus);
        globalVars.setTempConnected(tempStatus);

//        Log.d(TAG, "Timestamps - SpO2: " + lastSpO2Time + ", ECG: " + lastECGTime +
//                ", NIBP: " + lastNIBPTime + ", Temp: " + lastTempTime);
//        Log.d(TAG, "Sensor Status - SpO2: " + spo2Status + ", ECG: " + ecgStatus +
//                ", NIBP: " + nibpStatus + ", Temp: " + tempStatus);
    }

    public void onSpO2DataReceived() {
        lastSpO2Time = System.currentTimeMillis();
//        Log.d(TAG, "SpO2 data received, timestamp updated: " + lastSpO2Time);
    }

    public void onECGDataReceived() {
        lastECGTime = System.currentTimeMillis();
//        Log.d(TAG, "ECG data received, timestamp updated: " + lastECGTime);
    }

    public void onNIBPDataReceived() {
        lastNIBPTime = System.currentTimeMillis();
//        Log.d(TAG, "NIBP data received, timestamp updated: " + lastNIBPTime);
    }

    public void onTempDataReceived() {
        lastTempTime = System.currentTimeMillis();
//        Log.d(TAG, "Temp data received, timestamp updated: " + lastTempTime);
    }
}