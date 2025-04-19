package com.lztek.api.demo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.Date;
import java.util.List;

import android.graphics.pdf.PdfDocument;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_berry_device);

        initData();
        initView();
        initPermissions();

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
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
        finish();
    }

    @Override
    public void onDataReceived(byte[] data) {
        backgroundHandler.post(() -> mDataParser.add(data));
    }

    @Override
    public void onConnectionStatusChanged(String status) {
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
        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
    }

    @Override
    public void onSpO2Received(SpO2 spo2) {
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
            });
        });
    }

    @Override
    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
        backgroundHandler.post(() -> {
            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
            wfECG1.addAmp(ecgData[selectedECG[0]]);
            wfECG2.addAmp(ecgData[selectedECG[1]]);
            wfECG3.addAmp(ecgData[selectedECG[2]]);
        });
    }

    @Override
    public void onRespWaveReceived(int dat) {
        backgroundHandler.post(() -> wfResp.addAmp(dat));
    }

    @Override
    public void onECGReceived(ECG ecg) {
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
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
        });
    }

    @Override
    public void onNIBPReceived(NIBP nibp) {
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
        });
    }

    @Override
    public void onFirmwareReceived(String str) {
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
        });
    }

    @Override
    public void onHardwareReceived(String str) {
        backgroundHandler.post(() -> {
            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
        });
    }

    private void generateReport() {
        Log.d(TAG, "Starting PDF generation process");
        // Force refresh WaveformViews
        runOnUiThread(() -> {
            wfECG1.invalidate();
            wfECG2.invalidate();
            wfECG3.invalidate();
            wfSpO2.invalidate();
        });
        try {
            Thread.sleep(500); // Give time to refresh
        } catch (InterruptedException e) {
            Log.e(TAG, "Refresh interrupted: " + e.getMessage());
        }

        // Capture ECG and SpO2 graphs as bitmaps
        Bitmap[] ecgBitmaps = new Bitmap[3];
        ecgBitmaps[0] = Bitmap.createBitmap(wfECG1.getWidth(), wfECG1.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasECG1 = new Canvas(ecgBitmaps[0]);
        wfECG1.draw(canvasECG1);
        Log.d(TAG, "wfECG1 bitmap size: " + ecgBitmaps[0].getWidth() + "x" + ecgBitmaps[0].getHeight() + ", pixel(0,0): " + ecgBitmaps[0].getPixel(0, 0));
        ecgBitmaps[1] = Bitmap.createBitmap(wfECG2.getWidth(), wfECG2.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasECG2 = new Canvas(ecgBitmaps[1]);
        wfECG2.draw(canvasECG2);
        ecgBitmaps[2] = Bitmap.createBitmap(wfECG3.getWidth(), wfECG3.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasECG3 = new Canvas(ecgBitmaps[2]);
        wfECG3.draw(canvasECG3);
        Bitmap spO2Bitmap = Bitmap.createBitmap(wfSpO2.getWidth(), wfSpO2.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasSpO2 = new Canvas(spO2Bitmap);
        wfSpO2.draw(canvasSpO2);
        Log.d(TAG, "wfSpO2 bitmap size: " + spO2Bitmap.getWidth() + "x" + spO2Bitmap.getHeight() + ", pixel(0,0): " + spO2Bitmap.getPixel(0, 0));

        // Get values for labels
        String spO2Value = tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim();
        String pulseRateValue = tvSPO2info.getText().toString().split("\n")[1].replace("Pulse Rate: ", "").trim();
        String heartRateValue = tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim();

        // Create PDF using PdfDocument
        try {
            Log.d(TAG, "Checking external storage access");
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                Log.e(TAG, "External directory is null");
                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "External storage not accessible", Toast.LENGTH_SHORT).show());
                return;
            }
            File reportsDir = new File(externalDir, "Reports");
            if (!reportsDir.exists()) {
                boolean created = reportsDir.mkdirs();
                Log.d(TAG, "Reports directory created: " + created + " at " + reportsDir.getAbsolutePath());
                if (!created) {
                    Log.e(TAG, "Failed to create reports directory");
                    runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Failed to create directory", Toast.LENGTH_SHORT).show());
                    return;
                }
            }
            String fileName = "Medical_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf";
            File file = new File(reportsDir, fileName);
            Log.d(TAG, "Attempting to write PDF to: " + file.getAbsolutePath());

            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size in points
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            Paint gridPaint = new Paint();
            gridPaint.setColor(Color.argb(255, 255, 182, 193)); // Pinkish-red background (adjust shade as needed)
            gridPaint.setStyle(Paint.Style.FILL);
            Paint wavePaint = new Paint();
            wavePaint.setColor(Color.BLACK); // Graph line color
            wavePaint.setStrokeWidth(2);

            // Header
            paint.setTextSize(18);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Lead ECG Report", 50, 50, paint);
            paint.setTextSize(12);
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), 50, 80, paint);

            // Patient Info (Placeholder)
            canvas.drawText("AGE", 50, 110, paint);
            canvas.drawText("GENDER", 100, 110, paint);
            canvas.drawText("HEIGHT", 150, 110, paint);
            canvas.drawText("REPORT ID", 200, 110, paint);
            canvas.drawText("SpO2: " + spO2Value + "%", 400, 110, paint);
            canvas.drawText("Heart Rate: " + heartRateValue + " bpm", 450, 110, paint);

            // ECG Graph Section with Grid Background
            String[] leads = {"I", "II", "III", "aVR", "aVL", "aVF", "Rhythm (lead II)"};
            int graphHeight = 70;
            int graphWidth = 400;
            int startX = 50;
            int startY = 140;

            // Draw grid background
            for (int i = 0; i < 7; i++) {
                canvas.drawRect(startX, startY + (i * graphHeight), startX + graphWidth, startY + ((i + 1) * graphHeight), gridPaint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.5f);
                for (int j = 0; j <= 50; j++) {
                    float x = startX + j * 8;
                    canvas.drawLine(x, startY + (i * graphHeight), x, startY + ((i + 1) * graphHeight), paint);
                }
                for (int j = 0; j <= 1; j++) {
                    float yPos = startY + (i * graphHeight) + (j * graphHeight);
                    canvas.drawLine(startX, yPos, startX + graphWidth, yPos, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText(leads[i], startX - 20, startY + (i * graphHeight) + graphHeight / 2, paint);
                if (i < 3 && ecgBitmaps[i % 3] != null && !ecgBitmaps[i % 3].isRecycled()) {
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(ecgBitmaps[i % 3], graphWidth, graphHeight, true);
                    canvas.drawBitmap(scaledBitmap, startX, startY + (i * graphHeight), paint);
                    scaledBitmap.recycle();
                    // Add wave labels (static positions for demo)
                    canvas.drawText("P", startX + 50, startY + (i * graphHeight) + 20, paint);
                    canvas.drawText("Q", startX + 100, startY + (i * graphHeight) + 40, paint);
                    canvas.drawText("R", startX + 150, startY + (i * graphHeight) + 20, paint);
                    canvas.drawText("S", startX + 200, startY + (i * graphHeight) + 40, paint);
                    canvas.drawText("T", startX + 250, startY + (i * graphHeight) + 20, paint);
                    canvas.drawLine(startX + 50, startY + (i * graphHeight) + 30, startX + 150, startY + (i * graphHeight) + 30, wavePaint); // PR
                    canvas.drawLine(startX + 150, startY + (i * graphHeight) + 30, startX + 200, startY + (i * graphHeight) + 30, wavePaint); // ST
                    canvas.drawLine(startX + 100, startY + (i * graphHeight) + 50, startX + 250, startY + (i * graphHeight) + 50, wavePaint); // QT
                } else if (i >= 3 && ecgBitmaps[0] != null && !ecgBitmaps[0].isRecycled()) {
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(ecgBitmaps[0], graphWidth, graphHeight, true);
                    canvas.drawBitmap(scaledBitmap, startX, startY + (i * graphHeight), paint);
                    scaledBitmap.recycle();
                    canvas.drawText("P", startX + 50, startY + (i * graphHeight) + 20, paint);
                    canvas.drawText("Q", startX + 100, startY + (i * graphHeight) + 40, paint);
                    canvas.drawText("R", startX + 150, startY + (i * graphHeight) + 20, paint);
                    canvas.drawText("S", startX + 200, startY + (i * graphHeight) + 40, paint);
                    canvas.drawText("T", startX + 250, startY + (i * graphHeight) + 20, paint);
                    canvas.drawLine(startX + 50, startY + (i * graphHeight) + 30, startX + 150, startY + (i * graphHeight) + 30, wavePaint); // PR
                    canvas.drawLine(startX + 150, startY + (i * graphHeight) + 30, startX + 200, startY + (i * graphHeight) + 30, wavePaint); // ST
                    canvas.drawLine(startX + 100, startY + (i * graphHeight) + 50, startX + 250, startY + (i * graphHeight) + 50, wavePaint); // QT
                }
            }

            // SpO2 Graph Section
            int spO2StartY = startY + 7 * graphHeight + 20;
            canvas.drawRect(startX, spO2StartY, startX + graphWidth, spO2StartY + graphHeight, gridPaint);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i <= 50; i++) {
                float x = startX + i * 8;
                canvas.drawLine(x, spO2StartY, x, spO2StartY + graphHeight, paint);
            }
            for (int i = 0; i <= 1; i++) {
                float yPos = spO2StartY + i * graphHeight;
                canvas.drawLine(startX, yPos, startX + graphWidth, yPos, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText("SpO2", startX - 20, spO2StartY + graphHeight / 2, paint);
            if (spO2Bitmap != null && !spO2Bitmap.isRecycled()) {
                Bitmap scaledSpO2Bitmap = Bitmap.createScaledBitmap(spO2Bitmap, graphWidth, graphHeight, true);
                canvas.drawBitmap(scaledSpO2Bitmap, startX, spO2StartY, paint);
                canvas.drawText("SpO2: " + spO2Value + "%", startX + graphWidth + 10, spO2StartY + 20, paint);
                canvas.drawText("PR: " + pulseRateValue + " bpm", startX + graphWidth + 10, spO2StartY + 40, paint);
                scaledSpO2Bitmap.recycle();
            }

            // Scale and Labels
            paint.setTextSize(8);
            canvas.drawText("Scale: 25 mm/s, 10 mm/mV", startX, spO2StartY + graphHeight + 20, paint);

            // Footer
            int footerY = spO2StartY + graphHeight + 50;
            paint.setTextSize(10);
            canvas.drawText("Generated by Berry Device App - Device: [Placeholder]", 50, footerY, paint);

            document.finishPage(page);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                Log.d(TAG, "Writing PDF to file stream at: " + file.getAbsolutePath());
                document.writeTo(fos);
            }
            document.close();
            Log.d(TAG, "PDF created successfully at: " + file.getAbsolutePath());

            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Medical Report saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "IOException creating PDF: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.e(TAG, "SecurityException creating PDF: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Permission denied: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Unexpected error creating PDF: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

//    private void generateReport() {
//        Log.d(TAG, "Starting PDF generation process");
//        // Get latest data from UI elements
//        String spO2 = tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim();
//        String pulseRate = tvSPO2info.getText().toString().split("\n")[1].replace("Pulse Rate: ", "").trim();
//        String heartRate = tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim();
//        String stLevel = tvECGinfo.getText().toString().split("\n")[1].replace("ST Level: ", "").trim();
//        String arrythmia = tvECGinfo.getText().toString().split("\n")[2].replace("Arrythmia: ", "").trim();
//        String respRate = tvRespRate.getText().toString().replace("RoR/min: ", "").trim();
//        String temp = tvTEMPinfo.getText().toString();
//        String nibp = tvNIBPinfo.getText().toString();
//        String fwVersion = tvFWVersion.getText().toString().replace("Firmware Version: ", "").trim();
//        String hwVersion = tvHWVersion.getText().toString().replace("Hardware Version: ", "").trim();
//
//        // Create PDF using PdfDocument
//        try {
//            Log.d(TAG, "Checking external storage access");
//            File externalDir = getExternalFilesDir(null);
//            if (externalDir == null) {
//                Log.e(TAG, "External directory is null");
//                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "External storage not accessible", Toast.LENGTH_SHORT).show());
//                return;
//            }
//            File reportsDir = new File(externalDir, "Reports");
//            if (!reportsDir.exists()) {
//                boolean created = reportsDir.mkdirs();
//                Log.d(TAG, "Reports directory created: " + created + " at " + reportsDir.getAbsolutePath());
//                if (!created) {
//                    Log.e(TAG, "Failed to create reports directory");
//                    runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Failed to create directory", Toast.LENGTH_SHORT).show());
//                    return;
//                }
//            }
//            String fileName = "Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf";
//            File file = new File(reportsDir, fileName);
//            Log.d(TAG, "Attempting to write PDF to: " + file.getAbsolutePath());
//
//            PdfDocument document = new PdfDocument();
//            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size in points
//            PdfDocument.Page page = document.startPage(pageInfo);
//            Canvas canvas = page.getCanvas();
//            Paint paint = new Paint();
//            paint.setColor(Color.BLACK);
//            paint.setTextSize(12);
//
//            int y = 50;
//            canvas.drawText("Berry Device Health Report", 50, y, paint);
//            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//            y += 20;
//            canvas.drawText("------------------------", 50, y, paint);
//            paint.setTypeface(Typeface.DEFAULT);
//            y += 20;
//            canvas.drawText("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), 50, y, paint);
//            y += 20;
//            canvas.drawText("SpO2: " + spO2, 50, y, paint);
//            y += 20;
//            canvas.drawText("Pulse Rate: " + pulseRate, 50, y, paint);
//            y += 20;
//            canvas.drawText("Heart Rate: " + heartRate, 50, y, paint);
//            y += 20;
//            canvas.drawText("ST Level: " + stLevel, 50, y, paint);
//            y += 20;
//            canvas.drawText("Arrythmia: " + arrythmia, 50, y, paint);
//            y += 20;
//            canvas.drawText("Respiratory Rate: " + respRate, 50, y, paint);
//            y += 20;
//            canvas.drawText("Temperature: " + temp, 50, y, paint);
//            y += 20;
//            canvas.drawText("NIBP: " + nibp, 50, y, paint);
//            y += 20;
//            canvas.drawText("Firmware Version: " + fwVersion, 50, y, paint);
//            y += 20;
//            canvas.drawText("Hardware Version: " + hwVersion, 50, y, paint);
//
//            document.finishPage(page);
//            try (FileOutputStream fos = new FileOutputStream(file)) {
//                Log.d(TAG, "Writing PDF to file stream at: " + file.getAbsolutePath());
//                document.writeTo(fos); // This creates a proper PDF
//            }
//            document.close();
//            Log.d(TAG, "PDF created successfully at: " + file.getAbsolutePath());
//
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
//        } catch (IOException e) {
//            e.printStackTrace();
//            Log.e(TAG, "IOException creating PDF: " + e.getMessage());
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//        } catch (SecurityException e) {
//            e.printStackTrace();
//            Log.e(TAG, "SecurityException creating PDF: " + e.getMessage());
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Permission denied: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e(TAG, "Unexpected error creating PDF: " + e.getMessage());
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//        }
//    }

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
