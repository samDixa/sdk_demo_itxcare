package com.lztek.api.demo.data;

import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by ZXX on 2016/1/8.
 */

public class DataParser {

    private static final String TAG = "DataParser";

    // Buffer queue with limited size to prevent overload
    private LinkedBlockingQueue<Integer> bufferQueue = new LinkedBlockingQueue<>(512); // Increased to 512
    private int[] PACKAGE_HEAD = new int[]{0x55, 0xaa};

    // Packet IDs
    private final int PKG_ECG_WAVE = 0x01;
    private final int PKG_ECG_PARAMS = 0x02;
    private final int PKG_NIBP = 0x03;
    private final int PKG_SPO2_PARAMS = 0x04;
    private final int PKG_TEMP = 0x05;
    private final int PKG_SW_VER = 0xfc;
    private final int PKG_HW_VER = 0xfd;
    private final int PKG_SPO2_WAVE = 0xfe;
    private final int PKG_RESP_WAVE = 0xFF;

//    public static byte[] CMD_START_NIBP = new byte[]{0x55, (byte) 0xAA, 0x04, 0x09, 0x02, (byte) 0xF0};
    public static byte[] CMD_START_NIBP = new byte[]{0x55, (byte) 0xaa, 0x04, 0x02, 0x01, (byte) 0xf8};
    public static byte[] CMD_STOP_NIBP = new byte[]{0x55, (byte) 0xaa, 0x04, 0x02, 0x00, (byte) 0xf9};
//    public static byte[] CMD_FW_VERSION = new byte[]{0x55, (byte) 0xaa, 0x04, (byte) 0xfc, 0x00, (byte) 0xff};
//    public static byte[] CMD_HW_VERSION = new byte[]{0x55, (byte) 0xaa, 0x04, (byte) 0xfd, 0x00, (byte) 0xfe};

    private ParseRunnable mParseRunnable;
    private volatile boolean isStop = true; // Volatile for thread safety
    private onPackageReceivedListener mListener;

