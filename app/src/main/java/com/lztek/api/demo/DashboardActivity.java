package com.lztek.api.demo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class DashboardActivity extends AppCompatActivity {

    private Button buttonAppoin, buttonCamra, buttonBerryDevice, buttonBerryUsbDevice;
    private String statusMessage;
    private final String PROFILE_URL = Constants.PARAMEDIC_MY_PROFILE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        buttonAppoin = findViewById(R.id.btn_appoin);
        buttonCamra = findViewById(R.id.btn_camra);
        buttonBerryDevice = findViewById(R.id.berry_device);
        buttonBerryUsbDevice = findViewById(R.id.usb_berry);

        buttonAppoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ✅ Start AsyncTask
                new FetchProfileTask(DashboardActivity.this).execute();
            }
        });

        buttonCamra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, CameraFeedActivity.class);
                startActivity(intent);
            }
        });

        buttonBerryDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this,BerryDeviceActivity.class);
                startActivity(intent);
            }
        });

        buttonBerryUsbDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this,SerialPortActivity.class);
                startActivity(intent);
            }
        });

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }


    }

    // ✅ AsyncTask for Network Call
    private class FetchProfileTask extends AsyncTask<Void, Void, String> {
        private Context context;

        public FetchProfileTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(PROFILE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + GlobalVars.getAccessToken());
                conn.setRequestProperty("App-Tenant", GlobalVars.getClientId());

                InputStream inputStream = conn.getInputStream();
                Scanner inStream = new Scanner(inputStream);
                StringBuilder profileResponse = new StringBuilder();
                while (inStream.hasNextLine()) {
                    profileResponse.append(inStream.nextLine());
                }
                return profileResponse.toString();
            } catch (Exception e) {
                Log.e("DashboardActivity", "Error fetching profile data", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject profileResponse = new JSONObject(result);
                    boolean success = profileResponse.getBoolean("success");
                    statusMessage = profileResponse.getString("message");

                    if (success) {
                        // ✅ Profile Fetch Successful
                        Toast.makeText(context, "Profile Fetch Successful", Toast.LENGTH_SHORT).show();

                        // ✅ Parse profile data
                        JSONObject profileData = profileResponse.getJSONObject("data");
                        GlobalVars.setUserId(profileData.getInt("user_ID"));
                        GlobalVars.setFirstName(profileData.getString("first_name"));
                        GlobalVars.setLastName(profileData.getString("last_name"));
                        GlobalVars.setEmail(profileData.getString("email"));

                        // ✅ Navigate to Appointment List
                        Intent intent = new Intent(context, AppointmentListActivity.class);
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "Failed to fetch profile data", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("DashboardActivity", "Error parsing profile response", e);
                    Toast.makeText(context, "Failed to parse profile data", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
