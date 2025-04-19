package com.lztek.api.demo.data;

/**
 * Created by ZXX on 2016/8/3.
 */

public class SpO2 {
    public static final int SPO2_INVALID = 127;
    public static final int PULSE_RATE_INVALID = 255;

    private int SpO2;
    private int pulseRate;
    private String sensorStatus; // e.g., "Normal", "No Finger Ins"

    public SpO2(int spO2, int pulseRate, int status, String sensorStatus) {
        SpO2 = spO2;
        this.pulseRate = pulseRate;
        this.sensorStatus = sensorStatus; // Status will be parsed in DataParser
    }

    public int getSpO2() {
        return SpO2;
    }

    public void setSpO2(int spO2) {
        SpO2 = spO2;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public String getSensorStatus() {
        return sensorStatus != null ? sensorStatus : "Unknown";
    }

    @Override
    public String toString() {
        return "SpO2: " + (SpO2 != SPO2_INVALID ? SpO2 : "--") +
                "  Pulse Rate:" + (pulseRate != PULSE_RATE_INVALID ? pulseRate : "--") + " /min" +
                "  Status: " + (sensorStatus != null ? sensorStatus : "__");
    }
}


//package com.lztek.api.demo.data;
//
///**
// * Created by ZXX on 2016/8/3.
// */
//
//public class SpO2 {
//    public static final int SPO2_INVALID = 127;
//    public static final int PULSE_RATE_INVALID = 255;
//
//    private int SpO2;
//    private int pulseRate;
//    private String sensorStatus; // e.g., "Normal", "No Finger Ins"
//
//    public SpO2(int spO2, int pulseRate, int status, String sensorStatus) {
//        SpO2 = spO2;
//        this.pulseRate = pulseRate;
//        this.sensorStatus = parseSpO2Status(status); // Parse status to string
//    }
//
//    public int getSpO2() {
//        return SpO2;
//    }
//
//    public void setSpO2(int spO2) {
//        SpO2 = spO2;
//    }
//
//    public int getPulseRate() {
//        return pulseRate;
//    }
//
//    public String getSensorStatus() {
//        return sensorStatus != null ? sensorStatus : "Unknown";
//    }
//
//    @Override
//    public String toString() {
//        return "SpO2: " + (SpO2 != SPO2_INVALID ? SpO2 : "--") +
//                "Pulse Rate:" + (pulseRate != PULSE_RATE_INVALID ? pulseRate : "--") + " /min" +
//                "Status: " + (sensorStatus != null ? sensorStatus : "__");
//    }
//
//    // Helper method (will be moved to DataParser)
//    private String parseSpO2Status(int status) {
//        switch (status) {
//            case 0x00: return "Normal";
//            case 0x01: return "Sensor OFF";
//            case 0x02: return "No Finger Ins";
//            case 0x03: return "Searching Pulse";
//            case 0x04: return "Pulse Timeout";
//            default: return "Unknown";
//        }
//    }
//}
//
////package com.lztek.api.demo.data;
////
/////**
//// * Created by ZXX on 2016/8/3.
//// */
////
////public class SpO2 {
////    public int SPO2_INVALID = 127;
////    public int PULSE_RATE_INVALID = 255;
////
////    private int SpO2;
////    private int pulseRate;
////    private int status;
////
////    public SpO2(int spO2, int pulseRate, int status) {
////        SpO2 = spO2;
////        this.pulseRate = pulseRate;
////        this.status = status;
////    }
////
////    public int getSpO2() {
////        return SpO2;
////    }
////
////    public void setSpO2(int spO2) {
////        SpO2 = spO2;
////    }
////
////    public int getPulseRate() {
////        return pulseRate;
////    }
////
////
////    @Override
////    public String toString() {
////        return "SpO2: " + (SpO2 != SPO2_INVALID ? SpO2 : "-") + "\n" +
////                "Pulse Rate:" + (pulseRate != PULSE_RATE_INVALID ? pulseRate : "-");
////    }
////}
