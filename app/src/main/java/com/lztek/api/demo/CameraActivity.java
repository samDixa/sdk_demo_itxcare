package com.lztek.api.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String EXTRA_CAMERA_ID = "camera_id";
    private static final String EXTRA_CAMERA_NAME = "camera_name";

    private TextView tvCameraTitle;
    private TextureView textureView;
    private Button btnCloseCamera;

    private Camera camera;
    private SurfaceTexture surfaceTexture;
    private String cameraId = "0"; // Default to rear camera (ID "0")
    private String cameraName = "Rear Camera"; // Default camera name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_preview_dialog);

        // Log device API level for compatibility check
        Log.d(TAG, "Android API Level: " + Build.VERSION.SDK_INT);

        // Initialize views
        tvCameraTitle = findViewById(R.id.tv_camera_title);
        textureView = findViewById(R.id.texture_view);
        btnCloseCamera = findViewById(R.id.btn_close_camera);

        // Get camera ID and name from intent
        if (getIntent() != null) {
            cameraId = getIntent().getStringExtra(EXTRA_CAMERA_ID);
            if (cameraId == null) {
                cameraId = "0"; // Default to rear camera if not specified
            }
            cameraName = getIntent().getStringExtra(EXTRA_CAMERA_NAME);
            if (cameraName == null) {
                cameraName = cameraId.equals("0") ? "Rear Camera" : "Front Camera";
            }
        }

        // Validate camera ID to ensure it's either rear or front
        cameraId = findCameraId(cameraId);
        if (cameraId == null) {
            Toast.makeText(this, "No suitable camera found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set the camera title
        tvCameraTitle.setText(cameraName + " Preview");

        // Close button listener
        btnCloseCamera.setOnClickListener(v -> finish());

        // Check camera permission on start
        if (checkCameraPermission()) {
            setupCameraCallbacks();
        }
    }

    private String findCameraId(String desiredCameraId) {
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            // Only allow rear or front cameras, skip others (e.g., USB/external)
            String facing = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK ? "Back" :
                    cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "Front" : "Unknown");
            Log.d(TAG, "Camera ID: " + i + ", Facing: " + facing);

            // Skip any camera that is not explicitly back or front
            if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_BACK &&
                    cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.w(TAG, "Skipping camera ID " + i + " (not a back or front camera)");
                continue;
            }

            if (desiredCameraId.equals("0") && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return String.valueOf(i);
            } else if (desiredCameraId.equals("1") && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return String.valueOf(i);
            }
        }
        Log.e(TAG, "No matching camera found for desired ID: " + desiredCameraId);
        return null;
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCameraCallbacks();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void setupCameraCallbacks() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "Camera surface texture available for Camera ID: " + cameraId + ", width: " + width + ", height: " + height);
                surfaceTexture = surface;
                startCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "Surface texture size changed: " + width + "x" + height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "Camera surface texture destroyed for Camera ID: " + cameraId);
                stopCamera();
                surfaceTexture = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // No action needed for now
            }
        });

        // If TextureView is already available, start the camera immediately
        if (textureView.isAvailable()) {
            surfaceTexture = textureView.getSurfaceTexture();
            Log.d(TAG, "TextureView already available, starting camera immediately");
            startCamera();
        } else {
            Log.d(TAG, "TextureView not yet available, waiting for surfaceTextureAvailable callback");
        }
    }

    private void startCamera() {
        if (camera != null) {
            Log.d(TAG, "Camera already initialized, skipping");
            return;
        }
        if (surfaceTexture == null) {
            Log.w(TAG, "Camera surface texture not available");
            runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Surface not ready", Toast.LENGTH_SHORT).show());
            return;
        }
        try {
            int camId = Integer.parseInt(cameraId);
            Log.d(TAG, "Attempting to open camera ID: " + camId);
            camera = openCameraWithRetry(camId, 3);
            if (camera != null) {
                Log.d(TAG, "Camera opened successfully, ID: " + camId);
                Camera.Parameters parameters = camera.getParameters();
                List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                if (sizes == null || sizes.isEmpty()) {
                    throw new RuntimeException("No supported preview sizes available");
                }
                Camera.Size selectedSize = getCompatibleSize(sizes, 1280, 720); // Prefer 720p
                if (selectedSize != null) {
                    parameters.setPreviewSize(selectedSize.width, selectedSize.height);
                    Log.d(TAG, "Camera preview size set: " + selectedSize.width + "x" + selectedSize.height);
                } else {
                    throw new RuntimeException("No compatible preview sizes for camera");
                }
                camera.setParameters(parameters);
                Log.d(TAG, "Setting preview texture");
                camera.setPreviewTexture(surfaceTexture);
                Log.d(TAG, "Starting camera preview");
                camera.startPreview();
                Log.d(TAG, "Camera preview started successfully, ID: " + camId);
                runOnUiThread(() -> Toast.makeText(CameraActivity.this,
                        "Camera " + (cameraId.equals("0") ? "Rear" : "Front") + " opened", Toast.LENGTH_SHORT).show());
            } else {
                throw new RuntimeException("Failed to open camera ID: " + camId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera Error: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(CameraActivity.this, "Camera Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                textureView.invalidate();
            });
            stopCamera();
        }
    }

    private Camera openCameraWithRetry(int cameraId, int retries) {
        for (int i = 0; i < retries; i++) {
            try {
                Log.d(TAG, "Opening camera ID " + cameraId + ", attempt " + (i + 1));
                Camera cam = Camera.open(cameraId);
                if (cam != null) {
                    return cam;
                }
                Log.w(TAG, "Camera ID " + cameraId + " open failed, retry " + (i + 1));
                Thread.sleep(100);
            } catch (Exception e) {
                Log.w(TAG, "Camera ID " + cameraId + " open error: " + e.getMessage());
            }
        }
        return null;
    }

    private Camera.Size getCompatibleSize(List<Camera.Size> sizes, int preferredWidth, int preferredHeight) {
        Log.d(TAG, "Available preview sizes: " + sizes.size());
        for (Camera.Size size : sizes) {
            Log.d(TAG, "Preview size: " + size.width + "x" + size.height);
        }
        Camera.Size fallback = sizes.get(0);
        for (Camera.Size size : sizes) {
            if (size.width == preferredWidth && size.height == preferredHeight) {
                return size;
            }
            if (size.width <= preferredWidth && size.height <= preferredHeight) {
                fallback = size;
            }
        }
        return fallback;
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                Log.d(TAG, "Stopping camera preview");
                camera.stopPreview();
                Log.d(TAG, "Releasing camera");
                camera.release();
                camera = null;
                runOnUiThread(() -> textureView.invalidate());
                Log.d(TAG, "Camera stopped and released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera: " + e.getMessage(), e);
                runOnUiThread(() -> textureView.invalidate());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCamera();
    }
}