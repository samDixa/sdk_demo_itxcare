package com.lztek.api.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

public class CameraPreviewDialogFragment extends DialogFragment {

    private static final String TAG = "CameraPreviewDialog";
    private static final String ARG_CAMERA_ID = "camera_id";
    private static final String ARG_CAMERA_NAME = "camera_name";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private TextureView textureView;
    private TextView tvCameraTitle;
    private Button btnCloseCamera;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private HandlerThread cameraThread;
    private Handler cameraHandler;

    // Static method to create a new instance of the fragment
    public static CameraPreviewDialogFragment newInstance(String cameraId, String cameraName) {
        CameraPreviewDialogFragment fragment = new CameraPreviewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CAMERA_ID, cameraId);
        args.putString(ARG_CAMERA_NAME, cameraName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the style for the dialog to be full-screen with no title bar
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_preview_dialog, container, false);

        // Initialize views
        textureView = view.findViewById(R.id.texture_view);
        tvCameraTitle = view.findViewById(R.id.tv_camera_title);
        btnCloseCamera = view.findViewById(R.id.btn_close_camera);

        // Get arguments (camera ID and name)
        if (getArguments() != null) {
            cameraId = getArguments().getString(ARG_CAMERA_ID);
            String cameraName = getArguments().getString(ARG_CAMERA_NAME);
            tvCameraTitle.setText(cameraName + " Preview");
        }

        // Close button listener
        btnCloseCamera.setOnClickListener(v -> dismiss());

        // Check camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCameraPreview();
        }

        return view;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraPreview();
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        }
    }

    private void startCameraPreview() {
        // Start background thread for camera operations
        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NotNull SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NotNull SurfaceTexture surface, int width, int height) {
                // No action needed for now
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NotNull SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NotNull SurfaceTexture surface) {
                // No action needed for now
            }
        });
    }

    private void openCamera() {
        // Safety check for permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
            Toast.makeText(requireContext(), "Camera permission not granted", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        CameraManager cameraManager = (CameraManager) requireContext().getSystemService(CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NotNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected(@NotNull CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(@NotNull CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                        Toast.makeText(requireContext(), "Error opening camera: " + error, Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                }, cameraHandler);
            } else {
                Log.e(TAG, "CameraManager is null");
                Toast.makeText(requireContext(), "Camera service unavailable", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accessing camera: " + e.getMessage());
            Toast.makeText(requireContext(), "Error accessing camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) {
                Log.e(TAG, "SurfaceTexture is null");
                return;
            }
            surfaceTexture.setDefaultBufferSize(1280, 720); // Adjust resolution as needed
            Surface surface = new Surface(surfaceTexture);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NotNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                captureBuilder.addTarget(surface);
                                cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, cameraHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Error setting up camera preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NotNull CameraCaptureSession session) {
                            Toast.makeText(requireContext(), "Failed to configure camera preview", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating camera preview session: " + e.getMessage());
            Toast.makeText(requireContext(), "Error creating camera preview", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
    }
}