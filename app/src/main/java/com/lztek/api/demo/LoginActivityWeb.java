//package com.lztek.api.demo;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.graphics.Color;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.Gravity;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.CheckBox;
//import android.widget.EditText;
//import android.widget.GridLayout;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import org.json.JSONObject;
//
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.Locale;
//import java.util.Scanner;
//
//public class LoginActivityWeb extends AppCompatActivity {
//
//    private TextView timeTextView;
//    private TextView dateTextView;
//    private TextView bettryTextView;
//    private EditText usernameEditText;
//    private EditText passwordEditText;
//    private Button backButton;
//    private Button submitButton;
////    private CheckBox rememberMeCheckBox;
//
//    private Handler timeHandler; // For real-time updates
//    private Runnable timeRunnable; // Runnable to update time
//    private BroadcastReceiver batteryReceiver; // To receive battery updates from SerialPortService
//
//    private static final String TAG = "LoginActivityWeb";
//    private String hardcodedUsername = "dipti@gmail.com";
//    private String hardcodedPassword = "Tenant@1234";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_login_web);
//
//        backButton = findViewById(R.id.lgw_back_btn);
//        usernameEditText = findViewById(R.id.edit_userid);
//        passwordEditText = findViewById(R.id.edit_password);
//        submitButton = findViewById(R.id.btn_login);
//        timeTextView = findViewById(R.id.lgw_time);
//        dateTextView = findViewById(R.id.lgw_date);
//        bettryTextView = findViewById(R.id.lgw_bettry);
//
//
//        backButton.setOnClickListener(view -> finish());
//        usernameEditText.setText(hardcodedUsername);
//        passwordEditText.setText(hardcodedPassword);
//
//        submitButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                handleSubmit();
//            }
//        });
//
//        updateTimeTextView();
//        updateDayDateTextView();
//
//        startTimeUpdates();
//
//        registerBatteryReceiver();
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
//
//    // Update TIME TextView with current time in 12-hour format (without seconds)
//    private void updateTimeTextView() {
//        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
//        String currentTime = timeFormat.format(new Date());
//        timeTextView.setText(currentTime);
//    }
//
//    // Start real-time updates for the TIME TextView
//    private void startTimeUpdates() {
//        timeHandler = new Handler(Looper.getMainLooper());
//        timeRunnable = new Runnable() {
//            @Override
//            public void run() {
//                updateTimeTextView();
//                timeHandler.postDelayed(this, 60000); // Update every minute (60000 ms)
//            }
//        };
//        timeHandler.post(timeRunnable); // Start the updates
//    }
//
//    // Update Day and Date TextView with current day and date
//    private void updateDayDateTextView() {
//        SimpleDateFormat dayFormat = new SimpleDateFormat("EE ,dd.MM.yyyy", Locale.getDefault());
//        String currentDayDate = dayFormat.format(new Date());
//        dateTextView.setText(currentDayDate);
//    }
//
//    // Register BroadcastReceiver to get battery percentage from SerialPortService
//    private void registerBatteryReceiver() {
//        batteryReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if ("com.lztek.api.demo.STATUS_UPDATE".equals(intent.getAction())) {
//                    long batteryPercentage = intent.getLongExtra("battery_percentage", -1);
//                    long chargingStatus = intent.getLongExtra("charging_status", -1);
//                    if (batteryPercentage >= 0) {
//                        bettryTextView.setText(batteryPercentage + "\uD83D\uDD0B");
//                    } else {
//                        bettryTextView.setText("Battery %\nN/A");
//                    }
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(batteryReceiver, filter);
//    }
//
//    private void handleSubmit() {
//        String username = usernameEditText.getText().toString().trim();
//        String password = passwordEditText.getText().toString().trim();
//
//        if (username.isEmpty() || password.isEmpty()) {
//            Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
//        } else {
//            // Initialize and execute the LoginTask
//            LoginTask loginTask = new LoginTask(this);  // Pass the Activity context
//            loginTask.execute(username, password);
//        }
//    }
//
//    public class LoginTask extends AsyncTask<String, Void, String> {
//
//        private static final String TAG = "LoginTask";
//        private final String LOGIN_URL = Constants.LOGIN_ENDPOINT;
//        private Context context;
//        private String statusMessage;
//        private boolean isLoginSuccessful;
//
//        public LoginTask(Context context) {
//            this.context = context;
//        }
//
//        @Override
//        protected String doInBackground(String... params) {
//            String username = params[0];
//            String password = params[1];
//            try {
//                // Perform login
//                URL url = new URL(LOGIN_URL);
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("POST");
//                conn.setRequestProperty("Content-Type", "application/json");
//                conn.setRequestProperty(Constants.APP_TENANT_HEADER, GlobalVars.getClientId()); // Use clientId dynamically
//                conn.setDoOutput(true);
//
//                // Create JSON body for login request
//                JSONObject loginData = new JSONObject();
//                loginData.put("userName", username);
//                loginData.put("password", password);
//
//                OutputStream os = conn.getOutputStream();
//                os.write(loginData.toString().getBytes("UTF-8"));
//                os.close();
//
//                // Read login response
//                Scanner inStream = new Scanner(conn.getInputStream());
//                StringBuilder loginResponse = new StringBuilder();
//                while (inStream.hasNextLine()) {
//                    loginResponse.append(inStream.nextLine());
//                }
//
//                JSONObject jsonResponse = new JSONObject(loginResponse.toString());
//                isLoginSuccessful = jsonResponse.getBoolean("success");
//                statusMessage = jsonResponse.getString("message");
//
//                if (isLoginSuccessful) {
//                    JSONObject data = jsonResponse.getJSONObject("data");
//                    GlobalVars.setAccessToken(data.getString("accessToken"));
//                    GlobalVars.setRefreshToken(data.getString("refreshToken"));
//                }
//                return statusMessage;
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error during login", e);
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//            if (result != null) {
//                if (isLoginSuccessful) {
//                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show();
//                    Intent intent = new Intent(context, DashboardActivity.class);
//                    context.startActivity(intent);
//                } else {
//                    Toast.makeText(context, "Login failed: " + statusMessage, Toast.LENGTH_SHORT).show();
//                }
//            } else {
//                Toast.makeText(context, "Login error. Please try again.", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//}


