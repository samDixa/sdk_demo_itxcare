package com.lztek.api.demo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView deviceInfoText;
    private Button maintenanceButton;
    private Button nextButton;
    private Button calibrateButton;
    private Button helpButton;
    private Button timeButton, dayButton, batteryButton, acSupplyButton, networkButton, remoteHelpButton, shutDownButton;

    private LinearLayout footerLayout;
    private Handler timeHandler; // For real-time updates
    private Runnable timeRunnable; // Runnable to update time
    private BroadcastReceiver batteryReceiver; // To receive battery updates from SerialPortService

    private com.lztek.toolkit.Lztek mLztek;
    private Button mBtReboot = null;
    private Button mBtPowerOff = null;
    private EditText mEtTime = null;

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Intent intent = new Intent(this, SerialPortService.class);
        startService(intent);



//        startForegroundService(new Intent(this, SerialPortService.class));

        // Main Layout (Vertical)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 10, 50, 0);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#F1F8FF"));
        layout.setWeightSum(10);

        // Button Layout (Horizontal)
        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2
        );
        buttonLayoutParams.setMargins(0, 0, 0, 20);

        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonLayout.setWeightSum(8);
        buttonLayout.setLayoutParams(buttonLayoutParams);

        // Button Layout Params
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f
        );
        btnParams.setMargins(10, 0, 10, 0);

        // Buttons
        maintenanceButton = new Button(this);
        maintenanceButton.setText("Maintenance");
        maintenanceButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        maintenanceButton.setTextColor(Color.WHITE);
        maintenanceButton.setLayoutParams(btnParams);
        buttonLayout.addView(maintenanceButton);

        calibrateButton = new Button(this);
        calibrateButton.setText("Calibrate");
        calibrateButton.setBackgroundColor(Color.YELLOW);
        calibrateButton.setTextColor(Color.BLACK);
        calibrateButton.setLayoutParams(btnParams);
        buttonLayout.addView(calibrateButton);

        helpButton = new Button(this);
        helpButton.setText("Help !");
        helpButton.setBackgroundColor(Color.RED);
        helpButton.setTextColor(Color.WHITE);
        helpButton.setLayoutParams(btnParams);
        buttonLayout.addView(helpButton);

        nextButton = new Button(this);
        nextButton.setText("Next");
        nextButton.setBackgroundColor(Color.parseColor("#2196F3"));
        nextButton.setTextColor(Color.WHITE);
        nextButton.setLayoutParams(btnParams);
        buttonLayout.addView(nextButton);

        layout.addView(buttonLayout);

        // Logo ImageView
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3
        );
        logo = new ImageView(this);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(350);
        logo.setLayoutParams(logoParams);
        layout.addView(logo);

        // Device Info Text
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2
        );
        deviceInfoText = new TextView(this);
        deviceInfoText.setTextSize(18);
        deviceInfoText.setGravity(Gravity.CENTER);
        deviceInfoText.setLayoutParams(infoParams);
        layout.addView(deviceInfoText);

        // Footer Layout (Fixed Bottom)
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3
        );
        footerLayout = new LinearLayout(this);
        footerLayout.setOrientation(LinearLayout.HORIZONTAL);
        footerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        footerLayout.setPadding(10, 10, 10, 10);
        footerLayout.setWeightSum(7);
        footerLayout.setLayoutParams(footerParams);

        // Footer Button Layout Params
        LinearLayout.LayoutParams footerBtnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
        );
        footerBtnParams.setMargins(5, 200, 5, 0);

        // Footer Buttons
        String[] buttonNames = {"TIME", "Day and Date", "Battery %", "AC Supply", "Network", "Remote Access", "Shut Down"};
        int[] buttonColors = {Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.DKGRAY, Color.RED, Color.BLACK};

        // Initialize footer buttons
        timeButton = new Button(this);
        dayButton = new Button(this);
        batteryButton = new Button(this);
        acSupplyButton = new Button(this);
        networkButton = new Button(this);
        remoteHelpButton = new Button(this);
        shutDownButton = new Button(this);

        Button[] footerButtons = {timeButton, dayButton, batteryButton, acSupplyButton, networkButton, remoteHelpButton, shutDownButton};

        for (int i = 0; i < buttonNames.length; i++) {
            footerButtons[i].setText(buttonNames[i]);
            footerButtons[i].setTextColor(Color.WHITE);
            footerButtons[i].setBackgroundColor(buttonColors[i]);
            footerButtons[i].setLayoutParams(footerBtnParams);

            final int index = i;
            footerButtons[i].setOnClickListener(v -> handleFooterClick(index));

            footerLayout.addView(footerButtons[i]);
        }

        layout.addView(footerLayout);

        setContentView(layout);

        // Set click listener for Next button
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitCode();
            }
        });

        // Load device details
        loadDeviceDetails();

        // Update TIME and Day/Date buttons initially
        updateTimeButton();
        updateDayDateButton();

        // Start real-time time updates
        startTimeUpdates();

        // Register BroadcastReceiver for battery updates
        registerBatteryReceiver();
    }

    // Update TIME button with current time in 12-hour format (without seconds)
    private void updateTimeButton() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        timeButton.setText(currentTime);
    }

    // Start real-time updates for the TIME button
    private void startTimeUpdates() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeButton();
                timeHandler.postDelayed(this, 60000); // Update every minute (60000 ms)
            }
        };
        timeHandler.post(timeRunnable); // Start the updates
    }

    // Update Day and Date button with current day and date
    private void updateDayDateButton() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault());
        String currentDayDate = dayFormat.format(new Date());
        dayButton.setText(currentDayDate);
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
                        batteryButton.setText(batteryPercentage + "%");
                    } else {
                        batteryButton.setText("Battery %\nN/A");
                    }
                    // Optionally, you can also use chargingStatus to show charging state
                    if (chargingStatus >= 0) {
                        acSupplyButton.setText((chargingStatus == 1 ? "Connected" : "Disconnected"));
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
        registerReceiver(batteryReceiver, filter);
    }

    // Placeholder for AC Supply status (now handled via broadcast)
    private String getAcSupplyStatus() {
        return "Unknown"; // This will be updated via the broadcast
    }

    // Show network mode selection dialog
    private void showNetworkModeDialog() {
        String[] networkModes = {"Wi-Fi", "Mobile Data"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Network Mode");
        builder.setItems(networkModes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedMode = networkModes[which];
                networkButton.setText(selectedMode);
                Toast.makeText(LoginActivity.this, "Switched to " + selectedMode, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Show shutdown options dialog
    private void showShutdownDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Shutdown Options");
        builder.setMessage("Choose an option:");

        builder.setPositiveButton("Reboot", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                rebootDevice();
            }
        });

        builder.setNegativeButton("Power Off", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                powerOffDevice();
            }
        });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    // Function to reboot the device
    private void rebootDevice() {
        try {
            mLztek.softReboot();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Reboot failed!", Toast.LENGTH_SHORT).show();
        }
    }

    // Function to power off the device
    private void powerOffDevice() {
        int min = -1;
        try {
            min = Integer.parseInt(mEtTime.getText().toString());
        } catch (Exception e) {
        }
        if (min <= 0) {
            mEtTime.requestFocus();
            return;
        }

        mLztek.alarmPoweron(min * 60);
    }


    // Footer Button Click Handler
    private void handleFooterClick(int index) {
        switch (index) {
            case 0: // TIME
                updateTimeButton();
                break;
            case 1: // Day and Date
                updateDayDateButton();
                break;
            case 2: // Battery % (Updated via broadcast, no action needed on click)
                break;
            case 3: // AC Supply (Updated via broadcast, no action needed on click)
                break;
            case 4: // Network
                showNetworkModeDialog();
                break;
            case 5: // Remote Access
                Toast.makeText(this, "Remote Access Clicked!", Toast.LENGTH_SHORT).show();
                break;
            case 6: // Shut Down
                showShutdownDialog();
                break;
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Load API Data (devices_name, created_date, companyName, blobUrl)
    private void loadDeviceDetails() {
        String devicesName = GlobalVars.getDevicesName();
        String createdDate = GlobalVars.getCreatedDate();
        String companyName = GlobalVars.getCustomerCompanyName();
        String blobUrl = GlobalVars.getCustomerBlobUrl();

        String formattedDate = formatDate(createdDate);
        String deviceInfo = "Device: " + (devicesName != null ? devicesName : "N/A") +
                "\n\nCreated: " + formattedDate +
                "\n\nCompany: " + (companyName != null ? companyName : "N/A");
        deviceInfoText.setText(deviceInfo);

        if (blobUrl != null && !blobUrl.isEmpty()) {
            Glide.with(this)
                    .load(blobUrl)
                    .apply(new RequestOptions().placeholder(R.drawable.cnrgi_logo).error(R.drawable.cnrgi_logo))
                    .into(logo);
        } else {
            logo.setImageResource(R.drawable.cnrgi_logo);
        }
    }

    // Handles the Next button action
    private void submitCode() {
        Intent intent = new Intent(LoginActivity.this, LoginActivityWeb.class);
        startActivity(intent);
    }

    private String formatDate(String inputDate) {
        if (inputDate == null || inputDate.isEmpty()) {
            return "N/A";
        }
        try {
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            Date date = apiFormat.parse(inputDate);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return "N/A";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the time updates
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        // Unregister the battery receiver
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
    }
}