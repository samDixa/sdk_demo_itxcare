package com.lztek.api.demo;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private StringBuilder enteredKey = new StringBuilder();
    private TextView titleText;
    private TextView enteredKeyText;
    private TextView statusText;
    private ImageView logo;
    private TextView deviceInfoText; // To show `devices_name`, `created_date`, and `companyName`

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#F1F8FF")); // Set background color

        // Setting up Logo as ImageView
        logo = new ImageView(this);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(150);
        layout.addView(logo);

        // Set the company and device info
        deviceInfoText = new TextView(this);
        deviceInfoText.setTextSize(18);
        deviceInfoText.setGravity(Gravity.CENTER);
//        deviceInfoText.setPadding(10, 20, 10, 10);
        layout.addView(deviceInfoText);

        titleText = new TextView(this);
        titleText.setTextSize(24);
        titleText.setText("ENTER CODE");
        titleText.setGravity(Gravity.CENTER);
//        titleText.setPadding(10, 10, 10, 10);
        layout.addView(titleText);

        // Setting up entered key display
        enteredKeyText = new TextView(this);
        enteredKeyText.setTextSize(24);
        enteredKeyText.setGravity(Gravity.CENTER_HORIZONTAL);
//        enteredKeyText.setPadding(10, 10, 10, 10);
        layout.addView(enteredKeyText);

        // Creating keypad layout
        GridLayout keypadLayout = new GridLayout(this);
        keypadLayout.setColumnCount(3); // Arrange in 4 rows x 3 columns
//        keypadLayout.setPadding(50, 50, 50, 50);
        keypadLayout.setForegroundGravity(Gravity.CENTER_HORIZONTAL);

        // Adding numeric and control buttons
        String[] buttonLabels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "\uD83D\uDDD1", "0", "\u23CE"};

        for (String label : buttonLabels) {
            Button button = new Button(this);
            button.setText(label);
            button.setTextSize(24); // Larger text for bigger buttons
            button.setPadding(20, 20, 20, 20); // Add padding for larger appearance
            button.setGravity(Gravity.CENTER);

            // Set up click listeners
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (label) {
                        case "\uD83D\uDDD1": // Trash symbol for clear
                            enteredKey.setLength(0); // Clear all
                            break;
                        case "\u23CE": // Return symbol for enter
                            submitCode(enteredKey.toString()); // Action for Enter
                            break;
                        default:
                            enteredKey.append(label);
                            break;
                    }
                    enteredKeyText.setText(enteredKey.toString());
                }
            });
            keypadLayout.addView(button);
        }

        LinearLayout.LayoutParams keypadParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        keypadParams.gravity = Gravity.CENTER_HORIZONTAL;
        keypadLayout.setLayoutParams(keypadParams);
        layout.addView(keypadLayout);

        statusText = new TextView(this);
        statusText.setText("...");
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(statusText);

        // Adding layout to the content view
        setContentView(layout);

        // Load device details
        loadDeviceDetails();
    }

    /**
     * Load API Data (devices_name, created_date, companyName, blobUrl)
     */
    private void loadDeviceDetails() {
        // Retrieve values from GlobalVars
        String devicesName = GlobalVars.getDevicesName();
        String createdDate = GlobalVars.getCreatedDate();
        String companyName = GlobalVars.getCustomerCompanyName();
        String blobUrl = GlobalVars.getCustomerBlobUrl();

        String formattedDate = formatDate(createdDate); // Convert API date to DD.MM.YYYY format
        String deviceInfo = "Device: " + (devicesName != null ? devicesName : "N/A") +
                "\n\nCreated: " + formattedDate +
                "\n\nCompany: " + (companyName != null ? companyName : "N/A");
        deviceInfoText.setText(deviceInfo);

        // Load the image using Glide (or fallback to default image)
        if (blobUrl != null && !blobUrl.isEmpty()) {
            Glide.with(this)
                    .load(blobUrl)
                    .apply(new RequestOptions().placeholder(R.drawable.cnrgi_logo).error(R.drawable.cnrgi_logo))
                    .into(logo);
        } else {
            logo.setImageResource(R.drawable.cnrgi_logo);
        }
    }

    /**
     * Handles the Enter button action.
     */
    private void submitCode(String code) {
        Intent intent = new Intent(LoginActivity.this, LoginActivityWeb.class);
        intent.putExtra("enteredCode", code);
        startActivity(intent);
    }

    private String formatDate(String inputDate) {
        if (inputDate == null || inputDate.isEmpty()) {
            return "N/A";
        }
        try {
            // Original format from API (e.g., "2024-12-17T12:19:36.679Z")
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            // Desired format (DD.MM.YYYY)
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

            Date date = apiFormat.parse(inputDate); // Parse API date
            return outputFormat.format(date); // Convert to required format
        } catch (ParseException e) {
            e.printStackTrace();
            return "N/A"; // Return "N/A" if parsing fails
        }
    }
}
