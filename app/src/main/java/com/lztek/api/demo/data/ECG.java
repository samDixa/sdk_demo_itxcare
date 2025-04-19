package com.lztek.api.demo.data;

/**
 * Created by ZXX on 2016/8/3.
 */

public class ECG {
    public static final int HEART_RATE_INVALID = 0;
    public static final int RESP_RATE_INVALID = 0;

    private int heartRate;
    private int restRate;
    private int status;
    private float stLevel; // -1.0 to +1.0 mV
    private String arrythmia; // e.g., "Normal", "Asystole"

    public ECG(int heartRate, int restRate, int status, float stLevel, String arrythmia) {
        this.heartRate = heartRate;
        this.restRate = restRate;
        this.status = status;
        this.stLevel = stLevel;
        this.arrythmia = arrythmia;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public int getRestRate() {
        return restRate;
    }

    public int getStatus() {
        return status;
    }

    public float getSTLevel() {
        return stLevel;
    }

    public String getArrythmia() {
        return arrythmia != null ? arrythmia : "__";
    }

    @Override
    public String toString() {
        return "Heart Rate:" + (heartRate != HEART_RATE_INVALID ? heartRate : "--") +
                "  Resp Rate:" + (restRate != RESP_RATE_INVALID ? restRate : "--") +
                "  ST Level:" + (stLevel != -2.0f ? String.format("%.2f mV", stLevel) : "0 mV") +
                "  Arrythmia:" + (arrythmia != null ? arrythmia : "__");
    }

    // Add this static final field
    public static final String ARRYTHMIA_INVALID = "__";
}


//package com.lztek.api.demo.data;
//
///**
// * Created by ZXX on 2016/8/3.
// */
//
//public class ECG {
//    public int HEART_RATE_INVALID = 0;
//    public int RESP_RATE_INVALID  = 0;
//
//    private int heartRate;
//    private int restRate;
//    private int status;
//
//    private float stLevel; // -1.0 to +1.0 mV
//    private String arrythmia; // e.g., "Normal", "Asystole"
//
//    public ECG(int heartRate, int restRate, int status) {
//        this.heartRate = heartRate;
//        this.restRate = restRate;
//        this.status = status;
//    }
//
//    public int getHeartRate() {
//        return heartRate;
//    }
//
//    public int getRestRate() {
//        return restRate;
//    }
//
//    public int getStatus() {
//        return status;
//    }
//
//    @Override
//    public String toString() {
//        return "Heart Rate:" + (heartRate!=HEART_RATE_INVALID ? heartRate: "- -") +
//                "  Resp Rate:"+(restRate!=RESP_RATE_INVALID ? restRate: "- -");
//    }
//}
