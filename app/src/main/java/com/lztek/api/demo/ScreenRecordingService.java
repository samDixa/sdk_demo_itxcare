package com.lztek.api.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;

public class ScreenRecordingService extends Service {
    private static final String TAG = "ScreenRecordingService";
    private static final String CHANNEL_ID = "ScreenRecordingChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification());

        String action = intent.getAction();
        if ("START_RECORDING".equals(action)) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            startRecording(resultCode, data);
        } else if ("STOP_RECORDING".equals(action)) {
            stopRecording();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Recording",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startRecording(int resultCode, Intent data) {
        if (isRecording) {
            Log.d(TAG, "Recording already in progress");
            return;
        }

        try {
            // Initialize MediaProjection
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection");
                stopSelf();
                return;
            }

            // Initialize MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecordings");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            File outputFile = new File(outputDir, "screen_" + System.currentTimeMillis() + ".mp4");
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());

            // Get screen dimensions
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int densityDpi = metrics.densityDpi;

            // Configure video settings
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(8 * 1000 * 1000); // 8Mbps for better quality
            mediaRecorder.setVideoFrameRate(30); // 30 FPS for compatibility
            mediaRecorder.setVideoSize(screenWidth, screenHeight);

            // Use CamcorderProfile if QUALITY_1080P is supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.media.CamcorderProfile.hasProfile(android.media.CamcorderProfile.QUALITY_1080P)) {
                try {
                    mediaRecorder.setProfile(android.media.CamcorderProfile.get(android.media.CamcorderProfile.QUALITY_1080P));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set CamcorderProfile QUALITY_1080P, using manual settings: " + e.getMessage());
                }
            }

            mediaRecorder.prepare();

            // Create VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth, screenHeight, densityDpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, null
            );

            // Start recording
            mediaRecorder.start();
            isRecording = true;

            Log.d(TAG, "Screen recording started, saving to: " + outputFile.getAbsolutePath());
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            cleanupResources();
            stopSelf();
        }
    }

//    private void startRecording(int resultCode, Intent data) {
//        if (isRecording) {
//            Log.d(TAG, "Recording already in progress");
//            return;
//        }
//
//        try {
//            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
//            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
//            if (mediaProjection == null) {
//                Log.e(TAG, "Failed to obtain MediaProjection");
//                stopSelf();
//                return;
//            }
//
//            mediaRecorder = new MediaRecorder();
//            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//
//            File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecordings");
//            if (!outputDir.exists()) {
//                outputDir.mkdirs();
//            }
//            File outputFile = new File(outputDir, "screen_" + System.currentTimeMillis() + ".mp4");
//            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
//
//            DisplayMetrics metrics = getResources().getDisplayMetrics();
//            int screenWidth = metrics.widthPixels;
//            int screenHeight = metrics.heightPixels;
//            int densityDpi = metrics.densityDpi;
//
//            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//            mediaRecorder.setVideoEncodingBitRate(512 * 1000);
//            mediaRecorder.setVideoFrameRate(30);
//            mediaRecorder.setVideoSize(screenWidth, screenHeight);
//            mediaRecorder.prepare();
//
//            virtualDisplay = mediaProjection.createVirtualDisplay(
//                    "ScreenCapture",
//                    screenWidth, screenHeight, densityDpi,
//                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//                    mediaRecorder.getSurface(), null, null
//            );
//
//            mediaRecorder.start();
//            isRecording = true;
//
//            Log.d(TAG, "Screen recording started, saving to: " + outputFile.getAbsolutePath());
//        } catch (IOException | SecurityException e) {
//            Log.e(TAG, "Failed to start recording: " + e.getMessage());
//            cleanupResources();
//            stopSelf();
//        }
//    }

    private void stopRecording() {
        if (!isRecording) {
            Log.d(TAG, "No recording in progress");
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }

            isRecording = false;
            Log.d(TAG, "Screen recording stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
        } finally {
            cleanupResources();
            stopForeground(true);
            stopSelf();
        }
    }

    private void cleanupResources() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder: " + e.getMessage());
            }
            mediaRecorder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        isRecording = false;
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, ScreenRecordingService.class);
        stopIntent.setAction("STOP_RECORDING");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Screen Recording")
                .setContentText("Recording in progress...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}