package com.lztek.api.demo;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Default SPP UUID
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean isConnected = false;
    private BluetoothDevice lastConnectedDevice;
    private BluetoothCallback callback;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;
    private volatile boolean isListening = false;
    private final int CHUNK_SIZE = 900;
    private final int SAMPLE_RATE = 6000;
    private Handler signalHandler = new Handler(Looper.getMainLooper());

    public BluetoothService() {
        handlerThread = new HandlerThread("BluetoothThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    public interface BluetoothCallback {
        void onDataReceived(byte[] data);
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public void setCallback(BluetoothCallback callback) {
        this.callback = callback;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connectToDevice(BluetoothDevice device) {
        if (isConnected) {
            Log.d(TAG, "Already connected.");
            return;
        }

        backgroundHandler.post(() -> {
            try {
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        Log.d(TAG, "Close old socket failed: " + e.getMessage());
                    }
                }

                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID); // Using default SPP UUID
                Log.d(TAG, "Attempting connection with device: " + device.getAddress() + " using SPP UUID");
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
                isConnected = true;
                lastConnectedDevice = device;

                // Force disable power save mode with fallback
                if (!disablePowerSaveMode()) {
                    Log.d(TAG, "Falling back to manual power mode adjustment");
                    adjustBluetoothPowerManually();
                }
                sendAudioConfiguration();
                monitorSignalStrength();
                if (callback != null) callback.onConnected();
            } catch (IOException e) {
                Log.d(TAG, "Connect failed: " + e.getMessage());
                cleanupConnection();
                if (callback != null) callback.onError("Connect failed: " + e.getMessage());
                retryConnection(device);
            }
        });
    }

    private boolean disablePowerSaveMode() {
        try {
            Method setPowerMode = bluetoothSocket.getClass().getMethod("setPowerMode", int.class);
            setPowerMode.invoke(bluetoothSocket, 0); // 0 = Active mode
            Log.d(TAG, "Disabled power save mode successfully");
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Failed to disable power save mode: " + e.getMessage());
            return false;
        }
    }

    private void adjustBluetoothPowerManually() {
        try {
            Method setScanMode = bluetoothAdapter.getClass().getMethod("setScanMode", int.class, int.class);
            setScanMode.invoke(bluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0);
            Log.d(TAG, "Manual power mode adjustment applied");
        } catch (Exception e) {
            Log.d(TAG, "Manual power adjustment failed: " + e.getMessage());
        }
    }

    private void sendAudioConfiguration() {
        try {
            sendCommand("SRAT-6000");
            sendCommand("GAIN-8");
            sendCommand("LSFC-280");
            Log.d(TAG, "Audio config sent: SRAT-16000, GAIN-8, LSFC-280");
        } catch (Exception e) {
            Log.d(TAG, "Config failed: " + e.getMessage());
        }
    }



    private void monitorSignalStrength() {
        signalHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnected) return;
                try {
                    BluetoothDevice device = bluetoothSocket.getRemoteDevice();
                    Method getRssi = device.getClass().getMethod("readRssi");
                    int rssi = (int) getRssi.invoke(device);
                    Log.d(TAG, "RSSI: " + rssi);
                    if (rssi < -60) {
                        Log.d(TAG, "Weak signal (RSSI: " + rssi + "), reconnecting...");
                        reconnect();
                    }
                    signalHandler.postDelayed(this, 500);
                } catch (Exception e) {
                    Log.d(TAG, "Signal error: " + e.getMessage());
                }
            }
        }, 500);
    }

    private void reconnect() {
        backgroundHandler.post(() -> {
            try {
                cleanupConnection();
                if (lastConnectedDevice != null) {
                    connectToDevice(lastConnectedDevice);
                }
            } catch (Exception e) {
                Log.d(TAG, "Reconnect failed: " + e.getMessage());
            }
        });
    }

    private void retryConnection(BluetoothDevice device) {
        backgroundHandler.postDelayed(() -> {
            if (!isConnected && device != null) {
                Log.d(TAG, "Retrying connection... Attempt 1");
                try {
                    connectToDevice(device);
                } catch (Exception e) {
                    Log.d(TAG, "Retry 1 failed: " + e.getMessage());
                    backgroundHandler.postDelayed(() -> {
                        Log.d(TAG, "Retrying connection... Attempt 2");
                        connectToDevice(device);
                    }, 4000);
                }
            }
        }, 3000);
    }


    public void startListening() {
        if (!isListening && isConnected) {
            isListening = true;
            backgroundHandler.post(() -> {
                try {
                    sendCommand("START");
                    Log.d(TAG, "Sent START");

                    int bufferSize = AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                    );
                    audioTrack.setVolume(1.0f);
                    audioTrack.play();
                    isPlaying = true;

                    byte[] buffer = new byte[CHUNK_SIZE];

                    while (isConnected && isListening) {
                        int bytesRead;
                        try {
                            bytesRead = inputStream.read(buffer, 0, buffer.length);
                            if (bytesRead == -1) {
                                Log.d(TAG, "Socket closed");
                                cleanupConnection();
                                break;
                            }
                            if (bytesRead > 0) {
                                // GRAPH update
                                if (callback != null) {
                                    byte[] actualData = new byte[bytesRead];
                                    System.arraycopy(buffer, 0, actualData, 0, bytesRead);
                                    callback.onDataReceived(actualData);
                                }

                                // AUDIO play
                                audioTrack.write(buffer, 0, bytesRead);
                            }
                        } catch (IOException e) {
                            Log.d(TAG, "Read error: " + e.getMessage());
                            if (!isConnected) break;
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Listen failed: " + e.getMessage());
                    cleanupConnection();
                } finally {
                    stopAudioPlayback();
                }
            });
        }
    }



    public void stopListening() {
        isListening = false;
        backgroundHandler.post(() -> {
            try {
                sendCommand("STOP");
                Log.d(TAG, "Sent STOP");
            } catch (Exception e) {
                Log.d(TAG, "Stop failed: " + e.getMessage());
            }
        });
    }

    private void stopAudioPlayback() {
        try {
            isPlaying = false;
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception e) {
            Log.d(TAG, "Stop audio failed: " + e.getMessage());
        }
    }

    public void startRecording() {
        backgroundHandler.post(() -> {
            try {
                if (inputStream != null) {
                    while (inputStream.available() > 0) {
                        inputStream.read(new byte[CHUNK_SIZE]);
                    }
                }
                sendCommand("START");
                Log.d(TAG, "Recording started");
            } catch (Exception e) {
                Log.d(TAG, "Record start failed: " + e.getMessage());
            }
        });
    }

    public void stopRecording() {
        backgroundHandler.post(this::sendStopCommand);
    }

    private void sendStopCommand() {
        try {
            if (outputStream != null) {
                outputStream.write("STOP".getBytes());
                outputStream.flush();
                Log.d(TAG, "Sent STOP");
            }
        } catch (IOException e) {
            Log.d(TAG, "Stop failed: " + e.getMessage());
        }
    }

    public void sendCommand(String command) {
        backgroundHandler.post(() -> {
            try {
                if (isConnected && outputStream != null) {
                    outputStream.write(command.getBytes());
                    outputStream.flush();
                    Log.d(TAG, "Command: " + command);
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                Log.d(TAG, "Command failed: " + e.getMessage());
                if (callback != null) callback.onError("Command failed: " + command);
            }
        });
    }

    private void cleanupConnection() {
        try {
            isConnected = false;
            isListening = false;
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            stopAudioPlayback();
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onDisconnected());
            }
            Log.d(TAG, "Cleaned up");
        } catch (Exception e) {
            Log.d(TAG, "Cleanup failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        backgroundHandler.post(this::cleanupConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BluetoothBinder();
    }

    public class BluetoothBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            cleanupConnection();
            if (handlerThread != null && handlerThread.isAlive()) {
                handlerThread.quitSafely();
                handlerThread.join();
                handlerThread = null;
                backgroundHandler = null;
            }
        } catch (Exception e) {
            Log.d(TAG, "Destroy failed: " + e.getMessage());
        }
    }
}


