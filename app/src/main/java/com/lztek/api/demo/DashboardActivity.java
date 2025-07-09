package com.lztek.api.demo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lztek.api.demo.data.DataParser;
import com.lztek.api.demo.data.ECG;
import com.lztek.api.demo.data.NIBP;
import com.lztek.api.demo.data.SpO2;
import com.lztek.api.demo.data.Temp;

public class DashboardActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {

    private static final String TAG = "DashboardActivity";

    private Button backButton;

    private Button appoimentButton;
    private Button patienMonitorButton;
    private Button newAppoinmentButton;
    private Button offlineButton;
    private Button myfilesStorage;
    private Button ambulanceMode;

    private ImageView internalCameraIndicator, usbCameraIndicator, keyboardIndicator;
    private View spO2Indicator, ecgIndicator, nibpIndicator, tempIndicator;
    private GlobalVars globalVars;
    private CameraStatusChecker cameraStatusChecker;
    private BerrySerialPort serialPort;
    private DataParser dataParser;
    private BerrySensorChecker berrySensorChecker;
    private Handler handler;
    private Runnable indicatorUpdater;
    private TextView stethoBtn, othersButton;
    private TextView peramedicName, peramedicId;

    private AppCompatImageView profileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        globalVars = GlobalVars.getInstance();
        cameraStatusChecker = new CameraStatusChecker(this);
        cameraStatusChecker.startChecking();

        serialPort = new BerrySerialPort(this);
        serialPort.setOnDataReceivedListener(this);
        dataParser = new DataParser(this);
        dataParser.start();
        berrySensorChecker = new BerrySensorChecker(this, serialPort);
        serialPort.connect();
        berrySensorChecker.startChecking();

        backButton = findViewById(R.id.top_back_button);
        internalCameraIndicator = findViewById(R.id.inCam_view);
        usbCameraIndicator = findViewById(R.id.usbCam_view);
        keyboardIndicator = findViewById(R.id.keyboard_view);
        spO2Indicator = findViewById(R.id.spo2_indicator);
        ecgIndicator = findViewById(R.id.ecg_indicator);
        nibpIndicator = findViewById(R.id.nibp_indicator);
        tempIndicator = findViewById(R.id.temp_indicator);
        stethoBtn = findViewById(R.id.stetho_btn);
        othersButton = findViewById(R.id.othresButton);
        ambulanceMode = findViewById(R.id.ambulance_mode);

        peramedicName = findViewById(R.id.pramedic_name);
        peramedicId = findViewById(R.id.permedic_id);

        profileImage = findViewById(R.id.profile_image);

        peramedicName.setText("Name: "+GlobalVars.getFirstName() + " " + GlobalVars.getLastName());
        peramedicId.setText("ID: " + GlobalVars.getParamedicId());


        offlineButton = findViewById(R.id.offline_button);

        appoimentButton = findViewById(R.id.my_appointments);
        patienMonitorButton = findViewById(R.id.patient_monitor);

        newAppoinmentButton = findViewById(R.id.new_appoinments);

        myfilesStorage = findViewById(R.id.myfiles_storage);

