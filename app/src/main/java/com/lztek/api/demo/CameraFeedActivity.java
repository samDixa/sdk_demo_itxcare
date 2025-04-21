package com.lztek.api.demo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFeedActivity extends AppCompatActivity {

    private SurfaceView frontCameraView, usbCameraView;
    private Camera frontCamera, usbCamera;
    private Button btnCaptureFront, btnCaptureUSB, btnToggleFront, btnToggleUSB;
    private GlobalVars globalVars;
    private SurfaceHolder frontHolder, usbHolder;
    private BroadcastReceiver usbReceiver;
    private ExecutorService frontCameraExecutor;
    private ExecutorService usbCameraExecutor;

    private static final String TAG = "CameraFeed";
    private static final long CLEANUP_DELAY_MS = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_feed);

        globalVars = GlobalVars.getInstance();
        globalVars.setCameraPreviewActive(true);

        frontCameraView = findViewById(R.id.internalCameraView);
        usbCameraView = findViewById(R.id.usbCameraView);
        btnCaptureFront = findViewById(R.id.btn_capture_internal);
        btnCaptureUSB = findViewById(R.id.btn_capture_usb);
        btnToggleFront = findViewById(R.id.btn_toggle_front);
        btnToggleUSB = findViewById(R.id.btn_toggle_usb);

        frontHolder = frontCameraView.getHolder();
        usbHolder = usbCameraView.getHolder();

        frontCameraExecutor = Executors.newSingleThreadExecutor();
        usbCameraExecutor = Executors.newSingleThreadExecutor();

        if (checkPermissions()) {
            setupCameraCallbacks();
            logCameraInfo();
        } else {
            requestPermissions();
        }

        btnCaptureFront.setOnClickListener(view -> capturePhoto(frontCamera, "Front"));
        btnCaptureUSB.setOnClickListener(view -> capturePhoto(usbCamera, "USB"));

        btnToggleFront.setOnClickListener(view -> toggleFrontCamera());
        btnToggleUSB.setOnClickListener(view -> toggleUSBCamera());

        updateToggleButtons();
        setupUsbReceiver();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void logCameraInfo() {
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            Log.d(TAG, "Camera ID " + i + ": facing=" + (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "FRONT" : "NON-FRONT"));
        }
    }

    private void updateToggleButtons() {
        runOnUiThread(() -> {
            btnToggleFront.setText(frontCamera != null ? "Turn Off Front Camera" : "Turn On Front Camera");
            btnToggleUSB.setText(usbCamera != null ? "Turn Off USB Camera" : "Turn On USB Camera");
            btnCaptureFront.setEnabled(frontCamera != null);
            btnCaptureUSB.setEnabled(usbCamera != null);
        });
    }

    private void toggleFrontCamera() {
        frontCameraExecutor.submit(() -> {
            if (frontCamera != null) {
                stopFrontCamera();
                runOnUiThread(() -> {
                    updateToggleButtons();
                    frontCameraView.invalidate();
                    Toast.makeText(CameraFeedActivity.this, "Front Camera Off", Toast.LENGTH_SHORT).show();
                });
            } else {
                startFrontCamera();
                runOnUiThread(() -> {
                    updateToggleButtons();
                    frontCameraView.invalidate();
                    if (frontCamera != null) {
                        Toast.makeText(CameraFeedActivity.this, "Front Camera Started", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void toggleUSBCamera() {
        usbCameraExecutor.submit(() -> {
            if (usbCamera != null) {
                stopUSBCamera();
                runOnUiThread(() -> {
                    updateToggleButtons();
                    usbCameraView.invalidate();
                    Toast.makeText(CameraFeedActivity.this, "USB Camera Off", Toast.LENGTH_SHORT).show();
                });
            } else {
                startUSBCamera();
                runOnUiThread(() -> {
                    updateToggleButtons();
                    usbCameraView.invalidate();
                    if (usbCamera != null) {
                        Toast.makeText(CameraFeedActivity.this, "USB Camera Started", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 100);
    }

    private void setupCameraCallbacks() {
        frontHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Front camera surface created");
                frontCameraExecutor.submit(() -> startFrontCamera());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "Front camera surface destroyed");
                frontCameraExecutor.submit(() -> stopFrontCamera());
            }
        });

        usbHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "USB camera surface created");
                usbCameraExecutor.submit(() -> startUSBCamera());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "USB camera surface destroyed");
                usbCameraExecutor.submit(() -> stopUSBCamera());
            }
        });
    }

    private void startFrontCamera() {
        if (frontCamera != null) {
            Log.d(TAG, "Front camera already initialized, skipping");
            return;
        }
        try {
            int frontCameraId = -1;
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }
            // Fallback: Try first camera if no FRONT found
            if (frontCameraId == -1 && cameraCount > 0) {
                frontCameraId = 0;
                Log.w(TAG, "No FRONT camera found, trying ID 0");
            }
            Log.d(TAG, "Front camera ID: " + frontCameraId + ", total cameras: " + cameraCount);
            if (frontCameraId != -1) {
                frontCamera = Camera.open(frontCameraId);
                if (frontCamera != null) {
                    Camera.Parameters parameters = frontCamera.getParameters();
                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                    parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
                    frontCamera.setParameters(parameters);
                    frontCamera.setPreviewDisplay(frontHolder);
                    frontCamera.startPreview();
                    globalVars.setInternalCameraConnected(true);
                    Log.d(TAG, "Front camera preview started on thread: " + Thread.currentThread().getName() + ", ID: " + frontCameraId);
                } else {
                    throw new RuntimeException("Failed to open front camera");
                }
            } else {
                globalVars.setInternalCameraConnected(false);
                Log.w(TAG, "No camera available for front");
                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "Front Camera Error: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            stopFrontCamera();
        }
    }

    private void startUSBCamera() {
        if (usbCamera != null) {
            Log.d(TAG, "USB camera already initialized, skipping");
            return;
        }
        try {
            int usbCameraId = -1;
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    usbCameraId = i;
                    break;
                }
            }
            // Fallback: Try second camera if no non-FRONT found
            if (usbCameraId == -1 && cameraCount > 1) {
                usbCameraId = 1;
                Log.w(TAG, "No non-FRONT camera found, trying ID 1");
            }
            Log.d(TAG, "USB camera ID: " + usbCameraId + ", total cameras: " + cameraCount);
            if (usbCameraId != -1) {
                usbCamera = Camera.open(usbCameraId);
                if (usbCamera != null) {
                    Camera.Parameters parameters = usbCamera.getParameters();
                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                    parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
                    usbCamera.setParameters(parameters);
                    usbCamera.setPreviewDisplay(usbHolder);
                    usbCamera.startPreview();
                    globalVars.setUSBCameraConnected(true);
                    Log.d(TAG, "USB camera preview started on thread: " + Thread.currentThread().getName() + ", ID: " + usbCameraId);
                } else {
                    throw new RuntimeException("Failed to open USB camera");
                }
            } else {
                globalVars.setUSBCameraConnected(false);
                Log.w(TAG, "No USB camera available");
                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "USB Camera Error: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            stopUSBCamera();
        }
    }

    private void capturePhoto(Camera camera, String cameraType) {
        if (camera == null) {
            Toast.makeText(this, cameraType + " Camera not available", Toast.LENGTH_SHORT).show();
            return;
        }

        camera.takePicture(null, null, (data, cam) -> {
            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    cameraType + "_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                fos.write(data);
                fos.flush();
                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Error saving photo: " + e.getMessage(), e);
                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
            }
            cam.startPreview();
        });
    }

    private void stopFrontCamera() {
        if (frontCamera != null) {
            try {
                frontCamera.stopPreview();
                frontCamera.setPreviewDisplay(null);
                frontCamera.release();
                frontCamera = null;
                globalVars.setInternalCameraConnected(false);
                Log.d(TAG, "Front camera stopped on thread: " + Thread.currentThread().getName());
                runOnUiThread(() -> frontCameraView.invalidate());
            } catch (Exception e) {
                Log.e(TAG, "Error stopping front camera: " + e.getMessage(), e);
            }
        }
    }

    private void stopUSBCamera() {
        if (usbCamera != null) {
            try {
                usbCamera.stopPreview();
                usbCamera.setPreviewDisplay(null);
                usbCamera.release();
                usbCamera = null;
                globalVars.setUSBCameraConnected(false);
                Log.d(TAG, "USB camera stopped on thread: " + Thread.currentThread().getName());
                try {
                    Thread.sleep(CLEANUP_DELAY_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage(), e);
                }
                runOnUiThread(() -> usbCameraView.invalidate());
            } catch (Exception e) {
                Log.e(TAG, "Error stopping USB camera: " + e.getMessage(), e);
            }
        }
    }

    private void setupUsbReceiver() {
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d(TAG, "USB Device Attached: " + device.getDeviceName());
                    usbCameraExecutor.submit(() -> {
                        stopUSBCamera();
                        globalVars.setUSBCameraConnected(false);
                        runOnUiThread(() -> {
                            updateToggleButtons();
                            logCameraInfo();
                            Toast.makeText(CameraFeedActivity.this, "USB Camera Detected, Turn On to Start", Toast.LENGTH_SHORT).show();
                        });
                    });
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d(TAG, "USB Device Detached: " + device.getDeviceName());
                    usbCameraExecutor.submit(() -> {
                        stopUSBCamera();
                        globalVars.setUSBCameraConnected(false);
                        runOnUiThread(() -> {
                            updateToggleButtons();
                            logCameraInfo();
                            Toast.makeText(CameraFeedActivity.this, "USB Camera Disconnected", Toast.LENGTH_SHORT).show();
                        });
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        frontCameraExecutor.submit(() -> stopFrontCamera());
        usbCameraExecutor.submit(() -> stopUSBCamera());
        frontCameraExecutor.shutdown();
        usbCameraExecutor.shutdown();
        globalVars.setCameraPreviewActive(false);
        if (usbReceiver != null) {
            unregisterReceiver(usbReceiver);
        }
        Log.d(TAG, "CameraFeedActivity destroyed, threads shut down");
    }
}

//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.pm.PackageManager;
//import android.hardware.Camera;
//import android.hardware.usb.UsbDevice;
//import android.hardware.usb.UsbManager;
//import android.os.Bundle;
//import android.os.Environment;
//import android.util.Log;
//import android.view.SurfaceHolder;
//import android.view.SurfaceView;
//import android.widget.Button;
//import android.widget.Toast;
//
//import androidx.appcompat.app.ActionBar;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//
//public class CameraFeedActivity extends AppCompatActivity {
//
//    private SurfaceView frontCameraView, usbCameraView;
//    private Camera frontCamera, usbCamera;
//    private Button btnCaptureFront, btnCaptureUSB;
//    private GlobalVars globalVars;
//    private SurfaceHolder frontHolder, usbHolder;
//    private BroadcastReceiver usbReceiver;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_camera_feed);
//
//        globalVars = GlobalVars.getInstance();
//        globalVars.setCameraPreviewActive(true);
//
//        frontCameraView = findViewById(R.id.internalCameraView);
//        usbCameraView = findViewById(R.id.usbCameraView);
//        btnCaptureFront = findViewById(R.id.btn_capture_internal);
//        btnCaptureUSB = findViewById(R.id.btn_capture_usb);
//
//        frontHolder = frontCameraView.getHolder();
//        usbHolder = usbCameraView.getHolder();
//
//        if (checkPermissions()) {
//            setupCameraCallbacks();
//        } else {
//            requestPermissions();
//        }
//
//        btnCaptureFront.setOnClickListener(view -> capturePhoto(frontCamera, "Front"));
//        btnCaptureUSB.setOnClickListener(view -> capturePhoto(usbCamera, "USB"));
//
//        // Register USB receiver
//        setupUsbReceiver();
//
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
//    }
//
//    private boolean checkPermissions() {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private void requestPermissions() {
//        ActivityCompat.requestPermissions(this, new String[]{
//                Manifest.permission.CAMERA,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//        }, 100);
//    }
//
//    private void setupCameraCallbacks() {
//        frontHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                startFrontCamera();
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                stopFrontCamera();
//            }
//        });
//
//        usbHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                startUSBCamera();
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                stopUSBCamera();
//            }
//        });
//    }
//
//    private void startFrontCamera() {
//        if (frontCamera != null) return; // Prevent re-initialization
//        try {
//            int frontCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            for (int i = 0; i < cameraCount; i++) {
//                Camera.getCameraInfo(i, cameraInfo);
//                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                    frontCameraId = i;
//                    break;
//                }
//            }
//            if (frontCameraId != -1) {
//                frontCamera = Camera.open(frontCameraId);
//                if (frontCamera != null) {
//                    Camera.Parameters parameters = frontCamera.getParameters();
//                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
//                    parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    frontCamera.setParameters(parameters);
//                    frontCamera.setPreviewDisplay(frontHolder);
//                    frontCamera.startPreview();
//                    globalVars.setInternalCameraConnected(true);
//                    Log.d("CameraFeed", "Front camera preview started");
//                } else {
//                    throw new RuntimeException("Failed to open front camera");
//                }
//            } else {
//                globalVars.setInternalCameraConnected(false);
//                Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show();
//            }
//        } catch (Exception e) {
//            Log.e("CameraFeed", "Front Camera Error: " + e.getMessage());
//            Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            stopFrontCamera();
//        }
//    }
//
//    private void startUSBCamera() {
//        if (usbCamera != null) return; // Prevent re-initialization
//        try {
//            int usbCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            for (int i = 0; i < cameraCount; i++) {
//                Camera.getCameraInfo(i, cameraInfo);
//                if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                    usbCameraId = i;
//                    break;
//                }
//            }
//            if (usbCameraId != -1) {
//                usbCamera = Camera.open(usbCameraId);
//                if (usbCamera != null) {
//                    Camera.Parameters parameters = usbCamera.getParameters();
//                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
//                    parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    usbCamera.setParameters(parameters);
//                    usbCamera.setPreviewDisplay(usbHolder);
//                    usbCamera.startPreview();
//                    globalVars.setUSBCameraConnected(true);
//                    Log.d("CameraFeed", "USB camera preview started");
//                } else {
//                    throw new RuntimeException("Failed to open USB camera");
//                }
//            } else {
//                globalVars.setUSBCameraConnected(false);
//                Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show();
//            }
//        } catch (Exception e) {
//            Log.e("CameraFeed", "USB Camera Error: " + e.getMessage());
//            Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            stopUSBCamera();
//        }
//    }
//
//    private void capturePhoto(Camera camera, String cameraType) {
//        if (camera == null) {
//            Toast.makeText(this, cameraType + " Camera not available", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        camera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    cameraType + "_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Log.e("CameraFeed", "Error saving photo: " + e.getMessage());
//                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
//            }
//            cam.startPreview();
//        });
//    }
//
//    private void stopFrontCamera() {
//        if (frontCamera != null) {
//            try {
//                frontCamera.stopPreview();
//                frontCamera.release();
//                frontCamera = null;
//                globalVars.setInternalCameraConnected(false);
//                Log.d("CameraFeed", "Front camera stopped");
//            } catch (Exception e) {
//                Log.e("CameraFeed", "Error stopping front camera: " + e.getMessage());
//            }
//        }
//    }
//
//    private void stopUSBCamera() {
//        if (usbCamera != null) {
//            try {
//                usbCamera.stopPreview();
//                usbCamera.release();
//                usbCamera = null;
//                globalVars.setUSBCameraConnected(false);
//                Log.d("CameraFeed", "USB camera stopped");
//            } catch (Exception e) {
//                Log.e("CameraFeed", "Error stopping USB camera: " + e.getMessage());
//            }
//        }
//    }
//
//    private void restartCameras() {
//        stopFrontCamera();
//        stopUSBCamera();
//        startFrontCamera();
//        startUSBCamera();
//    }
//
//    private void setupUsbReceiver() {
//        usbReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                String action = intent.getAction();
//                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d("CameraFeed", "USB Device Attached: " + device.getDeviceName());
//                    restartCameras();
//                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d("CameraFeed", "USB Device Detached: " + device.getDeviceName());
//                    restartCameras();
//                }
//            }
//        };
//
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
//        registerReceiver(usbReceiver, filter);
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        stopFrontCamera();
//        stopUSBCamera();
//        globalVars.setCameraPreviewActive(false);
//        if (usbReceiver != null) {
//            unregisterReceiver(usbReceiver);
//        }
//    }
//}