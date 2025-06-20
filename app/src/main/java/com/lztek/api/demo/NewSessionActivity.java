package com.lztek.api.demo;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewSessionActivity extends AppCompatActivity {

    private static final String TAG = "NewSessionActivity";
    private EditText etSessionName, etPatientName, etLocationName, etDate;
    private Button btnEnterSession;
    private AppDatabase database;
    private SessionDao sessionDao;
    private Calendar selectedDate;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_session);

        // Initialize views
        etSessionName = findViewById(R.id.et_session_name);
        etPatientName = findViewById(R.id.et_patient_name);
        etLocationName = findViewById(R.id.et_location_name);
        etDate = findViewById(R.id.et_date);
        btnEnterSession = findViewById(R.id.btn_enter_session);

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

            if (sessionName.isEmpty() || patientName.isEmpty() || locationName.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get current time
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String currentTime = timeFormat.format(new Date());
            String dateTime = date + " " + currentTime;

            // Create Session object with status "pending"
            Session session = new Session(
                    sessionName,
                    patientName,
                    locationName,
                    dateTime,
                    "pending", // Hardcode status to "pending"
                    null, // vitalJson
                    null, // audioPath
                    null, // videoPath
                    null  // photoPath
            );

            // Save to database using Room
            executor.execute(() -> {
                try {
                    long newRowId = sessionDao.insert(session);
                    runOnUiThread(() -> {
                        if (newRowId != -1) {
                            Log.d(TAG, "Session saved with ID: " + newRowId);
                            Toast.makeText(this, "Session saved", Toast.LENGTH_SHORT).show();

                            // Start the OfflineSessionActivity
                            Intent intent = new Intent(NewSessionActivity.this, OfflineSessionActivity.class);
                            intent.putExtra("session_id", newRowId);
                            startActivity(intent);

                            finish();
                        } else {
                            Log.e(TAG, "Failed to insert session into database");
                            Toast.makeText(this, "Error saving session", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Exception while saving session: " + e.getMessage());
                        Toast.makeText(this, "Error saving session: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

//        disableSoftKeyboard(etSessionName);
//        disableSoftKeyboard(etPatientName);
//        disableSoftKeyboard(etLocationName);
//        disableSoftKeyboard(etDate);
//
        setEnterToNext(etSessionName);
        setEnterToNext(etPatientName);
        setEnterToNext(etLocationName);
        setEnterToNext(etDate);

        preventSoftKeyboard(etSessionName);
        preventSoftKeyboard(etPatientName);
        preventSoftKeyboard(etLocationName);
        preventSoftKeyboard(etDate);


        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    private void preventSoftKeyboard(EditText editText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            editText.setShowSoftInputOnFocus(false);
        } else {
            try {
                Method method = EditText.class.getMethod("setShowSoftInputOnFocus", boolean.class);
                method.setAccessible(true);
                method.invoke(editText, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void disableSoftKeyboard(EditText editText) {
        editText.setInputType(InputType.TYPE_NULL);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
    }

    private void setEnterToNext(EditText editText) {
        editText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                View nextView = v.focusSearch(View.FOCUS_DOWN);
                if (nextView != null) {
                    nextView.requestFocus();
                    return true;
                }
            }
            return false;
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}