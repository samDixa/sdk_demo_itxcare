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
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

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
    private CPUData previousPacket = null;
    private boolean isCpuOn = false;
    private boolean isVitalsOn = false;
    private boolean isSpeakerOn = false;
    private boolean isLightOn = false;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        try {
            mSerialPort = com.lztek.toolkit.Lztek.create(this)
                    .openSerialPort(SERIAL_PORT_PATH, BAUD_RATE, 8, 0, 1, 0);
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
                        Log.d(TAG, String.format("Raw Data - LIGHT: %d", newPacket.LIGHT));
                        checkVolumeChange(newPacket);
                        checkMicChange(newPacket);
                        checkCpuChange(newPacket);
                        checkVitalsChange(newPacket);
                        checkSpeakerChange(newPacket);
                        checkLightChange(newPacket);

                        Intent intent = new Intent("com.lztek.api.demo.STATUS_UPDATE");
                        intent.putExtra("battery_percentage", newPacket.Battery_percentage);
                        intent.putExtra("charging_status", newPacket.charging_status);
                        sendBroadcast(intent);
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

    private void checkVolumeChange(CPUData newPacket) {
        if (previousPacket == null) {
            previousPacket = newPacket;
            return;
        }

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

        previousPacket = newPacket;
    }

    private void adjustVolume(boolean increase) {
        if (audioManager != null) {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int minVolume = 0;

            int newVolume = increase ? Math.min(currentVolume + 1, maxVolume)
                    : Math.max(currentVolume - 1, minVolume);

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
            Log.d(TAG, "🔊 Volume Changed: " + newVolume + " (Max: " + maxVolume + ")");
        }
    }

    private void checkMicChange(CPUData newPacket) {
        if (previousPacket == null) {
            previousPacket = newPacket;
            return;
        }

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

        previousPacket = newPacket;
    }

    private void adjustMicLevel(boolean increase) {
        if (audioManager != null) {
            int currentMicLevel = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            int maxMicLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int minMicLevel = 0;

            int newMicLevel = increase ? Math.max(currentMicLevel - 1, minMicLevel)
                    : Math.min(currentMicLevel + 1, maxMicLevel);

            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newMicLevel, AudioManager.FLAG_SHOW_UI);
            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ")");
        }
    }

    private void checkCpuChange(CPUData newPacket) {
        if (previousPacket == null) {
            previousPacket = newPacket;
            return;
        }
        Log.d(TAG, "🖥️ CPU State - Previous: " + previousPacket.s_CPU + ", Current: " + newPacket.s_CPU);
        if (previousPacket.s_CPU != newPacket.s_CPU) {
            Log.d(TAG, "🖥️ CPU Button Pressed! Value: " + newPacket.s_CPU);
            toggleCpu();
        }
        previousPacket = newPacket;
    }

    private void checkVitalsChange(CPUData newPacket) {
        if (previousPacket == null) {
            previousPacket = newPacket;
            return;
        }
        Log.d(TAG, "❤️ Vitals State - Previous: " + previousPacket.VITAL + ", Current: " + newPacket.VITAL);
        if (previousPacket.VITAL != newPacket.VITAL) {
            Log.d(TAG, "❤️ Vitals Button Pressed! Value: " + newPacket.VITAL);
            toggleVitals();
        }
        previousPacket = newPacket;
    }

    private void checkSpeakerChange(CPUData newPacket) {
        if (previousPacket == null) {
            previousPacket = newPacket;
            return;
        }
        Log.d(TAG, "🔊 Speaker State - Previous: " + previousPacket.SPK + ", Current: " + newPacket.SPK);
        if (previousPacket.SPK != newPacket.SPK) {
            Log.d(TAG, "🔊 Speaker Button Pressed! Value: " + newPacket.SPK);
            toggleSpeaker();
        }
        previousPacket = newPacket;
    }

    private void checkLightChange(CPUData newPacket) {
        if (previousPacket == null) {
            // Initialize light state to off by default, then sync with first packet
            isLightOn = false; // Default to off on start
            previousPacket = newPacket;
            Log.d(TAG, "💡 Initial Light State Set to OFF, Packet Value: " + newPacket.LIGHT);
            sendToggleCommand((byte) 0x04, isLightOn, "Light"); // Force off initially
            return;
        }
        Log.d(TAG, "💡 Light State - Previous: " + previousPacket.LIGHT + ", Current: " + newPacket.LIGHT);
        if (previousPacket.LIGHT != newPacket.LIGHT) {
            Log.d(TAG, "💡 Light Button Pressed! Value Changed to: " + newPacket.LIGHT);
            // Toggle only if button press is detected as a change
            isLightOn = !isLightOn; // Toggle state on button press
            sendToggleCommand((byte) 0x04, isLightOn, "Light");
        } else if (newPacket.LIGHT != 0 && isLightOn) {
            // If light is on due to hardware but should be off, force off
            Log.d(TAG, "💡 Forcing Light OFF due to hardware state mismatch");
            isLightOn = false;
            sendToggleCommand((byte) 0x04, isLightOn, "Light");
        }
        previousPacket = newPacket;
    }

    private void sendToggleCommand(byte deviceId, boolean state, String deviceName) {
        if (mOutputStream == null) {
            Log.e(TAG, "❌ OutputStream is null - Cannot control " + deviceName);
            return;
        }
        try {
            byte[] command = new byte[4];
            command[0] = deviceId;           // Device identifier (e.g., 0x04 for LIGHT)
            command[1] = (byte) (state ? 1 : 0); // ON=1, OFF=0
            command[2] = 0x00;               // Padding
            command[3] = 0x00;               // Padding

            Log.d(TAG, "📤 Sending command for " + deviceName + ": " + bytesToHex(command));
            mOutputStream.write(command);
            mOutputStream.flush();
            Thread.sleep(200); // Debouncing delay
            Log.d(TAG, "✅ Command sent for " + deviceName + ". State: " + (state ? "ON" : "OFF"));
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to send command for " + deviceName + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, "❌ Delay interrupted for " + deviceName + ": " + e.getMessage());
        }
    }

    private void toggleCpu() {
        isCpuOn = !isCpuOn;
        sendToggleCommand((byte) 0x01, isCpuOn, "CPU");
    }

    private void toggleVitals() {
        isVitalsOn = !isVitalsOn;
        sendToggleCommand((byte) 0x02, isVitalsOn, "Vitals");
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        sendToggleCommand((byte) 0x03, isSpeakerOn, "Speaker");
    }

    private void toggleLight() {
        isLightOn = !isLightOn;
        sendToggleCommand((byte) 0x04, isLightOn, "Light");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("0x%02X ", b));
        }
        return sb.toString().trim();
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
//                        checkVolumeChange(newPacket);
//                        checkMicChange(newPacket);
//                        checkCpuChange(newPacket);
//                        checkVitalsChange(newPacket);
//                        checkSpeakerChange(newPacket);
//                        checkLightChange(newPacket);
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
//            int newMicLevel = increase ? Math.max(currentMicLevel - 1, minMicLevel)
//                    : Math.min(currentMicLevel + 1, maxMicLevel);
//
//            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newMicLevel, AudioManager.FLAG_SHOW_UI);
//            Log.d(TAG, "🎤 Mic Level Changed: " + newMicLevel + " (Max: " + maxMicLevel + ")");
//        }
//    }
//
//    private void checkCpuChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        Log.d(TAG, "🖥️ CPU State - Previous: " + previousPacket.s_CPU +
//                ", Current: " + newPacket.s_CPU +
//                ", isCpuOn: " + isCpuOn);
//
//        if (previousPacket.s_CPU == 0 && newPacket.s_CPU != 0) {
//            Log.d(TAG, "🖥️ CPU Button Pressed! Detected Value: " + newPacket.s_CPU);
//            toggleCpu();
//        }
//        previousPacket = newPacket;
//    }
//
//    private void checkVitalsChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        Log.d(TAG, "❤️ Vitals State - Previous: " + previousPacket.VITAL +
//                ", Current: " + newPacket.VITAL +
//                ", isVitalsOn: " + isVitalsOn);
//
//        if (previousPacket.VITAL == 0 && newPacket.VITAL != 0) {
//            Log.d(TAG, "❤️ Vitals Button Pressed! Detected Value: " + newPacket.VITAL);
//            toggleVitals();
//        }
//        previousPacket = newPacket;
//    }
//
//    private void checkSpeakerChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        Log.d(TAG, "🔊 Speaker State - Previous: " + previousPacket.SPK +
//                ", Current: " + newPacket.SPK +
//                ", isSpeakerOn: " + isSpeakerOn);
//
//        if (previousPacket.SPK == 0 && newPacket.SPK != 0) {
//            Log.d(TAG, "🔊 Speaker Button Pressed! Detected Value: " + newPacket.SPK);
//            toggleSpeaker();
//        }
//        previousPacket = newPacket;
//    }
//
//    private void checkLightChange(CPUData newPacket) {
//        if (previousPacket == null) {
//            previousPacket = newPacket;
//            return;
//        }
//
//        Log.d(TAG, "💡 Light State - Previous: " + previousPacket.LIGHT +
//                ", Current: " + newPacket.LIGHT +
//                ", isLightOn: " + isLightOn);
//
//        if (previousPacket.LIGHT == 0 && newPacket.LIGHT != 0) {
//            Log.d(TAG, "💡 Light Button Pressed! Detected Value: " + newPacket.LIGHT);
//            toggleLight();
//        }
//        previousPacket = newPacket;
//    }
//
//    private void sendToggleCommand(byte deviceId, boolean state, String deviceName) {
//        if (mOutputStream == null) {
//            Log.e(TAG, "❌ OutputStream is null - Cannot control " + deviceName);
//            return;
//        }
//
//        try {
//            byte[] command = new byte[4];
//            command[0] = deviceId;           // Device identifier (e.g., 0x01 for CPU)
//            command[1] = (byte) (state ? 1 : 0); // ON=1, OFF=0
//            command[2] = 0x00;               // Padding
//            command[3] = 0x00;               // Padding
//
//            Log.d(TAG, "📤 Sending command for " + deviceName + ": " + bytesToHex(command));
//            mOutputStream.write(command);
//            mOutputStream.flush();
//            Thread.sleep(100); // Delay for hardware response
//            Log.d(TAG, "✅ Command sent for " + deviceName + ". State: " + (state ? "ON" : "OFF"));
//        } catch (IOException e) {
//            Log.e(TAG, "❌ Failed to send command for " + deviceName + ": " + e.getMessage());
//        } catch (InterruptedException e) {
//            Log.e(TAG, "❌ Delay interrupted for " + deviceName + ": " + e.getMessage());
//        }
//    }
//
//    private void toggleCpu() {
//        isCpuOn = !isCpuOn;
//        sendToggleCommand((byte) 0x01, isCpuOn, "CPU");
//    }
//
//    private void toggleVitals() {
//        isVitalsOn = !isVitalsOn;
//        sendToggleCommand((byte) 0x02, isVitalsOn, "Vitals");
//    }
//
//    private void toggleSpeaker() {
//        isSpeakerOn = !isSpeakerOn;
//        sendToggleCommand((byte) 0x03, isSpeakerOn, "Speaker");
//    }
//
//    private void toggleLight() {
//        isLightOn = !isLightOn;
//        sendToggleCommand((byte) 0x04, isLightOn, "Light");
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("0x%02X ", b));
//        }
//        return sb.toString().trim();
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