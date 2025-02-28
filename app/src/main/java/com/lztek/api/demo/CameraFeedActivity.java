package com.lztek.api.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
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
import java.util.Locale;

public class CameraFeedActivity extends AppCompatActivity {

    private SurfaceView frontCameraView;
    private Camera frontCamera;
    private Button btnCaptureFront, btnStartVideoFront, btnStopVideoFront;
    private MediaRecorder mediaRecorderFront;
    private boolean isRecordingFront = false;
    private File videoFileFront;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_feed);

        frontCameraView = findViewById(R.id.usbCameraView);

        btnCaptureFront = findViewById(R.id.btn_capture_usb);
        btnStartVideoFront = findViewById(R.id.btn_start_video_usb);
        btnStopVideoFront = findViewById(R.id.btn_stop_video_usb);

        btnStopVideoFront.setVisibility(View.GONE);

        if (checkPermissions()) {
            startFrontCamera();
        } else {
            requestPermissions();
        }

        btnCaptureFront.setOnClickListener(view -> capturePhoto(frontCamera, "Front"));

        btnStartVideoFront.setOnClickListener(view -> startVideoRecording());
        btnStopVideoFront.setOnClickListener(view -> stopVideoRecording());

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        }, 100);
    }

    private void startFrontCamera() {
        SurfaceHolder holder = frontCameraView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    frontCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    Camera.Parameters parameters = frontCamera.getParameters();
                    parameters.setPreviewSize(640, 480);
                    frontCamera.setParameters(parameters);
                    frontCamera.setPreviewDisplay(holder);
                    frontCamera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(CameraFeedActivity.this, "Camera Error", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCamera();
            }
        });
    }

    private void capturePhoto(Camera camera, String cameraType) {
        if (camera == null) return;

        camera.takePicture(null, null, (data, cam) -> {
            File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    cameraType + "_IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                fos.write(data);
                fos.flush();
                Toast.makeText(CameraFeedActivity.this, "Photo Saved: " + pictureFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cam.startPreview();
        });
    }

    private void startVideoRecording() {
        if (frontCamera == null) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
            return;
        }

        btnStartVideoFront.setVisibility(View.GONE);
        btnStopVideoFront.setVisibility(View.VISIBLE);

        mediaRecorderFront = new MediaRecorder();
        frontCamera.unlock();
        mediaRecorderFront.setCamera(frontCamera);
        mediaRecorderFront.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorderFront.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorderFront.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorderFront.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorderFront.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorderFront.setVideoSize(1280, 720);
        mediaRecorderFront.setVideoFrameRate(30);
        mediaRecorderFront.setVideoEncodingBitRate(10000000);

        videoFileFront = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Front_VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4");
        mediaRecorderFront.setOutputFile(videoFileFront.getAbsolutePath());
        mediaRecorderFront.setPreviewDisplay(frontCameraView.getHolder().getSurface());

        try {
            mediaRecorderFront.prepare();
            mediaRecorderFront.start();
            isRecordingFront = true;
            Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Recording Failed", Toast.LENGTH_SHORT).show();
            stopVideoRecording();
        }
    }

    private void stopVideoRecording() {
        if (mediaRecorderFront != null) {
            try {
                mediaRecorderFront.stop();
                mediaRecorderFront.reset();
                mediaRecorderFront.release();
                mediaRecorderFront = null;
                frontCamera.lock();
                isRecordingFront = false;
                Toast.makeText(this, "Video Saved: " + videoFileFront.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error Stopping Video", Toast.LENGTH_SHORT).show();
            }
        }
        btnStartVideoFront.setVisibility(View.VISIBLE);
        btnStopVideoFront.setVisibility(View.GONE);
    }

    private void stopCamera() {
        if (frontCamera != null) {
            frontCamera.stopPreview();
            frontCamera.release();
            frontCamera = null;
        }
    }
}
