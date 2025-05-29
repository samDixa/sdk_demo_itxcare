package com.lztek.api.demo;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogUtils {
    private static final String LOG_FILE_PATH = "/sdcard/logs.txt"; // External storage
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public static void init(Context context) {
        // Clear the log file on app start
        try {
            File file = new File(LOG_FILE_PATH);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
        } catch (IOException e) {
            Log.e("LogUtils", "Failed to initialize log file: " + e.getMessage());
        }
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        writeToFile(tag, "DEBUG", message);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e(tag, message, t);
        writeToFile(tag, "ERROR", message + " - " + t.getMessage());
    }

    private static void writeToFile(String tag, String level, String message) {
        try {
            File file = new File(LOG_FILE_PATH);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            String timestamp = DATE_FORMAT.format(new Date());
            writer.write(String.format("%s [%s] %s: %s\n", timestamp, level, tag, message));
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e("LogUtils", "Failed to write log to file: " + e.getMessage());
        }
    }
}