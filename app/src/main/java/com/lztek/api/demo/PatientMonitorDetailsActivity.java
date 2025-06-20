package com.lztek.api.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class PatientMonitorDetailsActivity extends AppCompatActivity {

    private EditText editTextName, editTextAge;
    private Spinner spinnerGender;
    private Button buttonSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_monitor_details);

        // Initialize views
        editTextName = findViewById(R.id.edit_text_name);
        editTextAge = findViewById(R.id.edit_text_age);
        spinnerGender = findViewById(R.id.spinner_gender);
        buttonSubmit = findViewById(R.id.button_submit);

        // Submit button click listener
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get input values
                String name = editTextName.getText().toString().trim();
                String ageStr = editTextAge.getText().toString().trim();
                String gender = spinnerGender.getSelectedItem().toString();

                // Validate inputs
                if (name.isEmpty()) {
                    Toast.makeText(PatientMonitorDetailsActivity.this, "Please enter name", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ageStr.isEmpty()) {
                    Toast.makeText(PatientMonitorDetailsActivity.this, "Please enter age", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (gender.equals("Select Gender")) {
                    Toast.makeText(PatientMonitorDetailsActivity.this, "Please select gender", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Parse age
                int age;
                try {
                    age = Integer.parseInt(ageStr);
                    if (age <= 0 || age > 150) {
                        Toast.makeText(PatientMonitorDetailsActivity.this, "Please enter a valid age", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(PatientMonitorDetailsActivity.this, "Invalid age format", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Store values in global variables
                GlobalVars.setPatientMonitorName(name);
                GlobalVars.setPatientMonitorAge(age);
                GlobalVars.setPatientMonitorGender(gender);

                // Show success message (you can replace this with intent to next activity)
                Toast.makeText(PatientMonitorDetailsActivity.this, "Data saved successfully", Toast.LENGTH_SHORT).show();

//                 Optionally, proceed to next activity
                 Intent intent = new Intent(PatientMonitorDetailsActivity.this, BerryDeviceActivity.class);
                 startActivity(intent);
                 finish();
            }
        });
    }
}