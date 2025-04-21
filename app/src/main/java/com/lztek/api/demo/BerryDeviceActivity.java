package com.lztek.api.demo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.lztek.api.demo.data.DataParser;
import com.lztek.api.demo.data.ECG;
import com.lztek.api.demo.data.NIBP;
import com.lztek.api.demo.data.SpO2;
import com.lztek.api.demo.data.Temp;
import com.lztek.api.demo.view.WaveformView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.graphics.pdf.PdfDocument;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {

    private static final String TAG = "BerryDeviceActivity";

    private Button btnSerialCtr;
    private TextView tvSerialInfo;
    private TextView tvECGinfo;
    private TextView tvSPO2info;
    private TextView tvTEMPinfo;
    private TextView tvNIBPinfo;
    private TextView tvRespRate;
    private LinearLayout llAbout;
    private TextView tvFWVersion;
    private TextView tvHWVersion;
    private WaveformView wfSpO2;
    private WaveformView wfECG1;
    private WaveformView wfECG2;
    private WaveformView wfECG3;
    private WaveformView wfResp;
    private Spinner spinnerECG1, spinnerECG2, spinnerECG3;
    private Button btnGenerateReport;

    private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
    private int[] selectedECG = {0, 1, 2};

    private BerrySerialPort serialPort;
    private DataParser mDataParser;
    private ProgressDialog mConnectingDialog;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private int spO2Counter = 0;
    private static final int SPO2_SKIP_FACTOR = 2;

    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_CODE = 100;

    private BroadcastReceiver vitalStatusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_berry_device);

        initData();
        initView();
        initPermissions();
        setupVitalStatusReceiver();

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Check initial vital status
        checkVitalStatus();
    }

    private void initPermissions() {
        List<String> permissionList = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }
        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE);
        }
    }

    private void initData() {
        mDataParser = new DataParser(this);
        backgroundThread = new HandlerThread("BackgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        mDataParser.start();
        serialPort = new BerrySerialPort(this);
        serialPort.setOnDataReceivedListener(this);
    }

    private void initView() {
        btnSerialCtr = findViewById(R.id.btnBtCtr);
        btnSerialCtr.setText("Connect");
        tvSerialInfo = findViewById(R.id.tvbtinfo);
        tvECGinfo = findViewById(R.id.tvECGinfo);
        tvSPO2info = findViewById(R.id.tvSPO2info);
        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
        llAbout = findViewById(R.id.llAbout);
        tvFWVersion = findViewById(R.id.tvFWverison);
        tvHWVersion = findViewById(R.id.tvHWverison);
        wfECG1 = findViewById(R.id.wfECG1);
        wfECG2 = findViewById(R.id.wfECG2);
        wfECG3 = findViewById(R.id.wfECG3);
        wfSpO2 = findViewById(R.id.wfSpO2);
        wfResp = findViewById(R.id.wfResp);
        tvRespRate = findViewById(R.id.tvRespRate);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);

        spinnerECG1 = findViewById(R.id.spinnerECG1);
        spinnerECG2 = findViewById(R.id.spinnerECG2);
        spinnerECG3 = findViewById(R.id.spinnerECG3);

        setupDropdown(spinnerECG1, 0);
        setupDropdown(spinnerECG2, 1);
        setupDropdown(spinnerECG3, 2);

        spinnerECG1.bringToFront();
        spinnerECG1.setZ(100);
        wfECG1.setZ(1);

        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
        mConnectingDialog.setCancelable(false);

        llAbout.setOnClickListener(v -> {
            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
            serialPort.write(fwCmd);
            serialPort.write(hwCmd);
        });

        btnGenerateReport.setOnClickListener(v -> {
            Log.d("hum", "Generate Report button clicked");
            backgroundHandler.post(() -> generateReport());
        });
    }

    private void setupVitalStatusReceiver() {
        vitalStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long vitalStatus = intent.getLongExtra("vital_status", 0);
                Log.d(TAG, "📡 Received VITAL status: " + vitalStatus);
                if (vitalStatus == 1) {
                    GlobalVars.setVitalOn(true);
                    connectSerialPort();
                } else {
                    GlobalVars.setVitalOn(false);
                    disconnectSerialPort();
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
        registerReceiver(vitalStatusReceiver, filter);
    }

    private void checkVitalStatus() {
        if (GlobalVars.isVitalOn) {
            Log.d(TAG, "🌡️ Initial check: isVitalOn=true, connecting...");
            connectSerialPort();
        } else {
            Log.d(TAG, "🌡️ Initial check: isVitalOn=false, disconnecting...");
            disconnectSerialPort();
        }
    }

    private void connectSerialPort() {
        if (!serialPort.isConnected()) {
            Log.d(TAG, "🔌 Attempting to connect BerrySerialPort");
            runOnUiThread(() -> mConnectingDialog.show());
            serialPort.connect();
        } else {
            Log.d(TAG, "🔌 BerrySerialPort already connected");
        }
    }

    private void disconnectSerialPort() {
        if (serialPort.isConnected()) {
            Log.d(TAG, "🔌 Attempting to disconnect BerrySerialPort");
            serialPort.disconnect();
        } else {
            Log.d(TAG, "🔌 BerrySerialPort already disconnected");
        }
    }

    private void setupDropdown(Spinner spinner, int index) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, ecgOptions);
        spinner.setAdapter(adapter);
        spinner.setSelection(index);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedECG[index] = position;
                clearGraph(index);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void clearGraph(int index) {
        if (index == 0) wfECG1.clear();
        if (index == 1) wfECG2.clear();
        if (index == 2) wfECG3.clear();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnBtCtr:
                if (!serialPort.isConnected()) {
                    mConnectingDialog.show();
                    serialPort.connect();
                } else {
                    serialPort.disconnect();
                }
                break;
            case R.id.btnNIBPStart:
                serialPort.write(DataParser.CMD_START_NIBP);
                break;
            case R.id.btnNIBPStop:
                serialPort.write(DataParser.CMD_STOP_NIBP);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serialPort.disconnect();
        mDataParser.stop();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        if (vitalStatusReceiver != null) {
            unregisterReceiver(vitalStatusReceiver);
        }
        finish();
    }

    @Override
    public void onDataReceived(byte[] data) {
        Log.d(TAG, "📡 onDataReceived: " + bytesToHex(data));
        backgroundHandler.post(() -> mDataParser.add(data));
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        Log.d(TAG, "🔌 Connection Status: " + status);
        runOnUiThread(() -> {
            tvSerialInfo.setText(status);
            if (status.startsWith("Connected")) {
                btnSerialCtr.setText("Disconnect");
                if (mConnectingDialog.isShowing()) {
                    mConnectingDialog.dismiss();
                }
            } else {
                btnSerialCtr.setText("Connect");
                if (mConnectingDialog.isShowing()) {
                    mConnectingDialog.dismiss();
                }
            }
        });
    }

    @Override
    public void onSpO2WaveReceived(int dat) {
        Log.d(TAG, "📈 SpO2 Wave: " + dat);
        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
    }

    @Override
    public void onSpO2Received(SpO2 spo2) {
        Log.d(TAG, "📊 SpO2 Data: " + spo2.toString());
        backgroundHandler.post(() -> {
            String spo2Label = "SpO2: ";
            String pulseLabel = "Pulse Rate: ";
            String statusLabel = "Status: ";
            String spo2Value = (spo2.getSpO2() != SpO2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) + " %" : "--";
            String pulseValue = (spo2.getPulseRate() != SpO2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) + " /min" : "--";
            String statusValue = spo2.getSensorStatus() != null ? spo2.getSensorStatus() : "Unknown";
            String fullText = spo2Label + spo2Value + "\n" + pulseLabel + pulseValue + "\n" + statusLabel + statusValue;
            SpannableString spannable = new SpannableString(fullText);

            int spo2LabelStart = 0;
            int spo2LabelEnd = spo2Label.length();
            int spo2ValueStart = spo2LabelEnd;
            int spo2ValueEnd = spo2ValueStart + spo2Value.length();
            int pulseLabelStart = fullText.indexOf(pulseLabel);
            int pulseLabelEnd = pulseLabelStart + pulseLabel.length();
            int pulseValueStart = pulseLabelEnd;
            int pulseValueEnd = pulseValueStart + pulseValue.length();
            int statusLabelStart = fullText.indexOf(statusLabel);
            int statusLabelEnd = statusLabelStart + statusLabel.length();
            int statusValueStart = statusLabelEnd;
            int statusValueEnd = statusValueStart + statusValue.length();

            spannable.setSpan(new RelativeSizeSpan(1.5f), spo2LabelStart, spo2LabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(1.5f), pulseLabelStart, pulseLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(1.5f), statusLabelStart, statusLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2ValueStart, spo2ValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseValueStart, pulseValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            runOnUiThread(() -> {
                tvSPO2info.setText(spannable);
                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                wfSpO2.postInvalidate(); // Force redraw
            });
        });
    }

    @Override
    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
        Log.d(TAG, "📈 ECG Wave: I=" + leadI + ", II=" + leadII + ", III=" + leadIII);
        backgroundHandler.post(() -> {
            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
            wfECG1.addAmp(ecgData[selectedECG[0]]);
            wfECG2.addAmp(ecgData[selectedECG[1]]);
            wfECG3.addAmp(ecgData[selectedECG[2]]);
            runOnUiThread(() -> {
                wfECG1.postInvalidate(); // Force redraw
                wfECG2.postInvalidate();
                wfECG3.postInvalidate();
            });
        });
    }

    @Override
    public void onRespWaveReceived(int dat) {
        Log.d(TAG, "📈 Resp Wave: " + dat);
        backgroundHandler.post(() -> wfResp.addAmp(dat));
    }

    @Override
    public void onECGReceived(ECG ecg) {
        Log.d(TAG, "📊 ECG Data: " + ecg.toString());
        backgroundHandler.post(() -> {
            String heartRateLabel = "Heart Rate: ";
            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
            String stLevelValue = (ecg.getSTLevel() != -2.0f) ? String.format("%.2f mV", ecg.getSTLevel()) : "0 mV";
            String arrhyValue = (ecg.getArrythmia() != null) ? ecg.getArrythmia() : ECG.ARRYTHMIA_INVALID;
            String heartRateText = heartRateLabel + heartRateValue + "\nST Level: " + stLevelValue + "\nArrythmia: " + arrhyValue;
            SpannableString heartRateSpannable = new SpannableString(heartRateText);
            int hrStart = heartRateText.indexOf(heartRateValue);
            int hrEnd = hrStart + heartRateValue.length();
            heartRateSpannable.setSpan(new RelativeSizeSpan(2.8f), hrStart, hrEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            String respRateLabel = "RoR/min: ";
            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
            String respRateText = respRateLabel + respRateValue;
            SpannableString respRateSpannable = new SpannableString(respRateText);
            int respStart = respRateText.indexOf(respRateValue);
            int respEnd = respStart + respRateValue.length();
            respRateSpannable.setSpan(new RelativeSizeSpan(2.8f), respStart, respEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            runOnUiThread(() -> {
                tvECGinfo.setText(heartRateSpannable);
                tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
                tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvRespRate.setText(respRateSpannable);
                tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
                tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            });
        });
    }

    @Override
    public void onTempReceived(Temp temp) {
        Log.d(TAG, "🌡️ Temp Data: " + temp.toString());
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
        });
    }

    @Override
    public void onNIBPReceived(NIBP nibp) {
        Log.d(TAG, "🩺 NIBP Data: " + nibp.toString());
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
        });
    }

    @Override
    public void onFirmwareReceived(String str) {
        Log.d(TAG, "📟 Firmware Version: " + str);
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
        });
    }

    @Override
    public void onHardwareReceived(String str) {
        Log.d(TAG, "🔧 Hardware Version: " + str);
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
        });
    }

    private void generateReport() {
        Log.d(TAG, "Starting PDF generation with Flask API");

        // Ensure UI data is updated
        runOnUiThread(() -> {
            wfECG1.postInvalidate();
            wfECG2.postInvalidate();
            wfECG3.postInvalidate();
            wfSpO2.postInvalidate();
            try { Thread.sleep(1500); } catch (Exception e) { Log.e(TAG, "UI sleep failed: " + e.getMessage()); }
        });

        // Extract data from WaveformView
        int[] ecg1Data = wfECG1.getCurrentData();
        int[] ecg2Data = wfECG2.getCurrentData();
        int[] ecg3Data = wfECG3.getCurrentData();
        int[] spO2Data = wfSpO2.getCurrentData();

        Log.d(TAG, "ECG1 data size: " + (ecg1Data != null ? ecg1Data.length : 0));
        Log.d(TAG, "SpO2 data size: " + (spO2Data != null ? spO2Data.length : 0));

        // Get values for labels
        String spO2Value = tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim();
        String pulseRateValue = tvSPO2info.getText().toString().split("\n")[1].replace("Pulse Rate: ", "").trim();
        String heartRateValue = tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim();

        // Create JSON
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            jsonObject.put("spO2Value", spO2Value);
            jsonObject.put("heartRateValue", heartRateValue);
            jsonObject.put("pulseRateValue", pulseRateValue);
            jsonObject.put("ecg1Data", ecg1Data != null ? ecg1Data : new int[0]);
            jsonObject.put("ecg2Data", ecg2Data != null ? ecg2Data : new int[0]);
            jsonObject.put("ecg3Data", ecg3Data != null ? ecg3Data : new int[0]);
            jsonObject.put("spO2Data", spO2Data != null ? spO2Data : new int[0]);
        } catch (Exception e) {
            Log.e(TAG, "JSON creation failed: " + e.getMessage());
        }

        // Send to Flask API
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, jsonObject.toString());
            Request request = new Request.Builder()
                    .url("http://your-server-ip:5000/generate-pdf") // Replace with your Flask server URL
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] pdfBytes = response.body().bytes();
                    File externalDir = getExternalFilesDir(null);
                    if (externalDir != null) {
                        File reportsDir = new File(externalDir, "Reports");
                        if (!reportsDir.exists()) reportsDir.mkdirs();
                        String fileName = "Medical_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf";
                        File file = new File(reportsDir, fileName);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(pdfBytes);
                            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
                            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
                        }
                    }
                } else {
                    Log.e(TAG, "API call failed: " + response.code());
                    runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Failed to generate PDF", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in API call: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}

//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.ProgressDialog;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.Typeface;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.Spinner;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.lztek.api.demo.data.DataParser;
//import com.lztek.api.demo.data.ECG;
//import com.lztek.api.demo.data.NIBP;
//import com.lztek.api.demo.data.SpO2;
//import com.lztek.api.demo.data.Temp;
//import com.lztek.api.demo.view.WaveformView;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Date;
//import java.util.List;
//
//import android.graphics.pdf.PdfDocument;
//
//import org.json.JSONObject;
//
//import okhttp3.MediaType;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//
//    private Button btnSerialCtr;
//    private TextView tvSerialInfo;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
//    private TextView tvFWVersion;
//    private TextView tvHWVersion;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfResp;
//    private Spinner spinnerECG1, spinnerECG2, spinnerECG3;
//    private Button btnGenerateReport;
//
//    private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//
//    private int spO2Counter = 0;
//    private static final int SPO2_SKIP_FACTOR = 2;
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        initData();
//        initView();
//        initPermissions();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//    }
//
//    private void initPermissions() {
//        List<String> permissionList = new ArrayList<>();
//        for (String permission : permissions) {
//            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                permissionList.add(permission);
//            }
//        }
//        if (!permissionList.isEmpty()) {
//            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE);
//        }
//    }
//
//    private void initData() {
//        mDataParser = new DataParser(this);
//        backgroundThread = new HandlerThread("BackgroundThread");
//        backgroundThread.start();
//        backgroundHandler = new Handler(backgroundThread.getLooper());
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect");
//        tvSerialInfo = findViewById(R.id.tvbtinfo);
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
//        tvFWVersion = findViewById(R.id.tvFWverison);
//        tvHWVersion = findViewById(R.id.tvHWverison);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//        btnGenerateReport = findViewById(R.id.btnGenerateReport);
//
//        spinnerECG1 = findViewById(R.id.spinnerECG1);
//        spinnerECG2 = findViewById(R.id.spinnerECG2);
//        spinnerECG3 = findViewById(R.id.spinnerECG3);
//
//        setupDropdown(spinnerECG1, 0);
//        setupDropdown(spinnerECG2, 1);
//        setupDropdown(spinnerECG3, 2);
//
//        spinnerECG1.bringToFront();
//        spinnerECG1.setZ(100);
//        wfECG1.setZ(1);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        llAbout.setOnClickListener(v -> {
//            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
//            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
//            serialPort.write(fwCmd);
//            serialPort.write(hwCmd);
//        });
//
//        btnGenerateReport.setOnClickListener(v -> {
//            Log.d("hum", "Generate Report button clicked");
//            backgroundHandler.post(() -> generateReport());
//        });
//    }
//
//    private void setupDropdown(Spinner spinner, int index) {
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, ecgOptions);
//        spinner.setAdapter(adapter);
//        spinner.setSelection(index);
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedECG[index] = position;
//                clearGraph(index);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
//                    serialPort.disconnect();
//                }
//                break;
//            case R.id.btnNIBPStart:
//                serialPort.write(DataParser.CMD_START_NIBP);
//                break;
//            case R.id.btnNIBPStop:
//                serialPort.write(DataParser.CMD_STOP_NIBP);
//                break;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        serialPort.disconnect();
//        mDataParser.stop();
//        if (backgroundThread != null) {
//            backgroundThread.quitSafely();
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
//        backgroundHandler.post(() -> mDataParser.add(data));
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            tvSerialInfo.setText(status);
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            }
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
//        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        backgroundHandler.post(() -> {
//            String spo2Label = "SpO2: ";
//            String pulseLabel = "Pulse Rate: ";
//            String statusLabel = "Status: ";
//            String spo2Value = (spo2.getSpO2() != SpO2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) + " %" : "--";
//            String pulseValue = (spo2.getPulseRate() != SpO2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) + " /min" : "--";
//            String statusValue = spo2.getSensorStatus() != null ? spo2.getSensorStatus() : "Unknown";
//            String fullText = spo2Label + spo2Value + "\n" + pulseLabel + pulseValue + "\n" + statusLabel + statusValue;
//            SpannableString spannable = new SpannableString(fullText);
//
//            int spo2LabelStart = 0;
//            int spo2LabelEnd = spo2Label.length();
//            int spo2ValueStart = spo2LabelEnd;
//            int spo2ValueEnd = spo2ValueStart + spo2Value.length();
//            int pulseLabelStart = fullText.indexOf(pulseLabel);
//            int pulseLabelEnd = pulseLabelStart + pulseLabel.length();
//            int pulseValueStart = pulseLabelEnd;
//            int pulseValueEnd = pulseValueStart + pulseValue.length();
//            int statusLabelStart = fullText.indexOf(statusLabel);
//            int statusLabelEnd = statusLabelStart + statusLabel.length();
//            int statusValueStart = statusLabelEnd;
//            int statusValueEnd = statusValueStart + statusValue.length();
//
//            spannable.setSpan(new RelativeSizeSpan(1.5f), spo2LabelStart, spo2LabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(1.5f), pulseLabelStart, pulseLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(1.5f), statusLabelStart, statusLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2ValueStart, spo2ValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseValueStart, pulseValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvSPO2info.setText(spannable);
//                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
//                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
//            });
//        });
//    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        backgroundHandler.post(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
//        backgroundHandler.post(() -> wfResp.addAmp(dat));
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
//        backgroundHandler.post(() -> {
//            String heartRateLabel = "Heart Rate: ";
//            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
//            String stLevelValue = (ecg.getSTLevel() != -2.0f) ? String.format("%.2f mV", ecg.getSTLevel()) : "0 mV";
//            String arrhyValue = (ecg.getArrythmia() != null) ? ecg.getArrythmia() : ECG.ARRYTHMIA_INVALID;
//            String heartRateText = heartRateLabel + heartRateValue + "\nST Level: " + stLevelValue + "\nArrythmia: " + arrhyValue;
//            SpannableString heartRateSpannable = new SpannableString(heartRateText);
//            int hrStart = heartRateText.indexOf(heartRateValue);
//            int hrEnd = hrStart + heartRateValue.length();
//            heartRateSpannable.setSpan(new RelativeSizeSpan(2.8f), hrStart, hrEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            String respRateLabel = "RoR/min: ";
//            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
//            String respRateText = respRateLabel + respRateValue;
//            SpannableString respRateSpannable = new SpannableString(respRateText);
//            int respStart = respRateText.indexOf(respRateValue);
//            int respEnd = respStart + respRateValue.length();
//            respRateSpannable.setSpan(new RelativeSizeSpan(2.8f), respStart, respEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvECGinfo.setText(heartRateSpannable);
//                tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
//                tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//                tvRespRate.setText(respRateSpannable);
//                tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
//                tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//            });
//        });
//    }
//
//    @Override
//    public void onTempReceived(Temp temp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//        });
//    }
//
//    @Override
//    public void onFirmwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
//        });
//    }
//
//    @Override
//    public void onHardwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
//        });
//    }
//
//
//
//    private void generateReport() {
//        Log.d(TAG, "Starting PDF generation with Flask API");
//
//        // Ensure UI data is updated
//        runOnUiThread(() -> {
//            wfECG1.postInvalidate();
//            wfECG2.postInvalidate();
//            wfECG3.postInvalidate();
//            wfSpO2.postInvalidate();
//            try { Thread.sleep(1500); } catch (Exception e) { Log.e(TAG, "UI sleep failed: " + e.getMessage()); }
//        });
//
//        // Extract data from WaveformView
//        int[] ecg1Data = wfECG1.getCurrentData();
//        int[] ecg2Data = wfECG2.getCurrentData();
//        int[] ecg3Data = wfECG3.getCurrentData();
//        int[] spO2Data = wfSpO2.getCurrentData();
//
//        Log.d(TAG, "ECG1 data size: " + (ecg1Data != null ? ecg1Data.length : 0));
//        Log.d(TAG, "SpO2 data size: " + (spO2Data != null ? spO2Data.length : 0));
//
//        // Get values for labels
//        String spO2Value = tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim();
//        String pulseRateValue = tvSPO2info.getText().toString().split("\n")[1].replace("Pulse Rate: ", "").trim();
//        String heartRateValue = tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim();
//
//        // Create JSON
//        JSONObject jsonObject = new JSONObject();
//        try {
//            jsonObject.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
//            jsonObject.put("spO2Value", spO2Value);
//            jsonObject.put("heartRateValue", heartRateValue);
//            jsonObject.put("pulseRateValue", pulseRateValue);
//            jsonObject.put("ecg1Data", ecg1Data != null ? ecg1Data : new int[0]);
//            jsonObject.put("ecg2Data", ecg2Data != null ? ecg2Data : new int[0]);
//            jsonObject.put("ecg3Data", ecg3Data != null ? ecg3Data : new int[0]);
//            jsonObject.put("spO2Data", spO2Data != null ? spO2Data : new int[0]);
//        } catch (Exception e) {
//            Log.e(TAG, "JSON creation failed: " + e.getMessage());
//        }
//
//        // Send to Flask API
//        new Thread(() -> {
//            OkHttpClient client = new OkHttpClient();
//            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
//            RequestBody body = RequestBody.create(JSON, jsonObject.toString());
//            Request request = new Request.Builder()
//                    .url("http://your-server-ip:5000/generate-pdf") // Replace with your Flask server URL
//                    .post(body)
//                    .build();
//
//            try (Response response = client.newCall(request).execute()) {
//                if (response.isSuccessful() && response.body() != null) {
//                    byte[] pdfBytes = response.body().bytes();
//                    File externalDir = getExternalFilesDir(null);
//                    if (externalDir != null) {
//                        File reportsDir = new File(externalDir, "Reports");
//                        if (!reportsDir.exists()) reportsDir.mkdirs();
//                        String fileName = "Medical_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf";
//                        File file = new File(reportsDir, fileName);
//                        try (FileOutputStream fos = new FileOutputStream(file)) {
//                            fos.write(pdfBytes);
//                            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
//                            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
//                        }
//                    }
//                } else {
//                    Log.e(TAG, "API call failed: " + response.code());
//                    runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Failed to generate PDF", Toast.LENGTH_SHORT).show());
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error in API call: " + e.getMessage());
//                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//            }
//        }).start();
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//}
//



