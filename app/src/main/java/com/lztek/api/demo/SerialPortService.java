package com.lztek.api.demo;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.lztek.toolkit.Lztek;
import com.lztek.toolkit.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SerialPortService extends Service {
    private static final String TAG = "SerialPortService";
    private static final String SERIAL_PORT_PATH = "/dev/ttyS4";
    private static final int BAUD_RATE = 115200;
    private static final int STRUCT_SIZE = 60;

    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private boolean isRunning = false;
    private AudioManager audioManager;
    private static Lztek mLztek; // Singleton instance
    private CPUData previousPacket = null;
    private final Object packetLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Singleton Lztek instance
    public static synchronized Lztek getLztek(Context context) {
        if (mLztek == null) {
            mLztek = Lztek.create(context.getApplicationContext());
            Log.d(TAG, "✅ Lztek instance created");
        }
        return mLztek;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mLztek = getLztek(this);

        try {
            mSerialPort = mLztek.openSerialPort(SERIAL_PORT_PATH, BAUD_RATE, 8, 0, 1, 0);
            InputStream inputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();
            if (mOutputStream == null) {
                Log.e(TAG, "❌ OutputStream initialization failed");
            } else {
                Log.d(TAG, "✅ OutputStream initialized successfully");
            }
            isRunning = true;
            new Thread(() -> readSerialData(inputStream)).start();
            Log.d(TAG, "✅ Serial Port Opened Successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening serial port", e);
            stopSelf();
        }

        startForegroundService();
    }

    private void readSerialData(InputStream inputStream) {
        byte[] buffer = new byte[STRUCT_SIZE];
        int bytesRead;

        while (isRunning) {
            try {
                if (inputStream != null) {
                    int availableBytes = inputStream.available();
                    if (availableBytes < STRUCT_SIZE) {
                        Thread.sleep(50);
                        continue;
                    }

                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == STRUCT_SIZE) {
                        CPUData newPacket = new CPUData(buffer);

                        synchronized (packetLock) {
                            printCPUData(newPacket, buffer);

                            checkVolumeChange(newPacket);
                            checkMicChange(newPacket);
                            checkCpuChange(newPacket);
                            checkVitalChange(newPacket);
                            checkSpeakerChange(newPacket); // Added Speaker check

                            mainHandler.post(() -> {
                                Intent intent = new Intent("com.lztek.api.demo.STATUS_UPDATE");
                                intent.putExtra("battery_percentage", newPacket.Battery_percentage);
                                intent.putExtra("charging_status", newPacket.charging_status);
                                intent.putExtra("cpu_status", newPacket.s_CPU);
                                intent.putExtra("vital_status", newPacket.VITAL);
                                sendBroadcast(intent);
                            });

                            previousPacket = newPacket;
                        }
                    } else {
                        Log.w(TAG, "⚠️ Partial packet received: " + bytesRead + " bytes");
                    }
                }
                Thread.sleep(50);
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "❌ Error reading from serial port", e);
            }
        }
    }

    private void printCPUData(CPUData data, byte[] rawBuffer) {
        StringBuilder rawBytes = new StringBuilder();
        for (byte b : rawBuffer) {
            rawBytes.append(String.format("%02X ", b));
        }
        Log.d(TAG, "📜 Raw Data Bytes: " + rawBytes.toString());

        Log.d(TAG, "----------- CPU Data -----------");
        Log.d(TAG, "Header             : " + data.header);
        Log.d(TAG, "Battery %          : " + data.Battery_percentage);
        Log.d(TAG, "Charging Status    : " + data.charging_status);
        Log.d(TAG, "CPU                : " + data.s_CPU);
        Log.d(TAG, "VITAL              : " + data.VITAL);
        Log.d(TAG, "SPK                : " + data.SPK);
        Log.d(TAG, "LIGHT              : " + data.LIGHT);
        Log.d(TAG, "Backlight          : " + data.backlight);
        Log.d(TAG, "Spare ONE          : " + data.ONE);
        Log.d(TAG, "Spare TWO          : " + data.TWO);
        Log.d(TAG, "Volume Down        : " + data.vol_D);
        Log.d(TAG, "Volume Up          : " + data.vol_up);
        Log.d(TAG, "Mic Down (Spare)   : " + data.MIC_D);
        Log.d(TAG, "Mic Up   (Spare)   : " + data.MIC_up);
        Log.d(TAG, "Fan ON             : " + data.FAN_on);
        Log.d(TAG, "--------------------------------");
    }

    private void checkVolumeChange(CPUData newPacket) {
        if (previousPacket == null) {
            Log.d(TAG, "🔊 Initial Volume Packet: vol_up=" + newPacket.vol_up + ", vol_D=" + newPacket.vol_D);
            return;
        }

        Log.d(TAG, "🔊 Volume Check - Previous vol_up: " + previousPacket.vol_up + ", New vol_up: " + newPacket.vol_up +
                ", Previous vol_D: " + previousPacket.vol_D + ", New vol_D: " + newPacket.vol_D + ", Thread=" + Thread.currentThread().getId());

        if (previousPacket.vol_up == 0 && newPacket.vol_up == 1) {
            Log.d(TAG, "🔼 Volume Up Pressed!");
            adjustVolume(true);
        } else if (newPacket.vol_up == 1) {
            try {
                Thread.sleep(200);
                adjustVolume(true);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Sleep interrupted in volume up", e);
            }
        }

        if (previousPacket.vol_D == 0 && newPacket.vol_D == 1) {
            Log.d(TAG, "🔽 Volume Down Pressed!");
            adjustVolume(false);
        } else if (newPacket.vol_D == 1) {
            try {
                Thread.sleep(200);
                adjustVolume(false);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Sleep interrupted in volume down", e);
            }
        }
    }

    private void adjustVolume(boolean increase) {
        if (audioManager != null) {
            long startTime = System.currentTimeMillis();
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int minVolume = 0;

            int newVolume = increase ? Math.min(currentVolume + 1, maxVolume)
                    : Math.max(currentVolume - 1, minVolume);

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🔊 Volume Changed: " + newVolume + " (Max: " + maxVolume + ") in " + (endTime - startTime) + "ms");
        }
    }

    private void checkMicChange(CPUData newPacket) {
        if (previousPacket == null) {
            Log.d(TAG, "🎤 Initial Mic Packet: MIC_up=" + newPacket.MIC_up + ", MIC_D=" + newPacket.MIC_D);
            return;
        }

        Log.d(TAG, "🎤 Mic Check - Previous MIC_up: " + previousPacket.MIC_up + ", New MIC_up: " + newPacket.MIC_up +
                ", Previous MIC_D: " + previousPacket.MIC_D + ", New MIC_D: " + newPacket.MIC_D + ", Thread=" + Thread.currentThread().getId());

        if (previousPacket.MIC_up == 0 && newPacket.MIC_up == 1) {
            Log.d(TAG, "🎤 Mic Up Pressed! Increasing Mic Level");
            adjustMicLevel(true);
        } else if (newPacket.MIC_up == 1) {
            try {
                Thread.sleep(200);
                adjustMicLevel(true);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Sleep interrupted in mic up", e);
            }
        }

        if (previousPacket.MIC_D == 0 && newPacket.MIC_D == 1) {
            Log.d(TAG, "🎤 Mic Down Pressed! Decreasing Mic Level");
            adjustMicLevel(false);
        } else if (newPacket.MIC_D == 1) {
            try {
                Thread.sleep(200);
                adjustMicLevel(false);
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Sleep interrupted in mic down", e);
            }
        }
    }

    private void adjustMicLevel(boolean increase) {
        if (audioManager != null) {
            long startTime = System.currentTimeMillis();
            int currentMicLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxMicLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int minMicLevel = 0;

            int newMicLevel = increase ? Math.min(currentMicLevel + 1, maxMicLevel)
                    : Math.max(currentMicLevel - 1, minMicLevel);

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newMicLevel, AudioManager.FLAG_SHOW_UI);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ") in " + (endTime - startTime) + "ms");
        }
    }

    private void cpuOnMethod() {
        Log.d(TAG, "🖥️ CPU ON method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            mLztek.setLcdBackLight(false);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🖥️ Screen turned OFF successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn screen OFF: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
        }
    }

    private void cpuOffMethod() {
        Log.d(TAG, "🖥️ CPU OFF method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            mLztek.setLcdBackLight(true);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🖥️ Screen turned ON successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn screen ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
            try {
                long startTime = System.currentTimeMillis();
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "SerialPortService:ScreenOn");
                wakeLock.acquire(10);
                wakeLock.release();
                long endTime = System.currentTimeMillis();
                Log.d(TAG, "🖥️ Fallback: Screen turned ON using PowerManager in " + (endTime - startTime) + "ms");
            } catch (Exception pmException) {
                Log.e(TAG, "❌ Fallback PowerManager failed: " + pmException.getMessage() + " at " + System.currentTimeMillis(), pmException);
            }
        }
    }

    private void checkCpuChange(CPUData newPacket) {
        if (newPacket == null) {
            Log.e(TAG, "❌ New CPU packet is null");
            return;
        }

        Log.d(TAG, "🖥️ Processing CPU Packet: s_CPU=" + newPacket.s_CPU + ", Thread=" + Thread.currentThread().getId());

        if (previousPacket == null) {
            Log.d(TAG, "🖥️ Initial CPU Packet: s_CPU=" + newPacket.s_CPU);
            if (newPacket.s_CPU == 1) {
                Log.d(TAG, "🖥️ Initial CPU Button Pressed! Calling ON method");
                cpuOnMethod();
            } else if (newPacket.s_CPU == 0) {
                Log.d(TAG, "🖥️ Initial CPU Button Released! Calling OFF method");
                cpuOffMethod();
            }
            return;
        }

        Log.d(TAG, "🖥️ CPU Check - Previous s_CPU: " + previousPacket.s_CPU + ", New s_CPU: " + newPacket.s_CPU);

        if (previousPacket.s_CPU != newPacket.s_CPU) {
            if (newPacket.s_CPU == 1) {
                Log.d(TAG, "🖥️ CPU Button Pressed! Calling ON method");
                cpuOnMethod();
            } else if (newPacket.s_CPU == 0) {
                Log.d(TAG, "🖥️ CPU Button Released! Calling OFF method");
                cpuOffMethod();
            }
        } else {
            Log.d(TAG, "🖥️ No CPU state change detected");
        }
    }

    private void vitalOnMethod() {
        Log.d(TAG, "💉 VITAL ON method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            GlobalVars.setVitalOn(true);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "💉 VITAL turned ON successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn VITAL ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
        }
    }

    private void vitalOffMethod() {
        Log.d(TAG, "💉 VITAL OFF method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            GlobalVars.setVitalOn(false);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "💉 VITAL turned OFF successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn VITAL OFF: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
        }
    }

    private void checkVitalChange(CPUData newPacket) {
        if (newPacket == null) {
            Log.e(TAG, "❌ New VITAL packet is null");
            return;
        }

        Log.d(TAG, "💉 Processing VITAL Packet: VITAL=" + newPacket.VITAL + ", Thread=" + Thread.currentThread().getId());

        if (previousPacket == null) {
            Log.d(TAG, "💉 Initial VITAL Packet: VITAL=" + newPacket.VITAL);
            if (newPacket.VITAL == 1) {
                Log.d(TAG, "💉 Initial VITAL Button Pressed! Calling ON method");
                vitalOnMethod();
            } else if (newPacket.VITAL == 0) {
                Log.d(TAG, "💉 Initial VITAL Button Released! Calling OFF method");
                vitalOffMethod();
            }
            return;
        }

        Log.d(TAG, "💉 VITAL Check - Previous VITAL: " + previousPacket.VITAL + ", New VITAL: " + newPacket.VITAL);

        if (previousPacket.VITAL != newPacket.VITAL) {
            if (newPacket.VITAL == 1) {
                Log.d(TAG, "💉 VITAL Button Pressed! Calling ON method");
                vitalOnMethod();
            } else if (newPacket.VITAL == 0) {
                Log.d(TAG, "💉 VITAL Button Released! Calling OFF method");
                vitalOffMethod();
            }
        } else {
            Log.d(TAG, "💉 No VITAL state change detected");
        }
    }

    private void speakerOnMethod() {
        Log.d(TAG, "🔊 SPEAKER ON method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            if (audioManager != null) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true); // Mute
                Log.d(TAG, "🔊 Speaker muted");
                mainHandler.post(() -> {
                    Toast.makeText(getApplicationContext(), "Speaker Muted", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "🔊 Toast shown: Speaker Muted");
                });
            }
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🔊 SPEAKER turned ON successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn SPEAKER ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
        }
    }

    private void speakerOffMethod() {
        Log.d(TAG, "🔊 SPEAKER OFF method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
        try {
            long startTime = System.currentTimeMillis();
            if (audioManager != null) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false); // Unmute
                Log.d(TAG, "🔊 Speaker unmuted");
                mainHandler.post(() -> {
                    Toast.makeText(getApplicationContext(), "Speaker Unmuted", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "🔊 Toast shown: Speaker Unmuted");
                });
            }
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "🔊 SPEAKER turned OFF successfully in " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to turn SPEAKER OFF: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
        }
    }

    private void checkSpeakerChange(CPUData newPacket) {
        if (newPacket == null) {
            Log.e(TAG, "❌ New SPEAKER packet is null");
            return;
        }

        Log.d(TAG, "🔊 Processing SPEAKER Packet: SPK=" + newPacket.SPK + ", Thread=" + Thread.currentThread().getId());

        if (previousPacket == null) {
            Log.d(TAG, "🔊 Initial SPEAKER Packet: SPK=" + newPacket.SPK);
            if (newPacket.SPK == 1) {
                Log.d(TAG, "🔊 Initial SPEAKER Button Pressed! Calling ON method");
                speakerOnMethod();
            } else if (newPacket.SPK == 0) {
                Log.d(TAG, "🔊 Initial SPEAKER Button Released! Calling OFF method");
                speakerOffMethod();
            }
            return;
        }

        Log.d(TAG, "🔊 SPEAKER Check - Previous SPK: " + previousPacket.SPK + ", New SPK: " + newPacket.SPK);

        if (previousPacket.SPK != newPacket.SPK) {
            if (newPacket.SPK == 1) {
                Log.d(TAG, "🔊 SPEAKER Button Pressed! Calling ON method");
                speakerOnMethod();
            } else if (newPacket.SPK == 0) {
                Log.d(TAG, "🔊 SPEAKER Button Released! Calling OFF method");
                speakerOffMethod();
            }
        } else {
            Log.d(TAG, "🔊 No SPEAKER state change detected");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (mSerialPort != null) {
            try {
                mSerialPort.close();
                Log.d(TAG, "✅ Serial Port Closed");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error closing serial port", e);
            }
        }
        mLztek = null;
        Log.d(TAG, "✅ Service Destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class CPUData {
        public long header, Battery_percentage, charging_status, s_CPU, VITAL, SPK, LIGHT;
        public long backlight, ONE, TWO, vol_D, vol_up, MIC_D, MIC_up, FAN_on;

        public CPUData(byte[] buffer) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            header = getUnsignedInt(byteBuffer, 0);
            Battery_percentage = getUnsignedInt(byteBuffer, 4);
            charging_status = getUnsignedInt(byteBuffer, 8);
            s_CPU = getUnsignedInt(byteBuffer, 12);
            VITAL = getUnsignedInt(byteBuffer, 16);
            SPK = getUnsignedInt(byteBuffer, 20);
            LIGHT = getUnsignedInt(byteBuffer, 24);
            backlight = getUnsignedInt(byteBuffer, 28);
            ONE = getUnsignedInt(byteBuffer, 32);
            TWO = getUnsignedInt(byteBuffer, 36);
            vol_D = getUnsignedInt(byteBuffer, 40);
            vol_up = getUnsignedInt(byteBuffer, 44);
            MIC_D = getUnsignedInt(byteBuffer, 48);
            MIC_up = getUnsignedInt(byteBuffer, 52);
            FAN_on = getUnsignedInt(byteBuffer, 56);
        }

        private static long getUnsignedInt(ByteBuffer buffer, int offset) {
            return ((long) buffer.getInt(offset)) & 0xFFFFFFFFL;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void startForegroundService() {
        String CHANNEL_ID = "SerialPortServiceChannel";
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Serial Port Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SerialPortService Running")
                .setSmallIcon(R.drawable.ic_patient)
                .build();

        startForeground(1, notification);
    }
}

//package com.lztek.api.demo;
//
//import android.annotation.TargetApi;
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.media.AudioManager;
//import android.os.Build;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.os.PowerManager;
//import android.util.Log;
//
//import androidx.core.app.NotificationCompat;
//
//import com.lztek.toolkit.Lztek;
//import com.lztek.toolkit.SerialPort;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//
//public class SerialPortService extends Service {
//    private static final String TAG = "SerialPortService";
//    private static final String SERIAL_PORT_PATH = "/dev/ttyS4";
//    private static final int BAUD_RATE = 115200;
//    private static final int STRUCT_SIZE = 60;
//
//    private SerialPort mSerialPort;
//    private OutputStream mOutputStream;
//    private boolean isRunning = false;
//    private AudioManager audioManager;
//    private static Lztek mLztek; // Singleton instance
//    private CPUData previousPacket = null;
//    private final Object packetLock = new Object();
//    private final Handler mainHandler = new Handler(Looper.getMainLooper());
//
//    // Singleton Lztek instance
//    public static synchronized Lztek getLztek(Context context) {
//        if (mLztek == null) {
//            mLztek = Lztek.create(context.getApplicationContext());
//            Log.d(TAG, "✅ Lztek instance created");
//        }
//        return mLztek;
//    }
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        mLztek = getLztek(this);
//
//        try {
//            mSerialPort = mLztek.openSerialPort(SERIAL_PORT_PATH, BAUD_RATE, 8, 0, 1, 0);
//            InputStream inputStream = mSerialPort.getInputStream();
//            mOutputStream = mSerialPort.getOutputStream();
//            if (mOutputStream == null) {
//                Log.e(TAG, "❌ OutputStream initialization failed");
//            } else {
//                Log.d(TAG, "✅ OutputStream initialized successfully");
//            }
//            isRunning = true;
//            new Thread(() -> readSerialData(inputStream)).start();
//            Log.d(TAG, "✅ Serial Port Opened Successfully");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Error opening serial port", e);
//            stopSelf();
//        }
//
//        startForegroundService();
//    }
//
//    private void readSerialData(InputStream inputStream) {
//        byte[] buffer = new byte[STRUCT_SIZE];
//        int bytesRead;
//
//        while (isRunning) {
//            try {
//                if (inputStream != null) {
//                    int availableBytes = inputStream.available();
//                    if (availableBytes < STRUCT_SIZE) {
//                        Thread.sleep(50);
//                        continue;
//                    }
//
//                    bytesRead = inputStream.read(buffer);
//                    if (bytesRead == STRUCT_SIZE) {
//                        CPUData newPacket = new CPUData(buffer);
//
//                        synchronized (packetLock) {
//                            printCPUData(newPacket, buffer);
//
//                            checkVolumeChange(newPacket);
//                            checkMicChange(newPacket);
//                            checkCpuChange(newPacket);
//                            checkVitalChange(newPacket);
//
//                            mainHandler.post(() -> {
//                                Intent intent = new Intent("com.lztek.api.demo.STATUS_UPDATE");
//                                intent.putExtra("battery_percentage", newPacket.Battery_percentage);
//                                intent.putExtra("charging_status", newPacket.charging_status);
//                                intent.putExtra("cpu_status", newPacket.s_CPU);
//                                intent.putExtra("vital_status", newPacket.VITAL);
//                                sendBroadcast(intent);
//                            });
//
//                            previousPacket = newPacket;
//                        }
//                    } else {
//                        Log.w(TAG, "⚠️ Partial packet received: " + bytesRead + " bytes");
//                    }
//                }
//                Thread.sleep(50);
//            } catch (IOException | InterruptedException e) {
//                Log.e(TAG, "❌ Error reading from serial port", e);
//            }
//        }
//    }
//
//    private void printCPUData(CPUData data, byte[] rawBuffer) {
//        StringBuilder rawBytes = new StringBuilder();
//        for (byte b : rawBuffer) {
//            rawBytes.append(String.format("%02X ", b));
//        }
//        Log.d(TAG, "📜 Raw Data Bytes: " + rawBytes.toString());
//
//        Log.d(TAG, "----------- CPU Data -----------");
//        Log.d(TAG, "Header             : " + data.header);
//        Log.d(TAG, "Battery %          : " + data.Battery_percentage);
//        Log.d(TAG, "Charging Status    : " + data.charging_status);
//        Log.d(TAG, "CPU                : " + data.s_CPU);
//        Log.d(TAG, "VITAL              : " + data.VITAL);
//        Log.d(TAG, "SPK                : " + data.SPK);
//        Log.d(TAG, "LIGHT              : " + data.LIGHT);
//        Log.d(TAG, "Backlight          : " + data.backlight);
//        Log.d(TAG, "Spare ONE          : " + data.ONE);
//        Log.d(TAG, "Spare TWO          : " + data.TWO);
//        Log.d(TAG, "Volume Down        : " + data.vol_D);
//        Log.d(TAG, "Volume Up          : " + data.vol_up);
//        Log.d(TAG, "Mic Down (Spare)   : " + data.MIC_D);
//        Log.d(TAG, "Mic Up   (Spare)   : " + data.MIC_up);
//        Log.d(TAG, "Fan ON             : " + data.FAN_on);
//        Log.d(TAG, "--------------------------------");
//    }
//
//    private void checkVolumeChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            Log.d(TAG, "🔊 Initial Volume Packet: vol_up=" + newPacket.vol_up + ", vol_D=" + newPacket.vol_D);
//            return;
//        }
//
//        Log.d(TAG, "🔊 Volume Check - Previous vol_up: " + previousPacket.vol_up + ", New vol_up: " + newPacket.vol_up +
//                ", Previous vol_D: " + previousPacket.vol_D + ", New vol_D: " + newPacket.vol_D + ", Thread=" + Thread.currentThread().getId());
//
//        if (previousPacket.vol_up == 0 && newPacket.vol_up == 1) {
//            Log.d(TAG, "🔼 Volume Up Pressed!");
//            adjustVolume(true);
//        } else if (newPacket.vol_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume up", e);
//            }
//        }
//
//        if (previousPacket.vol_D == 0 && newPacket.vol_D == 1) {
//            Log.d(TAG, "🔽 Volume Down Pressed!");
//            adjustVolume(false);
//        } else if (newPacket.vol_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume down", e);
//            }
//        }
//    }
//
//    private void adjustVolume(boolean increase) {
//        if (audioManager != null) {
//            long startTime = System.currentTimeMillis();
//            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
//            int minVolume = 0;
//
//            int newVolume = increase ? Math.min(currentVolume + 1, maxVolume)
//                    : Math.max(currentVolume - 1, minVolume);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "🔊 Volume Changed: " + newVolume + " (Max: " + maxVolume + ") in " + (endTime - startTime) + "ms");
//        }
//    }
//
//    private void checkMicChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            Log.d(TAG, "🎤 Initial Mic Packet: MIC_up=" + newPacket.MIC_up + ", MIC_D=" + newPacket.MIC_D);
//            return;
//        }
//
//        Log.d(TAG, "🎤 Mic Check - Previous MIC_up: " + previousPacket.MIC_up + ", New MIC_up: " + newPacket.MIC_up +
//                ", Previous MIC_D: " + previousPacket.MIC_D + ", New MIC_D: " + newPacket.MIC_D + ", Thread=" + Thread.currentThread().getId());
//
//        if (previousPacket.MIC_up == 0 && newPacket.MIC_up == 1) {
//            Log.d(TAG, "🎤 Mic Up Pressed! Increasing Mic Level");
//            adjustMicLevel(true);
//        } else if (newPacket.MIC_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic up", e);
//            }
//        }
//
//        if (previousPacket.MIC_D == 0 && newPacket.MIC_D == 1) {
//            Log.d(TAG, "🎤 Mic Down Pressed! Decreasing Mic Level");
//            adjustMicLevel(false);
//        } else if (newPacket.MIC_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic down", e);
//            }
//        }
//    }
//
//    private void adjustMicLevel(boolean increase) {
//        if (audioManager != null) {
//            long startTime = System.currentTimeMillis();
//            int currentMicLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//            int maxMicLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
//            int minMicLevel = 0;
//
//            int newMicLevel = increase ? Math.min(currentMicLevel + 1, maxMicLevel)
//                    : Math.max(currentMicLevel - 1, minMicLevel);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newMicLevel, AudioManager.FLAG_SHOW_UI);
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ") in " + (endTime - startTime) + "ms");
//        }
//    }
//
//    private void cpuOnMethod() {
//        Log.d(TAG, "🖥️ CPU ON method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
//        try {
//            long startTime = System.currentTimeMillis();
//            mLztek.setLcdBackLight(false);
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "🖥️ Screen turned OFF successfully in " + (endTime - startTime) + "ms");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn screen OFF: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
//        }
//    }
//
//    private void cpuOffMethod() {
//        Log.d(TAG, "🖥️ CPU OFF method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
//        try {
//            long startTime = System.currentTimeMillis();
//            mLztek.setLcdBackLight(true);
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "🖥️ Screen turned ON successfully in " + (endTime - startTime) + "ms");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn screen ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
//            try {
//                long startTime = System.currentTimeMillis();
//                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//                PowerManager.WakeLock wakeLock = pm.newWakeLock(
//                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
//                        "SerialPortService:ScreenOn");
//                wakeLock.acquire(10);
//                wakeLock.release();
//                long endTime = System.currentTimeMillis();
//                Log.d(TAG, "🖥️ Fallback: Screen turned ON using PowerManager in " + (endTime - startTime) + "ms");
//            } catch (Exception pmException) {
//                Log.e(TAG, "❌ Fallback PowerManager failed: " + pmException.getMessage() + " at " + System.currentTimeMillis(), pmException);
//            }
//        }
//    }
//
//    private void checkCpuChange(CPUData newPacket) {
//        if (newPacket == null) {
//            Log.e(TAG, "❌ New CPU packet is null");
//            return;
//        }
//
//        Log.d(TAG, "🖥️ Processing CPU Packet: s_CPU=" + newPacket.s_CPU + ", Thread=" + Thread.currentThread().getId());
//
//        if (previousPacket == null) {
//            Log.d(TAG, "🖥️ Initial CPU Packet: s_CPU=" + newPacket.s_CPU);
//            if (newPacket.s_CPU == 1) {
//                Log.d(TAG, "🖥️ Initial CPU Button Pressed! Calling ON method");
//                cpuOnMethod();
//            } else if (newPacket.s_CPU == 0) {
//                Log.d(TAG, "🖥️ Initial CPU Button Released! Calling OFF method");
//                cpuOffMethod();
//            }
//            return;
//        }
//
//        Log.d(TAG, "🖥️ CPU Check - Previous s_CPU: " + previousPacket.s_CPU + ", New s_CPU: " + newPacket.s_CPU);
//
//        if (previousPacket.s_CPU != newPacket.s_CPU) {
//            if (newPacket.s_CPU == 1) {
//                Log.d(TAG, "🖥️ CPU Button Pressed! Calling ON method");
//                cpuOnMethod();
//            } else if (newPacket.s_CPU == 0) {
//                Log.d(TAG, "🖥️ CPU Button Released! Calling OFF method");
//                cpuOffMethod();
//            }
//        } else {
//            Log.d(TAG, "🖥️ No CPU state change detected");
//        }
//    }
//
//    private void vitalOnMethod() {
//        Log.d(TAG, "💉 VITAL ON method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
//        try {
//            long startTime = System.currentTimeMillis();
//            GlobalVars.setVitalOn(true); // Set global variable to true
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "💉 VITAL turned ON successfully in " + (endTime - startTime) + "ms");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn VITAL ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
//        }
//    }
//
//    private void vitalOffMethod() {
//        Log.d(TAG, "💉 VITAL OFF method called at " + System.currentTimeMillis() + ", Thread=" + Thread.currentThread().getId());
//        try {
//            long startTime = System.currentTimeMillis();
//            GlobalVars.setVitalOn(false); // Set global variable to false
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "💉 VITAL turned OFF successfully in " + (endTime - startTime) + "ms");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn VITAL OFF: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
//        }
//    }
//
//    private void checkVitalChange(CPUData newPacket) {
//        if (newPacket == null) {
//            Log.e(TAG, "❌ New VITAL packet is null");
//            return;
//        }
//
//        Log.d(TAG, "💉 Processing VITAL Packet: VITAL=" + newPacket.VITAL + ", Thread=" + Thread.currentThread().getId());
//
//        if (previousPacket == null) {
//            Log.d(TAG, "💉 Initial VITAL Packet: VITAL=" + newPacket.VITAL);
//            if (newPacket.VITAL == 1) {
//                Log.d(TAG, "💉 Initial VITAL Button Pressed! Calling ON method");
//                vitalOnMethod();
//            } else if (newPacket.VITAL == 0) {
//                Log.d(TAG, "💉 Initial VITAL Button Released! Calling OFF method");
//                vitalOffMethod();
//            }
//            return;
//        }
//
//        Log.d(TAG, "💉 VITAL Check - Previous VITAL: " + previousPacket.VITAL + ", New VITAL: " + newPacket.VITAL);
//
//        if (previousPacket.VITAL != newPacket.VITAL) {
//            if (newPacket.VITAL == 1) {
//                Log.d(TAG, "💉 VITAL Button Pressed! Calling ON method");
//                vitalOnMethod();
//            } else if (newPacket.VITAL == 0) {
//                Log.d(TAG, "💉 VITAL Button Released! Calling OFF method");
//                vitalOffMethod();
//            }
//        } else {
//            Log.d(TAG, "💉 No VITAL state change detected");
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        isRunning = false;
//        if (mSerialPort != null) {
//            try {
//                mSerialPort.close();
//                Log.d(TAG, "✅ Serial Port Closed");
//            } catch (Exception e) {
//                Log.e(TAG, "❌ Error closing serial port", e);
//            }
//        }
//        mLztek = null;
//        Log.d(TAG, "✅ Service Destroyed");
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    public static class CPUData {
//        public long header, Battery_percentage, charging_status, s_CPU, VITAL, SPK, LIGHT;
//        public long backlight, ONE, TWO, vol_D, vol_up, MIC_D, MIC_up, FAN_on;
//
//        public CPUData(byte[] buffer) {
//            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
//            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
//            header = getUnsignedInt(byteBuffer, 0);
//            Battery_percentage = getUnsignedInt(byteBuffer, 4);
//            charging_status = getUnsignedInt(byteBuffer, 8);
//            s_CPU = getUnsignedInt(byteBuffer, 12);
//            VITAL = getUnsignedInt(byteBuffer, 16);
//            SPK = getUnsignedInt(byteBuffer, 20);
//            LIGHT = getUnsignedInt(byteBuffer, 24);
//            backlight = getUnsignedInt(byteBuffer, 28);
//            ONE = getUnsignedInt(byteBuffer, 32);
//            TWO = getUnsignedInt(byteBuffer, 36);
//            vol_D = getUnsignedInt(byteBuffer, 40);
//            vol_up = getUnsignedInt(byteBuffer, 44);
//            MIC_D = getUnsignedInt(byteBuffer, 48);
//            MIC_up = getUnsignedInt(byteBuffer, 52);
//            FAN_on = getUnsignedInt(byteBuffer, 56);
//        }
//
//        private static long getUnsignedInt(ByteBuffer buffer, int offset) {
//            return ((long) buffer.getInt(offset)) & 0xFFFFFFFFL;
//        }
//    }
//
//    @TargetApi(Build.VERSION_CODES.O)
//    private void startForegroundService() {
//        String CHANNEL_ID = "SerialPortServiceChannel";
//        NotificationChannel channel = new NotificationChannel(
//                CHANNEL_ID, "Serial Port Service", NotificationManager.IMPORTANCE_LOW);
//        NotificationManager manager = getSystemService(NotificationManager.class);
//        if (manager != null) {
//            manager.createNotificationChannel(channel);
//        }
//
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("SerialPortService Running")
//                .setSmallIcon(R.drawable.ic_patient)
//                .build();
//
//        startForeground(1, notification);
//    }
//}

//package com.lztek.api.demo;
//
//import android.annotation.TargetApi;
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.media.AudioManager;
//import android.os.Build;
//import android.os.IBinder;
//import android.os.PowerManager;
//import android.util.Log;
//
//import androidx.core.app.NotificationCompat;
//
//import com.lztek.toolkit.Lztek;
//import com.lztek.toolkit.SerialPort;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//
//public class SerialPortService extends Service {
//    private static final String TAG = "SerialPortService";
//    private static final String SERIAL_PORT_PATH = "/dev/ttyS4";
//    private static final int BAUD_RATE = 115200;
//    private static final int STRUCT_SIZE = 60;
//
//    private SerialPort mSerialPort;
//    private OutputStream mOutputStream;
//    private boolean isRunning = false;
//    private AudioManager audioManager;
//    private Lztek mLztek;
//    private CPUData previousPacket = null;
//    private boolean isCpuOn = false;
//    private boolean isVitalsOn = false;
//    private boolean isSpeakerOn = false;
//    private boolean isLightOn = false;
//    private final Object packetLock = new Object(); // For thread safety
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        mLztek = Lztek.create(this);
//
//        try {
//            mSerialPort = com.lztek.toolkit.Lztek.create(this)
//                    .openSerialPort(SERIAL_PORT_PATH, BAUD_RATE, 8, 0, 1, 0);
//            InputStream inputStream = mSerialPort.getInputStream();
//            mOutputStream = mSerialPort.getOutputStream();
//            if (mOutputStream == null) {
//                Log.e(TAG, "❌ OutputStream initialization failed");
//            } else {
//                Log.d(TAG, "✅ OutputStream initialized successfully");
//            }
//            isRunning = true;
//            new Thread(() -> readSerialData(inputStream)).start();
//            Log.d(TAG, "✅ Serial Port Opened Successfully");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Error opening serial port", e);
//            stopSelf();
//        }
//
//        startForegroundService();
//    }
//
//    private void readSerialData(InputStream inputStream) {
//        byte[] buffer = new byte[STRUCT_SIZE];
//        int bytesRead;
//
//        while (isRunning) {
//            try {
//                if (inputStream != null) {
//                    int availableBytes = inputStream.available();
//                    if (availableBytes < STRUCT_SIZE) {
//                        Thread.sleep(50);
//                        continue;
//                    }
//
//                    bytesRead = inputStream.read(buffer);
//                    if (bytesRead == STRUCT_SIZE) {
//                        CPUData newPacket = new CPUData(buffer);
//
//                        // Process packet in synchronized block
//                        synchronized (packetLock) {
//                            // Detailed logging
//                            printCPUData(newPacket, buffer);
//
//                            checkVolumeChange(newPacket);
//                            checkMicChange(newPacket);
//                            checkCpuChange(newPacket);
//
//                            Intent intent = new Intent("com.lztek.api.demo.STATUS_UPDATE");
//                            intent.putExtra("battery_percentage", newPacket.Battery_percentage);
//                            intent.putExtra("charging_status", newPacket.charging_status);
//                            intent.putExtra("cpu_status", newPacket.s_CPU);
//                            sendBroadcast(intent);
//
//                            // Update previousPacket at the end
//                            previousPacket = newPacket;
//                        }
//                    } else {
//                        Log.w(TAG, "⚠️ Partial packet received: " + bytesRead + " bytes");
//                    }
//                }
//                Thread.sleep(50);
//            } catch (IOException | InterruptedException e) {
//                Log.e(TAG, "❌ Error reading from serial port", e);
//            }
//        }
//    }
//
//    private void printCPUData(CPUData data, byte[] rawBuffer) {
//        StringBuilder rawBytes = new StringBuilder();
//        for (byte b : rawBuffer) {
//            rawBytes.append(String.format("%02X ", b));
//        }
//        Log.d(TAG, "📜 Raw Data Bytes: " + rawBytes.toString());
//
//        Log.d(TAG, "----------- CPU Data -----------");
//        Log.d(TAG, "Header             : " + data.header);
//        Log.d(TAG, "Battery %          : " + data.Battery_percentage);
//        Log.d(TAG, "Charging Status    : " + data.charging_status);
//        Log.d(TAG, "CPU                : " + data.s_CPU);
//        Log.d(TAG, "VITAL              : " + data.VITAL);
//        Log.d(TAG, "SPK                : " + data.SPK);
//        Log.d(TAG, "LIGHT              : " + data.LIGHT);
//        Log.d(TAG, "Backlight          : " + data.backlight);
//        Log.d(TAG, "Spare ONE          : " + data.ONE);
//        Log.d(TAG, "Spare TWO          : " + data.TWO);
//        Log.d(TAG, "Volume Down        : " + data.vol_D);
//        Log.d(TAG, "Volume Up          : " + data.vol_up);
//        Log.d(TAG, "Mic Down (Spare)   : " + data.MIC_D);
//        Log.d(TAG, "Mic Up   (Spare)   : " + data.MIC_up);
//        Log.d(TAG, "Fan ON             : " + data.FAN_on);
//        Log.d(TAG, "--------------------------------");
//    }
//
//    private void checkVolumeChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            Log.d(TAG, "🔊 Initial Volume Packet: vol_up=" + newPacket.vol_up + ", vol_D=" + newPacket.vol_D);
//            return;
//        }
//
//        Log.d(TAG, "🔊 Volume Check - Previous vol_up: " + previousPacket.vol_up + ", New vol_up: " + newPacket.vol_up +
//                ", Previous vol_D: " + previousPacket.vol_D + ", New vol_D: " + newPacket.vol_D);
//
//        if (previousPacket.vol_up == 0 && newPacket.vol_up == 1) {
//            Log.d(TAG, "🔼 Volume Up Pressed!");
//            adjustVolume(true);
//        } else if (newPacket.vol_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume up", e);
//            }
//        }
//
//        if (previousPacket.vol_D == 0 && newPacket.vol_D == 1) {
//            Log.d(TAG, "🔽 Volume Down Pressed!");
//            adjustVolume(false);
//        } else if (newPacket.vol_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume down", e);
//            }
//        }
//    }
//
//    private void adjustVolume(boolean increase) {
//        if (audioManager != null) {
//            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
//            int minVolume = 0;
//
//            int newVolume = increase ? Math.min(currentVolume + 1, maxVolume)
//                    : Math.max(currentVolume - 1, minVolume);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
//            Log.d(TAG, "🔊 Volume Changed: " + newVolume + " (Max: " + maxVolume + ")");
//        }
//    }
//
//    private void checkMicChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            Log.d(TAG, "🎤 Initial Mic Packet: MIC_up=" + newPacket.MIC_up + ", MIC_D=" + newPacket.MIC_D);
//            return;
//        }
//
//        Log.d(TAG, "🎤 Mic Check - Previous MIC_up: " + previousPacket.MIC_up + ", New MIC_up: " + newPacket.MIC_up +
//                ", Previous MIC_D: " + previousPacket.MIC_D + ", New MIC_D: " + newPacket.MIC_D);
//
//        if (previousPacket.MIC_up == 0 && newPacket.MIC_up == 1) {
//            Log.d(TAG, "🎤 Mic Up Pressed! Increasing Mic Level");
//            adjustMicLevel(true);
//        } else if (newPacket.MIC_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic up", e);
//            }
//        }
//
//        if (previousPacket.MIC_D == 0 && newPacket.MIC_D == 1) {
//            Log.d(TAG, "🎤 Mic Down Pressed! Decreasing Mic Level");
//            adjustMicLevel(false);
//        } else if (newPacket.MIC_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic down", e);
//            }
//        }
//    }
//
//    private void adjustMicLevel(boolean increase) {
//        // Commented out due to MODIFY_PHONE_STATE permission issue
//
//        if (audioManager != null) {
//            int currentMicLevel = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
//            int maxMicLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
//            int minMicLevel = 0;
//
//            int newMicLevel = increase ? Math.min(currentMicLevel + 1, maxMicLevel)
//                    : Math.max(currentMicLevel - 1, minMicLevel);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newMicLevel, AudioManager.FLAG_SHOW_UI);
//            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ")");
//        }
//
//    }
//
//    private void cpuOnMethod() {
//        Log.d(TAG, "🖥️ CPU ON method called");
//        try {
//            mLztek.setLcdBackLight(false);
//            Log.d(TAG, "🖥️ Screen turned OFF");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn screen OFF", e);
//        }
//    }
//
//    private void cpuOffMethod() {
//        Log.d(TAG, "🖥️ CPU OFF method called at " + System.currentTimeMillis());
//        try {
//            long startTime = System.currentTimeMillis();
//            mLztek.setLcdBackLight(true);
//            long endTime = System.currentTimeMillis();
//            Log.d(TAG, "🖥️ Screen turned ON successfully in " + (endTime - startTime) + "ms");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Failed to turn screen ON: " + e.getMessage() + " at " + System.currentTimeMillis(), e);
//            // Fallback: PowerManager
//            try {
//                long startTime = System.currentTimeMillis();
//                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//                PowerManager.WakeLock wakeLock = pm.newWakeLock(
//                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
//                        "SerialPortService:ScreenOn");
//                wakeLock.acquire(100); // Reduced to 500ms
//                wakeLock.release();
//                long endTime = System.currentTimeMillis();
//                Log.d(TAG, "🖥️ Fallback: Screen turned ON using PowerManager in " + (endTime - startTime) + "ms");
//            } catch (Exception pmException) {
//                Log.e(TAG, "❌ Fallback PowerManager failed: " + pmException.getMessage() + " at " + System.currentTimeMillis(), pmException);
//            }
//        }
//    }
//
//    private void checkCpuChange(CPUData newPacket) {
//        if (newPacket == null) {
//            Log.e(TAG, "❌ New CPU packet is null");
//            return;
//        }
//
//        // Log current packet state with thread ID
//        Log.d(TAG, "🖥️ Processing CPU Packet: s_CPU=" + newPacket.s_CPU + ", Thread=" + Thread.currentThread().getId());
//
//        if (previousPacket == null) {
//            Log.d(TAG, "🖥️ Initial CPU Packet: s_CPU=" + newPacket.s_CPU);
//            if (newPacket.s_CPU == 1) {
//                Log.d(TAG, "🖥️ Initial CPU Button Pressed! Calling ON method");
//                cpuOnMethod();
//            } else if (newPacket.s_CPU == 0) {
//                Log.d(TAG, "🖥️ Initial CPU Button Released! Calling OFF method");
//                cpuOffMethod();
//            }
//            return;
//        }
//
//        // Debug log to verify states
//        Log.d(TAG, "🖥️ CPU Check - Previous s_CPU: " + previousPacket.s_CPU + ", New s_CPU: " + newPacket.s_CPU);
//
//        // Handle transitions
//        if (previousPacket.s_CPU != newPacket.s_CPU) {
//            if (newPacket.s_CPU == 1) {
//                Log.d(TAG, "🖥️ CPU Button Pressed! Calling ON method");
//                cpuOnMethod();
//            } else if (newPacket.s_CPU == 0) {
//                Log.d(TAG, "🖥️ CPU Button Released! Calling OFF method");
//                cpuOffMethod();
//            }
//        } else {
//            Log.d(TAG, "🖥️ No CPU state change detected");
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        isRunning = false;
//        if (mSerialPort != null) {
//            try {
//                mSerialPort.close();
//                Log.d(TAG, "✅ Serial Port Closed");
//            } catch (Exception e) {
//                Log.e(TAG, "❌ Error closing serial port", e);
//            }
//        }
//        Log.d(TAG, "✅ Service Destroyed");
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    public static class CPUData {
//        public long header, Battery_percentage, charging_status, s_CPU, VITAL, SPK, LIGHT;
//        public long backlight, ONE, TWO, vol_D, vol_up, MIC_D, MIC_up, FAN_on;
//
//        public CPUData(byte[] buffer) {
//            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
//            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
//            header = getUnsignedInt(byteBuffer, 0);
//            Battery_percentage = getUnsignedInt(byteBuffer, 4);
//            charging_status = getUnsignedInt(byteBuffer, 8);
//            s_CPU = getUnsignedInt(byteBuffer, 12);
//            VITAL = getUnsignedInt(byteBuffer, 16);
//            SPK = getUnsignedInt(byteBuffer, 20);
//            LIGHT = getUnsignedInt(byteBuffer, 24);
//            backlight = getUnsignedInt(byteBuffer, 28);
//            ONE = getUnsignedInt(byteBuffer, 32);
//            TWO = getUnsignedInt(byteBuffer, 36);
//            vol_D = getUnsignedInt(byteBuffer, 40);
//            vol_up = getUnsignedInt(byteBuffer, 44);
//            MIC_D = getUnsignedInt(byteBuffer, 48);
//            MIC_up = getUnsignedInt(byteBuffer, 52);
//            FAN_on = getUnsignedInt(byteBuffer, 56);
//        }
//
//        private static long getUnsignedInt(ByteBuffer buffer, int offset) {
//            return ((long) buffer.getInt(offset)) & 0xFFFFFFFFL;
//        }
//    }
//
//    @TargetApi(Build.VERSION_CODES.O)
//    private void startForegroundService() {
//        String CHANNEL_ID = "SerialPortServiceChannel";
//        NotificationChannel channel = new NotificationChannel(
//                CHANNEL_ID, "Serial Port Service", NotificationManager.IMPORTANCE_LOW);
//        NotificationManager manager = getSystemService(NotificationManager.class);
//        if (manager != null) {
//            manager.createNotificationChannel(channel);
//        }
//
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("SerialPortService Running")
//                .setSmallIcon(R.drawable.ic_patient)
//                .build();
//
//        startForeground(1, notification);
//    }
//}

//package com.lztek.api.demo;
//
//import android.annotation.TargetApi;
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.media.AudioManager;
//import android.os.Build;
//import android.os.IBinder;
//import android.util.Log;
//
//import androidx.core.app.NotificationCompat;
//
//import com.lztek.toolkit.SerialPort;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//
//public class SerialPortService extends Service {
//    private static final String TAG = "SerialPortService";
//    private static final String SERIAL_PORT_PATH = "/dev/ttyS4";
//    private static final int BAUD_RATE = 115200;
//    private static final int STRUCT_SIZE = 60;
//
//    private SerialPort mSerialPort;
//    private OutputStream mOutputStream;
//    private boolean isRunning = false;
//    private AudioManager audioManager;
//    private CPUData previousPacket = null;
//    private boolean isCpuOn = false;
//    private boolean isVitalsOn = false;
//    private boolean isSpeakerOn = false;
//    private boolean isLightOn = false;
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//
//        try {
//            mSerialPort = com.lztek.toolkit.Lztek.create(this)
//                    .openSerialPort(SERIAL_PORT_PATH, BAUD_RATE, 8, 0, 1, 0);
//            InputStream inputStream = mSerialPort.getInputStream();
//            mOutputStream = mSerialPort.getOutputStream();
//            if (mOutputStream == null) {
//                Log.e(TAG, "❌ OutputStream initialization failed");
//            } else {
//                Log.d(TAG, "✅ OutputStream initialized successfully");
//            }
//            isRunning = true;
//            new Thread(() -> readSerialData(inputStream)).start();
//            Log.d(TAG, "✅ Serial Port Opened Successfully");
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Error opening serial port", e);
//            stopSelf();
//        }
//
//        startForegroundService();
//    }
//
//    private void readSerialData(InputStream inputStream) {
//        byte[] buffer = new byte[STRUCT_SIZE];
//        int bytesRead;
//
//        while (isRunning) {
//            try {
//                if (inputStream != null) {
//                    int availableBytes = inputStream.available();
//                    if (availableBytes < STRUCT_SIZE) {
//                        Thread.sleep(50);
//                        continue;
//                    }
//
//                    bytesRead = inputStream.read(buffer);
//                    if (bytesRead == STRUCT_SIZE) {
//                        CPUData newPacket = new CPUData(buffer);
//
//                        // ✅ Detailed logging of CPUData fields
//                        printCPUData(newPacket, buffer);
//
//                        checkVolumeChange(newPacket);
//                        checkMicChange(newPacket);
//
//                        Intent intent = new Intent("com.lztek.api.demo.STATUS_UPDATE");
//                        intent.putExtra("battery_percentage", newPacket.Battery_percentage);
//                        intent.putExtra("charging_status", newPacket.charging_status);
//                        sendBroadcast(intent);
//                    } else {
//                        Log.w(TAG, "⚠️ Partial packet received: " + bytesRead + " bytes");
//                    }
//                }
//                Thread.sleep(50);
//            } catch (IOException | InterruptedException e) {
//                Log.e(TAG, "❌ Error reading from serial port", e);
//            }
//        }
//    }
//
//    // ✅ New method to print CPUData fields like .NET's PrintCPUData
//    private void printCPUData(CPUData data, byte[] rawBuffer) {
//        // Log raw bytes for extra debugging
//        StringBuilder rawBytes = new StringBuilder();
//        for (byte b : rawBuffer) {
//            rawBytes.append(String.format("%02X ", b));
//        }
//        Log.d(TAG, "📜 Raw Data Bytes: " + rawBytes.toString());
//
//        // Log structured CPUData fields
//        Log.d(TAG, "----------- CPU Data -----------");
//        Log.d(TAG, "Header             : " + data.header);
//        Log.d(TAG, "Battery %          : " + data.Battery_percentage);
//        Log.d(TAG, "Charging Status    : " + data.charging_status);
//        Log.d(TAG, "CPU                : " + data.s_CPU);
//        Log.d(TAG, "VITAL              : " + data.VITAL);
//        Log.d(TAG, "SPK                : " + data.SPK);
//        Log.d(TAG, "LIGHT              : " + data.LIGHT);
//        Log.d(TAG, "Backlight          : " + data.backlight);
//        Log.d(TAG, "Spare ONE          : " + data.ONE);
//        Log.d(TAG, "Spare TWO          : " + data.TWO);
//        Log.d(TAG, "Volume Down        : " + data.vol_D);
//        Log.d(TAG, "Volume Up          : " + data.vol_up);
//        Log.d(TAG, "Mic Down (Spare)   : " + data.MIC_D);
//        Log.d(TAG, "Mic Up   (Spare)   : " + data.MIC_up);
//        Log.d(TAG, "Fan ON             : " + data.FAN_on);
//        Log.d(TAG, "--------------------------------");
//    }
//
//    private void checkVolumeChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        if (previousPacket.vol_up == 0 && newPacket.vol_up == 1) {
//            Log.d(TAG, "🔼 Volume Up Pressed!");
//            adjustVolume(true);
//        } else if (newPacket.vol_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume up", e);
//            }
//        }
//
//        if (previousPacket.vol_D == 0 && newPacket.vol_D == 1) {
//            Log.d(TAG, "🔽 Volume Down Pressed!");
//            adjustVolume(false);
//        } else if (newPacket.vol_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustVolume(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in volume down", e);
//            }
//        }
//
//        previousPacket = newPacket;
//    }
//
//    private void adjustVolume(boolean increase) {
//        if (audioManager != null) {
//            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
//            int minVolume = 0;
//
//            int newVolume = increase ? Math.min(currentVolume + 1, maxVolume)
//                    : Math.max(currentVolume - 1, minVolume);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
//            Log.d(TAG, "🔊 Volume Changed: " + newVolume + " (Max: " + maxVolume + ")");
//        }
//    }
//
//    private void checkMicChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        if (previousPacket.MIC_up == 0 && newPacket.MIC_up == 1) {
//            Log.d(TAG, "🎤 Mic Up Pressed! Increasing Mic Level");
//            adjustMicLevel(true);
//        } else if (newPacket.MIC_up == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(true);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic up", e);
//            }
//        }
//
//        if (previousPacket.MIC_D == 0 && newPacket.MIC_D == 1) {
//            Log.d(TAG, "🎤 Mic Down Pressed! Decreasing Mic Level");
//            adjustMicLevel(false);
//        } else if (newPacket.MIC_D == 1) {
//            try {
//                Thread.sleep(200);
//                adjustMicLevel(false);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "❌ Sleep interrupted in mic down", e);
//            }
//        }
//
//        previousPacket = newPacket;
//    }
//
//    private void adjustMicLevel(boolean increase) {
//        if (audioManager != null) {
//            int currentMicLevel = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
//            int maxMicLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
//            int minMicLevel = 0;
//
//            // ✅ Fixed: Correct logic for mic level
//            int newMicLevel = increase ? Math.min(currentMicLevel + 1, maxMicLevel) // MIC_up pe badhao
//                    : Math.max(currentMicLevel - 1, minMicLevel); // MIC_D pe ghatao
//
//            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newMicLevel, AudioManager.FLAG_SHOW_UI);
//            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ")");
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        isRunning = false;
//        if (mSerialPort != null) {
//            try {
//                mSerialPort.close();
//                Log.d(TAG, "✅ Serial Port Closed");
//            } catch (Exception e) {
//                Log.e(TAG, "❌ Error closing serial port", e);
//            }
//        }
//        Log.d(TAG, "✅ Service Destroyed");
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    public static class CPUData {
//        public long header, Battery_percentage, charging_status, s_CPU, VITAL, SPK, LIGHT;
//        public long backlight, ONE, TWO, vol_D, vol_up, MIC_D, MIC_up, FAN_on;
//
//        public CPUData(byte[] buffer) {
//            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
//            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
//            header = getUnsignedInt(byteBuffer, 0);
//            Battery_percentage = getUnsignedInt(byteBuffer, 4);
//            charging_status = getUnsignedInt(byteBuffer, 8);
//            s_CPU = getUnsignedInt(byteBuffer, 12);
//            VITAL = getUnsignedInt(byteBuffer, 16);
//            SPK = getUnsignedInt(byteBuffer, 20);
//            LIGHT = getUnsignedInt(byteBuffer, 24);
//            backlight = getUnsignedInt(byteBuffer, 28);
//            ONE = getUnsignedInt(byteBuffer, 32);
//            TWO = getUnsignedInt(byteBuffer, 36);
//            vol_D = getUnsignedInt(byteBuffer, 40);
//            vol_up = getUnsignedInt(byteBuffer, 44);
//            MIC_D = getUnsignedInt(byteBuffer, 48);
//            MIC_up = getUnsignedInt(byteBuffer, 52);
//            FAN_on = getUnsignedInt(byteBuffer, 56);
//        }
//
//        private static long getUnsignedInt(ByteBuffer buffer, int offset) {
//            return ((long) buffer.getInt(offset)) & 0xFFFFFFFFL;
//        }
//    }
//
//    @TargetApi(Build.VERSION_CODES.O)
//    private void startForegroundService() {
//        String CHANNEL_ID = "SerialPortServiceChannel";
//        NotificationChannel channel = new NotificationChannel(
//                CHANNEL_ID, "Serial Port Service", NotificationManager.IMPORTANCE_LOW);
//        NotificationManager manager = getSystemService(NotificationManager.class);
//        if (manager != null) {
//            manager.createNotificationChannel(channel);
//        }
//
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("SerialPortService Running")
//                .setSmallIcon(R.drawable.ic_patient)
//                .build();
//
//        startForeground(1, notification);
//    }
//}

