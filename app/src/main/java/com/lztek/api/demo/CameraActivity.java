package com.lztek.api.demo;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private String cameraId;
    private String cameraName;
    private ActivityResultLauncher<Intent> videoCaptureLauncher;
    private ActivityResultLauncher<Intent> photoCaptureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera_preview_dialog);

        cameraId = getIntent().getStringExtra("camera_id");
        cameraName = getIntent().getStringExtra("camera_name");

        // Initialize launchers
        videoCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        String videoPath = getFilePathFromUri(videoUri);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("file_path", videoPath);
                        resultIntent.putExtra("file_type", "video");
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                }
        );

        photoCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri photoUri = result.getData().getData();
                        String photoPath = getFilePathFromUri(photoUri);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("file_path", photoPath);
                        resultIntent.putExtra("file_type", "photo");
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                }
        );

        // Start capturing video or photo (for simplicity, let's capture both)
        captureVideo();
        capturePhoto();
    }

    private void captureVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        videoCaptureLauncher.launch(intent);
    }

    private void capturePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        photoCaptureLauncher.launch(intent);
    }

    private String getFilePathFromUri(Uri uri) {
        // Similar to OfflineSessionActivity's getFilePathFromUri
        String filePath = "";
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        filePath = cursor.getString(index);
                    }
                    cursor.close();
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                filePath = new File(uri.getPath()).getAbsolutePath();
            }
            if (filePath.isEmpty()) {
                filePath = uri.toString();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to get file path: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return filePath;
    }
}