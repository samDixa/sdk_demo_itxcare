package com.lztek.api.demo;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
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
    private static final String ARG_CAMERA_ID = "camera_id";
    private static final String ARG_CAMERA_NAME = "camera_name";

    private String cameraId;
    private String cameraName;
    private TextureView textureView;

    public static CameraPreviewDialogFragment newInstance(String cameraId, String cameraName) {
        CameraPreviewDialogFragment fragment = new CameraPreviewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CAMERA_ID, cameraId);
        args.putString(ARG_CAMERA_NAME, cameraName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            cameraId = getArguments().getString(ARG_CAMERA_ID);
            cameraName = getArguments().getString(ARG_CAMERA_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera_preview_dialog, container, false);

        // Set up the UI elements
        TextView tvCameraName = view.findViewById(R.id.tv_camera_name);
        textureView = view.findViewById(R.id.texture_view_camera);
        Button btnClose = view.findViewById(R.id.btn_close);

        // Set the camera name
        tvCameraName.setText(cameraName);

        // Set up the close button
        btnClose.setOnClickListener(v -> dismiss());

        // Set up the camera preview (implementation depends on Camera2 API or CameraX)
        setupCameraPreview();

        return view;
    }

    private void setupCameraPreview() {
        // This would involve using the Camera2 API or CameraX to bind the camera preview
        // to the TextureView, using the cameraId. For brevity, this is a placeholder.
        // Example: Use Camera2 API to open the camera with cameraId and bind to textureView.
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}