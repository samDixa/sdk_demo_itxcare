package com.lztek.api.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionDetailActivity extends AppCompatActivity {

    private static final String TAG = "SessionDetailActivity";
    private TextView tvSessionName, tvPatientName, tvLocation, tvDateTime, tvStatus, tvVitalJson, tvAudioFile, tvPhotoFile, tvVideoFile;
    private Button btnUpload, btnUpdateRecord;
    private AppDatabase database;
    private SessionDao sessionDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Session session;
    private int sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);

        // Initialize views
        tvSessionName = findViewById(R.id.tv_session_name);
        tvPatientName = findViewById(R.id.tv_patient_name);
        tvLocation = findViewById(R.id.tv_location);
        tvDateTime = findViewById(R.id.tv_date_time);
        tvStatus = findViewById(R.id.tv_status);
        tvVitalJson = findViewById(R.id.tv_vital_json);
        tvAudioFile = findViewById(R.id.tv_audio_file);
        tvPhotoFile = findViewById(R.id.tv_photo_file);
        tvVideoFile = findViewById(R.id.tv_video_file);
        btnUpload = findViewById(R.id.btn_upload);
        btnUpdateRecord = findViewById(R.id.btn_update_record);

        // Initialize database
        database = AppDatabase.getDatabase(this);
        sessionDao = database.sessionDao();

        // Get session ID from intent
        sessionId = getIntent().getIntExtra("SESSION_ID", -1);
        if (sessionId == -1) {
            Log.e(TAG, "Invalid session ID");
            Toast.makeText(this, "Invalid session ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load session details
        loadSessionDetails();

        // Upload button listener
        btnUpload.setOnClickListener(v -> uploadSession());

        // Update Record button listener
        btnUpdateRecord.setOnClickListener(v -> {
            Intent intent = new Intent(SessionDetailActivity.this, OfflineSessionActivity.class);
            intent.putExtra("session_id", sessionId);
            startActivity(intent);
        });
    }

    private void loadSessionDetails() {
        executor.execute(() -> {
            session = sessionDao.getSessionById(sessionId);
            runOnUiThread(() -> {
                if (session != null) {
                    tvSessionName.setText(session.sessionName != null ? session.sessionName : "N/A");
                    tvPatientName.setText(session.patientName != null ? session.patientName : "N/A");
                    tvLocation.setText(session.locationName != null ? session.locationName : "N/A");
                    tvDateTime.setText(session.dateTime != null ? session.dateTime : "N/A");
                    tvStatus.setText(session.status != null ? session.status : "N/A");
                    tvVitalJson.setText(session.vitalJson != null ? session.vitalJson : "No vital data");
                    tvAudioFile.setText(session.audioPath != null ? getFileName(session.audioPath) : "No audio file");
                    tvPhotoFile.setText(session.photoPath != null ? getFileName(session.photoPath) : "No photo file");
                    tvVideoFile.setText(session.videoPath != null ? getFileName(session.videoPath) : "No video file");

                    // Disable upload and update buttons if already uploaded
                    if ("complete".equals(session.status)) {
                        btnUpload.setEnabled(false);
                        btnUpload.setText("Uploaded");
                        btnUpdateRecord.setEnabled(false);
                        btnUpdateRecord.setText("Record Updated");
                    }
                } else {
                    Log.e(TAG, "Session not found for ID: " + sessionId);
                    Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    private String getFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "N/A";
        int lastSlashIndex = filePath.lastIndexOf("/");
        if (lastSlashIndex != -1 && lastSlashIndex < filePath.length() - 1) {
            return filePath.substring(lastSlashIndex + 1);
        }
        return filePath;
    }

    private void uploadSession() {
        executor.execute(() -> {
            // Simulate upload (replace with actual upload logic)
            Log.d(TAG, "Uploading session data: " + sessionId);
            Log.d(TAG, "Vital JSON: " + session.vitalJson);
            Log.d(TAG, "Audio Path: " + session.audioPath);
            Log.d(TAG, "Video Path: " + session.videoPath);
            Log.d(TAG, "Photo Path: " + session.photoPath);

            // Update status to "complete"
            sessionDao.updateStatus(sessionId, "complete");

            // Refresh UI
            runOnUiThread(() -> {
                tvStatus.setText("complete");
                btnUpload.setEnabled(false);
                btnUpload.setText("Uploaded");
                btnUpdateRecord.setEnabled(false);
                btnUpdateRecord.setText("Record Updated");
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}