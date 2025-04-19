package com.lztek.api.demo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class FinalPatientActivity extends AppCompatActivity {

    private Button connectButton;
    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_final_patient);

        connectButton = findViewById(R.id.connect_button);
        backButton = findViewById(R.id.f_back_button);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(FinalPatientActivity.this,BerryDeviceActivity.class);
                startActivity(intent);
            }
        });
        backButton.setOnClickListener(view -> finish());

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

    }
}