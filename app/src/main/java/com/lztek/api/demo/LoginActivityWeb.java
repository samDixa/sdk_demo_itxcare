package com.lztek.api.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class LoginActivityWeb extends AppCompatActivity {

    private TextView titleText;
    private TextView statusText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button backButton,nextButton;
    private TextView viewButton;
//    private CheckBox rememberMeCheckBox;

    private static final String TAG = "LoginActivityWeb";
    private String hardcodedUsername = "dipti@gmail.com";
    private String hardcodedPassword = "Tenant@1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 10, 50, 10);
        layout.setForegroundGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#F1F8FF")); // Set background color

        LinearLayout topBarLayout = new LinearLayout(this);
        topBarLayout.setOrientation(LinearLayout.HORIZONTAL);
// Set layout params with bottom margin
        LinearLayout.LayoutParams topBarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        topBarParams.setMargins(0, 0, 0, 50); // Add 50px bottom margin
        topBarLayout.setLayoutParams(topBarParams);
        topBarLayout.setGravity(Gravity.CENTER_VERTICAL); // Optional: vertically center the buttons

// Back Button with left alignment
        backButton = new Button(this);
        backButton.setTextColor(Color.WHITE);
        backButton.setBackgroundColor(Color.BLUE);
        backButton.setText("Back");
// Set layout params for back button
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backParams.gravity = Gravity.START; // Align to left
        backButton.setLayoutParams(backParams);
        topBarLayout.addView(backButton);

        viewButton = new TextView(this);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        viewParams.gravity = Gravity.CENTER;
        viewParams.weight = 1;
        viewButton.setLayoutParams(viewParams);
        topBarLayout.addView(viewButton);




// Next Button with right alignment
        nextButton = new Button(this);
        nextButton.setText("Next");
        nextButton.setBackgroundColor(Color.BLUE);
        nextButton.setTextColor(Color.WHITE);
// Set layout params for next button
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nextParams.gravity = Gravity.END; // Align to right
//        nextParams.weight = 1; // This pushes the button to the right
        nextButton.setLayoutParams(nextParams);
        topBarLayout.addView(nextButton);

        layout.addView(topBarLayout);
        backButton.setOnClickListener(view -> finish());


        // Setting up Logo as ImageView
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.hp_logo);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(320);
        layout.addView(logo);

        titleText = new TextView(this);
        titleText.setTextSize(24);
        titleText.setText("WELCOME! Please Login");
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(10,10,10,10);
        layout.addView(titleText);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics()),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;

        // Username field
        usernameEditText = new EditText(this);
        usernameEditText.setHint("Username");
        usernameEditText.setText(hardcodedUsername);
        usernameEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        usernameEditText.setLayoutParams(params);  // Set limited width
        usernameEditText.setPadding(10,20,10,10);
        layout.addView(usernameEditText);

        // Password field
        passwordEditText = new EditText(this);
        passwordEditText.setHint("Password");
        passwordEditText.setText(hardcodedPassword);
        passwordEditText.setInputType(0x00000081); // TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setLayoutParams(params);  // Set limited width
        passwordEditText.setPadding(10,20,10,10);
        layout.addView(passwordEditText);

        // Submit button
        Button submitButton = new Button(this);
        submitButton.setText("Submit");
        submitButton.setBackgroundColor(Color.parseColor("#2196F3"));
        submitButton.setLayoutParams(params);  // Set limited width
        submitButton.setPadding(10,20,10,10);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSubmit();
            }
        });
        layout.addView(submitButton);

        // Setting up Status text
        statusText = new TextView(this);
        statusText.setText("...");
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(statusText);

        // Adding layout to the content view
        setContentView(layout);
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
        private Context context;
        private String statusMessage;
        private boolean isLoginSuccessful;

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
                if (isLoginSuccessful) {
                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(context, DashboardActivity.class);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Login failed: " + statusMessage, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Login error. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}