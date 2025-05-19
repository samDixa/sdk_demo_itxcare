package com.lztek.api.demo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private View btnGapView;
    private TextView timeTextView, dayTextView, batteryTextView, ftbtnGapView;
    private Button acSupplyButton, networkButton, remoteHelpButton, shutDownButton;

    private LinearLayout footerLayout;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private BroadcastReceiver batteryReceiver;

    private com.lztek.toolkit.Lztek mLztek;

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, SerialPortService.class);
        startService(intent);

        // Initialize Lztek
        mLztek = com.lztek.toolkit.Lztek.create(this);

        // Request runtime permissions for Wi-Fi scanning (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            }
        }

        // Main Layout (Vertical)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 10, 50, 0);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setWeightSum(10);
        layout.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_gradient));

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
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        btnParams.setMargins(10, 0, 10, 0);

        // Button View Params
        LinearLayout.LayoutParams btnViewParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f
        );

        // Buttons
        maintenanceButton = new Button(this);
        maintenanceButton.setText("Maintenance \uD83D\uDEE0");
        maintenanceButton.setTextColor(Color.WHITE);
        maintenanceButton.setLayoutParams(btnParams);
        maintenanceButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
        buttonLayout.addView(maintenanceButton);

        calibrateButton = new Button(this);
        calibrateButton.setText("Calibrate ⚙");
        calibrateButton.setTextColor(Color.WHITE);
        calibrateButton.setLayoutParams(btnParams);
        calibrateButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
        buttonLayout.addView(calibrateButton);

        helpButton = new Button(this);
        helpButton.setText("Help ?");
        helpButton.setTextColor(Color.WHITE);
        helpButton.setLayoutParams(btnParams);
        helpButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
        buttonLayout.addView(helpButton);

        btnGapView = new View(this);
        btnGapView.setLayoutParams(btnViewParams);
        buttonLayout.addView(btnGapView);

        nextButton = new Button(this);
        nextButton.setText("Next →");
        nextButton.setTextColor(Color.WHITE);
        nextButton.setLayoutParams(btnParams);
        nextButton.setBackground(ContextCompat.getDrawable(this, R.drawable.blue_button));
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
        footerLayout.setPadding(0, 0, 0, 0);
        footerLayout.setWeightSum(8);
        footerLayout.setLayoutParams(footerParams);

        // Footer Items
        String[] itemNames = {"TIME", "Day and Date", "Battery %", "AC Supply", "Network", "Remote Access", "Shut Down"};
        int[] itemDrawables = {
                R.drawable.blue_button,
                R.drawable.green_button,
                R.drawable.gray_button,
                R.drawable.green_button,
                R.drawable.blue_button,
                R.drawable.gray_button,
                R.drawable.red_button
        };

        View[] footerItems = new View[7];
        timeTextView = new TextView(this);
        dayTextView = new TextView(this);
        batteryTextView = new TextView(this);
        acSupplyButton = new Button(this);
        networkButton = new Button(this);
        remoteHelpButton = new Button(this);
        shutDownButton = new Button(this);

        footerItems[0] = timeTextView;
        footerItems[1] = dayTextView;
        footerItems[2] = batteryTextView;
        footerItems[3] = acSupplyButton;
        footerItems[4] = networkButton;
        footerItems[5] = remoteHelpButton;
        footerItems[6] = shutDownButton;

        for (int i = 0; i < itemNames.length; i++) {
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, i == 5 ? 1.2f : 1f
            );

            if (i < 3) {
                TextView textView = (TextView) footerItems[i];
                textView.setText(itemNames[i]);
                int textColor = Color.BLACK;
                textView.setTextColor(textColor);
                float textSize = i == 0 ? 16f : (i == 1 ? 16f : 18f);
                textView.setTextSize(textSize);
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(0, 0, 0, 0);
                itemParams.setMargins(0, 200, 0, 0);
                if (i == 0) {
                    itemParams.setMargins(-120, 200, 10, 0);
                }
                textView.setLayoutParams(itemParams);
                final int index = i;
                textView.setOnClickListener(v -> handleFooterClick(index));
            } else {
                Button button = (Button) footerItems[i];
                button.setText(itemNames[i]);
                button.setTextColor(Color.WHITE);
                button.setBackground(ContextCompat.getDrawable(this, itemDrawables[i]));
                button.setPadding(0, 0, 0, 0);
                if (i == 4) {
//                    button.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_network, 0);
                    button.setCompoundDrawablePadding(2);
                    button.setPadding(8, 0, 8, 0);
//                    itemParams.setMargins(30, 0, 30, 0);
                    button.setTextSize(12);
                }
                if (i == 5) {
                    button.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_remote, 0);
                    button.setCompoundDrawablePadding(2);
                    button.setPadding(0, 0, 8, 0);
                }
                if (i == 3) {
                    itemParams.setMargins(250, 200, 30, 0);
                } else {
                    itemParams.setMargins(30, 200, 30, 0);
                }
                if (i == 6) {
                    itemParams.setMargins(120, 200, -70, 0);
                }
                button.setLayoutParams(itemParams);
                final int index = i;
                button.setOnClickListener(v -> handleFooterClick(index));
            }
            footerLayout.addView(footerItems[i]);
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

        maintenanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent1 = new Intent(LoginActivity.this, NewMainActivity.class);
                startActivity(intent1);
            }
        });

        // Load device details
        loadDeviceDetails();

        // Update TIME and Day/Date TextViews initially
        updateTimeTextView();
        updateDayDateTextView();

        // Start real-time time updates
        startTimeUpdates();

        // Register BroadcastReceiver for battery updates
        registerBatteryReceiver();

        // Update network button text initially
        updateNetworkButtonText();

        // Set full-screen mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    private void updateTimeTextView() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        timeTextView.setText(currentTime);
    }

    private void startTimeUpdates() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeTextView();
                timeHandler.postDelayed(this, 60000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateDayDateTextView() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EE ,dd.MM.yyyy", Locale.getDefault());
        String currentDayDate = dayFormat.format(new Date());
        dayTextView.setText(currentDayDate);
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.lztek.api.demo.STATUS_UPDATE".equals(intent.getAction())) {
                    long batteryPercentage = intent.getLongExtra("battery_percentage", -1);
                    long chargingStatus = intent.getLongExtra("charging_status", -1);
                    if (batteryPercentage >= 0) {
                        batteryTextView.setText(batteryPercentage + "\uD83D\uDD0B");
                    } else {
                        batteryTextView.setText("Battery %\nN/A");
                    }
                    if (chargingStatus >= 0) {
                        acSupplyButton.setText((chargingStatus == 1 ? "Connected" : "Disconnected"));
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
        registerReceiver(batteryReceiver, filter);
    }

    private String getAcSupplyStatus() {
        return "Unknown";
    }

    private void showNetworkModeDialog() {
        String[] networkModes = {"Wi-Fi", "Mobile Data"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Network Mode");
        builder.setItems(networkModes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showWifiNetworksDialog();
                } else {
                    toggleMobileData();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showWifiNetworksDialog() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Toast.makeText(this, "Enabling Wi-Fi...", Toast.LENGTH_SHORT).show();
        }

        // Start Wi-Fi scan
        wifiManager.startScan();
        List<ScanResult> wifiList = wifiManager.getScanResults();
        List<String> wifiNames = new ArrayList<>();
        for (ScanResult result : wifiList) {
            if (result.SSID != null && !result.SSID.isEmpty()) {
                wifiNames.add(result.SSID);
            }
        }

        if (wifiNames.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi networks found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show dialog with Wi-Fi networks
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Available Wi-Fi Networks");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, wifiNames);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedSSID = wifiNames.get(which);
                networkButton.setText("Wi-Fi: " + selectedSSID);
                Toast.makeText(LoginActivity.this, "Selected: " + selectedSSID, Toast.LENGTH_SHORT).show();
                // Note: Actual connection requires credentials; implement if needed
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void toggleMobileData() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            // Check current Mobile Data state
            NetworkInfo mobileNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            boolean isMobileDataEnabled = mobileNetwork != null && mobileNetwork.isConnectedOrConnecting();

            // Toggle Mobile Data using reflection (for older APIs)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Class<?> cmClass = cm.getClass();
                Method setMobileDataEnabledMethod = cmClass.getDeclaredMethod("setMobileDataEnabled", boolean.class);
                setMobileDataEnabledMethod.setAccessible(true);
                setMobileDataEnabledMethod.invoke(cm, !isMobileDataEnabled);
            } else {
                // For newer APIs, open Settings
                Intent intent = new Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "Please toggle Mobile Data in Settings", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update button text
            networkButton.setText("Mobile Data: " + (!isMobileDataEnabled ? "On" : "Off"));
            Toast.makeText(this, "Mobile Data " + (!isMobileDataEnabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to toggle Mobile Data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNetworkButtonText() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo != null && wifiInfo.getSSID() != null ? wifiInfo.getSSID().replace("\"", "") : "Wi-Fi";
            networkButton.setText("Wi-Fi: " + ssid);
        } else {
            NetworkInfo mobileNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            boolean isMobileDataEnabled = mobileNetwork != null && mobileNetwork.isConnectedOrConnecting();
            networkButton.setText("Mobile Data: " + (isMobileDataEnabled ? "On" : "Off"));
        }
    }

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
                showPowerOffDialog();
            }
        });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void rebootDevice() {
        try {
            if (mLztek != null) {
                mLztek.softReboot();
                Toast.makeText(this, "Rebooting...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Lztek not initialized!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Reboot failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPowerOffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Schedule Power On");
        builder.setMessage("Enter time interval (in minutes) to schedule power-on:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes");
        input.setText("5"); // Default value of 5 minutes
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String inputText = input.getText().toString();
                powerOffDevice(inputText);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void powerOffDevice(String timeInput) {
        try {
            int min = -1;
            try {
                min = Integer.parseInt(timeInput);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (min <= 0) {
                Toast.makeText(this, "Time must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mLztek != null) {
                mLztek.alarmPoweron(min * 60);
                Toast.makeText(this, "Power-off scheduled in " + min + " minutes", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Lztek not initialized!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Power-off failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFooterClick(int index) {
        switch (index) {
            case 0: // TIME
                updateTimeTextView();
                break;
            case 1: // Day and Date
                updateDayDateTextView();
                break;
            case 2: // Battery %
                break;
            case 3: // AC Supply
                break;
            case 4: // Network
                showNetworkModeDialog();
                updateNetworkButtonText();
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

    private void submitCode() {
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
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
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
    }
}


//package com.lztek.api.demo;
//
//import android.annotation.TargetApi;
//import android.app.AlertDialog;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.graphics.Color;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.view.Gravity;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.content.ContextCompat;
//
//import com.bumptech.glide.Glide;
//import com.bumptech.glide.request.RequestOptions;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.Locale;
//
//public class LoginActivity extends AppCompatActivity {
//
//    private StringBuilder enteredKey = new StringBuilder();
//    private TextView titleText;
//    private TextView enteredKeyText;
//    private TextView statusText;
//    private ImageView logo;
//    private TextView deviceInfoText;
//    private Button maintenanceButton;
//    private Button nextButton;
//    private Button calibrateButton;
//    private Button helpButton;
//    private View btnGapView;
//    private TextView timeTextView, dayTextView, batteryTextView, ftbtnGapView; // Changed to TextView
//    private Button acSupplyButton, networkButton, remoteHelpButton, shutDownButton;
//
//    private LinearLayout footerLayout;
//    private Handler timeHandler; // For real-time updates
//    private Runnable timeRunnable; // Runnable to update time
//    private BroadcastReceiver batteryReceiver; // To receive battery updates from SerialPortService
//
//    private com.lztek.toolkit.Lztek mLztek;
//    private Button mBtReboot = null;
//    private Button mBtPowerOff = null;
//    private EditText mEtTime = null;
//
//    @TargetApi(Build.VERSION_CODES.O)
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        Intent intent = new Intent(this, SerialPortService.class);
//        startService(intent);
//
//        // Main Layout (Vertical)
//        LinearLayout layout = new LinearLayout(this);
//        layout.setOrientation(LinearLayout.VERTICAL);
//        layout.setPadding(50, 10, 50, 0);
//        layout.setGravity(Gravity.CENTER_HORIZONTAL);
//        layout.setWeightSum(10);
//        layout.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_gradient));
//
//        // Button Layout (Horizontal)
//        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2
//        );
//        buttonLayoutParams.setMargins(0, 0, 0, 20);
//
//        LinearLayout buttonLayout = new LinearLayout(this);
//        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
//        buttonLayout.setGravity(Gravity.CENTER_HORIZONTAL);
//        buttonLayout.setWeightSum(8);
//        buttonLayout.setLayoutParams(buttonLayoutParams);
//
//        // Button Layout Params
//        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
//                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
//        );
//        btnParams.setMargins(10, 0, 10, 0);
//
//        // Button View Params
//        LinearLayout.LayoutParams btnViewParams = new LinearLayout.LayoutParams(
//                0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f
//        );
//
//        // Buttons
//        maintenanceButton = new Button(this);
//        maintenanceButton.setText("Maintenance \uD83D\uDEE0");
//        maintenanceButton.setTextColor(Color.WHITE);
//        maintenanceButton.setLayoutParams(btnParams);
//        maintenanceButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
//        buttonLayout.addView(maintenanceButton);
//
//        calibrateButton = new Button(this);
//        calibrateButton.setText("Calibrate ⚙");
//        calibrateButton.setTextColor(Color.WHITE);
//        calibrateButton.setLayoutParams(btnParams);
//        calibrateButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
//        buttonLayout.addView(calibrateButton);
//
//        helpButton = new Button(this);
//        helpButton.setText("Help ?");
//        helpButton.setTextColor(Color.WHITE);
//        helpButton.setLayoutParams(btnParams);
//        helpButton.setBackground(ContextCompat.getDrawable(this, R.drawable.gray_button));
//        buttonLayout.addView(helpButton);
//
//        btnGapView = new View(this);
//        btnGapView.setLayoutParams(btnViewParams);
//        buttonLayout.addView(btnGapView);
//
//        nextButton = new Button(this);
//        nextButton.setText("Next →");
//        nextButton.setTextColor(Color.WHITE);
//        nextButton.setLayoutParams(btnParams);
//        nextButton.setBackground(ContextCompat.getDrawable(this, R.drawable.blue_button));
//        buttonLayout.addView(nextButton);
//
//        layout.addView(buttonLayout);
//
//        // Logo ImageView
//        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3
//        );
//        logo = new ImageView(this);
//        logo.setAdjustViewBounds(true);
//        logo.setMaxHeight(350);
//        logo.setLayoutParams(logoParams);
//        layout.addView(logo);
//
//        // Device Info Text
//        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2
//        );
//        deviceInfoText = new TextView(this);
//        deviceInfoText.setTextSize(18);
//        deviceInfoText.setGravity(Gravity.CENTER);
//        deviceInfoText.setLayoutParams(infoParams);
//        layout.addView(deviceInfoText);
//
//
//        // Footer Layout (Fixed Bottom)
//        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3
//        );
//        footerLayout = new LinearLayout(this);
//        footerLayout.setOrientation(LinearLayout.HORIZONTAL);
//        footerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
//        footerLayout.setPadding(0, 0, 0, 0); // Zero padding to eliminate external gaps
//        footerLayout.setWeightSum(8); // 7 items
//        footerLayout.setLayoutParams(footerParams);
//
//// Footer Items
//        String[] itemNames = {"TIME", "Day and Date", "Battery %", "AC Supply", "Network", "Remote Access", "Shut Down"};
//        int[] itemDrawables = {
//                R.drawable.blue_button,
//                R.drawable.green_button,
//                R.drawable.gray_button,
//                R.drawable.green_button,
//                R.drawable.blue_button,
//                R.drawable.gray_button,
//                R.drawable.red_button
//        };
//
//        View[] footerItems = new View[7];
//        timeTextView = new TextView(this);
//        dayTextView = new TextView(this);
//        batteryTextView = new TextView(this);
//        acSupplyButton = new Button(this);
//        networkButton = new Button(this);
//        remoteHelpButton = new Button(this);
//        shutDownButton = new Button(this);
//
//        footerItems[0] = timeTextView;
//        footerItems[1] = dayTextView;
//        footerItems[2] = batteryTextView;
//        footerItems[3] = acSupplyButton;
//        footerItems[4] = networkButton;
//        footerItems[5] = remoteHelpButton;
//        footerItems[6] = shutDownButton;
//
//        for (int i = 0; i < itemNames.length; i++) {
//            // Create new LayoutParams for each item
//            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
//                    0, ViewGroup.LayoutParams.WRAP_CONTENT, i == 5 ? 1.2f : 1f // Increase weight for Network button
//            );
//
//            if (i < 3) {
//                // Configure TextView for first 3 items
//                TextView textView = (TextView) footerItems[i];
//                textView.setText(itemNames[i]);
//                // Custom text color
//                int textColor = Color.BLACK;
//                textView.setTextColor(textColor);
//                // Custom text size (in sp)
//                float textSize = i == 0 ? 16f : (i == 1 ? 16f : 18f);
//                textView.setTextSize(textSize);
//                textView.setGravity(Gravity.CENTER);
//                textView.setPadding(0, 0, 0, 0); // Zero padding for TextView
//                // No background to avoid drawable padding
//                // textView.setBackground(ContextCompat.getDrawable(this, itemDrawables[i]));
//                itemParams.setMargins(0, 200, 0, 0); // Zero margins for TextViews
//                if (i == 0) {
//                    itemParams.setMargins(-120, 200, 10, 0);
//                }
//                textView.setLayoutParams(itemParams);
//                final int index = i;
//                textView.setOnClickListener(v -> handleFooterClick(index));
//            } else {
//                // Configure Button for last 4 items
//                Button button = (Button) footerItems[i];
//                button.setText(itemNames[i]);
//                button.setTextColor(Color.WHITE);
//                button.setBackground(ContextCompat.getDrawable(this, itemDrawables[i]));
//                button.setPadding(0, 0, 0, 0); // Zero padding for Button
//                if (i == 4) { // Network button
//                    button.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_network, 0); // Icon on the left
//                    button.setCompoundDrawablePadding(2); // Space between icon and text
//                    button.setPadding(0,0,8,0);
//                }
//                if (i == 5) { // Network button
//                    button.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_remote, 0); // Icon on the left
//                    button.setCompoundDrawablePadding(2); // Space between icon and text
//                    button.setPadding(0,0,8,0);
//                }
//                if (i == 3) {
//                    itemParams.setMargins(250, 200, 30, 0); // Increased left margin for AC Supply
//                } else {
//                    itemParams.setMargins(30, 200, 30, 0); // Default margins for other buttons
//                }
//                if (i == 6) {
//                    itemParams.setMargins(120, 200, -70, 0);
//                }
//                button.setLayoutParams(itemParams);
//                final int index = i;
//                button.setOnClickListener(v -> handleFooterClick(index));
//            }
//            footerLayout.addView(footerItems[i]);
//        }
//
//
//        layout.addView(footerLayout);
//
//        setContentView(layout);
//
//        // Set click listener for Next button
//        nextButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                submitCode();
//            }
//        });
//
//        maintenanceButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent intent1 = new Intent(LoginActivity.this, NewMainActivity.class);
//                startActivity(intent1);
//            }
//        });
//
//        // Load device details
//        loadDeviceDetails();
//
//        // Update TIME and Day/Date TextViews initially
//        updateTimeTextView();
//        updateDayDateTextView();
//
//        // Start real-time time updates
//        startTimeUpdates();
//
//        // Register BroadcastReceiver for battery updates
//        registerBatteryReceiver();
//
//        // Set full-screen mode
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
//        dayTextView.setText(currentDayDate);
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
//                        batteryTextView.setText(batteryPercentage + "\uD83D\uDD0B");
//                    } else {
//                        batteryTextView.setText("Battery %\nN/A");
//                    }
//                    // Optionally, you can also use chargingStatus to show charging state
//                    if (chargingStatus >= 0) {
//                        acSupplyButton.setText((chargingStatus == 1 ? "Connected" : "Disconnected"));
//                    }
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(batteryReceiver, filter);
//    }
//
//    // Placeholder for AC Supply status (now handled via broadcast)
//    private String getAcSupplyStatus() {
//        return "Unknown"; // This will be updated via the broadcast
//    }
//
//    // Show network mode selection dialog
//    private void showNetworkModeDialog() {
//        String[] networkModes = {"Wi-Fi", "Mobile Data"};
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Select Network Mode");
//        builder.setItems(networkModes, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                String selectedMode = networkModes[which];
//                networkButton.setText(selectedMode);
//                Toast.makeText(LoginActivity.this, "Switched to " + selectedMode, Toast.LENGTH_SHORT).show();
//            }
//        });
//        builder.setNegativeButton("Cancel", null);
//        builder.show();
//    }
//
////    // Show shutdown options dialog
////    private void showShutdownDialog() {
////        AlertDialog.Builder builder = new AlertDialog.Builder(this);
////        builder.setTitle("Shutdown Options");
////        builder.setMessage("Choose an option:");
////
////        builder.setPositiveButton("Reboot", new DialogInterface.OnClickListener() {
////            @Override
////            public void onClick(DialogInterface dialog, int which) {
////                rebootDevice();
////            }
////        });
////
////        builder.setNegativeButton("Power Off", new DialogInterface.OnClickListener() {
////            @Override
////            public void onClick(DialogInterface dialog, int which) {
////                powerOffDevice();
////            }
////        });
////
////        builder.setNeutralButton("Cancel", null);
////        builder.show();
////    }
////
////    // Function to reboot the device
////    private void rebootDevice() {
////        try {
////            mLztek.softReboot();
////        } catch (Exception e) {
////            e.printStackTrace();
////            Toast.makeText(this, "Reboot failed!", Toast.LENGTH_SHORT).show();
////        }
////    }
////
////    // Function to power off the device
////    private void powerOffDevice() {
////        int min = -1;
////        try {
////            min = Integer.parseInt(mEtTime.getText().toString());
////        } catch (Exception e) {
////        }
////        if (min <= 0) {
////            mEtTime.requestFocus();
////            return;
////        }
////
////        mLztek.alarmPoweron(min * 60);
////    }
//
//    private void showShutdownDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Shutdown Options");
//        builder.setMessage("Choose an option:");
//
//        builder.setPositiveButton("Reboot", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                rebootDevice(); // Directly call the reboot method
//            }
//        });
//
//        builder.setNegativeButton("Power Off", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                powerOffDevice(); // Directly call the power-off method
//            }
//        });
//
//        builder.setNeutralButton("Cancel", null);
//        builder.show();
//    }
//
//    private void rebootDevice() {
//        try {
//            // Reboot command (requires root permissions in most cases)
//            Runtime.getRuntime().exec(new String[] {"su", "-c", "reboot"});
//            Toast.makeText(this, "Rebooting...", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Reboot failed!", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private void powerOffDevice() {
//        try {
//            // Power off command (requires root permissions)
//
//            Toast.makeText(this, "Powering off...", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(this, "Power off failed!", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//
//    // Footer Item Click Handler
//    private void handleFooterClick(int index) {
//        switch (index) {
//            case 0: // TIME
//                updateTimeTextView();
//                break;
//            case 1: // Day and Date
//                updateDayDateTextView();
//                break;
//            case 2: // Battery % (Updated via broadcast, no action needed on click)
//                break;
//            case 3: // AC Supply (Updated via broadcast, no action needed on click)
//                break;
//            case 4: // Network
//                showNetworkModeDialog();
//                break;
//            case 5: // Remote Access
//                Toast.makeText(this, "Remote Access Clicked!", Toast.LENGTH_SHORT).show();
//                break;
//            case 6: // Shut Down
//                showShutdownDialog();
//                break;
//        }
//    }
//
//    private void showToast(String message) {
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
//    }
//
//    // Load API Data (devices_name, created_date, companyName, blobUrl)
//    private void loadDeviceDetails() {
//        String devicesName = GlobalVars.getDevicesName();
//        String createdDate = GlobalVars.getCreatedDate();
//        String companyName = GlobalVars.getCustomerCompanyName();
//        String blobUrl = GlobalVars.getCustomerBlobUrl();
//
//        String formattedDate = formatDate(createdDate);
//        String deviceInfo = "Device: " + (devicesName != null ? devicesName : "N/A") +
//                "\n\nCreated: " + formattedDate +
//                "\n\nCompany: " + (companyName != null ? companyName : "N/A");
//        deviceInfoText.setText(deviceInfo);
//
//        if (blobUrl != null && !blobUrl.isEmpty()) {
//            Glide.with(this)
//                    .load(blobUrl)
//                    .apply(new RequestOptions().placeholder(R.drawable.cnrgi_logo).error(R.drawable.cnrgi_logo))
//                    .into(logo);
//        } else {
//            logo.setImageResource(R.drawable.cnrgi_logo);
//        }
//    }
//
//    // Handles the Next button action
//    private void submitCode() {
//        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
//        startActivity(intent);
//    }
//
//    private String formatDate(String inputDate) {
//        if (inputDate == null || inputDate.isEmpty()) {
//            return "N/A";
//        }
//        try {
//            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
//            SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
//            Date date = apiFormat.parse(inputDate);
//            return outputFormat.format(date);
//        } catch (ParseException e) {
//            e.printStackTrace();
//            return "N/A";
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // Stop the time updates
//        if (timeHandler != null && timeRunnable != null) {
//            timeHandler.removeCallbacks(timeRunnable);
//        }
//        // Unregister the battery receiver
//        if (batteryReceiver != null) {
//            unregisterReceiver(batteryReceiver);
//        }
//    }
//}