//package com.lztek.api.demo;
//
//import android.app.Service;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothSocket;
//import android.content.Intent;
//import android.media.AudioFormat;
//import android.media.AudioManager;
//import android.media.AudioTrack;
//import android.os.Binder;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.IBinder;
//import android.os.Looper;
//import android.util.Log;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.UUID;
//
//public class BluetoothService extends Service {
//    private static final String TAG = "BluetoothService";
//    private static final UUID CHESSO_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//    private BluetoothSocket bluetoothSocket;
//    private InputStream inputStream;
//    private OutputStream outputStream;
//    private volatile boolean isConnected = false;
//    private BluetoothDevice lastConnectedDevice;
//    private BluetoothCallback callback;
//    private HandlerThread handlerThread;
//    private Handler backgroundHandler;
//    private AudioTrack audioTrack;
//    private volatile boolean isPlaying = false;
//    private final int CHUNK_SIZE = 512;
//    private final int SAMPLE_RATE = 16000;
//
//    public BluetoothService() {
//        handlerThread = new HandlerThread("BluetoothThread");
//        handlerThread.start();
//        backgroundHandler = new Handler(handlerThread.getLooper());
//    }
//
//    public interface BluetoothCallback {
//        void onDataReceived(byte[] data);
//        void onConnected();
//        void onDisconnected();
//        void onError(String error);
//    }
//
//    public void setCallback(BluetoothCallback callback) {
//        this.callback = callback;
//    }
//
//    public boolean isConnected() {
//        return isConnected;
//    }
//
//    public void connectToDevice(BluetoothDevice device) {
//        if (isConnected) {
//            Log.d(TAG, "Already connected.");
//            return;
//        }
//
//        backgroundHandler.post(() -> {
//            try {
//                if (bluetoothSocket != null) {
//                    try {
//                        bluetoothSocket.close();
//                    } catch (IOException e) {
//                        Log.d(TAG, "Close old socket failed: " + e.getMessage());
//                    }
//                }
//
//                bluetoothSocket = device.createRfcommSocketToServiceRecord(CHESSO_UUID);
//                bluetoothSocket.connect();
//                inputStream = bluetoothSocket.getInputStream();
//                outputStream = bluetoothSocket.getOutputStream();
//                isConnected = true;
//                lastConnectedDevice = device;
//
//                sendAudioConfiguration();
//                if (callback != null) callback.onConnected();
//            } catch (IOException e) {
//                Log.d(TAG, "Connect failed: " + e.getMessage());
//                cleanupConnection();
//                if (callback != null) callback.onError("Connect failed: " + e.getMessage());
//            }
//        });
//    }
//
//    private void sendAudioConfiguration() {
//        try {
//            sendCommand("SRAT-16000");
//            sendCommand("GAIN-4");
//            sendCommand("LSFC-280");
//            Log.d(TAG, "Audio config sent");
//        } catch (Exception e) {
//            Log.d(TAG, "Config failed: " + e.getMessage());
//        }
//    }
//
//    public void listenForData() {
//        backgroundHandler.post(() -> {
//            try {
//                sendCommand("START");
//                Log.d(TAG, "Sent START");
//                int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
//                        AudioFormat.CHANNEL_OUT_MONO,
//                        AudioFormat.ENCODING_PCM_16BIT);
//                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
//                        SAMPLE_RATE,
//                        AudioFormat.CHANNEL_OUT_MONO,
//                        AudioFormat.ENCODING_PCM_16BIT,
//                        bufferSize,
//                        AudioTrack.MODE_STREAM);
//                audioTrack.setVolume(0.8f);
//                audioTrack.play();
//                isPlaying = true;
//
//                byte[] buffer = new byte[CHUNK_SIZE];
//                int bytesRead;
//
//                while (isConnected) {
//                    try {
//                        bytesRead = inputStream.read(buffer);
//                        Log.d(TAG, "Bytes read: " + bytesRead);
//                        if (bytesRead == -1) {
//                            Log.d(TAG, "Socket closed");
//                            cleanupConnection();
//                            return;
//                        }
//                        if (bytesRead > 0) {
//                            byte[] actualData = new byte[bytesRead];
//                            System.arraycopy(buffer, 0, actualData, 0, bytesRead);
//                            if (callback != null) {
//                                callback.onDataReceived(actualData);
//                            }
//                            double[] rawAudio = AudioProcessor.convertToDoubleArray(actualData);
//                            double[] fftData = AudioProcessor.applyFFT(rawAudio);
//                            boolean hasNormalSounds = AudioProcessor.containsNormalSounds(fftData);
//                            if (!hasNormalSounds) {
//                                double[] filteredData = AudioProcessor.applyMedicalBandPassFilter(fftData);
//                                boolean isValidSignal = AudioProcessor.isValidMedicalSignal(filteredData);
//                                if (isValidSignal) {
//                                    double[] timeData = AudioProcessor.applyInverseFFT(filteredData);
//                                    byte[] processedAudio = AudioProcessor.convertToByteArray(timeData);
//                                    audioTrack.write(processedAudio, 0, processedAudio.length);
//                                }
//                            }
//                        }
//                    } catch (IOException e) {
//                        Log.d(TAG, "Read error: " + e.getMessage());
//                        if (!isConnected) break;
//                    }
//                }
//            } catch (Exception e) {
//                Log.d(TAG, "Listen failed: " + e.getMessage());
//                cleanupConnection();
//            } finally {
//                stopAudioPlayback();
//            }
//        });
//    }
//
//    private void stopAudioPlayback() {
//        try {
//            isPlaying = false;
//            if (audioTrack != null) {
//                audioTrack.stop();
//                audioTrack.release();
//                audioTrack = null;
//            }
//        } catch (Exception e) {
//            Log.d(TAG, "Stop audio failed: " + e.getMessage());
//        }
//    }
//
//    public void startRecording() {
//        backgroundHandler.post(() -> {
//            try {
//                if (inputStream != null) {
//                    while (inputStream.available() > 0) {
//                        inputStream.read(new byte[512]);
//                    }
//                }
//                sendCommand("START");
//                Log.d(TAG, "Recording started");
//            } catch (Exception e) {
//                Log.d(TAG, "Record start failed: " + e.getMessage());
//            }
//        });
//    }
//
//    public void stopRecording() {
//        backgroundHandler.post(this::sendStopCommand);
//    }
//
//    private void sendStopCommand() {
//        try {
//            if (outputStream != null) {
//                outputStream.write("STOP".getBytes());
//                outputStream.flush();
//                Log.d(TAG, "Sent STOP");
//            }
//        } catch (IOException e) {
//            Log.d(TAG, "Stop failed: " + e.getMessage());
//        }
//    }
//
//    public void sendCommand(String command) {
//        try {
//            if (isConnected && outputStream != null) {
//                outputStream.write(command.getBytes());
//                outputStream.flush();
//                Log.d(TAG, "Command: " + command);
//                Thread.sleep(100);
//            }
//        } catch (Exception e) {
//            Log.d(TAG, "Command failed: " + e.getMessage());
//            if (callback != null) callback.onError("Command failed: " + command);
//        }
//    }
//
//    private void cleanupConnection() {
//        try {
//            isConnected = false;
//            if (inputStream != null) {
//                inputStream.close();
//                inputStream = null;
//            }
//            if (outputStream != null) {
//                outputStream.close();
//                outputStream = null;
//            }
//            if (bluetoothSocket != null) {
//                bluetoothSocket.close();
//                bluetoothSocket = null;
//            }
//            stopAudioPlayback();
//            if (callback != null) {
//                new Handler(Looper.getMainLooper()).post(() -> callback.onDisconnected());
//            }
//            Log.d(TAG, "Cleaned up");
//        } catch (Exception e) {
//            Log.d(TAG, "Cleanup failed: " + e.getMessage());
//        }
//    }
//
//    public void disconnect() {
//        backgroundHandler.post(this::cleanupConnection);
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return new BluetoothBinder();
//    }
//
//    public class BluetoothBinder extends Binder {
//        public BluetoothService getService() {
//            return BluetoothService.this;
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        try {
//            cleanupConnection();
//            if (handlerThread != null && handlerThread.isAlive()) {
//                handlerThread.quitSafely();
//                handlerThread.join();
//                handlerThread = null;
//                backgroundHandler = null;
//            }
//        } catch (Exception e) {
//            Log.d(TAG, "Destroy failed: " + e.getMessage());
//        }
//    }
//}