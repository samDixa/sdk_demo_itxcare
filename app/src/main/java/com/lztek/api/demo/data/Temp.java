package com.lztek.api.demo.data;

import android.util.Log;

/**
 * Created by ZXX on 2016/8/3.
 */
public class Temp {
    private static final String TAG = "Temp";
    public static final int TEMP_INVALID = 0;

    private double temperature;
    private int status;

    public Temp(double temperature, int status) {
        this.temperature = temperature;
        this.status = status;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public String toString() {
        Log.d(TAG, "toString: temperature=" + temperature + ", status=" + status);
        // Check if temperature is in a reasonable human body range (30–45 °C)
        if (status == TEMP_INVALID && (temperature < 30.0 || temperature > 45.0)) {
            return "TEMP: Invalid";
        }
        double fahrenheit = (temperature * 9.0 / 5.0) + 32;
        return String.format("%.1f °C / %.1f °F", temperature, fahrenheit);
    }
}



//package com.lztek.api.demo.data;
//
///**
// * Created by ZXX on 2016/8/3.
// */
//
//public class Temp {
//    public int TEMP_INVALID = 0;
//
//    private double temperature;
//    private int status;
//
//
//    public Temp(double temperature, int status) {
//        this.temperature = temperature;
//        this.status = status;
//    }
//
//    public double getTemperature() {
//        return temperature;
//    }
//
//    public int getStatus() {
//        return status;
//    }
//
//    @Override
//    public String toString() {
//        return  String.format("TEMP: %.1f °C",temperature);
//    }
//}
