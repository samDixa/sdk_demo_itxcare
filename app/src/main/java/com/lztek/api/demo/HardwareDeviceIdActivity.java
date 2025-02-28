package com.lztek.api.demo;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class HardwareDeviceIdActivity extends AppCompatActivity {
    private EditText inputHardwareDeviceId;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_device_id);

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Set full-screen mode
//        getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_LOW_PROFILE
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//        );

        inputHardwareDeviceId = findViewById(R.id.input_hardware_device_id);
        btnSubmit = findViewById(R.id.btn_submit);

        btnSubmit.setOnClickListener(view -> {
            String hardwareDeviceId = inputHardwareDeviceId.getText().toString().trim();

            if (!hardwareDeviceId.isEmpty()) {
                // Save the hardwareDeviceId locally
                SharedPreferencesManager.updatePreference(this, "hardwareDeviceId", hardwareDeviceId);
                GlobalVars.setHardwareDeviceId(hardwareDeviceId);

                // Navigate back to BootscreenActivity
                Intent intent = new Intent(HardwareDeviceIdActivity.this, BootscreenActivity.class);
                startActivity(intent);
                finish(); // Close this activity

            } else {
                Toast.makeText(this, "Please enter a valid Hardware Device ID", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
