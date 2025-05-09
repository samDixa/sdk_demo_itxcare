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
//import android.media.CamcorderProfile;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.view.SurfaceHolder;
//import android.view.SurfaceView;
//import android.widget.Button;
//import android.widget.TextView;
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
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class CameraFeedActivity extends AppCompatActivity {
//
//    private SurfaceView frontCameraView, usbCameraView;
//    private Camera frontCamera, usbCamera;
//    private Button btnCaptureFront, btnCaptureUSB, btnToggleFront, btnToggleUSB;
//    private Button btnRecordFront, btnRecordUSB;
//    private TextView timerView;
//    private GlobalVars globalVars;
//    private SurfaceHolder frontHolder, usbHolder;
//    private BroadcastReceiver usbReceiver;
//    private ExecutorService frontCameraExecutor;
//    private ExecutorService usbCameraExecutor;
//    private MediaRecorder frontMediaRecorder, usbMediaRecorder;
//    private boolean isFrontRecording, isUSBRecording;
//    private Handler timerHandler;
//    private long startTime;
//    private Runnable timerRunnable;
//
//    private static final String TAG = "CameraFeed";
//    private static final long CLEANUP_DELAY_MS = 1000;
//    private static final long RETRY_DELAY_MS = 500;
//    private static final int MAX_RETRIES = 3;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_camera_feed);
//
//        globalVars = GlobalVars.getInstance();
//        globalVars.setCameraPreviewActive(true);
//
//        // Initialize UI components
//        frontCameraView = findViewById(R.id.usbCameraView);
//        usbCameraView = findViewById(R.id.internalCameraView);
//        btnCaptureFront = findViewById(R.id.btn_capture_usb);
//        btnCaptureUSB = findViewById(R.id.btn_capture_internal);
//        btnToggleFront = findViewById(R.id.btn_toggle_usb);
//        btnToggleUSB = findViewById(R.id.btn_toggle_front);
//        btnRecordFront = findViewById(R.id.btn_record_usb);
//        btnRecordUSB = findViewById(R.id.btn_record_internal);
//        timerView = findViewById(R.id.timer_view);
//
//        frontHolder = frontCameraView.getHolder();
//        usbHolder = usbCameraView.getHolder();
//
//        // Separate executors for each camera
//        frontCameraExecutor = Executors.newSingleThreadExecutor();
//        usbCameraExecutor = Executors.newSingleThreadExecutor();
//
//        // Initialize timer handler
//        timerHandler = new Handler(Looper.getMainLooper());
//        timerRunnable = new Runnable() {
//            @Override
//            public void run() {
//                long elapsedTime = System.currentTimeMillis() - startTime;
//                int seconds = (int) (elapsedTime / 1000) % 60;
//                int minutes = (int) (elapsedTime / (1000 * 60)) % 60;
//                timerView.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
//                timerHandler.postDelayed(this, 1000);
//            }
//        };
//
//        // Check permissions
//        if (checkPermissions()) {
//            setupCameraCallbacks();
//            logCameraInfo();
//        } else {
//            requestPermissions();
//        }
//
//        // Set button listeners
//        btnCaptureFront.setOnClickListener(view -> captureFrontCameraPhoto());
//        btnCaptureUSB.setOnClickListener(view -> captureUSBCameraPhoto());
//        btnToggleFront.setOnClickListener(view -> toggleFrontCamera());
//        btnToggleUSB.setOnClickListener(view -> toggleUSBCamera());
//        btnRecordFront.setOnClickListener(view -> toggleFrontVideoRecording());
//        btnRecordUSB.setOnClickListener(view -> toggleUSBVideoRecording());
//
//        updateToggleButtons();
//        setupUsbReceiver();
//
//        // Hide action bar
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
//    }
//
//    private void logCameraInfo() {
//        int cameraCount = Camera.getNumberOfCameras();
//        Log.d(TAG, "Total cameras detected: " + cameraCount);
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        for (int i = 0; i < cameraCount; i++) {
//            Camera.getCameraInfo(i, cameraInfo);
//            String facing = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "FRONT" : "BACK/USB";
//            Log.d(TAG, "Camera ID " + i + ": facing=" + facing);
//            try {
//                Camera tempCamera = Camera.open(i);
//                Camera.Parameters params = tempCamera.getParameters();
//                List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
//                StringBuilder sizes = new StringBuilder();
//                for (Camera.Size size : previewSizes) {
//                    sizes.append(size.width).append("x").append(size.height).append(", ");
//                }
//                Log.d(TAG, "Camera ID " + i + ": Supported preview sizes: " + sizes);
//                tempCamera.release();
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to inspect camera ID " + i + ": " + e.getMessage());
//            }
//        }
//    }
//
//    private void updateToggleButtons() {
//        runOnUiThread(() -> {
//            btnToggleFront.setText(frontCamera != null ? "Turn Off USB Camera" : "Turn On USB Camera");
//            btnToggleUSB.setText(usbCamera != null ? "Turn Off Front Camera" : "Turn On Front Camera");
//            btnCaptureFront.setEnabled(frontCamera != null && !isFrontRecording);
//            btnCaptureUSB.setEnabled(usbCamera != null && !isUSBRecording);
//            btnRecordFront.setEnabled(frontCamera != null);
//            btnRecordUSB.setEnabled(usbCamera != null);
//            btnRecordFront.setText(isFrontRecording ? "Stop & Save Video" : "Record USB Video");
//            btnRecordUSB.setText(isUSBRecording ? "Stop & Save Video" : "Record Front Video");
//        });
//    }
//
//    private void toggleFrontCamera() {
//        frontCameraExecutor.submit(() -> {
//            if (frontCamera != null) {
//                stopFrontCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "USB Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startFrontCameraWithRetry(0);
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    if (frontCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
//    }
//
//    private void toggleUSBCamera() {
//        usbCameraExecutor.submit(() -> {
//            if (usbCamera != null) {
//                stopUSBCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "Front Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startUSBCameraWithRetry(0);
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    if (usbCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
//    }
//
//    private boolean checkPermissions() {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private void requestPermissions() {
//        ActivityCompat.requestPermissions(this, new String[]{
//                Manifest.permission.CAMERA,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                Manifest.permission.RECORD_AUDIO
//        }, 100);
//    }
//
//    private void setupCameraCallbacks() {
//        frontHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                Log.d(TAG, "Front camera surface created");
//                frontCameraExecutor.submit(() -> startFrontCameraWithRetry(0));
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "Front camera surface destroyed");
//                frontCameraExecutor.submit(() -> stopFrontCamera());
//            }
//        });
//
//        usbHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface created");
//                usbCameraExecutor.submit(() -> startUSBCameraWithRetry(0));
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface destroyed");
//                usbCameraExecutor.submit(() -> stopUSBCamera());
//            }
//        });
//    }
//
//    private void startFrontCameraWithRetry(int retryCount) {
//        if (frontCamera != null) {
//            Log.d(TAG, "Front camera already initialized, skipping");
//            return;
//        }
//        try {
//            if (!frontHolder.getSurface().isValid()) {
//                Log.w(TAG, "Front camera surface not valid");
//                return;
//            }
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
//                    Camera.Size selectedSize = getOptimalPreviewSize(sizes, frontCameraView.getWidth(), frontCameraView.getHeight());
//                    if (selectedSize != null) {
//                        parameters.setPreviewSize(selectedSize.width, selectedSize.height);
//                    } else {
//                        throw new RuntimeException("No supported preview sizes for front camera");
//                    }
//                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//                    try {
//                        frontCamera.setParameters(parameters);
//                    } catch (Exception e) {
//                        Log.w(TAG, "Failed to set front camera parameters: " + e.getMessage());
//                    }
//                    frontCamera.setPreviewDisplay(frontHolder);
//                    frontCamera.startPreview();
//                    globalVars.setInternalCameraConnected(true);
//                    Log.d(TAG, "Front camera started, ID: " + frontCameraId + ", Size: " + selectedSize.width + "x" + selectedSize.height);
//                } else {
//                    throw new RuntimeException("Failed to open front camera ID: " + frontCameraId);
//                }
//            } else {
//                globalVars.setInternalCameraConnected(false);
//                Log.w(TAG, "No front-facing camera found");
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show();
//                    frontCameraView.invalidate();
//                });
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Front Camera Error: " + e.getMessage(), e);
//            if (retryCount < MAX_RETRIES) {
//                try {
//                    Thread.sleep(RETRY_DELAY_MS);
//                    Log.d(TAG, "Retrying front camera start, attempt " + (retryCount + 1));
//                    startFrontCameraWithRetry(retryCount + 1);
//                } catch (InterruptedException ie) {
//                    Log.e(TAG, "Retry interrupted: " + ie.getMessage());
//                }
//            } else {
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                    frontCameraView.invalidate();
//                });
//                stopFrontCamera();
//            }
//        }
//    }
//
//    private void startUSBCameraWithRetry(int retryCount) {
//        if (usbCamera != null) {
//            Log.d(TAG, "USB camera already initialized, skipping");
//            return;
//        }
//        try {
//            if (!usbHolder.getSurface().isValid()) {
//                Log.w(TAG, "USB camera surface not valid");
//                return;
//            }
//            int usbCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            // Try to find a non-front-facing camera (likely USB)
//            for (int i = 0; i < cameraCount; i++) {
//                Camera.getCameraInfo(i, cameraInfo);
//                if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                    usbCameraId = i;
//                    break;
//                }
//            }
//            // Fallback: If no non-front-facing camera is found, try all cameras
//            if (usbCameraId == -1) {
//                Log.w(TAG, "No non-front-facing camera found, trying all camera IDs");
//                for (int i = 0; i < cameraCount; i++) {
//                    try {
//                        Camera tempCamera = Camera.open(i);
//                        if (tempCamera != null) {
//                            usbCameraId = i;
//                            tempCamera.release();
//                            break;
//                        }
//                    } catch (Exception e) {
//                        Log.w(TAG, "Camera ID " + i + " not accessible: " + e.getMessage());
//                    }
//                }
//            }
//            if (usbCameraId != -1) {
//                Log.d(TAG, "Attempting to open USB camera with ID: " + usbCameraId);
//                usbCamera = Camera.open(usbCameraId);
//                if (usbCamera != null) {
//                    Camera.Parameters parameters = usbCamera.getParameters();
//                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
//                    if (sizes == null || sizes.isEmpty()) {
//                        throw new RuntimeException("No supported preview sizes for USB camera");
//                    }
//                    Camera.Size selectedSize = getOptimalPreviewSize(sizes, usbCameraView.getWidth(), usbCameraView.getHeight());
//                    if (selectedSize != null) {
//                        parameters.setPreviewSize(selectedSize.width, selectedSize.height);
//                    } else {
//                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                        selectedSize = sizes.get(0);
//                    }
//                    try {
//                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//                    } catch (Exception e) {
//                        Log.w(TAG, "Focus mode not supported: " + e.getMessage());
//                    }
//                    try {
//                        usbCamera.setParameters(parameters);
//                    } catch (Exception e) {
//                        Log.w(TAG, "Failed to set USB camera parameters, using defaults: " + e.getMessage());
//                    }
//                    usbCamera.setPreviewDisplay(usbHolder);
//                    usbCamera.startPreview();
//                    globalVars.setUSBCameraConnected(true);
//                    Log.d(TAG, "USB camera started, ID: " + usbCameraId + ", Size: " + selectedSize.width + "x" + selectedSize.height);
//                } else {
//                    throw new RuntimeException("Failed to open USB camera ID: " + usbCameraId);
//                }
//            } else {
//                globalVars.setUSBCameraConnected(false);
//                Log.e(TAG, "No USB camera found after trying all IDs");
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "No USB camera detected", Toast.LENGTH_SHORT).show();
//                    usbCameraView.invalidate();
//                });
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "USB Camera Error: " + e.getMessage(), e);
//            if (retryCount < MAX_RETRIES) {
//                try {
//                    Thread.sleep(RETRY_DELAY_MS);
//                    Log.d(TAG, "Retrying USB camera start, attempt " + (retryCount + 1));
//                    startUSBCameraWithRetry(retryCount + 1);
//                } catch (InterruptedException ie) {
//                    Log.e(TAG, "Retry interrupted: " + ie.getMessage());
//                }
//            } else {
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                    usbCameraView.invalidate();
//                });
//                stopUSBCamera();
//            }
//        }
//    }
//
//    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
//        if (sizes == null || sizes.isEmpty()) return null;
//        Camera.Size optimalSize = null;
//        double minDiff = Double.MAX_VALUE;
//        double targetRatio = (double) targetWidth / targetHeight;
//
//        for (Camera.Size size : sizes) {
//            double ratio = (double) size.width / size.height;
//            if (Math.abs(ratio - targetRatio) > 0.1) continue;
//            if (Math.abs(size.height - targetHeight) < minDiff) {
//                optimalSize = size;
//                minDiff = Math.abs(size.height - targetHeight);
//            }
//        }
//        return optimalSize != null ? optimalSize : sizes.get(0);
//    }
//
//    private void captureFrontCameraPhoto() {
//        if (frontCamera == null || isFrontRecording) {
//            Toast.makeText(this, "USB Camera not available or recording", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        frontCamera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    "USB_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
//            }
//            cam.startPreview();
//        });
//    }
//
//    private void captureUSBCameraPhoto() {
//        if (usbCamera == null || isUSBRecording) {
//            Toast.makeText(this, "Front Camera not available or recording", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        usbCamera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    "Front_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
//            }
//            cam.startPreview();
//        });
//    }
//
//    private void toggleFrontVideoRecording() {
//        if (isFrontRecording) {
//            stopFrontVideoRecording();
//        } else {
//            startFrontVideoRecording();
//        }
//    }
//
//    private void toggleUSBVideoRecording() {
//        if (isUSBRecording) {
//            stopUSBVideoRecording();
//        } else {
//            startUSBVideoRecordingWithRetry(0);
//        }
//    }
//
//    private void startFrontVideoRecording() {
//        if (frontCamera == null) {
//            runOnUiThread(() -> Toast.makeText(this, "USB Camera not available", Toast.LENGTH_SHORT).show());
//            return;
//        }
//        frontCameraExecutor.submit(() -> {
//            try {
//                if (!frontHolder.getSurface().isValid()) {
//                    throw new IllegalStateException("USB camera surface not valid");
//                }
//                // Stop preview and unlock camera
//                frontCamera.stopPreview();
//                frontCamera.unlock();
//                frontMediaRecorder = new MediaRecorder();
//                frontMediaRecorder.setCamera(frontCamera);
//                frontMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//                frontMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//
//                // Try CamcorderProfile first
//                boolean profileSuccess = false;
//                CamcorderProfile profile = null;
//                try {
//                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
//                    frontMediaRecorder.setProfile(profile);
//                    Log.d(TAG, "Using QUALITY_480P for USB camera");
//                    profileSuccess = true;
//                } catch (Exception e) {
//                    Log.w(TAG, "QUALITY_480P not supported for USB camera: " + e.getMessage());
//                    try {
//                        profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
//                        frontMediaRecorder.setProfile(profile);
//                        Log.d(TAG, "Falling back to QUALITY_LOW for USB camera");
//                        profileSuccess = true;
//                    } catch (Exception e2) {
//                        Log.w(TAG, "QUALITY_LOW not supported for USB camera: " + e2.getMessage());
//                    }
//                }
//
//                // If CamcorderProfile fails, use custom settings
//                if (!profileSuccess) {
//                    Log.d(TAG, "Using custom MediaRecorder settings for USB camera");
//                    frontMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//                    frontMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//                    frontMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//                    frontMediaRecorder.setVideoSize(640, 480); // Low resolution
//                    frontMediaRecorder.setVideoFrameRate(30);
//                    frontMediaRecorder.setVideoEncodingBitRate(3000000);
//                }
//
//                // Ensure output directory exists
//                File outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
//                if (outputDir != null && !outputDir.exists()) {
//                    outputDir.mkdirs();
//                }
//                if (outputDir == null || !outputDir.canWrite()) {
//                    throw new IOException("Cannot write to output directory");
//                }
//                File videoFile = new File(outputDir,
//                        "USB_VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4");
//                frontMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
//                frontMediaRecorder.setPreviewDisplay(frontHolder.getSurface());
//
//                try {
//                    frontMediaRecorder.prepare();
//                } catch (Exception e) {
//                    Log.e(TAG, "MediaRecorder prepare failed: " + e.getMessage(), e);
//                    throw new IOException("Prepare failed: " + e.getMessage());
//                }
//                frontMediaRecorder.start();
//                isFrontRecording = true;
//                startTime = System.currentTimeMillis();
//                runOnUiThread(() -> {
//                    timerHandler.post(timerRunnable);
//                    timerView.setVisibility(TextView.VISIBLE);
//                    updateToggleButtons();
//                    Toast.makeText(CameraFeedActivity.this, "USB Recording Started", Toast.LENGTH_SHORT).show();
//                });
//            } catch (IllegalStateException e) {
//                Log.e(TAG, "USB Video Recording IllegalStateException: " + e.getMessage(), e);
//                releaseFrontMediaRecorder();
//                frontCamera.lock();
//                frontCamera.startPreview();
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start USB recording: Illegal state", Toast.LENGTH_SHORT).show());
//            } catch (IOException e) {
//                Log.e(TAG, "USB Video Recording IOException: " + e.getMessage(), e);
//                releaseFrontMediaRecorder();
//                frontCamera.lock();
//                frontCamera.startPreview();
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start USB recording: IO error", Toast.LENGTH_SHORT).show());
//            } catch (RuntimeException e) {
//                Log.e(TAG, "USB Video Recording RuntimeException: " + e.getMessage(), e);
//                releaseFrontMediaRecorder();
//                frontCamera.lock();
//                frontCamera.startPreview();
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start USB recording: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//            } catch (Exception e) {
//                Log.e(TAG, "USB Video Recording General Exception: " + e.getMessage(), e);
//                releaseFrontMediaRecorder();
//                frontCamera.lock();
//                frontCamera.startPreview();
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start USB recording: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//            }
//        });
//    }
//
//    private void startUSBVideoRecordingWithRetry(int retryCount) {
//        if (usbCamera == null) {
//            runOnUiThread(() -> Toast.makeText(this, "Front Camera not available", Toast.LENGTH_SHORT).show());
//            return;
//        }
//        usbCameraExecutor.submit(() -> {
//            try {
//                if (!usbHolder.getSurface().isValid()) {
//                    throw new IllegalStateException("USB camera surface not valid");
//                }
//                usbCamera.stopPreview();
//                usbCamera.unlock();
//                usbMediaRecorder = new MediaRecorder();
//                usbMediaRecorder.setCamera(usbCamera);
//                usbMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//                usbMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//                CamcorderProfile profile = null;
//                try {
//                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
//                    Log.d(TAG, "Attempting USB recording with QUALITY_480P");
//                } catch (Exception e) {
//                    Log.w(TAG, "QUALITY_480P not supported, falling back to QUALITY_LOW");
//                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
//                }
//                usbMediaRecorder.setProfile(profile);
//                File videoFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
//                        "Front_VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4");
//                usbMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
//                usbMediaRecorder.setPreviewDisplay(usbHolder.getSurface());
//                try {
//                    usbMediaRecorder.prepare();
//                } catch (Exception e) {
//                    Log.e(TAG, "MediaRecorder prepare failed: " + e.getMessage(), e);
//                    throw new IOException("Prepare failed: " + e.getMessage());
//                }
//                usbMediaRecorder.start();
//                isUSBRecording = true;
//                startTime = System.currentTimeMillis();
//                runOnUiThread(() -> {
//                    timerHandler.post(timerRunnable);
//                    timerView.setVisibility(TextView.VISIBLE);
//                    updateToggleButtons();
//                    Toast.makeText(CameraFeedActivity.this, "Front Recording Started", Toast.LENGTH_SHORT).show();
//                });
//            } catch (IllegalStateException e) {
//                Log.e(TAG, "USB Video Recording IllegalStateException: " + e.getMessage(), e);
//                handleUSBRecordingFailure("Illegal state: " + e.getMessage(), retryCount);
//            } catch (IOException e) {
//                Log.e(TAG, "USB Video Recording IOException: " + e.getMessage(), e);
//                handleUSBRecordingFailure("IO error: " + e.getMessage(), retryCount);
//            } catch (Exception e) {
//                Log.e(TAG, "USB Video Recording General Exception: " + e.getMessage(), e);
//                handleUSBRecordingFailure("General error: " + e.getMessage(), retryCount);
//            }
//        });
//    }
//
//    private void handleUSBRecordingFailure(String errorMessage, int retryCount) {
//        releaseUSBMediaRecorder();
//        if (usbCamera != null) {
//            try {
//                usbCamera.lock();
//                usbCamera.startPreview();
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to restart USB camera preview: " + e.getMessage());
//            }
//        }
//        if (retryCount < MAX_RETRIES) {
//            try {
//                Thread.sleep(RETRY_DELAY_MS);
//                Log.d(TAG, "Retrying USB video recording, attempt " + (retryCount + 1));
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Retrying USB recording...", Toast.LENGTH_SHORT).show());
//                startUSBVideoRecordingWithRetry(retryCount + 1);
//            } catch (InterruptedException ie) {
//                Log.e(TAG, "Retry interrupted: " + ie.getMessage());
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start Front recording: " + errorMessage, Toast.LENGTH_SHORT).show());
//            }
//        } else {
//            runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Failed to start Front recording: " + errorMessage, Toast.LENGTH_SHORT).show());
//            usbCameraExecutor.submit(() -> {
//                stopUSBCamera();
//                startUSBCameraWithRetry(0);
//                runOnUiThread(() -> updateToggleButtons());
//            });
//        }
//    }
//
//    private void stopFrontVideoRecording() {
//        frontCameraExecutor.submit(() -> {
//            try {
//                if (frontMediaRecorder != null && isFrontRecording) {
//                    frontMediaRecorder.stop();
//                    releaseFrontMediaRecorder();
//                    frontCamera.lock();
//                    frontCamera.startPreview();
//                    isFrontRecording = false;
//                    runOnUiThread(() -> {
//                        timerHandler.removeCallbacks(timerRunnable);
//                        timerView.setVisibility(TextView.GONE);
//                        updateToggleButtons();
//                        Toast.makeText(CameraFeedActivity.this, "USB Video Saved", Toast.LENGTH_SHORT).show();
//                    });
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping front video: " + e.getMessage(), e);
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Error stopping USB recording", Toast.LENGTH_SHORT).show());
//            } finally {
//                releaseFrontMediaRecorder();
//                if (frontCamera != null) {
//                    try {
//                        frontCamera.lock();
//                        frontCamera.startPreview();
//                    } catch (Exception e) {
//                        Log.e(TAG, "Failed to restart front camera preview: " + e.getMessage());
//                    }
//                }
//            }
//        });
//    }
//
//    private void stopUSBVideoRecording() {
//        usbCameraExecutor.submit(() -> {
//            try {
//                if (usbMediaRecorder != null && isUSBRecording) {
//                    usbMediaRecorder.stop();
//                    releaseUSBMediaRecorder();
//                    usbCamera.lock();
//                    usbCamera.startPreview();
//                    isUSBRecording = false;
//                    runOnUiThread(() -> {
//                        timerHandler.removeCallbacks(timerRunnable);
//                        timerView.setVisibility(TextView.GONE);
//                        updateToggleButtons();
//                        Toast.makeText(CameraFeedActivity.this, "Front Video Saved", Toast.LENGTH_SHORT).show();
//                    });
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping USB video: " + e.getMessage(), e);
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Error stopping Front recording", Toast.LENGTH_SHORT).show());
//            } finally {
//                releaseUSBMediaRecorder();
//                if (usbCamera != null) {
//                    try {
//                        usbCamera.lock();
//                        usbCamera.startPreview();
//                    } catch (Exception e) {
//                        Log.e(TAG, "Failed to restart USB camera preview: " + e.getMessage());
//                    }
//                }
//            }
//        });
//    }
//
//    private void releaseFrontMediaRecorder() {
//        if (frontMediaRecorder != null) {
//            try {
//                frontMediaRecorder.reset();
//                frontMediaRecorder.release();
//            } catch (Exception e) {
//                Log.e(TAG, "Error releasing front media recorder: " + e.getMessage());
//            }
//            frontMediaRecorder = null;
//        }
//    }
//
//    private void releaseUSBMediaRecorder() {
//        if (usbMediaRecorder != null) {
//            try {
//                usbMediaRecorder.reset();
//                usbMediaRecorder.release();
//            } catch (Exception e) {
//                Log.e(TAG, "Error releasing USB media recorder: " + e.getMessage());
//            }
//            usbMediaRecorder = null;
//        }
//    }
//
//    private void stopFrontCamera() {
//        if (isFrontRecording) {
//            stopFrontVideoRecording();
//        }
//        if (frontCamera != null) {
//            try {
//                frontCamera.stopPreview();
//                frontCamera.setPreviewDisplay(null);
//                frontCamera.release();
//                frontCamera = null;
//                globalVars.setInternalCameraConnected(false);
//                try {
//                    Thread.sleep(CLEANUP_DELAY_MS);
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage());
//                }
//                runOnUiThread(() -> frontCameraView.invalidate());
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping front camera: " + e.getMessage());
//                runOnUiThread(() -> frontCameraView.invalidate());
//            }
//        }
//    }
//
//    private void stopUSBCamera() {
//        if (isUSBRecording) {
//            stopUSBVideoRecording();
//        }
//        if (usbCamera != null) {
//            try {
//                usbCamera.stopPreview();
//                usbCamera.setPreviewDisplay(null);
//                usbCamera.release();
//                usbCamera = null;
//                globalVars.setUSBCameraConnected(false);
//                try {
//                    Thread.sleep(CLEANUP_DELAY_MS);
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage());
//                }
//                runOnUiThread(() -> usbCameraView.invalidate());
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping USB camera: " + e.getMessage());
//                runOnUiThread(() -> usbCameraView.invalidate());
//            }
//        }
//    }
//
//    private void setupUsbReceiver() {
//        usbReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                String action = intent.getAction();
//                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Attached: " + (device != null ? device.getDeviceName() : "null"));
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        startUSBCameraWithRetry(0);
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Detected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
//                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Detached: " + (device != null ? device.getDeviceName() : "null"));
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Disconnected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
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
//        frontCameraExecutor.submit(() -> stopFrontCamera());
//        usbCameraExecutor.submit(() -> stopUSBCamera());
//        frontCameraExecutor.shutdown();
//        usbCameraExecutor.shutdown();
//        timerHandler.removeCallbacks(timerRunnable);
//        releaseFrontMediaRecorder();
//        releaseUSBMediaRecorder();
//        globalVars.setCameraPreviewActive(false);
//        if (usbReceiver != null) {
//            unregisterReceiver(usbReceiver);
//        }
//        Log.d(TAG, "CameraFeedActivity destroyed, threads shut down");
//    }
//}
//-----------------------------------------------



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

        // Initialize UI components
        frontCameraView = findViewById(R.id.usbCameraView); // Front camera (mapped to USB view)
        usbCameraView = findViewById(R.id.internalCameraView); // USB camera (mapped to internal view)

        btnCaptureFront = findViewById(R.id.btn_capture_usb); // Capture front camera
        btnCaptureUSB = findViewById(R.id.btn_capture_internal); // Capture USB camera

        btnToggleFront = findViewById(R.id.btn_toggle_usb); // Toggle front camera
        btnToggleUSB = findViewById(R.id.btn_toggle_front); // Toggle USB camera

        frontHolder = frontCameraView.getHolder();
        usbHolder = usbCameraView.getHolder();

        // Separate executors for each camera
        frontCameraExecutor = Executors.newSingleThreadExecutor();
        usbCameraExecutor = Executors.newSingleThreadExecutor();

        // Check permissions
        if (checkPermissions()) {
            setupCameraCallbacks();
            logCameraInfo();
            syncCameraStateWithGlobalVars(); // Sync cameras with GlobalVars
        } else {
            requestPermissions();
        }

        // Set button listeners
        btnCaptureFront.setOnClickListener(view -> captureFrontCameraPhoto());
        btnCaptureUSB.setOnClickListener(view -> captureUSBCameraPhoto());

        btnToggleFront.setOnClickListener(view -> toggleFrontCamera());
        btnToggleUSB.setOnClickListener(view -> toggleUSBCamera());

        updateToggleButtons();

        setupUsbReceiver();

        // Hide action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void syncCameraStateWithGlobalVars() {
        frontCameraExecutor.submit(() -> {
            if (GlobalVars.isCam1On() && frontCamera == null) {
                startFrontCamera();
            } else if (!GlobalVars.isCam1On() && frontCamera != null) {
                stopFrontCamera();
            }
        });

        usbCameraExecutor.submit(() -> {
            if (GlobalVars.isCam2On() && usbCamera == null) {
                startUSBCamera();
            } else if (!GlobalVars.isCam2On() && usbCamera != null) {
                stopUSBCamera();
            }
        });

        runOnUiThread(() -> updateToggleButtons());
    }

    private void logCameraInfo() {
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            Log.d(TAG, "Camera ID " + i + ": facing=" + (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "FRONT" : "BACK/USB"));
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
                GlobalVars.setCam1On(false);
                runOnUiThread(() -> {
                    updateToggleButtons();
                    frontCameraView.invalidate();
                    Toast.makeText(CameraFeedActivity.this, "Front Camera Off", Toast.LENGTH_SHORT).show();
                });
            } else {
                startFrontCamera();
                GlobalVars.setCam1On(true);
                GlobalVars.setCam2On(false); // Ensure USB camera is off
                usbCameraExecutor.submit(() -> stopUSBCamera());
                runOnUiThread(() -> {
                    updateToggleButtons();
                    frontCameraView.invalidate();
                    if (frontCamera != null) {
                        Toast.makeText(CameraFeedActivity.this, "Front Camera Started", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CameraFeedActivity.this, "Front Camera Not Available", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void toggleUSBCamera() {
        usbCameraExecutor.submit(() -> {
            if (usbCamera != null) {
                stopUSBCamera();
                GlobalVars.setCam2On(false);
                runOnUiThread(() -> {
                    updateToggleButtons();
                    usbCameraView.invalidate();
                    Toast.makeText(CameraFeedActivity.this, "USB Camera Off", Toast.LENGTH_SHORT).show();
                });
            } else {
                startUSBCamera();
                GlobalVars.setCam2On(true);
                GlobalVars.setCam1On(false); // Ensure front camera is off
                frontCameraExecutor.submit(() -> stopFrontCamera());
                runOnUiThread(() -> {
                    updateToggleButtons();
                    usbCameraView.invalidate();
                    if (usbCamera != null) {
                        Toast.makeText(CameraFeedActivity.this, "USB Camera Started", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CameraFeedActivity.this, "USB Camera Not Available", Toast.LENGTH_SHORT).show();
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
                frontCameraExecutor.submit(() -> {
                    if (GlobalVars.isCam1On()) {
                        startFrontCamera();
                    }
                });
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
                usbCameraExecutor.submit(() -> {
                    if (GlobalVars.isCam2On()) {
                        startUSBCamera();
                    }
                });
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

    // Completely separate method for front camera // this usb cam view
    private void startFrontCamera() {
        if (frontCamera != null) {
            Log.d(TAG, "Front camera already initialized, skipping");
            return;
        }
        try {
            int frontCameraId = -1;
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            // Strictly find front-facing camera
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
                    if (sizes != null && !sizes.isEmpty()) {
                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
                    } else {
                        throw new RuntimeException("No supported preview sizes for front camera");
                    }
                    frontCamera.setParameters(parameters);

                    frontCamera.setPreviewDisplay(frontHolder); // Strictly frontHolder
                    frontCamera.startPreview();
                    globalVars.setInternalCameraConnected(true);
                    Log.d(TAG, "Front camera started, ID: " + frontCameraId + ", Thread: " + Thread.currentThread().getName());
                } else {
                    throw new RuntimeException("Failed to open front camera ID: " + frontCameraId);
                }
            } else {
                globalVars.setInternalCameraConnected(false);
                Log.w(TAG, "No front-facing camera found");
                runOnUiThread(() -> {
                    Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show();
                    frontCameraView.invalidate(); // Ensure view is cleared
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Front Camera Error: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                frontCameraView.invalidate();
            });
            stopFrontCamera();
        }
    }

    // Completely separate method for USB camera // this is internal cam view
    private void startUSBCamera() {
        if (usbCamera != null) {
            Log.d(TAG, "USB camera already initialized, skipping");
            return;
        }
        try {
            int usbCameraId = -1;
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            // Strictly find non-front-facing camera (assumed USB)
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
                    if (sizes != null && !sizes.isEmpty()) {
                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
                    } else {
                        throw new RuntimeException("No supported preview sizes for USB camera");
                    }
                    usbCamera.setParameters(parameters);

                    usbCamera.setPreviewDisplay(usbHolder); // Strictly usbHolder
                    usbCamera.startPreview();
                    globalVars.setUSBCameraConnected(true);
                    Log.d(TAG, "USB camera started, ID: " + usbCameraId + ", Thread: " + Thread.currentThread().getName());
                } else {
                    throw new RuntimeException("Failed to open USB camera ID: " + usbCameraId);
                }
            } else {
                globalVars.setUSBCameraConnected(false);
                Log.w(TAG, "No USB camera found");
                runOnUiThread(() -> {
                    Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show();
                    usbCameraView.invalidate(); // Ensure view is cleared
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "USB Camera Error: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                usbCameraView.invalidate();
            });
            stopUSBCamera();
        }
    }

    // Separate method for front camera photo capture
    private void captureFrontCameraPhoto() {
        if (frontCamera == null) {
            Toast.makeText(this, "Front Camera not available", Toast.LENGTH_SHORT).show();
            return;
        }
        frontCamera.takePicture(null, null, (data, cam) -> {
            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "USB_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg"); // usb image path name
            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                fos.write(data);
                fos.flush();
                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
            }
            cam.startPreview();
        });
    }

    // Separate method for USB camera photo capture
    private void captureUSBCameraPhoto() {
        if (usbCamera == null) {
            Toast.makeText(this, "USB Camera not available", Toast.LENGTH_SHORT).show();
            return;
        }
        usbCamera.takePicture(null, null, (data, cam) -> {
            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Front_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                fos.write(data);
                fos.flush();
                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
            }
            cam.startPreview();
        });
    }

    // Separate method to stop front camera
    private void stopFrontCamera() {
        if (frontCamera != null) {
            try {
                frontCamera.stopPreview();
                frontCamera.setPreviewDisplay(null);
                frontCamera.release();
                frontCamera = null;
                globalVars.setInternalCameraConnected(false);
                runOnUiThread(() -> frontCameraView.invalidate());
            } catch (Exception e) {
                runOnUiThread(() -> frontCameraView.invalidate());
            }
        }
    }

    // Separate method to stop USB camera
    private void stopUSBCamera() {
        if (usbCamera != null) {
            try {
                usbCamera.stopPreview();
                usbCamera.setPreviewDisplay(null);
                usbCamera.release();
                usbCamera = null;
                globalVars.setUSBCameraConnected(false);
                try {
                    Thread.sleep(CLEANUP_DELAY_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage(), e);
                }
                runOnUiThread(() -> usbCameraView.invalidate());
            } catch (Exception e) {
                Log.e(TAG, "Error stopping USB camera: " + e.getMessage(), e);
                runOnUiThread(() -> usbCameraView.invalidate());
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
                        syncCameraStateWithGlobalVars(); // Sync with GlobalVars
                        runOnUiThread(() -> {
                            updateToggleButtons();
                            logCameraInfo();
                            Toast.makeText(CameraFeedActivity.this, "USB Camera Detected", Toast.LENGTH_SHORT).show();
                        });
                    });
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d(TAG, "USB Device Detached: " + device.getDeviceName());
                    usbCameraExecutor.submit(() -> {
                        stopUSBCamera();
                        globalVars.setUSBCameraConnected(false);
                        syncCameraStateWithGlobalVars(); // Sync with GlobalVars
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









//------------------




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
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class CameraFeedActivity extends AppCompatActivity {
//
//    private SurfaceView frontCameraView, usbCameraView;
//    private Camera frontCamera, usbCamera;
//    private Button btnCaptureFront, btnCaptureUSB, btnToggleFront, btnToggleUSB;
//    private GlobalVars globalVars;
//    private SurfaceHolder frontHolder, usbHolder;
//    private BroadcastReceiver usbReceiver;
//    private ExecutorService frontCameraExecutor;
//    private ExecutorService usbCameraExecutor;
//
//
//    private static final String TAG = "CameraFeed";
//    private static final long CLEANUP_DELAY_MS = 500;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_camera_feed);
//
//        globalVars = GlobalVars.getInstance();
//        globalVars.setCameraPreviewActive(true);
//
//        // Initialize UI components
//        frontCameraView = findViewById(R.id.usbCameraView); // usbcam
//        usbCameraView = findViewById(R.id.internalCameraView); // io Cam
//
//        btnCaptureFront = findViewById(R.id.btn_capture_usb);// btn capture usb
//        btnCaptureUSB = findViewById(R.id.btn_capture_internal); // btn capture inrnal
//
//        btnToggleFront = findViewById(R.id.btn_toggle_usb);// btn togel usb
//        btnToggleUSB = findViewById(R.id.btn_toggle_front);// btn togel front
//
//        frontHolder = frontCameraView.getHolder();
//        usbHolder = usbCameraView.getHolder();
//
//        // Separate executors for each camera
//        frontCameraExecutor = Executors.newSingleThreadExecutor();
//        usbCameraExecutor = Executors.newSingleThreadExecutor();
//
//        // Check permissions
//        if (checkPermissions()) {
//            setupCameraCallbacks();
//            logCameraInfo();
//        } else {
//            requestPermissions();
//        }
//
//        // Set button listeners
//        btnCaptureFront.setOnClickListener(view -> captureFrontCameraPhoto());
//        btnCaptureUSB.setOnClickListener(view -> captureUSBCameraPhoto());
//
//        btnToggleFront.setOnClickListener(view -> toggleFrontCamera());
//        btnToggleUSB.setOnClickListener(view -> toggleUSBCamera());
//
//        updateToggleButtons();
//        setupUsbReceiver();
//
//
//        // Hide action bar
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
//    }
//
//    private void logCameraInfo() {
//        int cameraCount = Camera.getNumberOfCameras();
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        for (int i = 0; i < cameraCount; i++) {
//            Camera.getCameraInfo(i, cameraInfo);
//            Log.d(TAG, "Camera ID " + i + ": facing=" + (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "FRONT" : "BACK/USB"));
//        }
//    }
//
//    private void updateToggleButtons() {
//        runOnUiThread(() -> {
//            btnToggleFront.setText(frontCamera != null ? "Turn Off Front Camera" : "Turn On Front Camera");
//            btnToggleUSB.setText(usbCamera != null ? "Turn Off USB Camera" : "Turn On USB Camera");
//            btnCaptureFront.setEnabled(frontCamera != null);
//            btnCaptureUSB.setEnabled(usbCamera != null);
//        });
//    }
//
//    private void toggleFrontCamera() {
//        frontCameraExecutor.submit(() -> {
//            if (frontCamera != null) {
//                stopFrontCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "Front Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startFrontCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    if (frontCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
//    }
//
//    private void toggleUSBCamera() {
//        usbCameraExecutor.submit(() -> {
//            if (usbCamera != null) {
//                stopUSBCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "USB Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startUSBCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    if (usbCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
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
//                Log.d(TAG, "Front camera surface created");
//                frontCameraExecutor.submit(() -> startFrontCamera());
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "Front camera surface destroyed");
//                frontCameraExecutor.submit(() -> stopFrontCamera());
//            }
//        });
//
//        usbHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface created");
//                usbCameraExecutor.submit(() -> startUSBCamera());
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface destroyed");
//                usbCameraExecutor.submit(() -> stopUSBCamera());
//            }
//        });
//    }
//
//    // Completely separate method for front camera // this usb cam view
//    private void startFrontCamera() {
//        if (frontCamera != null) {
//            Log.d(TAG, "Front camera already initialized, skipping");
//            return;
//        }
//        try {
//            int frontCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            // Strictly find front-facing camera
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
//                    if (sizes != null && !sizes.isEmpty()) {
//                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    } else {
//                        throw new RuntimeException("No supported preview sizes for front camera");
//                    }
//                    frontCamera.setParameters(parameters);
//
//
//                    frontCamera.setPreviewDisplay(frontHolder); // Strictly frontHolder
//                    frontCamera.startPreview();
//                    globalVars.setInternalCameraConnected(true);
//                    Log.d(TAG, "Front camera started, ID: " + frontCameraId + ", Thread: " + Thread.currentThread().getName());
//                } else {
//                    throw new RuntimeException("Failed to open front camera ID: " + frontCameraId);
//                }
//            } else {
//                globalVars.setInternalCameraConnected(false);
//                Log.w(TAG, "No front-facing camera found");
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show();
//                    frontCameraView.invalidate(); // Ensure view is cleared
//                });
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Front Camera Error: " + e.getMessage(), e);
//            runOnUiThread(() -> {
//                Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                frontCameraView.invalidate();
//            });
//            stopFrontCamera();
//        }
//    }
//
//    // Completely separate method for USB camera // this is intarnal cam view
//    private void startUSBCamera() {
//        if (usbCamera != null) {
//            Log.d(TAG, "USB camera already initialized, skipping");
//            return;
//        }
//        try {
//            int usbCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            // Strictly find non-front-facing camera (assumed USB)
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
//                    if (sizes != null && !sizes.isEmpty()) {
//                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    } else {
//                        throw new RuntimeException("No supported preview sizes for USB camera");
//                    }
//                    usbCamera.setParameters(parameters);
//
//                    usbCamera.setPreviewDisplay(usbHolder); // Strictly usbHolder
//                    usbCamera.startPreview();
//                    globalVars.setUSBCameraConnected(true);
//                    Log.d(TAG, "USB camera started, ID: " + usbCameraId + ", Thread: " + Thread.currentThread().getName());
//                } else {
//                    throw new RuntimeException("Failed to open USB camera ID: " + usbCameraId);
//                }
//            } else {
//                globalVars.setUSBCameraConnected(false);
//                Log.w(TAG, "No USB camera found");
//                runOnUiThread(() -> {
//                    Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show();
//                    usbCameraView.invalidate(); // Ensure view is cleared
//                });
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "USB Camera Error: " + e.getMessage(), e);
//            runOnUiThread(() -> {
//                Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//                usbCameraView.invalidate();
//            });
//            stopUSBCamera();
//        }
//    }
//
//    // Separate method for front camera photo capture
//    private void captureFrontCameraPhoto() {
//        if (frontCamera == null) {
//            Toast.makeText(this, "Front Camera not available", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        frontCamera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    "USB_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg"); // usb image path name
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
//            }
//            cam.startPreview();
//        });
//    }
//
//    // Separate method for USB camera photo capture
//    private void captureUSBCameraPhoto() {
//        if (usbCamera == null) {
//            return;
//        }
//        usbCamera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    "Front_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Toast.makeText(CameraFeedActivity.this, "Error saving photo", Toast.LENGTH_SHORT).show();
//            }
//            cam.startPreview();
//        });
//    }
//
//    // Separate method to stop front camera
//    private void stopFrontCamera() {
//        if (frontCamera != null) {
//            try {
//                frontCamera.stopPreview();
//                frontCamera.setPreviewDisplay(null);
//                frontCamera.release();
//                frontCamera = null;
//                globalVars.setInternalCameraConnected(false);
//                runOnUiThread(() -> frontCameraView.invalidate());
//            } catch (Exception e) {
//                runOnUiThread(() -> frontCameraView.invalidate());
//            }
//        }
//    }
//
//    // Separate method to stop USB camera
//    private void stopUSBCamera() {
//        if (usbCamera != null) {
//            try {
//                usbCamera.stopPreview();
//                usbCamera.setPreviewDisplay(null);
//                usbCamera.release();
//                usbCamera = null;
//                globalVars.setUSBCameraConnected(false);
//                try {
//                    Thread.sleep(CLEANUP_DELAY_MS);
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage(), e);
//                }
//                runOnUiThread(() -> usbCameraView.invalidate());
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping USB camera: " + e.getMessage(), e);
//                runOnUiThread(() -> usbCameraView.invalidate());
//            }
//        }
//    }
//
//    private void setupUsbReceiver() {
//        usbReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                String action = intent.getAction();
//                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Attached: " + device.getDeviceName());
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        startUSBCamera();
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Detected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
//                    // Front camera remains unaffected
//                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Detached: " + device.getDeviceName());
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Disconnected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
//                    // Front camera remains unaffected
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
//        frontCameraExecutor.submit(() -> stopFrontCamera());
//        usbCameraExecutor.submit(() -> stopUSBCamera());
//        frontCameraExecutor.shutdown();
//        usbCameraExecutor.shutdown();
//        globalVars.setCameraPreviewActive(false);
//        if (usbReceiver != null) {
//            unregisterReceiver(usbReceiver);
//        }
//        Log.d(TAG, "CameraFeedActivity destroyed, threads shut down");
//    }
//}

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
//import java.util.HashSet;
//import java.util.List;
//import java.util.Locale;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class CameraFeedActivity extends AppCompatActivity {
//
//    private SurfaceView frontCameraView, usbCameraView;
//    private Camera frontCamera, usbCamera;
//    private Button btnCaptureFront, btnCaptureUSB, btnToggleFront, btnToggleUSB;
//    private GlobalVars globalVars;
//    private SurfaceHolder frontHolder, usbHolder;
//    private BroadcastReceiver usbReceiver;
//    private ExecutorService frontCameraExecutor;
//    private ExecutorService usbCameraExecutor;
//    private Set<Integer> usedCameraIds; // Track used camera IDs to avoid conflicts
//
//    private static final String TAG = "CameraFeed";
//    private static final long CLEANUP_DELAY_MS = 500;
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
//        btnToggleFront = findViewById(R.id.btn_toggle_front);
//        btnToggleUSB = findViewById(R.id.btn_toggle_usb);
//
//        frontHolder = frontCameraView.getHolder();
//        usbHolder = usbCameraView.getHolder();
//
//        frontCameraExecutor = Executors.newSingleThreadExecutor();
//        usbCameraExecutor = Executors.newSingleThreadExecutor();
//        usedCameraIds = new HashSet<>();
//
//        if (checkPermissions()) {
//            setupCameraCallbacks();
//            logCameraInfo();
//        } else {
//            requestPermissions();
//        }
//
//        btnCaptureFront.setOnClickListener(view -> capturePhoto(frontCamera, "Front"));
//        btnCaptureUSB.setOnClickListener(view -> capturePhoto(usbCamera, "USB"));
//
//        btnToggleFront.setOnClickListener(view -> toggleFrontCamera());
//        btnToggleUSB.setOnClickListener(view -> toggleUSBCamera());
//
//        updateToggleButtons();
//        setupUsbReceiver();
//
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
//    }
//
//    private void logCameraInfo() {
//        int cameraCount = Camera.getNumberOfCameras();
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        for (int i = 0; i < cameraCount; i++) {
//            Camera.getCameraInfo(i, cameraInfo);
//            Log.d(TAG, "Camera ID " + i + ": facing=" + (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "FRONT" : "BACK/USB"));
//        }
//    }
//
//    private void updateToggleButtons() {
//        runOnUiThread(() -> {
//            btnToggleFront.setText(frontCamera != null ? "Turn Off Front Camera" : "Turn On Front Camera");
//            btnToggleUSB.setText(usbCamera != null ? "Turn Off USB Camera" : "Turn On USB Camera");
//            btnCaptureFront.setEnabled(frontCamera != null);
//            btnCaptureUSB.setEnabled(usbCamera != null);
//        });
//    }
//
//    private void toggleFrontCamera() {
//        frontCameraExecutor.submit(() -> {
//            if (frontCamera != null) {
//                stopFrontCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "Front Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startFrontCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    frontCameraView.invalidate();
//                    if (frontCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "Front Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
//    }
//
//    private void toggleUSBCamera() {
//        usbCameraExecutor.submit(() -> {
//            if (usbCamera != null) {
//                stopUSBCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    Toast.makeText(CameraFeedActivity.this, "USB Camera Off", Toast.LENGTH_SHORT).show();
//                });
//            } else {
//                startUSBCamera();
//                runOnUiThread(() -> {
//                    updateToggleButtons();
//                    usbCameraView.invalidate();
//                    if (usbCamera != null) {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Started", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraFeedActivity.this, "USB Camera Not Available", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//        });
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
//                Log.d(TAG, "Front camera surface created");
//                frontCameraExecutor.submit(() -> startFrontCamera());
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "Front camera surface destroyed");
//                frontCameraExecutor.submit(() -> stopFrontCamera());
//            }
//        });
//
//        usbHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface created");
//                usbCameraExecutor.submit(() -> startUSBCamera());
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "USB camera surface destroyed");
//                usbCameraExecutor.submit(() -> stopUSBCamera());
//            }
//        });
//    }
//
//    private void startFrontCamera() {
//        if (frontCamera != null) {
//            Log.d(TAG, "Front camera already initialized, skipping");
//            return;
//        }
//        try {
//            int frontCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            for (int i = 0; i < cameraCount; i++) {
//                Camera.getCameraInfo(i, cameraInfo);
//                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && !usedCameraIds.contains(i)) {
//                    frontCameraId = i;
//                    break;
//                }
//            }
//            if (frontCameraId != -1) {
//                frontCamera = Camera.open(frontCameraId);
//                if (frontCamera != null) {
//                    Camera.Parameters parameters = frontCamera.getParameters();
//                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
//                    if (sizes != null && !sizes.isEmpty()) {
//                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    }
//                    frontCamera.setParameters(parameters);
//                    frontCamera.setPreviewDisplay(frontHolder);
//                    frontCamera.startPreview();
//                    usedCameraIds.add(frontCameraId);
//                    globalVars.setInternalCameraConnected(true);
//                    Log.d(TAG, "Front camera started, ID: " + frontCameraId + ", Thread: " + Thread.currentThread().getName());
//                } else {
//                    throw new RuntimeException("Failed to open front camera ID: " + frontCameraId);
//                }
//            } else {
//                globalVars.setInternalCameraConnected(false);
//                Log.w(TAG, "No front-facing camera available");
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "No front camera available", Toast.LENGTH_SHORT).show());
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Front Camera Error: " + e.getMessage(), e);
//            runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "Front Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//            stopFrontCamera();
//        }
//    }
//
//    private void startUSBCamera() {
//        if (usbCamera != null) {
//            Log.d(TAG, "USB camera already initialized, skipping");
//            return;
//        }
//        try {
//            int usbCameraId = -1;
//            int cameraCount = Camera.getNumberOfCameras();
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            for (int i = 0; i < cameraCount; i++) {
//                Camera.getCameraInfo(i, cameraInfo);
//                if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT && !usedCameraIds.contains(i)) {
//                    usbCameraId = i;
//                    break;
//                }
//            }
//            if (usbCameraId != -1) {
//                usbCamera = Camera.open(usbCameraId);
//                if (usbCamera != null) {
//                    Camera.Parameters parameters = usbCamera.getParameters();
//                    List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
//                    if (sizes != null && !sizes.isEmpty()) {
//                        parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
//                    }
//                    usbCamera.setParameters(parameters);
//                    usbCamera.setPreviewDisplay(usbHolder);
//                    usbCamera.startPreview();
//                    usedCameraIds.add(usbCameraId);
//                    globalVars.setUSBCameraConnected(true);
//                    Log.d(TAG, "USB camera started, ID: " + usbCameraId + ", Thread: " + Thread.currentThread().getName());
//                } else {
//                    throw new RuntimeException("Failed to open USB camera ID: " + usbCameraId);
//                }
//            } else {
//                globalVars.setUSBCameraConnected(false);
//                Log.w(TAG, "No USB camera available");
//                runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "No USB camera available", Toast.LENGTH_SHORT).show());
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "USB Camera Error: " + e.getMessage(), e);
//            runOnUiThread(() -> Toast.makeText(CameraFeedActivity.this, "USB Camera Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//            stopUSBCamera();
//        }
//    }
//
//    private void capturePhoto(Camera camera, String cameraType) {
//        if (camera == null) {
//            Toast.makeText(this, cameraType + " Camera not available", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        camera.takePicture(null, null, (data, cam) -> {
//            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                    cameraType + "_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
//            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
//                fos.write(data);
//                fos.flush();
//                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//            } catch (IOException e) {
//                Log.e(TAG, "Error saving photo: " + e.getMessage(), e);
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
//                frontCamera.setPreviewDisplay(null);
//                int cameraId = -1;
//                for (int id : usedCameraIds) {
//                    Camera.CameraInfo info = new Camera.CameraInfo();
//                    Camera.getCameraInfo(id, info);
//                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                        cameraId = id;
//                        break;
//                    }
//                }
//                frontCamera.release();
//                frontCamera = null;
//                if (cameraId != -1) {
//                    usedCameraIds.remove(cameraId);
//                }
//                globalVars.setInternalCameraConnected(false);
//                Log.d(TAG, "Front camera stopped, Thread: " + Thread.currentThread().getName());
//                runOnUiThread(() -> frontCameraView.invalidate());
//            } catch (Exception e) {
//                Log.e(TAG, "Error stopping front camera: " + e.getMessage(), e);
//            }
//        }
//    }
//
//    private void stopUSBCamera() {
//        if (usbCamera != null) {
//            try {
//                usbCamera.stopPreview();
//                usbCamera.setPreviewDisplay(null);
//                int cameraId = -1;
//                for (int id : usedCameraIds) {
//                    Camera.CameraInfo info = new Camera.CameraInfo();
//                    Camera.getCameraInfo(id, info);
//                    if (info.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                        cameraId = id;
//                        break;
//                    }
//                }
//                usbCamera.release();
//                usbCamera = null;
//                if (cameraId != -1) {
//                    usedCameraIds.remove(cameraId);
//                }
//                globalVars.setUSBCameraConnected(false);
//                Log.d(TAG, "USB camera stopped, Thread: " + Thread.currentThread().getName());
//                try {
//                    Thread.sleep(CLEANUP_DELAY_MS);
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "Cleanup delay interrupted: " + e.getMessage(), e);
//                }
//                runOnUiThread(() -> usbCameraView.invalidate());
//            } catch (Exception e) {
////                byggLog.e(TAG, "Error stopping USB camera: " + e.getMessage(), e);
//            }
//        }
//    }
//
//    private void setupUsbReceiver() {
//        usbReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                String action = intent.getAction();
//                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Attached: " + device.getDeviceName());
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        startUSBCamera();
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Detected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
//                    frontCameraExecutor.submit(() -> {
//                        stopFrontCamera();
//                        startFrontCamera();
//                    });
//                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
//                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    Log.d(TAG, "USB Device Detached: " + device.getDeviceName());
//                    usbCameraExecutor.submit(() -> {
//                        stopUSBCamera();
//                        globalVars.setUSBCameraConnected(false);
//                        runOnUiThread(() -> {
//                            updateToggleButtons();
//                            logCameraInfo();
//                            Toast.makeText(CameraFeedActivity.this, "USB Camera Disconnected", Toast.LENGTH_SHORT).show();
//                        });
//                    });
//                    frontCameraExecutor.submit(() -> {
//                        stopFrontCamera();
//                        startFrontCamera();
//                    });
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
//        frontCameraExecutor.submit(() -> stopFrontCamera());
//        usbCameraExecutor.submit(() -> stopUSBCamera());
//        frontCameraExecutor.shutdown();
//        usbCameraExecutor.shutdown();
//        globalVars.setCameraPreviewActive(false);
//        if (usbReceiver != null) {
//            unregisterReceiver(usbReceiver);
//        }
//        Log.d(TAG, "CameraFeedActivity destroyed, threads shut down");
//    }
//}

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