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


package com.lztek.api.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineModeActivity extends AppCompatActivity {

    private static final String TAG = "OfflineModeActivity";
    private RecyclerView rvSessionsList;
    private Button btnNewSession;
    private SessionsAdapter sessionsAdapter;
    private List<Session> sessionsList;
    private AppDatabase database;
    private SessionDao sessionDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // ✅ Background thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_mode);

        // Initialize views
        rvSessionsList = findViewById(R.id.rv_sessions_list);
        btnNewSession = findViewById(R.id.btn_new_session);

        if (rvSessionsList == null || btnNewSession == null) {
            Log.e(TAG, "Failed to find rv_sessions_list or btn_new_session in activity_offline_mode.xml");
            return;
        }

        // Setup RecyclerView
        rvSessionsList.setLayoutManager(new LinearLayoutManager(this));
        sessionsList = new ArrayList<>();
        sessionsAdapter = new SessionsAdapter(this, sessionsList);
        rvSessionsList.setAdapter(sessionsAdapter);

        // Initialize Room database
        database = AppDatabase.getDatabase(this);
        sessionDao = database.sessionDao();

        // Load sessions from database
        loadSessions();

        // New Session button click
        btnNewSession.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineModeActivity.this, NewSessionActivity.class);
            startActivity(intent);
        });
    }

    public void loadSessions() {
        executor.execute(() -> {
            List<Session> sessionsFromDb = sessionDao.getAllSessions();

            runOnUiThread(() -> {
                sessionsList.clear();
                if (sessionsFromDb != null && !sessionsFromDb.isEmpty()) {
                    sessionsList.addAll(sessionsFromDb);
                    Log.d(TAG, "Loaded " + sessionsList.size() + " sessions from database");
                } else {
                    Log.d(TAG, "No sessions found in database");
                }
                sessionsAdapter.notifyDataSetChanged();
            });
        });
    }

    public void deleteSession(int id) {
        executor.execute(() -> {
            Session sessionToDelete = sessionDao.getSessionById(id);
            if (sessionToDelete != null) {
                sessionDao.delete(sessionToDelete);
                Log.d(TAG, "Deleted session with ID: " + id);
            } else {
                Log.e(TAG, "Session with ID " + id + " not found for deletion");
            }

            // Refresh list after deletion
            loadSessions(); // this will run in background again
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
        executor.shutdown(); // ✅ Clean shutdown of thread pool
    }
}