        handler = new Handler(Looper.getMainLooper());
        indicatorUpdater = new Runnable() {
            @Override
            public void run() {
                updateIndicators();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(indicatorUpdater);

        backButton.setOnClickListener(view -> finish());

        appoimentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, AppointmentListActivity.class);
                startActivity(intent);
            }
        });

        patienMonitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, PatientMonitorDetailsActivity.class);
                startActivity(intent);
            }
        });

        newAppoinmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, TextActivity.class);
                startActivity(intent);
            }
        });

        offlineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, OfflineModeActivity.class);
                startActivity(intent);
            }
        });

        ambulanceMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this,OnlineSessionActivity.class);
                startActivity(intent);
            }
        });

        stethoBtn.setOnClickListener(view -> {
            Intent chestoIntent = new Intent(DashboardActivity.this, ChestoDeviceActivity.class);
            startActivity(chestoIntent);
        });
        othersButton.setOnClickListener(view -> {
            Intent ointent = new Intent(DashboardActivity.this, CameraFeedActivity.class);
            startActivity(ointent);
        });
        myfilesStorage.setOnClickListener(view -> {
            Intent intent = new Intent(DashboardActivity.this, OfflineModeActivity.class);
            startActivity(intent);
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraStatusChecker.stopChecking();
        berrySensorChecker.stopChecking();
        serialPort.disconnect();
        dataParser.stop();
        handler.removeCallbacks(indicatorUpdater);
    }

    private void updateIndicators() {
        boolean internalCam = globalVars.isInternalCameraConnected();
        boolean usbCam = globalVars.isUSBCameraConnected();
        boolean keyboard = globalVars.isKeyboardConnected();

        internalCameraIndicator.setBackgroundColor(internalCam ? Color.GREEN : Color.RED);
        usbCameraIndicator.setBackgroundColor(usbCam ? Color.GREEN : Color.RED);
        keyboardIndicator.setBackgroundColor(keyboard ? Color.GREEN : Color.RED);

        boolean spO2Connected = globalVars.isSpO2Connected();
        boolean ecgConnected = globalVars.isECGConnected();
        boolean nibpConnected = globalVars.isNIBPConnected();
        boolean tempConnected = globalVars.isTempConnected();

        spO2Indicator.setBackgroundColor(spO2Connected ? Color.GREEN : Color.RED);
        ecgIndicator.setBackgroundColor(ecgConnected ? Color.GREEN : Color.RED);
        nibpIndicator.setBackgroundColor(nibpConnected ? Color.GREEN : Color.RED);
        tempIndicator.setBackgroundColor(tempConnected ? Color.GREEN : Color.RED);

//        Log.d(TAG, "Indicators updated - Internal: " + internalCam +
//                ", USB: " + usbCam + ", Keyboard: " + keyboard +
//                ", SpO2: " + spO2Connected + ", ECG: " + ecgConnected +
//                ", NIBP: " + nibpConnected + ", Temp: " + tempConnected);
    }

    @Override
    public void onDataReceived(byte[] data) {
//        Log.d(TAG, "📥 Received raw data: " + bytesToHex(data));
        dataParser.add(data);
    }

    @Override
    public void onConnectionStatusChanged(String status) {
//        Log.d(TAG, "🔄 Connection status: " + status);
//        runOnUiThread(() -> Toast.makeText(this, "Berry: " + status, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onSpO2WaveReceived(int dat) {
//        Log.d(TAG, "📈 SpO2 Wave received: " + dat);
        berrySensorChecker.onSpO2DataReceived();
    }

    @Override
    public void onSpO2Received(SpO2 spo2) {
//        Log.d(TAG, "📊 SpO2 received - SpO2: " + spo2.getSpO2() + ", Pulse: " + spo2.getPulseRate());
        berrySensorChecker.onSpO2DataReceived();
    }

    @Override
    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        Log.d(TAG, "📈 ECG Wave received - I: " + leadI + ", II: " + leadII + ", III: " + leadIII);
        berrySensorChecker.onECGDataReceived();
    }

//    @Override
//    public void onECGAllWaveReceived(int leadI) {
//
//    }

    @Override
    public void onRespWaveReceived(int dat) {
//        Log.d(TAG, "📈 Resp Wave received: " + dat);
        berrySensorChecker.onECGDataReceived();
    }

    @Override
    public void onECGReceived(ECG ecg) {
//        Log.d(TAG, "📊 ECG received - HR: " + ecg.getHeartRate());
        berrySensorChecker.onECGDataReceived();
    }

    @Override
    public void onTempReceived(Temp temp) {
//        Log.d(TAG, "📊 Temp received: " + temp.toString());
        berrySensorChecker.onTempDataReceived();
    }

    @Override
    public void onNIBPReceived(NIBP nibp) {
//        Log.d(TAG, "📊 NIBP received: " + nibp.toString());
        berrySensorChecker.onNIBPDataReceived();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}