    /**
     * Interface for parameters changed.
     */
    public interface onPackageReceivedListener {
        void onSpO2WaveReceived(int dat);
        void onSpO2Received(SpO2 spo2);
        void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead);
//        void onECGAllWaveReceived(int leadI);
        void onECGReceived(ECG ecg);
        void onTempReceived(Temp temp);
        void onNIBPReceived(NIBP nibp);
//        void onFirmwareReceived(String str);
//        void onHardwareReceived(String str);
        void onRespWaveReceived(int dat);
    }

    // Constructor
    public DataParser(onPackageReceivedListener listener) {
        this.mListener = listener;
    }

    public void start() {
        if (mParseRunnable == null) {
            mParseRunnable = new ParseRunnable();
            new Thread(mParseRunnable, "DataParserThread").start();
//            Log.d(TAG, "DataParser started");
        }
    }

    public void stop() {
        isStop = true;
//        Log.d(TAG, "DataParser stopping");
    }

    /**
     * ParseRunnable
     */
    class ParseRunnable implements Runnable {
        int dat;
        int[] packageData;

        @Override
        public void run() {
            isStop = false;
            while (!isStop) {
                try {
                    dat = getData(); // Block until data is available
                    if (dat == PACKAGE_HEAD[0]) {
                        dat = getData();
                        if (dat == PACKAGE_HEAD[1]) {
                            int packageLen = getData();
                            if (packageLen > 0 && packageLen < 256) { // Validate length
                                packageData = new int[packageLen + PACKAGE_HEAD.length];
                                packageData[0] = PACKAGE_HEAD[0];
                                packageData[1] = PACKAGE_HEAD[1];
                                packageData[2] = packageLen;

                                for (int i = 3; i < packageLen + PACKAGE_HEAD.length; i++) {
                                    packageData[i] = getData();
                                }

                                if (CheckSum(packageData)) {
                                    parsePackageInBackground(packageData);
                                } else {
//                                    Log.w(TAG, "Checksum failed for packet: " + bytesToHex(packageData));
                                }
                            } else {
//                                Log.w(TAG, "Invalid package length: " + packageLen);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in parsing: " + e.getMessage());
                }
            }
            Log.d(TAG, "DataParser thread stopped");
        }
    }

    // Separate method to parse in background
    private void parsePackageInBackground(int[] pkgData) {
        new Thread(() -> {
            int pkgType = pkgData[3];
//            Log.d(TAG, "Parsing packet type: " + String.format("0x%02X", pkgType));

            switch (pkgType) {
                case PKG_ECG_WAVE:
                    if (pkgData.length >= 10) {
                        int leadI = pkgData[4];
                        int leadII = pkgData[5];
                        int leadIII = pkgData[6];
                        int aVR = pkgData[7];
                        int aVL = pkgData[8];
                        int aVF = pkgData[9];
                        int vLead = pkgData[10];
                        if (mListener != null) {
                            mListener.onECGWaveReceived(leadI, leadII, leadIII, aVR, aVL, aVF, vLead);
//                            mListener.onECGAllWaveReceived(leadI);
                        }
                    }
                    break;

                case PKG_SPO2_WAVE:
                    if (mListener != null) {
                        mListener.onSpO2WaveReceived(pkgData[4]);
                    }
                    break;

                case PKG_ECG_PARAMS:
                    if (pkgData.length >= 7) {
                        int status = pkgData[4];
                        int heartRate = pkgData[5];
                        int respRate = pkgData[6];
                        float stLevel = (byte) pkgData[7] / 100.0f;
                        int arrCode = pkgData[8] & 0xFF;
                        String arrythmia = parseArrythmia(arrCode);
                        ECG ecg = new ECG(heartRate, respRate, status, stLevel, arrythmia);
                        if (mListener != null) {
                            mListener.onECGReceived(ecg);
                        }
                    }
                    break;

                case PKG_NIBP:
                    if (pkgData.length >= 9) {
                        NIBP nibp = new NIBP(pkgData[6], pkgData[7], pkgData[8], pkgData[5] * 2, pkgData[4]);
                        if (mListener != null) {
                            mListener.onNIBPReceived(nibp);
                        }
                    }
                    break;

                case PKG_SPO2_PARAMS:
                    if (pkgData.length >= 5) {
                        int status = pkgData[4];
                        int spO2 = pkgData[5];
                        int pulseRate = pkgData[6];
                        String sensorStatus = parseSpO2Status(status);
                        SpO2 spo2 = new SpO2(spO2, pulseRate, status, sensorStatus);
                        if (mListener != null) {
                            mListener.onSpO2Received(spo2);
                        }
                    }
                    break;

                case PKG_TEMP:
                    if (pkgData.length >= 7) {
                        Temp temp = new Temp((pkgData[5] * 10 + pkgData[6]) / 10.0, pkgData[4]);
                        if (mListener != null) {
                            mListener.onTempReceived(temp);
                        }
                    }
                    break;

//                case PKG_SW_VER:
//                    StringBuilder sb = new StringBuilder();
//                    for (int i = 4; i < pkgData.length - 1; i++) {
//                        sb.append((char) (pkgData[i] & 0xff));
//                    }
//                    if (mListener != null) {
//                        mListener.onFirmwareReceived(sb.toString());
//                    }
//                    break;
//
//                case PKG_HW_VER:
//                    StringBuilder sb1 = new StringBuilder();
//                    for (int i = 4; i < pkgData.length - 1; i++) {
//                        sb1.append((char) (pkgData[i] & 0xff));
//                    }
//                    if (mListener != null) {
//                        mListener.onHardwareReceived(sb1.toString());
//                    }
//                    break;

                case PKG_RESP_WAVE:
                    if (pkgData.length >= 5) {
                        if (mListener != null) {
                            mListener.onRespWaveReceived(pkgData[4]);
                        }
                    }
                    break;

                default:
                    Log.w(TAG, "Unknown packet type: " + String.format("0x%02X", pkgType));
                    break;
            }
        }).start();
    }

    /**
     * Add the data received from USB or Bluetooth
     */
    public void add(byte[] dat) {
        if (dat != null) {
            for (byte b : dat) {
                try {
                    if (!bufferQueue.offer(toUnsignedInt(b))) {
//                        Log.w(TAG, "Buffer queue full, dropping data");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error adding to queue: " + e.getMessage());
                }
            }
//            Log.d(TAG, "Added data to queue, size: " + bufferQueue.size());
        }
    }

    /**
     * Get Data from Queue
     */
    private int getData() {
        try {
            return bufferQueue.take(); // Blocks until data is available
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while getting data: " + e.getMessage());
            return 0;
        }
    }

    private boolean CheckSum(int[] packageData) {
        int sum = 0;
        for (int i = 2; i < packageData.length - 1; i++) {
            sum += packageData[i];
        }
        return ((~sum) & 0xff) == (packageData[packageData.length - 1] & 0xff);
    }

    private int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    private String parseArrythmia(int code) {
        switch (code) {
            case 0x00: return "Analysis";
            case 0x01: return "Normal";
            case 0x02: return "Asystole";
            case 0x03: return "VFIB/VTAC";
            case 0x04: return "R ON T";
            case 0x05: return "Multi PVCs";
            case 0x06: return "Couple PVCs";
            case 0x07: return "PVC";
            case 0x08: return "BIGEMINY";
            case 0x09: return "TRIGEMINY";
            case 0x0A: return "TACHYCARDIA";
            case 0x0B: return "BRADYCARDIA";
            case 0x0C: return "Missed Beats";
            default: return ECG.ARRYTHMIA_INVALID;
        }
    }

    private String parseSpO2Status(int status) {
        switch (status) {
            case 0x00: return "Normal";
            case 0x01: return "Sensor OFF";
            case 0x02: return "No Finger Ins";
            case 0x03: return "Searching Pulse";
            case 0x04: return "Pulse Timeout";
            default: return "Unknown";
        }
    }

    private String bytesToHex(int[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}





//package com.lztek.api.demo.data;
//
//import android.util.Log;
//import java.util.concurrent.LinkedBlockingQueue;
//
///**
// * Created by ZXX on 2016/1/8.
// */
//
//public class DataParser {
//
//    // Const
//    public String TAG = this.getClass().getSimpleName();
//
//    // Buffer queue
//    private LinkedBlockingQueue<Integer> bufferQueue = new LinkedBlockingQueue<>(256);
//    private int[] PACKAGE_HEAD = new int[]{0x55, 0xaa};
//
//    // Packet IDs
//    private final int PKG_ECG_WAVE = 0x01;
//    private final int PKG_ECG_PARAMS = 0x02;
//    private final int PKG_NIBP = 0x03;
//    private final int PKG_SPO2_PARAMS = 0x04;
//    private final int PKG_TEMP = 0x05;
//    private final int PKG_SW_VER = 0xfc;
//    private final int PKG_HW_VER = 0xfd;
//    private final int PKG_SPO2_WAVE = 0xfe;
//    private final int PKG_RESP_WAVE = 0xFF; // Added RESP (RoR) wave packet ID
//
//    public static byte[] CMD_START_NIBP = new byte[]{0x55, (byte) 0xaa, 0x04, 0x02, 0x01, (byte) 0xf8};
//    public static byte[] CMD_STOP_NIBP = new byte[]{0x55, (byte) 0xaa, 0x04, 0x02, 0x00, (byte) 0xf9};
//
//    public static byte[] CMD_FW_VERSION = new byte[]{0x55, (byte) 0xaa, 0x04, (byte) 0xfc, 0x00, (byte) 0xff};
//    public static byte[] CMD_HW_VERSION = new byte[]{0x55, (byte) 0xaa, 0x04, (byte) 0xfd, 0x00, (byte) 0xfe};
//
//    // Parse Runnable
//    private ParseRunnable mParseRunnable;
//    private boolean isStop = true;
//    private onPackageReceivedListener mListener;
//
//    /**
//     * Interface for parameters changed.
//     */
//    public interface onPackageReceivedListener {
//        void onSpO2WaveReceived(int dat);
//        void onSpO2Received(SpO2 spo2);
//        void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead);
//        void onECGReceived(ECG ecg);
//        void onTempReceived(Temp temp);
//        void onNIBPReceived(NIBP nibp);
//        void onFirmwareReceived(String str);
//        void onHardwareReceived(String str);
//
//        // New RESP (RoR) wave callback
//        void onRespWaveReceived(int dat);
//    }
//
//    // Constructor
//    public DataParser(onPackageReceivedListener listener) {
//        this.mListener = listener;
//    }
//
//    public void start() {
//        mParseRunnable = new ParseRunnable();
//        new Thread(mParseRunnable).start();
//    }
//
//    public void stop() {
//        isStop = true;
//    }
//
//    /**
//     * ParseRunnable
//     */
//    class ParseRunnable implements Runnable {
//        int dat;
//        int[] packageData;
//
//        @Override
//        public void run() {
//            while (isStop) {
//                dat = getData();
//                if (dat == PACKAGE_HEAD[0]) {
//                    dat = getData();
//                    if (dat == PACKAGE_HEAD[1]) {
//                        int packageLen = getData();
//                        packageData = new int[packageLen + PACKAGE_HEAD.length];
//
//                        packageData[0] = PACKAGE_HEAD[0];
//                        packageData[1] = PACKAGE_HEAD[1];
//                        packageData[2] = packageLen;
//
//                        for (int i = 3; i < packageLen + PACKAGE_HEAD.length; i++) {
//                            packageData[i] = getData();
//                        }
//
//                        if (CheckSum(packageData)) {
//                            ParsePackage(packageData);
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private void ParsePackage(int[] pkgData) {
//        int pkgType = pkgData[3];
//
//        switch (pkgType) {
//            case PKG_ECG_WAVE:
//                if (pkgData.length >= 10) {
//                    int leadI = pkgData[4];
//                    int leadII = pkgData[5];
//                    int leadIII = pkgData[6];
//                    int aVR = pkgData[7];
//                    int aVL = pkgData[8];
//                    int aVF = pkgData[9];
//                    int vLead = pkgData[10];
//
//                    mListener.onECGWaveReceived(leadI, leadII, leadIII, aVR, aVL, aVF, vLead);
//                }
//                break;
//
//            case PKG_SPO2_WAVE:
//                mListener.onSpO2WaveReceived(pkgData[4]);
//                break;
//
//            case PKG_ECG_PARAMS:
//                int heartRate = pkgData[5];
//                int respRate = pkgData[6];
//                ECG params = new ECG(heartRate, respRate, pkgData[4]);
//                mListener.onECGReceived(params);
//                break;
//
//            case PKG_NIBP:
//                NIBP params2 = new NIBP(pkgData[6], pkgData[7], pkgData[8], pkgData[5] * 2, pkgData[4]);
//                mListener.onNIBPReceived(params2);
//                break;
//
//            case PKG_SPO2_PARAMS:
//                SpO2 params3 = new SpO2(pkgData[5], pkgData[6], pkgData[4]);
//                mListener.onSpO2Received(params3);
//                break;
//
//            case PKG_TEMP:
//                Temp params4 = new Temp((pkgData[5] * 10 + pkgData[6]) / 10.0, pkgData[4]);
//                mListener.onTempReceived(params4);
//                break;
//
//            case PKG_SW_VER:
//                StringBuilder sb = new StringBuilder();
//                for (int i = 4; i < pkgData.length - 1; i++) {
//                    sb.append((char) (pkgData[i] & 0xff));
//                }
//                mListener.onFirmwareReceived(sb.toString());
//                break;
//
//            case PKG_HW_VER:
//                StringBuilder sb1 = new StringBuilder();
//                for (int i = 4; i < pkgData.length - 1; i++) {
//                    sb1.append((char) (pkgData[i] & 0xff));
//                }
//                mListener.onHardwareReceived(sb1.toString());
//                break;
//
//            case PKG_RESP_WAVE:  // Added RESP (RoR) wave parsing
//                if (pkgData.length >= 5) {
//                    int respWave = pkgData[4]; // RESP wave amplitude
//                    mListener.onRespWaveReceived(respWave);
//                }
//                break;
//
//            default:
//                break;
//        }
//    }
//
//    /**
//     * Add the data received from USB or Bluetooth
//     */
//    public void add(byte[] dat) {
//        for (byte b : dat) {
//            try {
//                bufferQueue.put(toUnsignedInt(b));
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    /**
//     * Get Data from Queue
//     */
//    private int getData() {
//        int dat = 0;
//        try {
//            dat = bufferQueue.take();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return dat;
//    }
//
//    private boolean CheckSum(int[] packageData) {
//        int sum = 0;
//        for (int i = 2; i < packageData.length - 1; i++) {
//            sum += (packageData[i]);
//        }
//
//        return ((~sum) & 0xff) == (packageData[packageData.length - 1] & 0xff);
//    }
//
//    private int toUnsignedInt(byte x) {
//        return ((int) x) & 0xff;
//    }
//}
