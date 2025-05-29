package com.lztek.api.demo;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditSessionActivity extends AppCompatActivity {

    private static final String TAG = "EditSessionActivity";
    private EditText etSessionName, etPatientName, etLocationName, etDate;
    private Spinner spinnerStatus;
    private Button btnSaveSession;
    private AppDatabase database;
    private SessionDao sessionDao;
    private int sessionId;
    private Calendar selectedDate;
    private String originalTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_session);

        // Initialize views
        etSessionName = findViewById(R.id.et_session_name);
        etPatientName = findViewById(R.id.et_patient_name);
        etLocationName = findViewById(R.id.et_location_name);
        etDate = findViewById(R.id.et_date);
        spinnerStatus = findViewById(R.id.spinner_status);
        btnSaveSession = findViewById(R.id.btn_save_session);

        // Setup Spinner for Status
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.status_items, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(adapter);

        // Setup DatePicker for Date field
        selectedDate = Calendar.getInstance();
        etDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    EditSessionActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(year, month, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        etDate.setText(dateFormat.format(selectedDate.getTime()));
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Get session ID from Intent
        sessionId = getIntent().getIntExtra("session_id", -1);
        if (sessionId == -1) {
            Toast.makeText(this, "Invalid session ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Room database
        database = AppDatabase.getDatabase(this);
        sessionDao = database.sessionDao();

        // Load session data
        loadSessionData();

        // Save updated session
        btnSaveSession.setOnClickListener(v -> {
            String sessionName = etSessionName.getText().toString().trim();
            String patientName = etPatientName.getText().toString().trim();
            String locationName = etLocationName.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String status = spinnerStatus.getSelectedItem().toString();

            if (sessionName.isEmpty() || patientName.isEmpty() || locationName.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get current time (or preserve original time)
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String currentTime = originalTime != null ? originalTime : timeFormat.format(new Date());
            String dateTime = date + " " + currentTime;

            // Create updated Session object
            Session updatedSession = new Session(sessionName, patientName, locationName, dateTime, status);
            updatedSession.id = sessionId;

            // Update in database using Room
            new Thread(() -> {
                try {
                    sessionDao.update(updatedSession);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Session updated", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Error updating session: " + e.getMessage());
                        Toast.makeText(this, "Error updating session: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
    }

    private void loadSessionData() {
        new Thread(() -> {
            Session session = sessionDao.getSessionById(sessionId);
            runOnUiThread(() -> {
                if (session != null) {
                    etSessionName.setText(session.sessionName);
                    etPatientName.setText(session.patientName);
                    etLocationName.setText(session.locationName);

                    // Split dateTime into date and time
                    try {
                        String[] parts = session.dateTime.split(" ");
                        if (parts.length == 2) {
                            String date = parts[0];
                            originalTime = parts[1];
                            etDate.setText(date);

                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date parsedDate = dateFormat.parse(date);
                            if (parsedDate != null) {
                                selectedDate.setTime(parsedDate);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing dateTime: " + e.getMessage());
                        etDate.setText(session.dateTime);
                    }

                    // Set Spinner selection
                    String[] statusItems = getResources().getStringArray(R.array.status_items);
                    int statusPosition = Arrays.asList(statusItems).indexOf(session.status);
                    spinnerStatus.setSelection(statusPosition != -1 ? statusPosition : 0);
                } else {
                    Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }
}