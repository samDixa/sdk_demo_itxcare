//package com.lztek.api.demo;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.util.Log;
//import android.widget.Button;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class OfflineModeActivity extends AppCompatActivity {
//
//    private static final String TAG = "OfflineModeActivity";
//    private RecyclerView rvSessionsList;
//    private Button btnNewSession;
//    private SessionsAdapter sessionsAdapter;
//    private List<Session> sessionsList;
//    private AppDatabase database;
//    private SessionDao sessionDao;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_offline_mode);
//
//        // Initialize views
//        rvSessionsList = findViewById(R.id.rv_sessions_list);
//        btnNewSession = findViewById(R.id.btn_new_session);
//
//        if (rvSessionsList == null || btnNewSession == null) {
//            Log.e(TAG, "Failed to find rv_sessions_list or btn_new_session in activity_offline_mode.xml");
//            return;
//        }
//
//        // Setup RecyclerView
//        rvSessionsList.setLayoutManager(new LinearLayoutManager(this));
//        sessionsList = new ArrayList<>();
//        sessionsAdapter = new SessionsAdapter(this, sessionsList);
//        rvSessionsList.setAdapter(sessionsAdapter);
//
//        // Initialize Room database
//        database = AppDatabase.getDatabase(this);
//        sessionDao = database.sessionDao();
//
//        // Load sessions from database
//        loadSessions();
//
//        // New Session button click
//        btnNewSession.setOnClickListener(v -> {
//            Intent intent = new Intent(OfflineModeActivity.this, NewSessionActivity.class);
//            startActivity(intent);
//        });
//    }
//
//    public void loadSessions() {
//        sessionsList.clear();
//        List<Session> sessionsFromDb = sessionDao.getAllSessions();
//        if (sessionsFromDb != null && !sessionsFromDb.isEmpty()) {
//            sessionsList.addAll(sessionsFromDb);
//            Log.d(TAG, "Loaded " + sessionsList.size() + " sessions from database");
//        } else {
//            Log.d(TAG, "No sessions found in database");
//        }
//        sessionsAdapter.notifyDataSetChanged();
//    }
//
//    public void deleteSession(int id) {
//        Session sessionToDelete = sessionDao.getSessionById(id);
//        if (sessionToDelete != null) {
//            sessionDao.delete(sessionToDelete);
//            Log.d(TAG, "Deleted session with ID: " + id);
//        } else {
//            Log.e(TAG, "Session with ID " + id + " not found for deletion");
//        }
//        loadSessions();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        loadSessions();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // Room automatically manages database connections, no need to close
//    }
//}

//
//package com.lztek.api.demo;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class OfflineModeActivity extends AppCompatActivity {
//
//    private static final String TAG = "OfflineModeActivity";
//    private RecyclerView rvSessionsList;
//    private Button btnNewSession;
//    private SessionsAdapter sessionsAdapter;
//    private List<Session> sessionsList;
//    private AppDatabase database;
//    private SessionDao sessionDao;
//    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // Background thread
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_offline_mode);
//
//        // Initialize views
////        rvSessionsList = findViewById(R.id.rv_sessions_list);
//        btnNewSession = findViewById(R.id.btn_new_session);
//
//        if (rvSessionsList == null || btnNewSession == null) {
//            Log.e(TAG, "Failed to find rv_sessions_list or btn_new_session in activity_offline_mode.xml");
//            return;
//        }
//
//        // Setup RecyclerView
//        rvSessionsList.setLayoutManager(new LinearLayoutManager(this));
//        sessionsList = new ArrayList<>();
//        sessionsAdapter = new SessionsAdapter(this, sessionsList);
//        rvSessionsList.setAdapter(sessionsAdapter);
//
//        // Initialize Room database
//        database = AppDatabase.getDatabase(this);
//        sessionDao = database.sessionDao();
//
//        // Load sessions from database
//        loadSessions();
//
//        // New Session button click
//        btnNewSession.setOnClickListener(v -> {
//            Intent intent = new Intent(OfflineModeActivity.this, NewSessionActivity.class);
//            startActivity(intent);
//        });
//
//        getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_LOW_PROFILE
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//        );
//    }
//
//    public void loadSessions() {
//        executor.execute(() -> {
//            List<Session> sessionsFromDb = sessionDao.getAllSessions();
//
//            runOnUiThread(() -> {
//                sessionsList.clear();
//                if (sessionsFromDb != null && !sessionsFromDb.isEmpty()) {
//                    sessionsList.addAll(sessionsFromDb);
//                    Log.d(TAG, "Loaded " + sessionsList.size() + " sessions from database");
//                } else {
//                    Log.d(TAG, "No sessions found in database");
//                }
//                sessionsAdapter.notifyDataSetChanged();
//            });
//        });
//    }
//
//    public void deleteSession(int id) {
//        executor.execute(() -> {
//            Session sessionToDelete = sessionDao.getSessionById(id);
//            if (sessionToDelete != null) {
//                sessionDao.delete(sessionToDelete);
//                Log.d(TAG, "Deleted session with ID: " + id);
//            } else {
//                Log.e(TAG, "Session with ID " + id + " not found for deletion");
//            }
//
//            // Refresh list after deletion
//            loadSessions(); // this will run in background again
//        });
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        loadSessions();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        executor.shutdown(); // ✅ Clean shutdown of thread pool
//    }
//}


