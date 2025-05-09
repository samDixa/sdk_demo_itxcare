package com.lztek.api.demo;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.lztek.toolkit.Lztek;
import com.lztek.toolkit.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BerrySerialPort {
    private static final String TAG = "BerrySerialPort";
    private static final String SERIAL_PORT_PATH_RX = "/dev/ttyS9"; // COM9 for RX (data input)
    private static final String SERIAL_PORT_PATH_TX = "/dev/ttyS7"; // COM7 for TX (commands)
    private static final int BAUD_RATE = 115200;
    private static final byte[] ENABLE_ECG = {0x55, (byte) 0xAA, 0x04, 0x01, 0x01, (byte) 0xF9};
    private static final byte[] ENABLE_SPO2 = {0x55, (byte) 0xAA, 0x04, 0x03, 0x01, (byte) 0xF7};
    private static final byte[] ENABLE_ECG_WAVE = {0x55, (byte) 0xAA, 0x04, (byte) 0xFB, 0x01, (byte) 0xFF};
    private static final byte[] ENABLE_SPO2_WAVE = {0x55, (byte) 0xAA, 0x04, (byte) 0xFE, 0x01, (byte) 0xFC};

    private SerialPort serialPortRx; // For receiving data
    private SerialPort serialPortTx; // For sending commands
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private final Context context;
    private OnDataReceivedListener dataListener;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;

    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data);
        void onConnectionStatusChanged(String status);
    }

    public BerrySerialPort(Context context) {
        this.context = context;
    }

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.dataListener = listener;
    }

    public void connect() {
        try {
            // Open RX port (COM9)
            serialPortRx = Lztek.create(context)
                    .openSerialPort(SERIAL_PORT_PATH_RX, BAUD_RATE, 8, 0, 1, 0);
            if (serialPortRx == null) {
                throw new IOException("Failed to initialize RX serial port on " + SERIAL_PORT_PATH_RX);
            }
            inputStream = serialPortRx.getInputStream();

            // Open TX port (COM7)
            serialPortTx = Lztek.create(context)
                    .openSerialPort(SERIAL_PORT_PATH_TX, BAUD_RATE, 8, 0, 1, 0);
            if (serialPortTx == null) {
                throw new IOException("Failed to initialize TX serial port on " + SERIAL_PORT_PATH_TX);
            }
            outputStream = serialPortTx.getOutputStream();

            isConnected = true;

            // Send initialization commands via TX (COM7)
            write(ENABLE_ECG);
            write(ENABLE_SPO2);
            write(ENABLE_ECG_WAVE);
            write(ENABLE_SPO2_WAVE);
            byte[] SET_ECG_GAIN = {0x55, (byte) 0xAA, 0x04, 0x07, 0x03, (byte) 0xF1}; // ECG x1 gain
            byte[] SET_ECG_FILTER = {0x55, (byte) 0xAA, 0x04, 0x08, 0x02, (byte) 0xF1}; // ECG monitor mode
            byte[] SET_RESP_GAIN = {0x55, (byte) 0xAA, 0x04, 0x0F, 0x03, (byte) 0xE9}; // Resp x1 gain
            write(SET_ECG_GAIN);
            write(SET_ECG_FILTER);
            write(SET_RESP_GAIN);

            // Start HandlerThread for background processing
            handlerThread = new HandlerThread("SerialReadThread");
            handlerThread.start();
            backgroundHandler = new Handler(handlerThread.getLooper());
            backgroundHandler.post(this::readSerialData);

            if (dataListener != null) {
                dataListener.onConnectionStatusChanged("Connected - RX: " + SERIAL_PORT_PATH_RX + ", TX: " + SERIAL_PORT_PATH_TX);
            }
        } catch (Exception e) {
            isConnected = false;
            if (dataListener != null) {
                dataListener.onConnectionStatusChanged("Failed to connect: " + e.getMessage());
            }
            disconnect();
        }
    }

    public void disconnect() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        if (serialPortRx != null) {
            try {
                serialPortRx.close();
            } catch (Exception e) {}
            serialPortRx = null;
        }
        if (serialPortTx != null) {
            try {
                serialPortTx.close();
            } catch (Exception e) {}
            serialPortTx = null;
        }
        isConnected = false;
        inputStream = null;
        outputStream = null;
        if (dataListener != null) {
            dataListener.onConnectionStatusChanged("Disconnected");
        }
    }

    public void write(byte[] data) {
        if (outputStream != null && isConnected) {
            try {
                outputStream.write(data);
                outputStream.flush();
            } catch (IOException e) {}
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void readSerialData() {
        byte[] buffer = new byte[128];
        while (isConnected) {
            try {
                int bytesRead = inputStream.available(); // Check available data
                if (bytesRead > 0) {
                    bytesRead = Math.min(bytesRead, buffer.length); // Limit to buffer size
                    bytesRead = inputStream.read(buffer, 0, bytesRead);
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        if (dataListener != null) {
                            new Handler(context.getMainLooper()).post(() -> dataListener.onDataReceived(data));
                        }
                    }
                } else {
                    Thread.sleep(10); // Small delay to avoid CPU overuse
                }
            } catch (IOException e) {
                if (isConnected && dataListener != null) {
                    new Handler(context.getMainLooper()).post(() -> dataListener.onConnectionStatusChanged("Read error on " + SERIAL_PORT_PATH_RX + ": " + e.getMessage()));
                }
                break;
            } catch (InterruptedException e) {
                break;
            }
        }
    }


    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}



