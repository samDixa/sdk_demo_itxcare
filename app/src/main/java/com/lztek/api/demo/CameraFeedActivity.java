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

public class CameraFeedActivity extends AppCompatActivity {

    private SurfaceView frontCameraView, usbCameraView;
    private Camera frontCamera, usbCamera;
    private Button btnCaptureFront, btnCaptureUSB;
    private GlobalVars globalVars;
    private SurfaceHolder frontHolder, usbHolder;
    private BroadcastReceiver usbReceiver;

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

        frontHolder = frontCameraView.getHolder();
        usbHolder = usbCameraView.getHolder();

        if (checkPermissions()) {
            setupCameraCallbacks();
        } else {
            requestPermissions();
        }

        btnCaptureFront.setOnClickListener(view -> capturePhoto(frontCamera, "Front"));
        btnCaptureUSB.setOnClickListener(view -> capturePhoto(usbCamera, "USB"));

        // Register USB receiver
        setupUsbReceiver();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
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
                startFrontCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopFrontCamera();
            }
        });

        usbHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startUSBCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopUSBCamera();
            }
        });
    }

    private void startFrontCamera() {
        if (frontCamera != null) return; // Prevent re-initialization
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
                    Log.d("CameraFeed", "Front camera preview started");
                } else {
                    throw new RuntimeException("Failed to open front camera");
                }
            } else {
                globalVars.setInternalCameraConnected(false);
                Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("CameraFeed", "Front Camera Error: " + e.getMessage());
            Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopFrontCamera();
        }
    }

    private void startUSBCamera() {
        if (usbCamera != null) return; // Prevent re-initialization
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
                    Log.d("CameraFeed", "USB camera preview started");
                } else {
                    throw new RuntimeException("Failed to open USB camera");
                }
            } else {
                globalVars.setUSBCameraConnected(false);
                Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("CameraFeed", "USB Camera Error: " + e.getMessage());
            Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                Log.e("CameraFeed", "Error saving photo: " + e.getMessage());
                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
            }
            cam.startPreview();
        });
    }

    private void stopFrontCamera() {
        if (frontCamera != null) {
            try {
                frontCamera.stopPreview();
                frontCamera.release();
                frontCamera = null;
                globalVars.setInternalCameraConnected(false);
                Log.d("CameraFeed", "Front camera stopped");
            } catch (Exception e) {
                Log.e("CameraFeed", "Error stopping front camera: " + e.getMessage());
            }
        }
    }

    private void stopUSBCamera() {
        if (usbCamera != null) {
            try {
                usbCamera.stopPreview();
                usbCamera.release();
                usbCamera = null;
                globalVars.setUSBCameraConnected(false);
                Log.d("CameraFeed", "USB camera stopped");
            } catch (Exception e) {
                Log.e("CameraFeed", "Error stopping USB camera: " + e.getMessage());
            }
        }
    }

    private void restartCameras() {
        stopFrontCamera();
        stopUSBCamera();
        startFrontCamera();
        startUSBCamera();
    }

    private void setupUsbReceiver() {
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d("CameraFeed", "USB Device Attached: " + device.getDeviceName());
                    restartCameras();
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d("CameraFeed", "USB Device Detached: " + device.getDeviceName());
                    restartCameras();
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
        stopFrontCamera();
        stopUSBCamera();
        globalVars.setCameraPreviewActive(false);
        if (usbReceiver != null) {
            unregisterReceiver(usbReceiver);
        }
    }
}