package com.lztek.api.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class LoginActivityWeb extends AppCompatActivity {

    private TextView timeTextView;
    private TextView dateTextView;
    private TextView bettryTextView;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button backButton;
    private Button submitButton;

    private Handler timeHandler; // For real-time updates
    private Runnable timeRunnable; // Runnable to update time
    private BroadcastReceiver batteryReceiver; // To receive battery updates from SerialPortService

    private static final String TAG = "LoginActivityWeb";
//    private String hardcodedUsername = "dipti@gmail.com";
    private String hardcodedUsername = "venkat@gmail.com";
//    private String hardcodedPassword = "Tenant@1234";
    private String hardcodedPassword = "Venkat23!";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_web);

        // Initialize UI elements
        backButton = findViewById(R.id.lgw_back_btn);
        usernameEditText = findViewById(R.id.edit_userid);
        passwordEditText = findViewById(R.id.edit_password);
        submitButton = findViewById(R.id.btn_login);
        timeTextView = findViewById(R.id.lgw_time);
        dateTextView = findViewById(R.id.lgw_date);
        bettryTextView = findViewById(R.id.lgw_bettry);

        // Set same width and text size for buttons
        int buttonWidth = 200; // Width in pixels (adjust as needed)
        float buttonTextSize = 16; // Text size in sp (adjust as needed)

        // Configure backButton
        LinearLayout.LayoutParams backButtonParams = new LinearLayout.LayoutParams(
                buttonWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        backButton.setLayoutParams(backButtonParams);
        backButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonTextSize);

        // Configure submitButton
        LinearLayout.LayoutParams submitButtonParams = new LinearLayout.LayoutParams(
                buttonWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        submitButton.setLayoutParams(submitButtonParams);
        submitButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonTextSize);

        // Set click listeners
        backButton.setOnClickListener(view -> finish());
        usernameEditText.setText(hardcodedUsername);
        passwordEditText.setText(hardcodedPassword);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleSubmit();
            }
        });

        // Initialize time and date displays
        updateTimeTextView();
        updateDayDateTextView();

        // Start real-time updates
        startTimeUpdates();

        // Register battery receiver
        registerBatteryReceiver();

        // Set system UI flags for immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    // Update TIME TextView with current time in 12-hour format (without seconds)
    private void updateTimeTextView() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        timeTextView.setText(currentTime);
    }

    // Start real-time updates for the TIME TextView
    private void startTimeUpdates() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeTextView();
                timeHandler.postDelayed(this, 60000); // Update every minute (60000 ms)
            }
        };
        timeHandler.post(timeRunnable); // Start the updates
    }

    // Update Day and Date TextView with current day and date
    private void updateDayDateTextView() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EE ,dd.MM.yyyy", Locale.getDefault());
        String currentDayDate = dayFormat.format(new Date());
        dateTextView.setText(currentDayDate);
    }

    // Register BroadcastReceiver to get battery percentage from SerialPortService
    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.lztek.api.demo.STATUS_UPDATE".equals(intent.getAction())) {
                    long batteryPercentage = intent.getLongExtra("battery_percentage", -1);
                    long chargingStatus = intent.getLongExtra("charging_status", -1);
                    if (batteryPercentage >= 0) {
                        bettryTextView.setText(batteryPercentage + "\uD83D\uDD0B");
                    } else {
                        bettryTextView.setText("Battery %\nN/A");
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
        registerReceiver(batteryReceiver, filter);
    }

    private void handleSubmit() {
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
        } else {
            // Initialize and execute the LoginTask
            LoginTask loginTask = new LoginTask(this);  // Pass the Activity context
            loginTask.execute(username, password);
        }
    }

    public class LoginTask extends AsyncTask<String, Void, String> {

        private static final String TAG = "LoginTask";
        private final String LOGIN_URL = Constants.LOGIN_ENDPOINT;
        private final String PROFILE_URL = Constants.PARAMEDIC_MY_PROFILE;
        private Context context;
        private String statusMessage;
        private boolean isLoginSuccessful;
        private boolean isProfileFetchSuccessful;

        public LoginTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... params) {
            String username = params[0];
            String password = params[1];
            try {
                // Perform login
                URL url = new URL(LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty(Constants.APP_TENANT_HEADER, GlobalVars.getClientId()); // Use clientId dynamically
                conn.setDoOutput(true);

                // Create JSON body for login request
                JSONObject loginData = new JSONObject();
                loginData.put("userName", username);
                loginData.put("password", password);

                OutputStream os = conn.getOutputStream();
                os.write(loginData.toString().getBytes("UTF-8"));
                os.close();

                // Read login response
                Scanner inStream = new Scanner(conn.getInputStream());
                StringBuilder loginResponse = new StringBuilder();
                while (inStream.hasNextLine()) {
                    loginResponse.append(inStream.nextLine());
                }

                JSONObject jsonResponse = new JSONObject(loginResponse.toString());
                isLoginSuccessful = jsonResponse.getBoolean("success");
                statusMessage = jsonResponse.getString("message");

                if (isLoginSuccessful) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    GlobalVars.setAccessToken(data.getString("accessToken"));
                    GlobalVars.setRefreshToken(data.getString("refreshToken"));

                    // ------ Fetch Permedic
                    URL profileURL = new URL(PROFILE_URL);
                    HttpURLConnection profileConn = (HttpURLConnection) profileURL.openConnection();
                    profileConn.setRequestMethod("GET");
                    profileConn.setRequestProperty("Content-Type", "application/json");
                    profileConn.setRequestProperty(Constants.AUTHORIZATION_HEADER,"Bearer " + GlobalVars.getAccessToken());
                    profileConn.setRequestProperty(Constants.APP_TENANT_HEADER,GlobalVars.getClientId());

                    // Read profile response
                    InputStream profileInputStream;
                    int responseCode = profileConn.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        profileInputStream = profileConn.getInputStream();
                    } else {
                        profileInputStream = profileConn.getErrorStream();
                        statusMessage = "Failed to fetch profile: HTTP " + responseCode;
                        isProfileFetchSuccessful = false;
                        profileConn.disconnect();
                        return statusMessage;
                    }

                    Scanner profileInStream = new Scanner(profileInputStream);
                    StringBuilder profileResponse = new StringBuilder();
                    while (profileInStream.hasNextLine()) {
                        profileResponse.append(profileInStream.nextLine());
                    }
                    profileInStream.close();
                    profileConn.disconnect();

                    JSONObject profileJsonResponse = new JSONObject(profileResponse.toString());
                    isProfileFetchSuccessful = profileJsonResponse.getBoolean("success");
                    if (isProfileFetchSuccessful) {
                        JSONObject profileData = profileJsonResponse.getJSONObject("data");
                        // Save paramedic details to GlobalVars
                        GlobalVars.setParamedicId(profileData.getInt("paramedic_Id"));
                        GlobalVars.setUserId(profileData.getInt("user_ID"));
                        GlobalVars.setFirstName(profileData.getString("first_name"));
                        GlobalVars.setLastName(profileData.getString("last_name"));
                        GlobalVars.setEmail(profileData.getString("email"));
                        GlobalVars.setAddress(profileData.getString("address"));
                        GlobalVars.setDateOfBirth(profileData.getString("date_of_birth"));
                        GlobalVars.setGender(profileData.getString("gender"));
                        GlobalVars.setPhoneNumber(profileData.getString("phone_number"));
                        GlobalVars.setTraining(profileData.optString("training", ""));
                        GlobalVars.setPreviousPositions(profileData.optString("previous_positions", "[]"));
                        GlobalVars.setEducation(profileData.optString("education", ""));
                        GlobalVars.setWorkingHours(profileData.optString("working_hours", ""));
                        GlobalVars.setLanguage(profileData.isNull("language") ? null : profileData.getString("language"));
                        GlobalVars.setExpertise(profileData.optString("expertise", ""));
                        GlobalVars.setNotes(profileData.optString("notes", ""));
                        GlobalVars.setYearsOfExperience(profileData.getString("years_of_experience"));
                        GlobalVars.setPassword(profileData.getString("password"));
                        GlobalVars.setIsActive(profileData.getBoolean("is_active"));
                        GlobalVars.setAverageRating(profileData.isNull("average_rating") ? null : profileData.getDouble("average_rating"));
                        GlobalVars.setProfilePicture(profileData.isNull("profile_picture") ? null : profileData.getString("profile_picture"));
                        GlobalVars.setProfilePhotoUrl(profileData.getString("profilephotourl"));
                        statusMessage = "Login and profile fetch successful";
                    } else {
                        statusMessage = profileJsonResponse.getString("message");
                    }
                }
                return statusMessage;

            } catch (Exception e) {
                Log.e(TAG, "Error during login", e);
                return null;
            }
        }


        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if (isLoginSuccessful && isProfileFetchSuccessful) {
                    Toast.makeText(context, "Login and profile fetch successful", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(context, DashboardActivity.class);
                    context.startActivity(intent);
                    ((AppCompatActivity) context).finish(); // Close LoginActivity
                } else if (isLoginSuccessful) {
                    Toast.makeText(context, "Login successful but profile fetch failed: " + statusMessage, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Login failed: " + statusMessage, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context, "Error during login or profile fetch. Please try again.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the battery receiver to prevent memory leaks
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        // Stop the time updates
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
}