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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NewSessionActivity extends AppCompatActivity {

    private static final String TAG = "NewSessionActivity";
    private EditText etSessionName, etPatientName, etLocationName, etDate;
    private Spinner spinnerStatus;
    private Button btnEnterSession;
    private AppDatabase database;
    private SessionDao sessionDao;
    private Calendar selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_session);

        // Initialize views
        etSessionName = findViewById(R.id.et_session_name);
        etPatientName = findViewById(R.id.et_patient_name);
        etLocationName = findViewById(R.id.et_location_name);
        etDate = findViewById(R.id.et_date);
        spinnerStatus = findViewById(R.id.spinner_status);
        btnEnterSession = findViewById(R.id.btn_enter_session);

        // Setup Spinner for Status
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.status_items, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(adapter);
        spinnerStatus.setSelection(0); // Default to "Pending"

        // Setup DatePicker for Date field
        selectedDate = Calendar.getInstance();
        etDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    NewSessionActivity.this,
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

        // Initialize Room database
        database = AppDatabase.getDatabase(this);
        sessionDao = database.sessionDao();

        // Enter button click
        btnEnterSession.setOnClickListener(v -> {
            String sessionName = etSessionName.getText().toString().trim();
            String patientName = etPatientName.getText().toString().trim();
            String locationName = etLocationName.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String status = spinnerStatus.getSelectedItem().toString();

            if (sessionName.isEmpty() || patientName.isEmpty() || locationName.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get current time
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String currentTime = timeFormat.format(new Date());
            String dateTime = date + " " + currentTime;

            // Create Session object
            Session session = new Session(sessionName, patientName, locationName, dateTime, status);

            // Save to database using Room
            try {
                new Thread(() -> {
                    long newRowId = sessionDao.insert(session);
                    runOnUiThread(() -> {
                        if (newRowId != -1) {
                            Log.d(TAG, "Session saved with ID: " + newRowId);
                            Toast.makeText(this, "Session saved", Toast.LENGTH_SHORT).show();

                            // Start the Offline Session Activity
                            Intent intent = new Intent(NewSessionActivity.this, OfflineSessionActivity.class);
                            intent.putExtra("session_id", newRowId);
                            startActivity(intent);

                            finish();
                        } else {
                            Log.e(TAG, "Failed to insert session into database");
                            Toast.makeText(this, "Error saving session", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            } catch (Exception e) {
                Log.e(TAG, "Exception while saving session: " + e.getMessage());
                Toast.makeText(this, "Error saving session: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}