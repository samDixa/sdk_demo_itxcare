package com.lztek.api.demo;

import android.annotation.SuppressLint;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class BootscreenActivity extends AppCompatActivity {
    private static final int SPLASH_SCREEN_DELAY_MILLIS = 5000;

    @SuppressLint("InlinedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content view directly from the XML layout
        setContentView(R.layout.activity_bootscreen);

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Set full-screen mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        // Transition to the next activity after a delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if hardwareDeviceId is stored
//            SharedPreferencesManager.removePreference(this, "hardwareDeviceId");
            String hardwareDeviceId = SharedPreferencesManager.getPreference(this, "hardwareDeviceId", null);
            String existingDeviceId = SharedPreferencesManager.getPreference(this, "deviceId", null);

            if (hardwareDeviceId == null || hardwareDeviceId.isEmpty()) {
                // If hardwareDeviceId is not set, open the HardwareDeviceIdActivity
                Intent intent = new Intent(BootscreenActivity.this, HardwareDeviceIdActivity.class);
                startActivity(intent);
                finish();
            } else {
                // Load all values from SharedPreferences into GlobalVars
                loadSharedPreferencesToGlobalVars();

                // Fetch deviceId using hardwareDeviceId
                new FetchDeviceIdTask(hardwareDeviceId, deviceId -> {
                    if (deviceId == null) {
                        // If fetching fails, use the existing deviceId or assign a dummy ID
                        if (existingDeviceId != null && !existingDeviceId.isEmpty()) {
                            Log.w("BootscreenActivity", "Using existing device ID: " + existingDeviceId);
                            GlobalVars.setDeviceId(existingDeviceId);
                            fetchDeviceDetails(Integer.parseInt(existingDeviceId));
                        } else {
                            Log.w("BootscreenActivity", "Device ID fetch failed. Assigning dummy device ID.");
                            GlobalVars.setDeviceId("0"); // Dummy deviceId
                            proceedToLogin();
                        }
                    } else {
                        // Save the deviceId to GlobalVars and SharedPreferences
                        GlobalVars.setDeviceId(String.valueOf(deviceId));
                        SharedPreferencesManager.updatePreference(this, "deviceId", String.valueOf(deviceId));
                        fetchDeviceDetails(deviceId);
                    }
                }).execute();
            }
        }, SPLASH_SCREEN_DELAY_MILLIS);
    }

    /**
     * Step 2: Fetch device details using deviceId.
     */
    private void fetchDeviceDetails(int deviceId) {
        new FetchDeviceDetailsTask(deviceId, deviceDetails -> {
            if (deviceDetails != null) {
                // Save details to SharedPreferences and GlobalVars
                SharedPreferencesManager.updatePreference(this, "deviceDetails", deviceDetails.toString());
                GlobalVars.updateDeviceDetails(deviceDetails);
                Log.d("BootscreenActivity", "Device details fetched and saved successfully.");
            } else {
                Log.w("BootscreenActivity", "Failed to fetch device details. Using existing SharedPreferences values.");
            }
            proceedToLogin(); // Move to login only after fetching details
        }).execute();
    }

    /**
     * Step 3: Navigate to `LoginActivity`.
     */
    private void proceedToLogin() {
        Log.d("BootscreenActivity", "Proceeding to LoginActivity.");
        startActivity(new Intent(BootscreenActivity.this, LoginActivity.class));
        finish();
    }

    private void loadSharedPreferencesToGlobalVars() {
        GlobalVars.setHardwareDeviceId(SharedPreferencesManager.getPreference(this, "hardwareDeviceId", null));
        GlobalVars.setDeviceId(SharedPreferencesManager.getPreference(this, "deviceId", null));
        // Add other necessary preferences to load into GlobalVars if required
    }

    public class FetchDeviceIdTask extends AsyncTask<Void, Void, Integer> {
        private final String hardwareDeviceId;
        private final Consumer<Integer> callback;

        public FetchDeviceIdTask(String hardwareDeviceId, Consumer<Integer> callback) {
            this.hardwareDeviceId = hardwareDeviceId;
            this.callback = callback;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                // Construct the API URL
                String apiUrl = Constants.DEVICE_BY_HARDWARE_ID + "?hardwareDeviceId=" + hardwareDeviceId;
                URL url = new URL(apiUrl);

                // Open connection
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("x-api-key", "META_ORANGE_COMMUNICATION_SECRET"); // Required API key header
                connection.setConnectTimeout(15000); // Timeout for connection
                connection.setReadTimeout(15000);   // Timeout for reading response

                // Check response code
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    // Parse the JSON response
                    JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                    if (jsonResponse.getBoolean("success")) {
                        return jsonResponse.getInt("deviceId");
                    } else {
                        Log.e("FetchDeviceIdTask", "API Error: " + jsonResponse.optString("message", "Unknown error"));
                        return null;
                    }
                } else if (responseCode == 500) {
                    Log.e("FetchDeviceIdTask", "Server Error: 500 Internal Server Error");
                    return null;
                } else {
                    Log.e("FetchDeviceIdTask", "HTTP Error: " + responseCode);
                    return null;
                }
            } catch (Exception e) {
                Log.e("FetchDeviceIdTask", "Error fetching deviceId", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(Integer deviceId) {
            if (deviceId != null) {
                callback.accept(deviceId); // Pass the deviceId to the callback
            } else {
                callback.accept(null); // Notify callback of failure
            }
        }
    }

    public class FetchDeviceDetailsTask extends AsyncTask<Void, Void, JSONObject> {
        private final int deviceId;
        private final Consumer<JSONObject> callback;

        public FetchDeviceDetailsTask(int deviceId, Consumer<JSONObject> callback) {
            this.deviceId = deviceId;
            this.callback = callback;
        }

        @Override
        protected JSONObject doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                // Construct the API URL
                String apiUrl = Constants.GET_DEVICE_INSTANCE.replace("{deviceId}", String.valueOf(deviceId));
                URL url = new URL(apiUrl);

                // Open connection
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("x-api-key", "META_ORANGE_COMMUNICATION_SECRET"); // Required API key header
                connection.setConnectTimeout(15000); // Connection timeout
                connection.setReadTimeout(15000);   // Read timeout

                // Check response code
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    // Parse the JSON response
                    JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                    if (jsonResponse.getBoolean("success")) {
                        return jsonResponse.getJSONObject("data").getJSONObject("device");
                    } else {
                        Log.e("FetchDeviceDetailsTask", "API Error: " + jsonResponse.optString("message", "Unknown error"));
                        return null;
                    }
                } else if (responseCode == 500) {
                    Log.e("FetchDeviceDetailsTask", "Server Error: 500 Internal Server Error");
                    return null;
                } else {
                    Log.e("FetchDeviceDetailsTask", "HTTP Error: " + responseCode);
                    return null;
                }
            } catch (Exception e) {
                Log.e("FetchDeviceDetailsTask", "Error fetching device details", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(JSONObject deviceDetails) {
            if (deviceDetails != null) {
                callback.accept(deviceDetails); // Pass the device details to the callback
            } else {
                callback.accept(null); // Notify callback of failure
            }
        }
    }
}