package com.lztek.api.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineModeActivity extends AppCompatActivity {

    private static final String TAG = "OfflineModeActivity";
    private TableLayout dataRowsContainer;
    private Button btnNewSession;
    private List<Session> sessionsList;
    private AppDatabase database;
    private SessionDao sessionDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ProgressBar loadingProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_mode);

        // Initialize views
        dataRowsContainer = findViewById(R.id.data_rows_container);
        btnNewSession = findViewById(R.id.btn_new_session);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);

        if (dataRowsContainer == null || btnNewSession == null || loadingProgressBar == null) {
            Log.e(TAG, "Failed to find data_rows_container, btn_new_session, or loading_progress_bar in activity_offline_mode.xml");
            return;
        }

        // Initialize Room database with error handling
        try {
            database = AppDatabase.getDatabase(this);
            if (database == null) {
                Log.e(TAG, "Database initialization failed");
                Toast.makeText(this, "Database error, please restart the app", Toast.LENGTH_SHORT).show();
                return;
            }
            sessionDao = database.sessionDao();
            if (sessionDao == null) {
                Log.e(TAG, "SessionDao initialization failed");
                Toast.makeText(this, "Database error, please restart the app", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing database or DAO: " + e.getMessage());
            Toast.makeText(this, "Database error, please restart the app", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load sessions from database
        loadSessions();

        // New Session button click
        btnNewSession.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineModeActivity.this, NewSessionActivity.class);
            startActivity(intent);
        });

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    public void loadSessions() {
        executor.execute(() -> {
            runOnUiThread(() -> loadingProgressBar.setVisibility(View.VISIBLE));
            if (sessionDao == null) {
                Log.e(TAG, "sessionDao is null, cannot load sessions");
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Database error, please restart the app", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            List<Session> sessionsFromDb = sessionDao.getAllSessions();

            runOnUiThread(() -> {
                loadingProgressBar.setVisibility(View.GONE);
                dataRowsContainer.removeAllViews();
                sessionsList = new ArrayList<>();
                if (sessionsFromDb != null && !sessionsFromDb.isEmpty()) {
                    sessionsList.addAll(sessionsFromDb);
                    Log.d(TAG, "Loaded " + sessionsList.size() + " sessions from database");
                    for (int i = 0; i < sessionsList.size(); i++) {
                        addSessionRow(sessionsList.get(i), i + 1);
                    }
                } else {
                    Log.d(TAG, "No sessions found in database");
                }
            });
        });
    }

    private void addSessionRow(Session session, int rowNumber) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.item_session, null);

        TextView srNo = rowView.findViewById(R.id.sr_no);
        TextView tvSessionName = rowView.findViewById(R.id.tv_session_name);
        TextView tvPatientName = rowView.findViewById(R.id.tv_patient_name);
        TextView tvLocationName = rowView.findViewById(R.id.tv_location_name);
        TextView tvDateTime = rowView.findViewById(R.id.tv_date_time);
        TextView tvStatus = rowView.findViewById(R.id.tv_status);
        Button btnEdit = rowView.findViewById(R.id.btn_edit_session);
        Button btnDelete = rowView.findViewById(R.id.btn_delete_session);

        if (srNo == null || tvSessionName == null || tvPatientName == null || tvLocationName == null ||
                tvDateTime == null || tvStatus == null || btnEdit == null || btnDelete == null) {
            Log.e(TAG, "One or more views not found in item_session.xml");
            return;
        }

        srNo.setText(String.valueOf(rowNumber));
        tvSessionName.setText(session.sessionName != null ? session.sessionName : "N/A");
        tvPatientName.setText(session.patientName != null ? session.patientName : "N/A");
        tvLocationName.setText(session.locationName != null ? session.locationName : "N/A");
        tvDateTime.setText(session.dateTime != null ? session.dateTime : "N/A");
        tvStatus.setText(session.status != null ? session.status : "N/A");

        // Add item click listener to open SessionDetailActivity
        rowView.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineModeActivity.this, SessionDetailActivity.class);
            intent.putExtra("SESSION_ID", session.id);
            startActivity(intent);
        });

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineModeActivity.this, EditSessionActivity.class);
            intent.putExtra("session_id", session.id);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> deleteSession(session.id));

        dataRowsContainer.addView(rowView);
    }

    public void deleteSession(int id) {
        executor.execute(() -> {
            runOnUiThread(() -> loadingProgressBar.setVisibility(View.VISIBLE));
            if (sessionDao == null) {
                Log.e(TAG, "sessionDao is null, cannot delete session");
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Database error", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            Session sessionToDelete = sessionDao.getSessionById(id);
            if (sessionToDelete != null) {
                sessionDao.delete(sessionToDelete);
                Log.d(TAG, "Deleted session with ID: " + id);
            } else {
                Log.e(TAG, "Session with ID " + id + " not found for deletion");
            }

            // Refresh list after deletion
            loadSessions();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