//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.ProgressDialog;
//import android.content.pm.PackageManager;
//import android.graphics.Typeface;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.Spinner;
//import android.widget.TextView;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.lztek.api.demo.data.DataParser;
//import com.lztek.api.demo.data.ECG;
//import com.lztek.api.demo.data.NIBP;
//import com.lztek.api.demo.data.SpO2;
//import com.lztek.api.demo.data.Temp;
//import com.lztek.api.demo.view.WaveformView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//
//    private Button btnSerialCtr;
//    private TextView tvSerialInfo;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
//    private TextView tvFWVersion;
//    private TextView tvHWVersion;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfResp;
//    private Spinner spinnerECG1, spinnerECG2, spinnerECG3;
//
//
//    private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//
//    private int spO2Counter = 0; // Counter for downsampling
//    private static final int SPO2_SKIP_FACTOR = 2; // Skip every 2nd point for spacing
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        initData();
//        initView();
//        initPermissions();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//    }
//
//    private void initPermissions() {
//        List<String> permissionList = new ArrayList<>();
//        for (String permission : permissions) {
//            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                permissionList.add(permission);
//            }
//        }
//        if (!permissionList.isEmpty()) {
//            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE);
//        }
//    }
//
//    private void initData() {
//        mDataParser = new DataParser(this);
//        backgroundThread = new HandlerThread("BackgroundThread");
//        backgroundThread.start();
//        backgroundHandler = new Handler(backgroundThread.getLooper());
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect");
//        tvSerialInfo = findViewById(R.id.tvbtinfo);
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
//        tvFWVersion = findViewById(R.id.tvFWverison);
//        tvHWVersion = findViewById(R.id.tvHWverison);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//
//        spinnerECG1 = findViewById(R.id.spinnerECG1);
//        spinnerECG2 = findViewById(R.id.spinnerECG2);
//        spinnerECG3 = findViewById(R.id.spinnerECG3);
//
//        setupDropdown(spinnerECG1, 0);
//        setupDropdown(spinnerECG2, 1);
//        setupDropdown(spinnerECG3, 2);
//
//        spinnerECG1.bringToFront();
//        spinnerECG1.setZ(100);
//        wfECG1.setZ(1);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        llAbout.setOnClickListener(v -> {
//            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
//            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
//            serialPort.write(fwCmd);
//            serialPort.write(hwCmd);
//        });
//    }
//
//    private void setupDropdown(Spinner spinner, int index) {
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, ecgOptions);
//        spinner.setAdapter(adapter);
//        spinner.setSelection(index);
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedECG[index] = position;
//                clearGraph(index);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
//                    serialPort.disconnect();
//                }
//                break;
//            case R.id.btnNIBPStart:
//                serialPort.write(DataParser.CMD_START_NIBP);
//                break;
//            case R.id.btnNIBPStop:
//                serialPort.write(DataParser.CMD_STOP_NIBP);
//                break;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        serialPort.disconnect();
//        mDataParser.stop();
//        if (backgroundThread != null) {
//            backgroundThread.quitSafely();
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
//        backgroundHandler.post(() -> mDataParser.add(data));
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            tvSerialInfo.setText(status);
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            }
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
//        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
////        runOnUiThread(() -> wfSpO2.addAmp(dat));
//    }
//
//
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        backgroundHandler.post(() -> {
//            String spo2Label = "SpO2: ";
//            String pulseLabel = "Pulse Rate: "; // Fixed typo from "plusLable"
//            String statusLabel = "Status: ";
//            String spo2Value = (spo2.getSpO2() != SpO2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) + " %" : "--";
//            String pulseValue = (spo2.getPulseRate() != SpO2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) + " /min" : "--";
//            String statusValue = spo2.getSensorStatus() != null ? spo2.getSensorStatus() : "Unknown";
//            String fullText = spo2Label + spo2Value + "\n" + pulseLabel + pulseValue + "\n" + statusLabel + statusValue;
//            SpannableString spannable = new SpannableString(fullText);
//
//            // Set spans for labels and values
//            int spo2LabelStart = 0;
//            int spo2LabelEnd = spo2Label.length();
//            int spo2ValueStart = spo2LabelEnd;
//            int spo2ValueEnd = spo2ValueStart + spo2Value.length();
//            int pulseLabelStart = fullText.indexOf(pulseLabel);
//            int pulseLabelEnd = pulseLabelStart + pulseLabel.length();
//            int pulseValueStart = pulseLabelEnd;
//            int pulseValueEnd = pulseValueStart + pulseValue.length();
//            int statusLabelStart = fullText.indexOf(statusLabel);
//            int statusLabelEnd = statusLabelStart + statusLabel.length();
//            int statusValueStart = statusLabelEnd;
//            int statusValueEnd = statusValueStart + statusValue.length();
//
//            // Apply spans for labels (larger size)
//            spannable.setSpan(new RelativeSizeSpan(1.5f), spo2LabelStart, spo2LabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // 1.5x size for label
//            spannable.setSpan(new RelativeSizeSpan(1.5f), pulseLabelStart, pulseLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(1.5f), statusLabelStart, statusLabelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            // Apply spans for values (larger size)
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2ValueStart, spo2ValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseValueStart, pulseValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvSPO2info.setText(spannable);
//                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
//                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9); // Base text size
//            });
//        });
//    }
//
////    @Override
////    public void onSpO2Received(SpO2 spo2) {
////        backgroundHandler.post(() -> {
////            String spo2Label = "SpO2: ";
////            String plusLable ="PlusRate: ";
////            String statusLable = "Status: ";
////            String spo2Value = (spo2.getSpO2() != SpO2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) + " %" :"";
////            String pulseValue = (spo2.getPulseRate() != SpO2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) + " /min" : "";
////            String statusValue = spo2.getSensorStatus() != null ? spo2.getSensorStatus() : "Unknown";
////            String fullText = spo2Label + spo2Value + "\n" + plusLable+ pulseValue + "\n" + statusLable+ statusValue;
////            SpannableString spannable = new SpannableString(fullText);
////            int spo2Start = fullText.indexOf(spo2Value.split(" ")[0]);
////            int spo2End = spo2Start + spo2Value.split(" ")[0].length();
////            int pulseStart = fullText.indexOf(pulseValue.split(" ")[0]);
////            int pulseEnd = pulseStart + pulseValue.split(" ")[0].length();
////            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2Start, spo2End, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
////            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseStart, pulseEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
////
////            runOnUiThread(() -> {
////                tvSPO2info.setText(spannable);
////                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
////                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
////            });
////        });
////    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        backgroundHandler.post(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
//        backgroundHandler.post(() -> wfResp.addAmp(dat));
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
//        backgroundHandler.post(() -> {
//            // Heart Rate for tvECGinfo
//            String heartRateLabel = "Heart Rate: ";
//            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
//            String stLevelValue = (ecg.getSTLevel() != -2.0f) ? String.format("%.2f mV", ecg.getSTLevel()) : "0 mV";
//            String arrhyValue = (ecg.getArrythmia() != null) ? ecg.getArrythmia() : ECG.ARRYTHMIA_INVALID;
//            String heartRateText = heartRateLabel + heartRateValue + "\nST Level: " + stLevelValue + "\nArrythmia: " + arrhyValue;
//            SpannableString heartRateSpannable = new SpannableString(heartRateText);
//            int hrStart = heartRateText.indexOf(heartRateValue);
//            int hrEnd = hrStart + heartRateValue.length();
//            heartRateSpannable.setSpan(new RelativeSizeSpan(2.8f), hrStart, hrEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            // Resp Rate for tvRespRate
//            String respRateLabel = "RoR/min: ";
//            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
//            String respRateText = respRateLabel + respRateValue;
//            SpannableString respRateSpannable = new SpannableString(respRateText);
//            int respStart = respRateText.indexOf(respRateValue);
//            int respEnd = respStart + respRateValue.length();
//            respRateSpannable.setSpan(new RelativeSizeSpan(2.8f), respStart, respEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvECGinfo.setText(heartRateSpannable);
//                tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
//                tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//                tvRespRate.setText(respRateSpannable);
//                tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
//                tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//            });
//        });
//    }
//
//    @Override
//    public void onTempReceived(Temp temp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//        });
//    }
//
//    @Override
//    public void onFirmwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
//        });
//    }
//
//    @Override
//    public void onHardwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
//        });
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//}

//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.ProgressDialog;
//import android.content.pm.PackageManager;
//import android.graphics.Typeface;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.Spinner;
//import android.widget.TextView;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.lztek.api.demo.data.DataParser;
//import com.lztek.api.demo.data.ECG;
//import com.lztek.api.demo.data.NIBP;
//import com.lztek.api.demo.data.SpO2;
//import com.lztek.api.demo.data.Temp;
//import com.lztek.api.demo.view.WaveformView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//
//    private Button btnSerialCtr;
//    private TextView tvSerialInfo;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
//    private TextView tvFWVersion;
//    private TextView tvHWVersion;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfResp;
//    private Spinner spinnerECG1, spinnerECG2, spinnerECG3;
//
//    private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        initData();
//        initView();
//        initPermissions();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//    }
//
//    private void initPermissions() {
//        List<String> permissionList = new ArrayList<>();
//        for (String permission : permissions) {
//            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                permissionList.add(permission);
//            }
//        }
//        if (!permissionList.isEmpty()) {
//            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE);
//        }
//    }
//
//    private void initData() {
//        mDataParser = new DataParser(this);
//        backgroundThread = new HandlerThread("BackgroundThread");
//        backgroundThread.start();
//        backgroundHandler = new Handler(backgroundThread.getLooper());
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect");
//        tvSerialInfo = findViewById(R.id.tvbtinfo);
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
//        tvFWVersion = findViewById(R.id.tvFWverison);
//        tvHWVersion = findViewById(R.id.tvHWverison);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//
//        spinnerECG1 = findViewById(R.id.spinnerECG1);
//        spinnerECG2 = findViewById(R.id.spinnerECG2);
//        spinnerECG3 = findViewById(R.id.spinnerECG3);
//
//        setupDropdown(spinnerECG1, 0);
//        setupDropdown(spinnerECG2, 1);
//        setupDropdown(spinnerECG3, 2);
//
//        spinnerECG1.bringToFront();
//        spinnerECG1.setZ(100);
//        wfECG1.setZ(1);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        llAbout.setOnClickListener(v -> {
//            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
//            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
//            serialPort.write(fwCmd);
//            serialPort.write(hwCmd);
//        });
//    }
//
//    private void setupDropdown(Spinner spinner, int index) {
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, ecgOptions);
//        spinner.setAdapter(adapter);
//        spinner.setSelection(index);
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedECG[index] = position;
//                clearGraph(index);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
//                    serialPort.disconnect();
//                }
//                break;
//            case R.id.btnNIBPStart:
//                serialPort.write(DataParser.CMD_START_NIBP);
//                break;
//            case R.id.btnNIBPStop:
//                serialPort.write(DataParser.CMD_STOP_NIBP);
//                break;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        serialPort.disconnect();
//        mDataParser.stop();
//        if (backgroundThread != null) {
//            backgroundThread.quitSafely();
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
//        backgroundHandler.post(() -> mDataParser.add(data));
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            tvSerialInfo.setText(status);
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            }
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
//        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        backgroundHandler.post(() -> {
//            String spo2Label = "SpO2: ";
//            String pulseLabel = "Pulse Rate: ";
//            String spo2Value = (spo2.getSpO2() != spo2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) : "--";
//            String pulseValue = (spo2.getPulseRate() != spo2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) : "--";
//            String fullText = spo2Label + spo2Value + "\n" + pulseLabel + pulseValue;
//            SpannableString spannable = new SpannableString(fullText);
//            int spo2Start = fullText.indexOf(spo2Value);
//            int spo2End = spo2Start + spo2Value.length();
//            int pulseStart = fullText.indexOf(pulseValue);
//            int pulseEnd = pulseStart + pulseValue.length();
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2Start, spo2End, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseStart, pulseEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            runOnUiThread(() -> {
//                tvSPO2info.setText(spannable);
//                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
//                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//            });
//        });
//    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        backgroundHandler.post(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
//        backgroundHandler.post(() -> wfResp.addAmp(dat));
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
//        backgroundHandler.post(() -> {
//            // Heart Rate for tvECGinfo
//            String heartRateLabel = "Heart Rate: ";
//            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
//            String heartRateText = heartRateLabel + heartRateValue;
//            SpannableString heartRateSpannable = new SpannableString(heartRateText);
//            int hrStart = heartRateText.indexOf(heartRateValue);
//            int hrEnd = hrStart + heartRateValue.length();
//            heartRateSpannable.setSpan(new RelativeSizeSpan(2.8f), hrStart, hrEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            // Resp Rate for tvRespRate
//            String respRateLabel = "RoR/min: ";
//            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
//            String respRateText = respRateLabel + respRateValue;
//            SpannableString respRateSpannable = new SpannableString(respRateText);
//            int respStart = respRateText.indexOf(respRateValue);
//            int respEnd = respStart + respRateValue.length();
//            respRateSpannable.setSpan(new RelativeSizeSpan(2.8f), respStart, respEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvECGinfo.setText(heartRateSpannable);
//                tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
//                tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//                tvRespRate.setText(respRateSpannable);
//                tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
//                tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//            });
//        });
//    }
//
//    @Override
//    public void onTempReceived(Temp temp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//        });
//    }
//
//    @Override
//    public void onFirmwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
//        });
//    }
//
//    @Override
//    public void onHardwareReceived(String str) {
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
//        });
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//}



//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.ProgressDialog;
//import android.content.pm.PackageManager;
//import android.graphics.Typeface;
//import android.os.Bundle;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.Spinner;
//import android.widget.TextView;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.lztek.api.demo.data.DataParser;
//import com.lztek.api.demo.data.ECG;
//import com.lztek.api.demo.data.NIBP;
//import com.lztek.api.demo.data.SpO2;
//import com.lztek.api.demo.data.Temp;
//import com.lztek.api.demo.view.WaveformView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//
//    private Button btnSerialCtr;
//    private TextView tvSerialInfo;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
//    private TextView tvFWVersion;
//    private TextView tvHWVersion;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfResp;
//    private Spinner spinnerECG1, spinnerECG2, spinnerECG3;
//
//    private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        initData();
//        initView();
//        initPermissions();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//    }
//
//    private void initPermissions() {
//        List<String> permissionList = new ArrayList<>();
//        for (String permission : permissions) {
//            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                permissionList.add(permission);
//            }
//        }
//        if (!permissionList.isEmpty()) {
//            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE);
//        }
//    }
//
//    private void initData() {
//        mDataParser = new DataParser(this);
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect");
//        tvSerialInfo = findViewById(R.id.tvbtinfo);
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
//        tvFWVersion = findViewById(R.id.tvFWverison);
//        tvHWVersion = findViewById(R.id.tvHWverison);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//
//        spinnerECG1 = findViewById(R.id.spinnerECG1);
//        spinnerECG2 = findViewById(R.id.spinnerECG2);
//        spinnerECG3 = findViewById(R.id.spinnerECG3);
//
//        setupDropdown(spinnerECG1, 0);
//        setupDropdown(spinnerECG2, 1);
//        setupDropdown(spinnerECG3, 2);
//
//        spinnerECG1.bringToFront();
//        spinnerECG1.setZ(100);
//        wfECG1.setZ(1);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        llAbout.setOnClickListener(v -> {
//            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
//            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
//            serialPort.write(fwCmd);
//            serialPort.write(hwCmd);
////            Log.d(TAG, "🔄 Sent firmware and hardware version commands");
//        });
//    }
//
//    private void setupDropdown(Spinner spinner, int index) {
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, ecgOptions);
//        spinner.setAdapter(adapter);
//        spinner.setSelection(index);
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedECG[index] = position;
//                clearGraph(index);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
////                    Log.d(TAG, "🔄 Connect button clicked");
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
////                    Log.d(TAG, "🔄 Disconnect button clicked");
//                    serialPort.disconnect();
//                }
//                break;
//            case R.id.btnNIBPStart:
////                byte[] startNIBP = {0x05, 0x0A, 0x04, 0x02, 0x01, (byte) 0xFA};
//                serialPort.write(DataParser.CMD_START_NIBP);
////                Log.d(TAG, "🔄 Sent NIBP start command");
//                break;
//            case R.id.btnNIBPStop:
////                byte[] stopNIBP = {0x05, 0x0A, 0x04, 0x02, 0x00, (byte) 0xF9};
//                serialPort.write(DataParser.CMD_STOP_NIBP);
////                Log.d(TAG, "🔄 Sent NIBP stop command");
//                break;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        serialPort.disconnect();
//        mDataParser.stop();
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
////        Log.d(TAG, "📥 Received raw data: " + bytesToHex(data));
//        mDataParser.add(data);
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            tvSerialInfo.setText(status);
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            }
////            Log.d(TAG, "🔄 Connection status: " + status);
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
////        Log.d(TAG, "📈 SPO2 Wave received: " + dat);
//        runOnUiThread(() -> wfSpO2.addAmp(dat));
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        Log.d(TAG, "📊 SpO2 received - SpO2: " + spo2.getSpO2() + ", Pulse: " + spo2.getPulseRate());
//        runOnUiThread(() -> {
//            String spo2Label = "SpO2: ";
//            String pulseLabel = "Pulse Rate: ";
//            String spo2Value = (spo2.getSpO2() != spo2.SPO2_INVALID) ? String.valueOf(spo2.getSpO2()) : "--";
//            String pulseValue = (spo2.getPulseRate() != spo2.PULSE_RATE_INVALID) ? String.valueOf(spo2.getPulseRate()) : "--";
//            String fullText = spo2Label + spo2Value + "\n" + pulseLabel + pulseValue;
//            SpannableString spannable = new SpannableString(fullText);
//            int spo2Start = fullText.indexOf(spo2Value);
//            int spo2End = spo2Start + spo2Value.length();
//            int pulseStart = fullText.indexOf(pulseValue);
//            int pulseEnd = pulseStart + pulseValue.length();
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2Start, spo2End, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseStart, pulseEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            tvSPO2info.setText(spannable);
//            tvSPO2info.setTypeface(tvSPO2info.getTypeface(), Typeface.BOLD);
//            tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//        });
//    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
////        Log.d(TAG, "📈 ECG Wave received - I: " + leadI + ", II: " + leadII + ", III: " + leadIII +
////                ", aVR: " + aVR + ", aVL: " + aVL + ", aVF: " + aVF + ", V: " + vLead);
//        runOnUiThread(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
////        Log.d(TAG, "📈 Resp Wave received: " + dat);
//        runOnUiThread(() -> wfResp.addAmp(dat));
//    }
//
//
//    @Override
//    public void onECGReceived(ECG ecg) {
////        Log.d(TAG, "📊 ECG received - HR: " + ecg.getHeartRate() + ", Resp: " + ecg.getRestRate());
//        runOnUiThread(() -> {
//            // Heart Rate for tvECGinfo
//            String heartRateLabel = "Heart Rate: ";
//            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
//            String heartRateText = heartRateLabel + heartRateValue;
//            SpannableString heartRateSpannable = new SpannableString(heartRateText);
//            int hrStart = heartRateText.indexOf(heartRateValue);
//            int hrEnd = hrStart + heartRateValue.length();
//            heartRateSpannable.setSpan(new RelativeSizeSpan(2.8f), hrStart, hrEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            tvECGinfo.setText(heartRateSpannable);
//            tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
//            tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//
//            // Resp Rate for tvRespRate
//            String respRateLabel = "RoR/min: "; // Match sir's label
//            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
//            String respRateText = respRateLabel + respRateValue;
//            SpannableString respRateSpannable = new SpannableString(respRateText);
//            int respStart = respRateText.indexOf(respRateValue);
//            int respEnd = respStart + respRateValue.length();
//            respRateSpannable.setSpan(new RelativeSizeSpan(2.8f), respStart, respEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            tvRespRate.setText(respRateSpannable);
//            tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
//            tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
//        });
//    }
//
////    @Override
////    public void onECGReceived(ECG ecg) {
////        Log.d(TAG, "📊 ECG received - HR: " + ecg.getHeartRate() + ", Resp: " + ecg.getRestRate());
////        runOnUiThread(() -> {
////            String heartRateLabel = "Heart Rate: ";
////            String respRateLabel = "Resp Rate: ";
////            String heartRateValue = (ecg.getHeartRate() != ecg.HEART_RATE_INVALID) ? String.valueOf(ecg.getHeartRate()) : "--";
////            String respRateValue = (ecg.getRestRate() != ecg.RESP_RATE_INVALID) ? String.valueOf(ecg.getRestRate()) : "--";
////            String fullText = heartRateLabel + heartRateValue + "\n" + respRateLabel + respRateValue;
////            SpannableString spannable = new SpannableString(fullText);
////            int ecgHrRateStart = fullText.indexOf(heartRateValue);
////            int ecgHrRateEnd = ecgHrRateStart + heartRateValue.length();
////            int ecgRespRateStart = fullText.indexOf(respRateValue);
////            int ecgRespRateEnd = ecgRespRateStart + respRateValue.length();
////            spannable.setSpan(new RelativeSizeSpan(2.8f), ecgHrRateStart, ecgHrRateEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
////            spannable.setSpan(new RelativeSizeSpan(2.8f), ecgRespRateStart, ecgRespRateEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
////            tvECGinfo.setText(spannable);
////            tvECGinfo.setTypeface(tvECGinfo.getTypeface(), Typeface.BOLD);
////            tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
////        });
////    }
//
//    @Override
//    public void onTempReceived(Temp temp) {
////        Log.d(TAG, "📊 Temp received: " + temp.toString());
//        runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
////        Log.d(TAG, "📊 NIBP received: " + nibp.toString());
//        runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//    }
//
//    @Override
//    public void onFirmwareReceived(String str) {
////        Log.d(TAG, "📊 Firmware received: " + str);
//        runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
//    }
//
//    @Override
//    public void onHardwareReceived(String str) {
////        Log.d(TAG, "📊 Hardware received: " + str);
//        runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//}