////
////package com.lztek.api.demo;
////
////import android.content.Context;
////import android.util.Log;
////
////import com.lztek.toolkit.Lztek;
////import com.lztek.toolkit.SerialPort;
////
////import java.io.IOException;
////import java.io.InputStream;
////import java.io.OutputStream;
////
////
////public class BerrySerialPort {
////
////    private static final String TAG = "BerrySerialPort";
////    private static final String SERIAL_PORT_PATH = "/dev/ttyS7";
////    private static final int BAUD_RATE = 115200;
////    private static final byte[] ENABLE_ECG = {0x05, 0x0A, 0x04, 0x01, 0x01, (byte) 0xFA};
////    private static final byte[] ENABLE_SPO2 = {0x05, 0x0A, 0x04, 0x03, 0x01, (byte) 0xFA};
////    private static final byte[] ENABLE_ECG_WAVE = {0x05, 0x0A, 0x04, (byte) 0xFB, 0x01, (byte) 0xFF};
////    private static final byte[] ENABLE_SPO2_WAVE = {0x05, 0x0A, 0x04, (byte) 0xFE, 0x01, (byte) 0xFE};
////
////    private SerialPort serialPort;
////    private InputStream inputStream;
////    private OutputStream outputStream;
////    private boolean isConnected = false;
////    private final Context context;
////    private OnDataReceivedListener dataListener;
////    private Thread readThread;
////    private String currentPort;
////
////    public interface OnDataReceivedListener {
////        void onDataReceived(byte[] data);
////        void onConnectionStatusChanged(String status);
////    }
////
////    public BerrySerialPort(Context context) {
////        this.context = context;
////    }
////
////    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
////        this.dataListener = listener;
////        Log.d(TAG, "✅ Data listener set");
////    }
////
////    public void connect() {
////        currentPort = SERIAL_PORT_PATH;
////        Log.d(TAG, "🔄 Attempting to connect to Berry PM6750 on " + currentPort);
////        try {
////            serialPort = Lztek.create(context)
////                    .openSerialPort(currentPort, BAUD_RATE, 8, 0, 1, 0);
////            if (serialPort == null) {
////                Log.w(TAG, "⚠️ SerialPort object is null for " + currentPort);
////                throw new IOException("Failed to initialize serial port");
////            }
////            inputStream = serialPort.getInputStream();
////            outputStream = serialPort.getOutputStream();
////
////            isConnected = true;
////            Log.d(TAG, "✅ Connected to " + currentPort);
////
////            write(ENABLE_ECG);
////            write(ENABLE_SPO2);
////            write(ENABLE_ECG_WAVE);
////            write(ENABLE_SPO2_WAVE);
////            Log.d(TAG, "📤 Sent ECG enable: " + bytesToHex(ENABLE_ECG));
////            Log.d(TAG, "📤 Sent SPO2 enable: " + bytesToHex(ENABLE_SPO2));
////            Log.d(TAG, "📤 Sent ECG wave enable: " + bytesToHex(ENABLE_ECG_WAVE));
////            Log.d(TAG, "📤 Sent SPO2 wave enable: " + bytesToHex(ENABLE_SPO2_WAVE));
////
////            readThread = new Thread(this::readSerialData);
////            readThread.start();
////            Log.d(TAG, "✅ Read thread started");
////
////            if (dataListener != null) {
////                dataListener.onConnectionStatusChanged("Connected to " + currentPort);
////            }
////        } catch (Exception e) {
////            isConnected = false;
////            if (dataListener != null) {
////                dataListener.onConnectionStatusChanged("Failed to connect to " + currentPort + ": " + e.getMessage());
////            }
////            Log.e(TAG, "❌ Failed to connect to " + currentPort + ": " + e.getMessage(), e);
////        }
////    }
////
////    public void disconnect() {
////        Log.d(TAG, "🔄 Disconnecting from " + currentPort);
////        if (readThread != null) {
////            readThread.interrupt();
////            readThread = null;
////            Log.d(TAG, "✅ Read thread stopped");
////        }
////        if (serialPort != null) {
////            try {
////                serialPort.close();
////                Log.d(TAG, "✅ SerialPort closed");
////            } catch (Exception e) {
////                Log.e(TAG, "❌ Error closing serial port: " + e.getMessage(), e);
////            }
////            serialPort = null;
////        }
////        isConnected = false;
////        if (dataListener != null) {
////            dataListener.onConnectionStatusChanged("Disconnected");
////        }
////        Log.d(TAG, "✅ Serial Port Disconnected");
////    }
////
////    public void write(byte[] data) {
////        if (outputStream != null && isConnected) {
////            try {
////                outputStream.write(data);
////                outputStream.flush();
////                Log.d(TAG, "📤 Data written to " + currentPort + ": " + bytesToHex(data));
////            } catch (IOException e) {
////                Log.e(TAG, "❌ Error writing to " + currentPort + ": " + e.getMessage(), e);
////            }
////        } else {
////            Log.w(TAG, "⚠️ Cannot write: Not connected or outputStream is null");
////        }
////    }
////
////    public boolean isConnected() {
////        return isConnected;
////    }
////
////    private void readSerialData() {
////        byte[] buffer = new byte[256];
////        Log.d(TAG, "🔄 Read thread running for " + currentPort);
////        try {
////            Thread.sleep(100);
////        } catch (InterruptedException e) {
////            Log.e(TAG, "❌ Read thread initial delay interrupted", e);
////        }
////        while (isConnected && !Thread.currentThread().isInterrupted()) {
////            try {
////                int bytesRead = inputStream.read(buffer);
////                if (bytesRead > 0) {
////                    byte[] data = new byte[bytesRead];
////                    System.arraycopy(buffer, 0, data, 0, bytesRead);
////                    if (dataListener != null) {
////                        Log.d(TAG, "📥 Data received from " + currentPort + ": " + bytesToHex(data));
////                        dataListener.onDataReceived(data);
////                    } else {
////                        Log.w(TAG, "⚠️ DataListener is null");
////                    }
////                }
////                Thread.sleep(40);
////            } catch (IOException e) {
////                if (isConnected && dataListener != null) {
////                    dataListener.onConnectionStatusChanged("Read error on " + currentPort + ": " + e.getMessage());
////                }
////                Log.e(TAG, "❌ Error reading from " + currentPort + ": " + e.getMessage(), e);
////                break;
////            } catch (InterruptedException e) {
////                Log.d(TAG, "✅ Read thread interrupted");
////                break;
////            }
////        }
////    }
////
////    private String bytesToHex(byte[] bytes) {
////        StringBuilder sb = new StringBuilder();
////        for (byte b : bytes) {
////            sb.append(String.format("%02X ", b));
////        }
////        return sb.toString().trim();
////    }
////}
//
//package com.lztek.api.demo;
//
//import android.content.Context;
//import android.util.Log;
//
//import com.lztek.toolkit.Lztek;
//import com.lztek.toolkit.SerialPort;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//public class BerrySerialPort {
//
//    private static final String TAG = "BerrySerialPort";
//    private static final String SERIAL_PORT_PATH_RX = "/dev/ttyS9"; // COM9 for RX (data input)
//    private static final String SERIAL_PORT_PATH_TX = "/dev/ttyS7"; // COM7 for TX (commands)
//    private static final int BAUD_RATE = 115200;
//    private static final byte[] ENABLE_ECG = {0x55, (byte) 0xAA, 0x04, 0x01, 0x01, (byte) 0xF9};
//    private static final byte[] ENABLE_SPO2 = {0x55, (byte) 0xAA, 0x04, 0x03, 0x01, (byte) 0xF7};
//    private static final byte[] ENABLE_ECG_WAVE = {0x55, (byte) 0xAA, 0x04, (byte) 0xFB, 0x01, (byte) 0xFF};
//    private static final byte[] ENABLE_SPO2_WAVE = {0x55, (byte) 0xAA, 0x04, (byte) 0xFE, 0x01, (byte) 0xFC};
//
//    private SerialPort serialPortRx; // For receiving data
//    private SerialPort serialPortTx; // For sending commands
//    private InputStream inputStream;
//    private OutputStream outputStream;
//    private boolean isConnected = false;
//    private final Context context;
//    private OnDataReceivedListener dataListener;
//    private Thread readThread;
//
//    public interface OnDataReceivedListener {
//        void onDataReceived(byte[] data);
//        void onConnectionStatusChanged(String status);
//    }
//
//    public BerrySerialPort(Context context) {
//        this.context = context;
//    }
//
//    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
//        this.dataListener = listener;
////        Log.d(TAG, "✅ Data listener set");
//    }
//
//    public void connect() {
////        Log.d(TAG, "🔄 Attempting to connect to Berry PM6750 - RX: " + SERIAL_PORT_PATH_RX + ", TX: " + SERIAL_PORT_PATH_TX);
//        try {
//            // Open RX port (COM9)
//            serialPortRx = Lztek.create(context)
//                    .openSerialPort(SERIAL_PORT_PATH_RX, BAUD_RATE, 8, 0, 1, 0);
//            if (serialPortRx == null) {
//                throw new IOException("Failed to initialize RX serial port on " + SERIAL_PORT_PATH_RX);
//            }
//            inputStream = serialPortRx.getInputStream();
//
//            // Open TX port (COM7)
//            serialPortTx = Lztek.create(context)
//                    .openSerialPort(SERIAL_PORT_PATH_TX, BAUD_RATE, 8, 0, 1, 0);
//            if (serialPortTx == null) {
//                throw new IOException("Failed to initialize TX serial port on " + SERIAL_PORT_PATH_TX);
//            }
//            outputStream = serialPortTx.getOutputStream();
//
//            isConnected = true;
////            Log.d(TAG, "✅ Connected - RX on " + SERIAL_PORT_PATH_RX + ", TX on " + SERIAL_PORT_PATH_TX);
//
//            // Send initialization commands via TX (COM7)
////            write(ENABLE_ECG);
////            write(ENABLE_SPO2);
////            write(ENABLE_ECG_WAVE);
////            write(ENABLE_SPO2_WAVE);
//            write(ENABLE_ECG);
//            write(ENABLE_SPO2);
//            write(ENABLE_ECG_WAVE);
//            write(ENABLE_SPO2_WAVE);
//            byte[] SET_ECG_GAIN = {0x55, (byte) 0xAA, 0x04, 0x07, 0x03, (byte) 0xF1}; // ECG x1 gain
//            byte[] SET_ECG_FILTER = {0x55, (byte) 0xAA, 0x04, 0x08, 0x02, (byte) 0xF1}; // ECG monitor mode
//            byte[] SET_RESP_GAIN = {0x55, (byte) 0xAA, 0x04, 0x0F, 0x03, (byte) 0xE9}; // Resp x1 gain
//            write(SET_ECG_GAIN);
//            write(SET_ECG_FILTER);
//            write(SET_RESP_GAIN);
////            Log.d(TAG, "📤 Sent ECG enable: " + bytesToHex(ENABLE_ECG));
////            Log.d(TAG, "📤 Sent SPO2 enable: " + bytesToHex(ENABLE_SPO2));
////            Log.d(TAG, "📤 Sent ECG wave enable: " + bytesToHex(ENABLE_ECG_WAVE));
////            Log.d(TAG, "📤 Sent SPO2 wave enable: " + bytesToHex(ENABLE_SPO2_WAVE));
//
//            readThread = new Thread(this::readSerialData);
//            readThread.start();
////            Log.d(TAG, "✅ Read thread started");
//
//            if (dataListener != null) {
//                dataListener.onConnectionStatusChanged("Connected - RX: " + SERIAL_PORT_PATH_RX + ", TX: " + SERIAL_PORT_PATH_TX);
//            }
//        } catch (Exception e) {
//            isConnected = false;
//            if (dataListener != null) {
//                dataListener.onConnectionStatusChanged("Failed to connect: " + e.getMessage());
//            }
////            Log.e(TAG, "❌ Failed to connect: " + e.getMessage(), e);
//            disconnect(); // Cleanup on failure
//        }
//    }
//
//    public void disconnect() {
////        Log.d(TAG, "🔄 Disconnecting from RX: " + SERIAL_PORT_PATH_RX + ", TX: " + SERIAL_PORT_PATH_TX);
//        if (readThread != null) {
//            readThread.interrupt();
//            readThread = null;
////            Log.d(TAG, "✅ Read thread stopped");
//        }
//        if (serialPortRx != null) {
//            try {
//                serialPortRx.close();
////                Log.d(TAG, "✅ RX SerialPort closed");
//            } catch (Exception e) {
////                Log.e(TAG, "❌ Error closing RX serial port: " + e.getMessage(), e);
//            }
//            serialPortRx = null;
//        }
//        if (serialPortTx != null) {
//            try {
//                serialPortTx.close();
////                Log.d(TAG, "✅ TX SerialPort closed");
//            } catch (Exception e) {
////                Log.e(TAG, "❌ Error closing TX serial port: " + e.getMessage(), e);
//            }
//            serialPortTx = null;
//        }
//        isConnected = false;
//        inputStream = null;
//        outputStream = null;
//        if (dataListener != null) {
////            dataListener.onConnectionStatusChanged("Disconnected");
//        }
////        Log.d(TAG, "✅ Serial Port Disconnected");
//    }
//
//    public void write(byte[] data) {
//        if (outputStream != null && isConnected) {
//            try {
//                outputStream.write(data);
//                outputStream.flush();
////                Log.d(TAG, "📤 Data written to " + SERIAL_PORT_PATH_TX + ": " + bytesToHex(data));
//            } catch (IOException e) {
////                Log.e(TAG, "❌ Error writing to " + SERIAL_PORT_PATH_TX + ": " + e.getMessage(), e);
//            }
//        } else {
////            Log.w(TAG, "⚠️ Cannot write: Not connected or outputStream is null");
//        }
//    }
//
//    public boolean isConnected() {
//        return isConnected;
//    }
//
//    private void readSerialData() {
//        byte[] buffer = new byte[256];
////        Log.d(TAG, "🔄 Read thread running for " + SERIAL_PORT_PATH_RX);
//        while (isConnected && !Thread.currentThread().isInterrupted()) {
//            try {
//                int bytesRead = inputStream.read(buffer);
//                if (bytesRead > 0) {
//                    byte[] data = new byte[bytesRead];
//                    System.arraycopy(buffer, 0, data, 0, bytesRead);
//                    if (dataListener != null) {
////                        Log.d(TAG, "📥 Data received from " + SERIAL_PORT_PATH_RX + ": " + bytesToHex(data));
//                        dataListener.onDataReceived(data);
//                    }
//                }
//            } catch (IOException e) {
//                if (isConnected && dataListener != null) {
//                    dataListener.onConnectionStatusChanged("Read error on " + SERIAL_PORT_PATH_RX + ": " + e.getMessage());
//                }
////                Log.e(TAG, "❌ Error reading from " + SERIAL_PORT_PATH_RX + ": " + e.getMessage(), e);
//                break;
//            }
//        }
//    }
//
////    private void readSerialData() {
////        byte[] buffer = new byte[256];
////        Log.d(TAG, "🔄 Read thread running for " + SERIAL_PORT_PATH_RX);
////        try {
////            Thread.sleep(100);
////        } catch (InterruptedException e) {
////            Log.e(TAG, "❌ Read thread initial delay interrupted", e);
////        }
////        while (isConnected && !Thread.currentThread().isInterrupted()) {
////            try {
////                int bytesRead = inputStream.read(buffer);
////                if (bytesRead > 0) {
////                    byte[] data = new byte[bytesRead];
////                    System.arraycopy(buffer, 0, data, 0, bytesRead);
////                    if (dataListener != null) {
////                        Log.d(TAG, "📥 Data received from " + SERIAL_PORT_PATH_RX + ": " + bytesToHex(data));
////                        dataListener.onDataReceived(data);
////                    } else {
////                        Log.w(TAG, "⚠️ DataListener is null");
////                    }
////                }
////                Thread.sleep(40);
////            } catch (IOException e) {
////                if (isConnected && dataListener != null) {
////                    dataListener.onConnectionStatusChanged("Read error on " + SERIAL_PORT_PATH_RX + ": " + e.getMessage());
////                }
////                Log.e(TAG, "❌ Error reading from " + SERIAL_PORT_PATH_RX + ": " + e.getMessage(), e);
////                break;
////            } catch (InterruptedException e) {
////                Log.d(TAG, "✅ Read thread interrupted");
////                break;
////            }
////        }
////    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//}