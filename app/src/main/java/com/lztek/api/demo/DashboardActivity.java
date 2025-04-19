package com.lztek.api.demo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.lztek.api.demo.data.DataParser;
import com.lztek.api.demo.data.ECG;
import com.lztek.api.demo.data.NIBP;
import com.lztek.api.demo.data.SpO2;
import com.lztek.api.demo.data.Temp;

public class DashboardActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {

    private static final String TAG = "DashboardActivity";

    private Button backButton, nextButton, enterButton;
    private View internalCameraIndicator, usbCameraIndicator, keyboardIndicator;
    private View spO2Indicator, ecgIndicator, nibpIndicator, tempIndicator;
    private GlobalVars globalVars;
    private CameraStatusChecker cameraStatusChecker;
    private BerrySerialPort serialPort;
    private DataParser dataParser;
    private BerrySensorChecker berrySensorChecker;
    private Handler handler;
    private Runnable indicatorUpdater;
    private TextView stethoBtn, othersButton;

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
        nextButton = findViewById(R.id.top_Next_button);
        enterButton = findViewById(R.id.enter_Dbtn);
        internalCameraIndicator = findViewById(R.id.inCam_view);
        usbCameraIndicator = findViewById(R.id.usbCam_view);
        keyboardIndicator = findViewById(R.id.keyboard_view);
        spO2Indicator = findViewById(R.id.spo2_indicator);
        ecgIndicator = findViewById(R.id.ecg_indicator);
        nibpIndicator = findViewById(R.id.nibp_indicator);
        tempIndicator = findViewById(R.id.temp_indicator);
        stethoBtn = findViewById(R.id.stetho_btn);
        othersButton = findViewById(R.id.othresButton);

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
        enterButton.setOnClickListener(view -> {
            Intent patientIntent = new Intent(DashboardActivity.this, PatientEntryActivity.class);
            startActivity(patientIntent);
        });
        nextButton.setOnClickListener(view -> {
            Intent cameraIntent = new Intent(DashboardActivity.this, CameraFeedActivity.class);
            startActivity(cameraIntent);
        });
        stethoBtn.setOnClickListener(view -> {
            Intent chestoIntent = new Intent(DashboardActivity.this, ChestoDeviceActivity.class);
            startActivity(chestoIntent);
        });
        othersButton.setOnClickListener(view -> {
            Intent ointent = new Intent(DashboardActivity.this, OthersActivity.class);
            startActivity(ointent);
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
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

    @Override
    public void onFirmwareReceived(String str) {}
    @Override
    public void onHardwareReceived(String str) {}

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}