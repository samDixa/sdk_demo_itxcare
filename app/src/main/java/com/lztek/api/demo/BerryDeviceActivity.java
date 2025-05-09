package com.lztek.api.demo;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.lztek.api.demo.data.DataParser;
import com.lztek.api.demo.data.ECG;
import com.lztek.api.demo.data.NIBP;
import com.lztek.api.demo.data.SpO2;
import com.lztek.api.demo.data.Temp;
import com.lztek.api.demo.view.WaveformView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {

    private static final String TAG = "BerryDeviceActivity";
    private static final int MAX_BUFFER_SIZE = 5000; // 10 seconds at 500 Hz for ECG
    private static final int SPO2_BUFFER_MAX_SIZE = 1000; // 10 seconds at 100 Hz for SpO2
    private static final int REQUEST_CODE = 100;
    private static final long BUFFER_CLEANUP_INTERVAL_MS = 10_000; // 10 seconds

    // UI Elements
    private Button btnSerialCtr;
    private TextView tvECGinfo;
    private TextView tvSPO2info;
    private TextView tvTEMPinfo;
    private TextView tvNIBPinfo;
    private TextView tvRespRate;
    private WaveformView wfSpO2;
    private WaveformView wfECG1;
    private WaveformView wfECG2;
    private WaveformView wfECG3;
    private WaveformView wfECG4;
    private WaveformView wfResp;
    private Spinner spinnerECG3;
    private Button btnGenerateReport;
    private Button chestoConnett;
    private ImageButton liveChesto;
    private ImageButton recordChestoo;
    private TextView recordingTimer;
    private ImageButton recordPauseChesto;
    private ImageButton playOrstop;
    private ImageButton saveAudio;
    private ImageButton refreshAudio;
    private ImageButton uploadAudio;
    private LineChart cAudioGraph;
    private RelativeLayout audioGrpahContainer;
    private ImageView btnShowAllLeads;
    private Dialog ecgDialog;
    private WaveformView wfLeadI, wfLeadII, wfLeadIII, wfLeadAVR, wfLeadAVL, wfLeadAVF, wfLeadV;
    private Button nibpStopButton;
    private Button nibp5MinButton, nibp15MinButton, nibp30MinButton;

    // Thread Handling
    private HandlerThread vitalThread;
    private Handler vitalHandler;
    private HandlerThread generalThread;
    private Handler generalHandler;
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService;
    private final Handler bufferCleanupHandler = new Handler(Looper.getMainLooper());

    // Data Handling
    private BerrySerialPort serialPort;
    private DataParser mDataParser;
    private ProgressDialog mConnectingDialog;
    private BluetoothService bluetoothService;
    private boolean isRecording = false;
    private boolean isListening = false;
    private boolean isPlaying = false;
    private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private final List<Byte> plotBuffer = Collections.synchronizedList(new ArrayList<>());
    private AudioTrack audioTrack;
    private final Deque<int[]> ecgDataBuffers = new ConcurrentLinkedDeque<>();
    private final List<Integer> spo2Buffer = Collections.synchronizedList(new ArrayList<>());
    private final List<int[]> ecgBatchBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int DATA_COLLECTION_DURATION_MS = 10000; // 10 seconds

    // NIBP Auto Trigger
    private Handler autoTriggerHandler = new Handler();
    private Runnable autoTriggerRunnable;
    private int intervalInMillis = 0;

    // ECG Options
    private String[] ecgOptions = {"ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
    private int[] selectedECG = {0, 1, 2, 3};

    // Permissions
    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // Broadcast Receiver
    private BroadcastReceiver vitalStatusReceiver;

    // Periodic Buffer Cleanup
    private final Runnable bufferCleanupRunnable = new Runnable() {
        @Override
        public void run() {
            ecgDataBuffers.clear();
            Log.d(TAG, "Cleared ecgDataBuffers");
            synchronized (spo2Buffer) {
                spo2Buffer.clear();
                Log.d(TAG, "Cleared spo2Buffer");
            }
            synchronized (ecgBatchBuffer) {
                ecgBatchBuffer.clear();
                Log.d(TAG, "Cleared ecgBatchBuffer");
            }
            bufferCleanupHandler.postDelayed(this, BUFFER_CLEANUP_INTERVAL_MS);
        }
    };

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
        checkVitalStatus();
        startSpO2BatchUpdates();
        startECGBatchUpdates();
        bufferCleanupHandler.post(bufferCleanupRunnable);
    }

    private void initData() {
        mDataParser = new DataParser(this);
        vitalThread = new HandlerThread("VitalThread", Thread.MAX_PRIORITY);
        vitalThread.start();
        vitalHandler = new Handler(vitalThread.getLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (!serialPort.isConnected()) {
                    Log.d(TAG, "Ignoring message: Device disconnected");
                    return;
                }
                super.handleMessage(msg);
            }
        };
        generalThread = new HandlerThread("GeneralThread");
        generalThread.start();
        generalHandler = new Handler(generalThread.getLooper());
        executorService = Executors.newFixedThreadPool(2);
        mDataParser.start();
        serialPort = new BerrySerialPort(this);
        serialPort.setOnDataReceivedListener(this);
        bluetoothService = new BluetoothService();
        bluetoothService.setCallback(new BluetoothService.BluetoothCallback() {
            @Override
            public void onDataReceived(byte[] data) {
                if (isRecording) {
                    synchronized (audioBuffer) {
                        try {
                            audioBuffer.write(data);
                        } catch (IOException e) {
                            Log.e(TAG, "Write failed: " + e.getMessage());
                        }
                    }
                }
                if (isListening) {
                    synchronized (plotBuffer) {
                        plotBuffer.clear();
                        for (byte b : data) {
                            plotBuffer.add(b);
                        }
                        if (plotBuffer.size() > 512) {
                            plotBuffer.subList(0, plotBuffer.size() - 512).clear();
                        }
                    }
                }
            }

            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    chestoConnett.setText("Disconnect");
                    chestoConnett.setEnabled(true);
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    chestoConnett.setText("Connect");
                    isListening = false;
                    graphUpdateHandler.removeCallbacks(graphUpdater);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
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

    private void initView() {
        btnSerialCtr = findViewById(R.id.btnBtCtr);
        btnSerialCtr.setText("Connect Vitals");
        tvECGinfo = findViewById(R.id.tvECGinfo);
        tvSPO2info = findViewById(R.id.tvSPO2info);
        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
        wfECG1 = findViewById(R.id.wfECG1);
        wfECG2 = findViewById(R.id.wfECG2);
        wfECG3 = findViewById(R.id.wfECG3);
        wfECG4 = findViewById(R.id.wfECG4);
        wfSpO2 = findViewById(R.id.wfSpO2);
        wfResp = findViewById(R.id.wfResp);
        tvRespRate = findViewById(R.id.tvRespRate);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnShowAllLeads = findViewById(R.id.btnShowAllLeads);
        spinnerECG3 = findViewById(R.id.spinnerECG4);
        nibpStopButton = findViewById(R.id.btnNIBPStop);
        nibp5MinButton = findViewById(R.id.nibp5minbtn);
        nibp15MinButton = findViewById(R.id.nibp15minbtn);
        nibp30MinButton = findViewById(R.id.nibp30minbtn);

        chestoConnett = findViewById(R.id.chesto_connect_vt);
        liveChesto = findViewById(R.id.live_chesto);
        recordChestoo = findViewById(R.id.record_chesto);
        recordingTimer = findViewById(R.id.recording_timer);
        recordPauseChesto = findViewById(R.id.record_pause);
        playOrstop = findViewById(R.id.play_or_stop);
        saveAudio = findViewById(R.id.save_audio);
        refreshAudio = findViewById(R.id.refresh_audio);
        uploadAudio = findViewById(R.id.upload_audio);
        audioGrpahContainer = findViewById(R.id.audio_graph_container);
        cAudioGraph = findViewById(R.id.cAudioGraph);

        setupGraph();
        setupDropdown(spinnerECG3, 3);

        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
        mConnectingDialog.setCancelable(false);

        btnGenerateReport.setOnClickListener(v -> generalHandler.post(this::generateReport));
        btnShowAllLeads.setOnClickListener(v -> showEcgDialog());
        nibp5MinButton.setOnClickListener(v -> startAutoTrigger(5));
        nibp15MinButton.setOnClickListener(v -> startAutoTrigger(15));
        nibp30MinButton.setOnClickListener(v -> startAutoTrigger(30));
        nibpStopButton.setOnClickListener(v -> stopAutoTrigger());

        chestoConnett.setOnClickListener(v -> {
            if (chestoConnett.getText().toString().equals("Connect")) {
                connectToChesto();
            } else {
                try {
                    bluetoothService.disconnect();
                } catch (Exception e) {
                    Toast.makeText(this, "Disconnect failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        liveChesto.setOnClickListener(v -> {
            if (!isListening) {
                if (bluetoothService.isConnected()) {
                    bluetoothService.startListening();
                    audioGrpahContainer.setVisibility(View.VISIBLE);
                    liveChesto.setBackgroundResource(R.drawable.stop_live_strem);
                    isListening = true;
                    Toast.makeText(this, "Started Listening", Toast.LENGTH_SHORT).show();
                    graphUpdateHandler.post(graphUpdater);
                } else {
                    Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show();
                }
            } else {
                bluetoothService.stopListening();
                liveChesto.setBackgroundResource(R.drawable.live_stream);
                isListening = false;
                audioGrpahContainer.setVisibility(View.GONE);
                Toast.makeText(this, "Stopped Listening", Toast.LENGTH_SHORT).show();
                graphUpdateHandler.removeCallbacks(graphUpdater);
            }
        });

        recordChestoo.setOnClickListener(v -> {
            if (!isRecording) {
                synchronized (audioBuffer) {
                    audioBuffer.reset();
                }
                isRecording = true;
                bluetoothService.startRecording();
                recordChestoo.setBackgroundResource(R.drawable.stop_recording);
                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
            } else {
                isRecording = false;
                recordChestoo.setBackgroundResource(R.drawable.play);
                byte[] recordedData;
                synchronized (audioBuffer) {
                    recordedData = audioBuffer.toByteArray();
                }
                if (recordedData.length == 0) {
                    Toast.makeText(this, "Recording Failed: No Data", Toast.LENGTH_SHORT).show();
                    return;
                }
                executorService.execute(() -> saveAudioToFile(recordedData));
            }
        });
    }

    private void saveAudioToFile(byte[] recordedData) {
        try {
            File file = new File(getExternalFilesDir(null), "recorded_audio.wav");
            FileOutputStream fos = new FileOutputStream(file);
            byte[] header = getWavHeader(recordedData.length, 6000);
            fos.write(header);
            fos.write(recordedData);
            fos.flush();
            fos.close();
            runOnUiThread(() -> Toast.makeText(this, "Audio saved at: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to save audio: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private byte[] getWavHeader(int totalAudioLen, int sampleRate) {
        int channels = 1;
        int byteRate = sampleRate * 2;
        int totalDataLen = totalAudioLen + 36;
        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2;
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        return header;
    }

    private void connectToChesto() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getName() != null && device.getName().contains("Chesto")) {
                bluetoothService.connectToDevice(device);
                return;
            }
        }
        Toast.makeText(this, "Chesto device not found", Toast.LENGTH_SHORT).show();
    }

    private void setupGraph() {
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "Audio Signal");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        LineData lineData = new LineData(dataSet);
        cAudioGraph.setData(lineData);
        cAudioGraph.getXAxis().setDrawLabels(false);
        cAudioGraph.getAxisLeft().setDrawLabels(false);
        cAudioGraph.getAxisRight().setDrawLabels(false);
        cAudioGraph.setHardwareAccelerationEnabled(true);
        cAudioGraph.invalidate();
    }

    private final Handler graphUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable graphUpdater = new Runnable() {
        @Override
        public void run() {
            updateGraph();
            graphUpdateHandler.postDelayed(this, 10);
        }
    };

    private void updateGraph() {
        List<Byte> bufferCopy;
        synchronized (plotBuffer) {
            if (plotBuffer.isEmpty()) {
                return;
            }
            bufferCopy = new ArrayList<>(plotBuffer);
        }
        List<Entry> newEntries = new ArrayList<>();
        for (int i = 0; i < bufferCopy.size(); i++) {
            newEntries.add(new Entry(i, bufferCopy.get(i)));
        }
        runOnUiThread(() -> {
            try {
                LineData data = cAudioGraph.getData();
                if (data != null && data.getDataSetCount() > 0) {
                    LineDataSet dataSet = (LineDataSet) data.getDataSetByIndex(0);
                    dataSet.setValues(newEntries);
                    data.notifyDataChanged();
                    cAudioGraph.notifyDataSetChanged();
                    cAudioGraph.invalidate();
                }
            } catch (Exception e) {
                Log.e(TAG, "Graph update failed: " + e.getMessage());
            }
        });
    }

    private void setupVitalStatusReceiver() {
        vitalStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long vitalStatus = intent.getLongExtra("vital_status", 0);
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
            connectSerialPort();
        } else {
            disconnectSerialPort();
        }
    }

    private void connectSerialPort() {
        if (!serialPort.isConnected()) {
            runOnUiThread(() -> mConnectingDialog.show());
            serialPort.connect();
            startSpO2BatchUpdates();
            startECGBatchUpdates();
            bufferCleanupHandler.post(bufferCleanupRunnable);
            Log.d(TAG, "Started SpO2 and ECG batch updates on connect");
        }
    }

    private void disconnectSerialPort() {
        if (serialPort.isConnected()) {
            serialPort.disconnect();
            vitalHandler.removeCallbacksAndMessages(null);
            generalHandler.removeCallbacksAndMessages(null);
            uiUpdateHandler.removeCallbacksAndMessages(null);
            bufferCleanupHandler.removeCallbacksAndMessages(null);
            ecgDataBuffers.clear();
            synchronized (spo2Buffer) {
                spo2Buffer.clear();
            }
            synchronized (ecgBatchBuffer) {
                ecgBatchBuffer.clear();
            }
            clearWaveformViews();
            Log.d(TAG, "Disconnected and cleared all buffers and handlers");
        }
    }

    private void clearWaveformViews() {
        runOnUiThread(() -> {
            wfECG1.clear();
            wfECG2.clear();
            wfECG3.clear();
            wfECG4.clear();
            wfResp.clear();
            wfSpO2.clear();
            wfECG1.postInvalidate();
            wfECG2.postInvalidate();
            wfECG3.postInvalidate();
            wfECG4.postInvalidate();
            wfResp.postInvalidate();
            wfSpO2.postInvalidate();
            if (ecgDialog != null && ecgDialog.isShowing()) {
                wfLeadI.clear();
                wfLeadII.clear();
                wfLeadIII.clear();
                wfLeadAVR.clear();
                wfLeadAVL.clear();
                wfLeadAVF.clear();
                wfLeadV.clear();
                wfLeadI.postInvalidate();
                wfLeadII.postInvalidate();
                wfLeadIII.postInvalidate();
                wfLeadAVR.postInvalidate();
                wfLeadAVL.postInvalidate();
                wfLeadAVF.postInvalidate();
                wfLeadV.postInvalidate();
            }
            Log.d(TAG, "Cleared all waveform views and invalidated");
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
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void clearGraph(int index) {
        if (index == 0) wfECG1.clear();
        if (index == 1) wfECG2.clear();
        if (index == 2) wfECG3.clear();
        if (index == 3) wfECG4.clear();
        runOnUiThread(() -> {
            if (index == 0) wfECG1.postInvalidate();
            if (index == 1) wfECG2.postInvalidate();
            if (index == 2) wfECG3.postInvalidate();
            if (index == 3) wfECG4.postInvalidate();
            Log.d(TAG, "Cleared graph for index: " + index);
        });
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnBtCtr:
                if (!serialPort.isConnected()) {
                    mConnectingDialog.show();
                    connectSerialPort();
                } else {
                    disconnectSerialPort();
                }
                break;
            case R.id.btnNIBPStart:
                serialPort.write(DataParser.CMD_START_NIBP);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectSerialPort();
        mDataParser.stop();
        if (vitalThread != null) {
            vitalThread.quitSafely();
            vitalThread = null;
        }
        if (generalThread != null) {
            generalThread.quitSafely();
            generalThread = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (vitalStatusReceiver != null) {
            unregisterReceiver(vitalStatusReceiver);
            vitalStatusReceiver = null;
        }
        uiUpdateHandler.removeCallbacksAndMessages(null);
        graphUpdateHandler.removeCallbacksAndMessages(null);
        bufferCleanupHandler.removeCallbacksAndMessages(null);
        finish();
    }

    @Override
    public void onDataReceived(byte[] data) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (serialPort.isConnected()) {
                mDataParser.add(data);
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        runOnUiThread(() -> {
            if (status.startsWith("Connected")) {
                btnSerialCtr.setText("Disconnect");
                if (mConnectingDialog.isShowing()) {
                    mConnectingDialog.dismiss();
                }
                startSpO2BatchUpdates();
                startECGBatchUpdates();
                bufferCleanupHandler.post(bufferCleanupRunnable);
                Log.d(TAG, "Connection established, restarted batch updates");
            } else {
                btnSerialCtr.setText("Connect Vitals");
                if (mConnectingDialog.isShowing()) {
                    mConnectingDialog.dismiss();
                }
                ecgDataBuffers.clear();
                synchronized (spo2Buffer) {
                    spo2Buffer.clear();
                }
                synchronized (ecgBatchBuffer) {
                    ecgBatchBuffer.clear();
                }
                vitalHandler.removeCallbacksAndMessages(null);
                uiUpdateHandler.removeCallbacksAndMessages(null);
                bufferCleanupHandler.removeCallbacksAndMessages(null);
                clearWaveformViews();
                Log.d(TAG, "Disconnected, cleared buffers and handlers");
            }
        });
    }

    private void startSpO2BatchUpdates() {
        uiUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (spo2Buffer) {
                    if (!spo2Buffer.isEmpty()) {
                        for (int dat : spo2Buffer) {
                            wfSpO2.addAmp(dat);
                        }
                        spo2Buffer.clear();
                        wfSpO2.postInvalidate();
                        Log.d(TAG, "SpO2 batch update: added " + spo2Buffer.size() + " points");
                    }
                }
                uiUpdateHandler.postDelayed(this, 100);
            }
        }, 100);
    }

    private void startECGBatchUpdates() {
        uiUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (ecgBatchBuffer) {
                    if (!ecgBatchBuffer.isEmpty()) {
                        for (int[] ecgData : ecgBatchBuffer) {
                            wfECG1.addAmp(ecgData[selectedECG[0]]);
                            wfECG2.addAmp(ecgData[selectedECG[1]]);
                            wfECG3.addAmp(ecgData[selectedECG[2]]);
                            wfECG4.addAmp(ecgData[selectedECG[3]]);
                            if (ecgDialog != null && ecgDialog.isShowing()) {
                                if (wfLeadI != null) wfLeadI.addAmp(ecgData[0]);
                                if (wfLeadII != null) wfLeadII.addAmp(ecgData[1]);
                                if (wfLeadIII != null) wfLeadIII.addAmp(ecgData[2]);
                                if (wfLeadAVR != null) wfLeadAVR.addAmp(ecgData[3]);
                                if (wfLeadAVL != null) wfLeadAVL.addAmp(ecgData[4]);
                                if (wfLeadAVF != null) wfLeadAVF.addAmp(ecgData[5]);
                                if (wfLeadV != null) wfLeadV.addAmp(ecgData[6]);
                            }
                        }
                        ecgBatchBuffer.clear();
                        runOnUiThread(() -> {
                            wfECG1.postInvalidate();
                            wfECG2.postInvalidate();
                            wfECG3.postInvalidate();
                            wfECG4.postInvalidate();
                            if (ecgDialog != null && ecgDialog.isShowing()) {
                                if (wfLeadI != null) wfLeadI.postInvalidate();
                                if (wfLeadII != null) wfLeadII.postInvalidate();
                                if (wfLeadIII != null) wfLeadIII.postInvalidate();
                                if (wfLeadAVR != null) wfLeadAVR.postInvalidate();
                                if (wfLeadAVL != null) wfLeadAVL.postInvalidate();
                                if (wfLeadAVF != null) wfLeadAVF.postInvalidate();
                                if (wfLeadV != null) wfLeadV.postInvalidate();
                            }
                            Log.d(TAG, "ECG batch update: processed " + ecgBatchBuffer.size() + " points");
                        });
                    }
                }
                uiUpdateHandler.postDelayed(this, 100);
            }
        }, 100);
    }

    @Override
    public void onSpO2WaveReceived(int dat) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
            synchronized (spo2Buffer) {
                spo2Buffer.add(dat);
                if (spo2Buffer.size() > SPO2_BUFFER_MAX_SIZE) {
                    spo2Buffer.subList(0, spo2Buffer.size() - SPO2_BUFFER_MAX_SIZE).clear();
                }
                Log.d(TAG, "SpO2 data received: " + dat);
            }
        });
    }

    @Override
    public void onSpO2Received(SpO2 spo2) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
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
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
            if (ecgDataBuffers.size() >= MAX_BUFFER_SIZE) {
                ecgDataBuffers.removeFirst();
            }
            ecgDataBuffers.addLast(ecgData.clone());
            synchronized (ecgBatchBuffer) {
                ecgBatchBuffer.add(ecgData.clone());
                if (ecgBatchBuffer.size() > MAX_BUFFER_SIZE) {
                    ecgBatchBuffer.subList(0, ecgBatchBuffer.size() - MAX_BUFFER_SIZE).clear();
                }
                Log.d(TAG, "ECG data received: leads=" + ecgData.length + ", batch size=" + ecgBatchBuffer.size());
            }
        });
    }

    @Override
    public void onRespWaveReceived(int dat) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
            wfResp.addAmp(dat);
            runOnUiThread(() -> {
                wfResp.postInvalidate();
                Log.d(TAG, "Resp data received: " + dat);
            });
        });
    }

    @Override
    public void onECGReceived(ECG ecg) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
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
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
            runOnUiThread(() -> {
                tvTEMPinfo.setText(temp.toString());
            });
        });
    }

    @Override
    public void onNIBPReceived(NIBP nibp) {
        if (!serialPort.isConnected()) {
            return;
        }
        vitalHandler.post(() -> {
            if (!serialPort.isConnected()) {
                return;
            }
            runOnUiThread(() -> {
                tvNIBPinfo.setText(nibp.toString());
            });
        });
    }

    private void generateReport() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable stopRecording = () -> {
            final int[][] allData;
            synchronized (ecgDataBuffers) {
                if (!ecgDataBuffers.isEmpty()) {
                    int dataPoints = Math.min(500, ecgDataBuffers.size());
                    int[][] tempData = new int[7][dataPoints];
                    int index = 0;
                    for (int[] data : ecgDataBuffers) {
                        if (index >= dataPoints) break;
                        for (int i = 0; i < 7; i++) {
                            tempData[i][index] = data[i];
                        }
                        index++;
                    }
                    allData = tempData;
                } else {
                    allData = new int[7][0];
                }
            }
            executorService.execute(() -> generatePdfFromData(allData));
        };

        ecgDataBuffers.clear();
        vitalHandler.post(() -> {
            handler.postDelayed(stopRecording, DATA_COLLECTION_DURATION_MS);
        });

        runOnUiThread(() -> {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Generating ECG PDF (10 seconds)...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            handler.postDelayed(() -> {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }, DATA_COLLECTION_DURATION_MS);
        });
    }

    private void generatePdfFromData(int[][] ecgData) {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        Paint title = new Paint();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(842, 595, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        String patientName = "DixaMomo";
        int patientAge = 40;
        String reportNumber = "ECG-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String deviceName = "CNRGI Remote Patient Monitoring Solution";
        String dateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? parseHeartRate(tvECGinfo.getText().toString()) : 60;
        String pQRSAxis = "(66)-(249)-(59) deg";
        String qtC = "360 ms";
        String prInterval = "148 ms";
        String rrInterval = "996 ms";
        String qrsDuration = "84 ms";
        String interpretation = "Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
        String doctor = "Dr. Mangeshkar";
        String calibration = "10 mm/mv, 25.0 mm/sec Nasan M-Cardia 1.0/1.15";

        int leftMargin = 20, topMargin = 20, lineSpacing = 20, sectionSpacing = 25;
        int leftX = 40;
        int centerX = 300;
        int rightX = 560;
        int startY = 60;
        int lineGap = 20;

        title.setTextSize(16);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ECG and Vitals with Measurement and Interpretation", 842 / 2, startY, title);

        startY += 30;
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT);
        title.setTextAlign(Paint.Align.LEFT);

        int y = startY;
        canvas.drawText("Patient Name :", leftX, y, title);
        y += lineGap;
        canvas.drawText("Age   :", leftX, y, title);
        canvas.drawText("Gender :", leftX + 120, y, title);
        y += lineGap;
        canvas.drawText("P-QRS-T Axis ( 66)-( 249)-( 59) deg", leftX, y, title);
        y += lineGap;
        canvas.drawText("QTc: 360 ms", leftX, y, title);

        y = startY;
        canvas.drawText("Report Number :", centerX, y, title);
        y += lineGap;
        canvas.drawText("Date :", centerX, y, title);
        y += lineGap;
        canvas.drawText("PR Interval:148 ms", centerX, y, title);
        y += lineGap;
        canvas.drawText("QRS Duration: 84 ms", centerX, y, title);

        y = startY;
        canvas.drawText("Name of Device: CNRGI Remote Patient Monitoring Solution", rightX, y, title);
        y += lineGap;
        canvas.drawText("Time :", rightX, y, title);
        y += lineGap;
        canvas.drawText("RR Interval:996 ms", rightX, y, title);
        y += lineGap + 10;
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(14);
        canvas.drawText("HR : 60", rightX, y, title);

        drawECGSection(canvas, ecgData);
        drawFooterSection(canvas);

        pdfDocument.finishPage(page);
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ECG_Report_" + reportNumber + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            runOnUiThread(() -> Toast.makeText(this, "ECG PDF generated successfully.", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to generate ECG PDF.", Toast.LENGTH_SHORT).show());
        }
        pdfDocument.close();
    }

    private int parseHeartRate(String text) {
        try {
            return Integer.parseInt(text.split("\n")[0].replace("Heart Rate: ", "").trim());
        } catch (Exception e) {
            return 87;
        }
    }

    private void drawECGSection(Canvas canvas, int[][] ecgData) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.RED);
        gridPaint.setStrokeWidth(1);
        Paint wavePaint = new Paint();
        wavePaint.setColor(Color.LTGRAY);
        wavePaint.setStrokeWidth(2);
        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.LTGRAY);
        labelPaint.setTextSize(12);
        labelPaint.setTypeface(Typeface.DEFAULT);

        String[][] rows = {{"I", "aVR"}, {"II", "aVL"}, {"III", "aVF"}, {"V"}};
        int[] leadIndices = {0, 3, 1, 4, 2, 5, 6};
        int startX = 20;
        int startY = 180;
        int totalWidth = 842;
        int boxHeight = 90;
        int horizontalGap = 1;
        int boxWidth = (totalWidth - horizontalGap) / 2;
        int verticalGap = 5;

        for (int row = 0; row < rows.length; row++) {
            String[] leads = rows[row];
            for (int col = 0; col < leads.length; col++) {
                int currentBoxWidth = leads.length == 1 ? (boxWidth * 2 + horizontalGap) : boxWidth;
                int x = startX + col * (boxWidth + horizontalGap);
                int y = startY + row * (boxHeight + verticalGap);
                drawGrid(canvas, x, y, currentBoxWidth, boxHeight, gridPaint);
                int leadIndex = leadIndices[row * 2 + col];
                int[] leadData = ecgData[leadIndex];
                if (leadData.length > 0) {
                    int minValue = leadData[0];
                    int maxValue = leadData[0];
                    for (int value : leadData) {
                        if (value < minValue) minValue = value;
                        if (value > maxValue) maxValue = value;
                    }
                    int dataRange = Math.max(Math.abs(minValue), Math.abs(maxValue)) * 2;
                    if (dataRange == 0) dataRange = 1;
                    float xStep = (float) currentBoxWidth / leadData.length;
                    float yMid = y + boxHeight / 2;
                    float yScale = (float) (boxHeight * 0.8) / dataRange;
                    for (int i = 0; i < leadData.length - 1; i++) {
                        float x1 = x + i * xStep;
                        float y1 = yMid - (leadData[i] * yScale);
                        float x2 = x + (i + 1) * xStep;
                        float y2 = yMid - (leadData[i + 1] * yScale);
                        y1 = Math.max(y, Math.min(y + boxHeight, y1));
                        y2 = Math.max(y, Math.min(y + boxHeight, y2));
                        canvas.drawLine(x1, y1, x2, y2, wavePaint);
                    }
                } else {
                    int midY = y + boxHeight / 2;
                    canvas.drawLine(x, midY, x + currentBoxWidth, midY, wavePaint);
                }
                canvas.drawText(leads[col], x + 10, y + boxHeight - 10, labelPaint);
            }
        }
    }

    private void drawGrid(Canvas canvas, int x, int y, int width, int height, Paint paint) {
        int smallGrid = 10;
        for (int i = 0; i <= height / smallGrid; i++) {
            canvas.drawLine(x, y + i * smallGrid, x + width, y + i * smallGrid, paint);
        }
        for (int i = 0; i <= width / smallGrid; i++) {
            canvas.drawLine(x + i * smallGrid, y, x + i * smallGrid, y + height, paint);
        }
    }

    private void drawFooterSection(Canvas canvas) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.BLACK);
        footerPaint.setTextSize(12);
        footerPaint.setTypeface(Typeface.DEFAULT);
        int startX = 20;
        int startY = 504;
        String interpretationText = "Interpretation: Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
        canvas.drawText(interpretationText, startX, startY, footerPaint);
        int lineGap = 20;
        String unconfirmedText = "Unconfirmed ECG Report. Please refer Physician";
        String doctorLabel = "Name of Doctor: ____________________";
        int nextY = startY + lineGap;
        canvas.drawText(unconfirmedText, startX, nextY, footerPaint);
        float doctorLabelX = startX + 300;
        canvas.drawText(doctorLabel, doctorLabelX, nextY, footerPaint);
        float textWidth = footerPaint.measureText("Name of Doctor: ");
        float underlineStartX = doctorLabelX + textWidth;
        float underlineEndX = doctorLabelX + footerPaint.measureText(doctorLabel);
        footerPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(underlineStartX, nextY + 2, underlineEndX, nextY + 2, footerPaint);
    }

    private void showEcgDialog() {
        if (ecgDialog != null && ecgDialog.isShowing()) {
            return;
        }
        ecgDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ecgDialog.setContentView(R.layout.layout_seven_lead_popup);
        wfLeadI = ecgDialog.findViewById(R.id.wfLeadI);
        wfLeadII = ecgDialog.findViewById(R.id.wfLeadII);
        wfLeadIII = ecgDialog.findViewById(R.id.wfLeadIII);
        wfLeadAVR = ecgDialog.findViewById(R.id.wfLeadAVR);
        wfLeadAVL = ecgDialog.findViewById(R.id.wfLeadAVL);
        wfLeadAVF = ecgDialog.findViewById(R.id.wfLeadAVF);
        wfLeadV = ecgDialog.findViewById(R.id.wfLeadV);
        ImageView ivClosePopup = ecgDialog.findViewById(R.id.ivClosePopup);
        ivClosePopup.setOnClickListener(v -> ecgDialog.dismiss());
        ecgDialog.show();
    }

    private void startAutoTrigger(int minutes) {
        stopAutoTrigger();
        intervalInMillis = minutes * 60 * 1000;
        autoTriggerRunnable = new Runnable() {
            @Override
            public void run() {
                serialPort.write(DataParser.CMD_START_NIBP);
                autoTriggerHandler.postDelayed(this, intervalInMillis);
            }
        };
        autoTriggerHandler.post(autoTriggerRunnable);
    }

    private void stopAutoTrigger() {
        if (autoTriggerRunnable != null) {
            autoTriggerHandler.removeCallbacks(autoTriggerRunnable);
            autoTriggerRunnable = null;
        }
    }

}




//-----------------------------


//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.Dialog;
//import android.app.ProgressDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.Typeface;
//import android.media.AudioTrack;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.Looper;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.ImageButton;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.RelativeLayout;
//import android.widget.Spinner;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//
//import com.github.mikephil.charting.charts.LineChart;
//import com.github.mikephil.charting.data.Entry;
//import com.github.mikephil.charting.data.LineData;
//import com.github.mikephil.charting.data.LineDataSet;
//import com.lztek.api.demo.data.DataParser;
//import com.lztek.api.demo.data.ECG;
//import com.lztek.api.demo.data.NIBP;
//import com.lztek.api.demo.data.SpO2;
//import com.lztek.api.demo.data.Temp;
//import com.lztek.api.demo.view.WaveformView;
//
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.Deque;
//import java.util.List;
//
//import android.graphics.pdf.PdfDocument;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//    private static final int MAX_BUFFER_SIZE = 1000; // Limit buffer size to prevent overflow
//
//    private Button btnSerialCtr;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfECG4;
//    private WaveformView wfResp;
//    private Spinner spinnerECG3;
//    private Button btnGenerateReport;
//
//
//    private Button chestoConnett;
//    private ImageButton liveChesto;
//    private ImageButton recordChestoo;
//    private TextView recordingTimer;
//    private ImageButton recordPauseChesto;
//    private ImageButton playOrstop;
//    private ImageButton saveAudio;
//    private ImageButton refreshAudio;
//    private ImageButton uploadAudio;
//
////    private Dialog chestoGraphDialog;
//
//    private LineChart cAudioGraph;
//
//    private RelativeLayout audioGrpahContainer;
//
//
//    private ImageView btnShowAllLeads;
//    private Dialog ecgDialog;
//    private Dialog chestoDialog;
//
//    private WaveformView wfLeadI, wfLeadII, wfLeadIII, wfLeadAVR, wfLeadAVL, wfLeadAVF, wfLeadV;
//
//    private Button nibpStopButton;
//
//    private Button nibp5MinButton, nibp15MinButton, nibp30MinButton;
//    private Handler autoTriggerHandler = new Handler();
//    private Runnable autoTriggerRunnable;
//    private int intervalInMillis = 0;
//
//    private String[] ecgOptions = {"ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2, 3};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//
////
//
//    private BluetoothService bluetoothService;
//    private TextView statusText;
//    private Button connectButton, recordButton, playPauseButton, chestoButton;
//    private boolean isRecording = false;
//    private boolean isListening = false;
//    private boolean isPlaying = false;
//    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
//    private LineChart audioGraph;
//    private Handler graphUpdateHandler = new Handler(Looper.getMainLooper());
//    private AudioTrack audioTrack;
//    private List<Byte> plotBuffer = new ArrayList<>(); // Local buffer for graphing
//
//
////    private BluetoothService bluetoothService;
//
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    private BroadcastReceiver vitalStatusReceiver;
//
//    private Deque<int[]> ecgDataBuffers = new ArrayDeque<>(); // Store 10 seconds data for 7 leads
//    private static final int DATA_COLLECTION_DURATION_MS = 10000; // 10 seconds
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
////        bluetoothService = new BluetoothService();
//        initData();
//        initView();
//        initPermissions();
//        setupVitalStatusReceiver();
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//        checkVitalStatus();
//        bluetoothService = new BluetoothService();
//
//        bluetoothService.setCallback(new BluetoothService.BluetoothCallback() {
//            @Override
//            public void onDataReceived(byte[] data) {
////                runOnUiThread(() -> statusText.setText("Receiving Data..."));
//                if (isRecording) {
//                    try {
//                        audioBuffer.write(data);
//                    } catch (IOException e) {
//                        Log.d("ChestoDeviceActivity", "Write failed: " + e.getMessage());
//                    }
//                }
//                if (isListening) {
//                    synchronized (plotBuffer) {
//                        plotBuffer.clear();
//                        for (byte b : data) {
//                            plotBuffer.add(b);
//                        }
//                        if (plotBuffer.size() > 512) {
//                            plotBuffer.subList(0, plotBuffer.size() - 512).clear();
//                        }
//                    }
//                }
//            }
//
//            @Override
//            public void onConnected() {
//                runOnUiThread(() -> {
////                    statusText.setText("Connected to Chesto");
//                    chestoConnett.setText("Disconnect");
//                    chestoConnett.setEnabled(true);
//                });
//            }
//
//            @Override
//            public void onDisconnected() {
//                runOnUiThread(() -> {
////                    statusText.setText("Disconnected");
//                    chestoConnett.setText("Connect");
////                    chestoButton.setEnabled(false);
////                    playPauseButton.setEnabled(false);
//                    isListening = false;
////                    chestoConnett.setText("Start Chesto");
//                    graphUpdateHandler.removeCallbacks(graphUpdater);
//                });
//            }
//
//            @Override
//            public void onError(String error) {
//                runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, error, Toast.LENGTH_SHORT).show());
//            }
//
//        });
//
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
//
//
//        mDataParser = new DataParser(this);
//        backgroundThread = new HandlerThread("BackgroundThread");
//        backgroundThread.start();
//        backgroundHandler = new Handler(backgroundThread.getLooper()) {
//            @Override
//            public void handleMessage(android.os.Message msg) {
//                if (!serialPort.isConnected()) {
//                    Log.d(TAG, "Ignoring message: Device disconnected");
//                    return;
//                }
//                super.handleMessage(msg);
//            }
//        };
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect Vitals");
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfECG4 = findViewById(R.id.wfECG4);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//        btnGenerateReport = findViewById(R.id.btnGenerateReport);
//        btnShowAllLeads = findViewById(R.id.btnShowAllLeads);
//        spinnerECG3 = findViewById(R.id.spinnerECG4);
////        chestoDialog.setContentView(R.layout.chesto_graph);
//        nibpStopButton = findViewById(R.id.btnNIBPStop);
//
//
//        nibp5MinButton = findViewById(R.id.nibp5minbtn);
//        nibp15MinButton = findViewById(R.id.nibp15minbtn);
//        nibp30MinButton = findViewById(R.id.nibp30minbtn);
//
//
//        // chesto buttons
//
//
//        chestoConnett = findViewById(R.id.chesto_connect_vt);
//        liveChesto = findViewById(R.id.live_chesto);
//        recordChestoo = findViewById(R.id.record_chesto);
//        recordingTimer = findViewById(R.id.recording_timer);
//        recordPauseChesto = findViewById(R.id.record_pause);
//        playOrstop = findViewById(R.id.play_or_stop);
//        saveAudio = findViewById(R.id.save_audio);
//        refreshAudio = findViewById(R.id.refresh_audio);
//        uploadAudio = findViewById(R.id.upload_audio);
//
//        audioGrpahContainer = findViewById(R.id.audio_graph_container);
//        cAudioGraph = findViewById(R.id.cAudioGraph);
//
//        setupGraph();
//
//
//        setupDropdown(spinnerECG3, 3);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        btnGenerateReport.setOnClickListener(v -> {
//            backgroundHandler.post(() -> generateReport());
////            backgroundHandler.post(() -> generatePdfFromData(this));
//        });
//
//
//        btnShowAllLeads.setOnClickListener(v -> showEcgDialog());
//
//        nibp5MinButton.setOnClickListener(v -> startAutoTrigger(5));
//        nibp15MinButton.setOnClickListener(v -> startAutoTrigger(15));
//        nibp30MinButton.setOnClickListener(v -> startAutoTrigger(30));
//
//        nibpStopButton.setOnClickListener(view -> stopAutoTrigger());
//
//        chestoConnett.setOnClickListener(v -> {
//            if (chestoConnett.getText().toString().equals("Connect")) {
//                connectToChesto();
//            } else {
//                try {
//                    bluetoothService.disconnect();
//                } catch (Exception e) {
////                    Log.d("ChestoDeviceActivity", "Disconnect failed: " + e.getMessage());
//                    Toast.makeText(this, "Disconnect failed", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//
//
//        liveChesto.setOnClickListener(v -> {
//            if (!isListening) {
//                if (bluetoothService.isConnected()) {
//                    bluetoothService.startListening();
////                    showChestoAudioGraph();
//
//                    audioGrpahContainer.setVisibility(View.VISIBLE);
//
//                    liveChesto.setBackgroundResource(R.drawable.stop_live_strem);
//                    isListening = true;
//                    Toast.makeText(this, "Started Listening", Toast.LENGTH_SHORT).show();
//                    graphUpdateHandler.post(graphUpdater);
//
//                } else {
//                    Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show();
//                }
//            } else {
//                bluetoothService.stopListening();
//                liveChesto.setBackgroundResource(R.drawable.live_stream);
//                isListening = false;
//
//                audioGrpahContainer.setVisibility(View.GONE);
//
//                Toast.makeText(this, "Stopped Listening", Toast.LENGTH_SHORT).show();
//                graphUpdateHandler.removeCallbacks(graphUpdater);
//
//            }
//        });
//
//        recordChestoo.setOnClickListener(v -> {
//            if (!isRecording) {
//                audioBuffer.reset();
//                isRecording = true;
//                bluetoothService.startRecording();
////
//                recordChestoo.setBackgroundResource(R.drawable.stop_recording);
//
//                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
//
//            } else {
//                isRecording = false;
//
//                recordChestoo.setBackgroundResource(R.drawable.play);
//
//                byte[] recordedData = audioBuffer.toByteArray();
//                if (recordedData.length == 0) {
//                    Toast.makeText(this, "Recording Failed: No Data", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//                new Thread(() -> saveAudioToFile(recordedData)).start();
////                playPauseButton.setEnabled(true);
//            }
//        });
//    }
//
//    private void saveAudioToFile(byte[] recordedData) {
//        try {
//            File file = new File(getExternalFilesDir(null), "recorded_audio.wav");
//            FileOutputStream fos = new FileOutputStream(file);
//
//            // 6000 Hz sample rate, 16-bit PCM mono
//            byte[] header = getWavHeader(recordedData.length, 6000);
//            fos.write(header);
//            fos.write(recordedData);  // direct write, no conversion
//            fos.flush();
//            fos.close();
//
//            final String filePath = file.getAbsolutePath();
//            runOnUiThread(() ->
//                    Toast.makeText(this, "Audio saved at: " + filePath, Toast.LENGTH_LONG).show()
//            );
//        } catch (IOException e) {
//            Log.d("ChestoDeviceActivity", "Save failed: " + e.getMessage());
//            runOnUiThread(() ->
//                    Toast.makeText(this, "Failed to save audio: " + e.getMessage(), Toast.LENGTH_SHORT).show()
//            );
//        }
//    }
//
//    private byte[] getWavHeader(int totalAudioLen, int sampleRate) {
//        int channels = 1;
//        int byteRate = sampleRate * 2;  // 16-bit = 2 bytes * 1 channel
//
//        int totalDataLen = totalAudioLen + 36;
//
//        byte[] header = new byte[44];
//        header[0] = 'R';
//        header[1] = 'I';
//        header[2] = 'F';
//        header[3] = 'F';
//        header[4] = (byte) (totalDataLen & 0xff);
//        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
//        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
//        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
//        header[8] = 'W';
//        header[9] = 'A';
//        header[10] = 'V';
//        header[11] = 'E';
//        header[12] = 'f';
//        header[13] = 'm';
//        header[14] = 't';
//        header[15] = ' ';
//        header[16] = 16;
//        header[17] = 0;
//        header[18] = 0;
//        header[19] = 0;   // Subchunk1Size
//        header[20] = 1;
//        header[21] = 0;   // PCM format
//        header[22] = (byte) channels;
//        header[23] = 0;
//        header[24] = (byte) (sampleRate & 0xff);
//        header[25] = (byte) ((sampleRate >> 8) & 0xff);
//        header[26] = (byte) ((sampleRate >> 16) & 0xff);
//        header[27] = (byte) ((sampleRate >> 24) & 0xff);
//        header[28] = (byte) (byteRate & 0xff);
//        header[29] = (byte) ((byteRate >> 8) & 0xff);
//        header[30] = (byte) ((byteRate >> 16) & 0xff);
//        header[31] = (byte) ((byteRate >> 24) & 0xff);
//        header[32] = 2;
//        header[33] = 0;   // Block align = 2 bytes
//        header[34] = 16;
//        header[35] = 0;   // Bits per sample
//        header[36] = 'd';
//        header[37] = 'a';
//        header[38] = 't';
//        header[39] = 'a';
//        header[40] = (byte) (totalAudioLen & 0xff);
//        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
//        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
//        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
//
//        return header;
//    }
//
//    private void connectToChesto() {
//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (bluetoothAdapter == null) {
//            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, 1);
//        }
//
//        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
//            if (device.getName() != null && device.getName().contains("Chesto")) {
//                bluetoothService.connectToDevice(device);
//                return;
//            }
//        }
//        Toast.makeText(this, "Chesto device not found", Toast.LENGTH_SHORT).show();
//    }
//
//
//    private void setupGraph() {
//        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "Audio Signal");
//        dataSet.setDrawCircles(false);
//        dataSet.setDrawValues(false);
//        dataSet.setColor(Color.BLUE);
//        dataSet.setLineWidth(1f);
//        LineData lineData = new LineData(dataSet);
//        cAudioGraph.setData(lineData);
//        cAudioGraph.getXAxis().setDrawLabels(false);
//        cAudioGraph.getAxisLeft().setDrawLabels(false);
//        cAudioGraph.getAxisRight().setDrawLabels(false);
//        cAudioGraph.setHardwareAccelerationEnabled(true);
//        cAudioGraph.invalidate();
//    }
//
//    private Runnable graphUpdater = new Runnable() {
//        @Override
//        public void run() {
//            updateGraph();
//            graphUpdateHandler.postDelayed(this, 10);
//        }
//    };
//
//    private void updateGraph() {
//        List<Byte> bufferCopy;
//        synchronized (plotBuffer) {
//            if (plotBuffer.isEmpty()) {
//                return;
//            }
//            bufferCopy = new ArrayList<>(plotBuffer);
//        }
//
//        List<Entry> newEntries = new ArrayList<>();
//        for (int i = 0; i < bufferCopy.size(); i++) {
//            newEntries.add(new Entry(i, bufferCopy.get(i)));
//        }
//
//        runOnUiThread(() -> {
//            try {
//                LineData data = cAudioGraph.getData();
//                if (data != null && data.getDataSetCount() > 0) {
//                    LineDataSet dataSet = (LineDataSet) data.getDataSetByIndex(0);
//                    dataSet.setValues(newEntries);
//                    data.notifyDataChanged();
//                    cAudioGraph.notifyDataSetChanged();
//                    cAudioGraph.invalidate();
//                }
//            } catch (Exception e) {
//                Log.d("GraphUpdate", "Update failed: " + e.getMessage());
//            }
//        });
//    }
//
//    private void setupVitalStatusReceiver() {
//        vitalStatusReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                long vitalStatus = intent.getLongExtra("vital_status", 0);
//                if (vitalStatus == 1) {
//                    GlobalVars.setVitalOn(true);
//                    connectSerialPort();
//                } else {
//                    GlobalVars.setVitalOn(false);
//                    disconnectSerialPort();
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(vitalStatusReceiver, filter);
//    }
//
//    private void checkVitalStatus() {
//        if (GlobalVars.isVitalOn) {
//            connectSerialPort();
//        } else {
//            disconnectSerialPort();
//        }
//    }
//
//    private void connectSerialPort() {
//        if (!serialPort.isConnected()) {
//            runOnUiThread(() -> mConnectingDialog.show());
//            serialPort.connect();
//        }
//    }
//
//    private void disconnectSerialPort() {
//        if (serialPort.isConnected()) {
//            Log.d(TAG, "Disconnecting serial port...");
//            serialPort.disconnect();
//            backgroundHandler.removeCallbacksAndMessages(null);
//            ecgDataBuffers.clear();
//            clearWaveformViews();
//            Log.d(TAG, "Cleared buffers and handler tasks");
//        }
//    }
//
//    private void clearWaveformViews() {
//        runOnUiThread(() -> {
//            wfECG1.clear();
//            wfECG2.clear();
//            wfECG3.clear();
//            wfECG4.clear();
//            wfResp.clear();
//            // Explicitly clear SpO2 with forced redraw
//            wfSpO2.clear();
//            wfSpO2.invalidate();
//            wfSpO2.requestLayout();
//            // Fallback: Force SpO2 refresh by toggling visibility
//            wfSpO2.setVisibility(View.GONE);
//            wfSpO2.setVisibility(View.VISIBLE);
//            if (ecgDialog != null && ecgDialog.isShowing()) {
//                wfLeadI.clear();
//                wfLeadII.clear();
//                wfLeadIII.clear();
//                wfLeadAVR.clear();
//                wfLeadAVL.clear();
//                wfLeadAVF.clear();
//                wfLeadV.clear();
//            }
//
////            Log.d(TAG, "Cleared all waveform views, forced SpO2 redraw and refresh");
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
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//        if (index == 3) wfECG4.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
//                    disconnectSerialPort();
//                }
//                break;
//            case R.id.btnNIBPStart:
//                serialPort.write(DataParser.CMD_START_NIBP);
//                break;
////            case R.id.btnNIBPStop:
////                serialPort.write(DataParser.CMD_STOP_NIBP);
////                break;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        disconnectSerialPort();
//        mDataParser.stop();
//        if (backgroundThread != null) {
//            backgroundThread.quitSafely();
//            backgroundThread = null;
//        }
//        if (vitalStatusReceiver != null) {
//            unregisterReceiver(vitalStatusReceiver);
//            vitalStatusReceiver = null;
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
//        if (!serialPort.isConnected()) {
//            Log.d(TAG, "Ignoring data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (serialPort.isConnected()) {
//                mDataParser.add(data);
//            } else {
//                Log.d(TAG, "Dropped data: Device disconnected");
//            }
//        });
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect Vitals");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//                ecgDataBuffers.clear();
//                clearWaveformViews();
////                Log.d(TAG, "Connection status changed to: " + status + ", cleared all views including SpO2");
//            }
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring SpO2 wave data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped SpO2 wave data: Device disconnected");
//                return;
//            }
//            wfSpO2.addAmp(dat);
//            runOnUiThread(() -> {
//                wfSpO2.postInvalidate();
////                Log.d(TAG, "SpO2 wave updated");
//            });
//        });
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring SpO2 data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped SpO2 data: Device disconnected");
//                return;
//            }
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
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2ValueStart, spo2ValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseValueStart, pulseValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvSPO2info.setText(spannable);
//                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
//                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
//                wfSpO2.postInvalidate();
////                Log.d(TAG, "SpO2 info updated: " + spo2Value);
//            });
//        });
//    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring ECG data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped ECG data: Device disconnected");
//                return;
//            }
//
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            if (ecgDataBuffers.size() >= MAX_BUFFER_SIZE) {
//                ecgDataBuffers.removeFirst();
//            }
//            ecgDataBuffers.addLast(ecgData.clone());
//
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//            wfECG4.addAmp(ecgData[selectedECG[3]]);
//
//            if (ecgDialog != null && ecgDialog.isShowing()) {
//                if (wfLeadI != null) wfLeadI.addAmp(leadI);
//                if (wfLeadII != null) wfLeadII.addAmp(leadII);
//                if (wfLeadIII != null) wfLeadIII.addAmp(leadIII);
//                if (wfLeadAVR != null) wfLeadAVR.addAmp(aVR);
//                if (wfLeadAVL != null) wfLeadAVL.addAmp(aVL);
//                if (wfLeadAVF != null) wfLeadAVF.addAmp(aVF);
//                if (wfLeadV != null) wfLeadV.addAmp(vLead);
//            }
//
//            runOnUiThread(() -> {
//                wfECG1.postInvalidate();
//                wfECG2.postInvalidate();
//                wfECG3.postInvalidate();
//                wfECG4.postInvalidate();
//                if (ecgDialog != null && ecgDialog.isShowing()) {
//                    if (wfLeadI != null) wfLeadI.postInvalidate();
//                    if (wfLeadII != null) wfLeadII.postInvalidate();
//                    if (wfLeadIII != null) wfLeadIII.postInvalidate();
//                    if (wfLeadAVR != null) wfLeadAVR.postInvalidate();
//                    if (wfLeadAVL != null) wfLeadAVL.postInvalidate();
//                    if (wfLeadAVF != null) wfLeadAVF.postInvalidate();
//                    if (wfLeadV != null) wfLeadV.postInvalidate();
//                }
////                Log.d(TAG, "ECG graphs updated");
//            });
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring Resp data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped Resp data: Device disconnected");
//                return;
//            }
////            for (int i = 0; i < 3; i++) wfResp.addAmp(dat);
//            wfResp.addAmp(dat);
//            runOnUiThread(() -> {
//                wfResp.postInvalidate();
////                Log.d(TAG, "Resp graph updated");
//            });
//        });
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring ECG data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped ECG data: Device disconnected");
//                return;
//            }
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
////                Log.d(TAG, "ECG info updated: HR=" + heartRateValue);
//            });
//        });
//    }
//
//    @Override
//    public void onTempReceived(Temp temp) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring Temp data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped Temp data: Device disconnected");
//                return;
//            }
//            runOnUiThread(() -> {
//                tvTEMPinfo.setText(temp.toString());
////                Log.d(TAG, "Temp info updated: " + temp.toString());
//            });
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "Ignoring NIBP data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
////                Log.d(TAG, "Dropped NIBP data: Device disconnected");
//                return;
//            }
//            runOnUiThread(() -> {
//                tvNIBPinfo.setText(nibp.toString());
////                Log.d(TAG, "NIBP info updated: " + nibp.toString());
//            });
//        });
//    }
//
////
//
//    private void generateReport() {
//        final Handler handler = new Handler(Looper.getMainLooper());
//        final Runnable stopRecording = () -> {
//            int[][] allData = new int[7][];
//            if (!ecgDataBuffers.isEmpty()) {
//                int dataPoints = Math.min(500, ecgDataBuffers.size());
//                allData = new int[7][dataPoints];
//                for (int i = 0; i < 7; i++) {
//                    int[] tempData = new int[dataPoints];
//                    for (int j = 0; j < dataPoints; j++) {
//                        tempData[j] = ecgDataBuffers.toArray(new int[0][])[ecgDataBuffers.size() - 1 - j][i];
//                    }
//                    allData[i] = tempData;
//                }
//            } else {
//                for (int i = 0; i < 7; i++) allData[i] = new int[0];
//            }
//            generatePdfFromData(allData);
//        };
//
//        ecgDataBuffers.clear();
//        backgroundHandler.post(() -> {
//            handler.postDelayed(stopRecording, 10000);
//        });
//
//        runOnUiThread(() -> {
//            ProgressDialog progressDialog = new ProgressDialog(this);
//            progressDialog.setMessage("Generating ECG PDF (10 seconds)...");
//            progressDialog.setCancelable(false);
//            progressDialog.show();
//            handler.postDelayed(() -> {
//                if (progressDialog.isShowing()) {
//                    progressDialog.dismiss();
//                }
//            }, 10000);
//        });
//    }
//
//    private void generatePdfFromData(int[][] ecgData) {
//        PdfDocument pdfDocument = new PdfDocument();
//
//        Paint paint = new Paint();
//        Paint title = new Paint();
//
////        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(1191, 842, 1).create();
//        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(842, 595, 1).create();
//        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
//        Canvas canvas = page.getCanvas();
//
//        // Sample data
//        String patientName = "DixaMomo";
//        int patientAge = 40;
//        String reportNumber = "ECG-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
//        String deviceName = "CNRGI Remote Patient Monitoring Solution";
//        String dateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
//        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? parseHeartRate(tvECGinfo.getText().toString()) : 60;
//        String pQRSAxis = "(66)-(249)-(59) deg";
//        String qtC = "360 ms";
//        String prInterval = "148 ms";
//        String rrInterval = "996 ms";
//        String qrsDuration = "84 ms";
//        String interpretation = "Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
//        String doctor = "Dr. Mangeshkar";
//        String calibration = "10 mm/mv, 25.0 mm/sec Nasan M-Cardia 1.0/1.15";
//
//        int leftMargin = 20, topMargin = 20, lineSpacing = 20, sectionSpacing = 25;
//
//        int leftX = 40;
//        int centerX = 300;
//        int rightX = 560;
//        int startY = 60;
//        int lineGap = 20;
//
//// Title (centered)
//        title.setTextSize(16);
//        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        title.setTextAlign(Paint.Align.CENTER);
//        canvas.drawText("ECG and Vitals with Measurement and Interpretation", 842 / 2, startY, title);
//
//        startY += 30;
//        title.setTextSize(12);
//        title.setTypeface(Typeface.DEFAULT);
//        title.setTextAlign(Paint.Align.LEFT);
//
//// Left Column
//        int y = startY;
//        canvas.drawText("Patient Name :", leftX, y, title);
//        y += lineGap;
//        canvas.drawText("Age   :", leftX, y, title);
//        canvas.drawText("Gender :", leftX + 120, y, title);  // inline gender
//        y += lineGap;
//        canvas.drawText("P-QRS-T Axis ( 66)-( 249)-( 59) deg", leftX, y, title);
//        y += lineGap;
//        canvas.drawText("QTc: 360 ms", leftX, y, title);
//
//// Center Column
//        y = startY;
//        canvas.drawText("Report Number :", centerX, y, title);
//        y += lineGap;
//        canvas.drawText("Date :", centerX, y, title);
//        y += lineGap;
//        canvas.drawText("PR Interval:148 ms", centerX, y, title);
//        y += lineGap;
//        canvas.drawText("QRS Duration: 84 ms", centerX, y, title);
//
//// Right Column
//        y = startY;
//        canvas.drawText("Name of Device: CNRGI Remote Patient Monitoring Solution", rightX, y, title);
//        y += lineGap;
//        canvas.drawText("Time :", rightX, y, title);
//        y += lineGap;
//        canvas.drawText("RR Interval:996 ms", rightX, y, title);
//        y += lineGap + 10;
//        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        title.setTextSize(14);
//        canvas.drawText("HR : 60", rightX, y, title);
//
//        drawECGSection(canvas, ecgData);
//        drawFooterSection(canvas);
//
//        pdfDocument.finishPage(page);
//        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//                "ECG_Report_" + reportNumber + ".pdf");
//        try {
//            pdfDocument.writeTo(new FileOutputStream(file));
//            Toast.makeText(this, "ECG PDF generated successfully.", Toast.LENGTH_SHORT).show();
//            Log.d("PDF_GENERATION", "PDF saved at: " + file.getAbsolutePath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to generate ECG PDF.", Toast.LENGTH_SHORT).show();
//            Log.e("PDF_GENERATION", "Error saving PDF: " + e.getMessage());
//        }
//        pdfDocument.close();
//    }
//
//    private int parseHeartRate(String text) {
//        try {
//            return Integer.parseInt(text.split("\n")[0].replace("Heart Rate: ", "").trim());
//        } catch (Exception e) {
//            return 87;
//        }
//    }
//
//    private void drawECGSection(Canvas canvas, int[][] ecgData) {
//        Paint gridPaint = new Paint();
//        gridPaint.setColor(Color.RED);
//        gridPaint.setStrokeWidth(1);
//
//        Paint wavePaint = new Paint();
//        wavePaint.setColor(Color.LTGRAY);
//        wavePaint.setStrokeWidth(2);
//
//        Paint labelPaint = new Paint();
//        labelPaint.setColor(Color.LTGRAY);
//        labelPaint.setTextSize(12);
//        labelPaint.setTypeface(Typeface.DEFAULT);
//
//        // ECG leads in matrix layout
//        String[][] rows = {
//                {"I", "aVR"},
//                {"II", "aVL"},
//                {"III", "aVF"},
//                {"V"} // V lead full width
//        };
//
//        // Lead mapping to ecgData indices
//        int[] leadIndices = {0, 3, 1, 4, 2, 5, 6}; // I, aVR, II, aVL, III, aVF, V
//
//        int startX = 20;
//        int startY = 180;
//        int totalWidth = 842;
//        int boxHeight = 90; // Increased from 70 to 90 for more vertical space
//        int horizontalGap = 1;
//        int boxWidth = (totalWidth - horizontalGap) / 2;
//        int verticalGap = 5;
//
//        for (int row = 0; row < rows.length; row++) {
//            String[] leads = rows[row];
//            for (int col = 0; col < leads.length; col++) {
//                int currentBoxWidth = leads.length == 1 ? (boxWidth * 2 + horizontalGap) : boxWidth;
//                int x = startX + col * (boxWidth + horizontalGap);
//                int y = startY + row * (boxHeight + verticalGap);
//
//                // Draw grid box
//                drawGrid(canvas, x, y, currentBoxWidth, boxHeight, gridPaint);
//
//                // Draw waveform using ecgData
//                int leadIndex = leadIndices[row * 2 + col]; // Map lead to ecgData index
//                int[] leadData = ecgData[leadIndex];
//                if (leadData.length > 0) {
//                    // Calculate min and max values for this lead to determine the range
//                    int minValue = leadData[0];
//                    int maxValue = leadData[0];
//                    for (int value : leadData) {
//                        if (value < minValue) minValue = value;
//                        if (value > maxValue) maxValue = value;
//                    }
//                    int dataRange = Math.max(Math.abs(minValue), Math.abs(maxValue)) * 2; // Symmetric range
//                    if (dataRange == 0) dataRange = 1; // Avoid division by zero
//
//                    float xStep = (float) currentBoxWidth / leadData.length; // Horizontal step per data point
//                    float yMid = y + boxHeight / 2; // Baseline (middle of the box)
//                    float yScale = (float) (boxHeight * 0.8) / dataRange; // Scale to use 80% of box height
//
//                    // Draw the waveform as a series of line segments
//                    for (int i = 0; i < leadData.length - 1; i++) {
//                        float x1 = x + i * xStep;
//                        float y1 = yMid - (leadData[i] * yScale); // Invert y-axis (higher values go up)
//                        float x2 = x + (i + 1) * xStep;
//                        float y2 = yMid - (leadData[i + 1] * yScale);
//
//                        // Clip y values to stay within the grid box
//                        y1 = Math.max(y, Math.min(y + boxHeight, y1));
//                        y2 = Math.max(y, Math.min(y + boxHeight, y2));
//
//                        canvas.drawLine(x1, y1, x2, y2, wavePaint);
//                    }
//                } else {
//                    // Fallback: draw a straight line if no data
//                    int midY = y + boxHeight / 2;
//                    canvas.drawLine(x, midY, x + currentBoxWidth, midY, wavePaint);
//                }
//
//                // Draw lead name below the waveform
//                canvas.drawText(leads[col], x + 10, y + boxHeight - 10, labelPaint);
//            }
//        }
//    }
//
//    private void drawGrid(Canvas canvas, int x, int y, int width, int height, Paint paint) {
//        int smallGrid = 10;
//
//        for (int i = 0; i <= height / smallGrid; i++) {
//            canvas.drawLine(x, y + i * smallGrid, x + width, y + i * smallGrid, paint);
//        }
//        for (int i = 0; i <= width / smallGrid; i++) {
//            canvas.drawLine(x + i * smallGrid, y, x + i * smallGrid, y + height, paint);
//        }
//    }
//
//    private void drawFooterSection(Canvas canvas) {
//        Paint footerPaint = new Paint();
//        footerPaint.setColor(Color.BLACK);
//        footerPaint.setTextSize(12);
//        footerPaint.setTypeface(Typeface.DEFAULT);
//
//        // Calculate starting position (below ECG section)
//        int startX = 20;
//        int startY = 504; // 484 (end of ECG section) + 20 (gap)
//
//        // Line 1: Interpretation
//        String interpretationText = "Interpretation: Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
//        canvas.drawText(interpretationText, startX, startY, footerPaint);
//
//        // Line 2: Unconfirmed report and doctor's name
//        int lineGap = 20;
//        String unconfirmedText = "Unconfirmed ECG Report. Please refer Physician";
//        String doctorLabel = "Name of Doctor: ____________________";
//        int nextY = startY + lineGap;
//
//        // Draw unconfirmed text
//        canvas.drawText(unconfirmedText, startX, nextY, footerPaint);
//
//        // Draw "Name of Doctor" with underline
//        float doctorLabelX = startX + 300; // Position to the right of unconfirmed text
//        canvas.drawText(doctorLabel, doctorLabelX, nextY, footerPaint);
//
//        // Draw underline for the doctor's name
//        float textWidth = footerPaint.measureText("Name of Doctor: ");
//        float underlineStartX = doctorLabelX + textWidth;
//        float underlineEndX = doctorLabelX + footerPaint.measureText(doctorLabel);
//        footerPaint.setStyle(Paint.Style.STROKE);
//        canvas.drawLine(underlineStartX, nextY + 2, underlineEndX, nextY + 2, footerPaint);
//    }
//
//    private void showEcgDialog() {
//        if (ecgDialog != null && ecgDialog.isShowing()) {
//            return;
//        }
//
//        ecgDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
//        ecgDialog.setContentView(R.layout.layout_seven_lead_popup);
//
//        wfLeadI = ecgDialog.findViewById(R.id.wfLeadI);
//        wfLeadII = ecgDialog.findViewById(R.id.wfLeadII);
//        wfLeadIII = ecgDialog.findViewById(R.id.wfLeadIII);
//        wfLeadAVR = ecgDialog.findViewById(R.id.wfLeadAVR);
//        wfLeadAVL = ecgDialog.findViewById(R.id.wfLeadAVL);
//        wfLeadAVF = ecgDialog.findViewById(R.id.wfLeadAVF);
//        wfLeadV = ecgDialog.findViewById(R.id.wfLeadV);
//
//        ImageView ivClosePopup = ecgDialog.findViewById(R.id.ivClosePopup);
//        ivClosePopup.setOnClickListener(v -> ecgDialog.dismiss());
//
//        ecgDialog.show();
//    }
//
////    private void showChestoAudioGraph() {
////        if (chestoDialog != null && chestoDialog.isShowing()) {
////            return;
////        }
////
////        chestoDialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
////        chestoDialog.setContentView(R.layout.chesto_graph);
////
////        dialogAudioGraph = chestoDialog.findViewById(R.id.diloug_audioGraph);
////
////        setupGraph();
////
////        ImageView ivClosePopup = chestoDialog.findViewById(R.id.ivClosechPopupp);
////        ivClosePopup.setOnClickListener(v -> chestoDialog.dismiss());
////
////        chestoDialog.show();
////    }
//
//    private void startAutoTrigger(int minutes) {
////        stopAutoTrigger(); // Purani runnable hatao agar koi chal rahi ho
//
//        intervalInMillis = minutes * 60 * 1000;
////        intervalInMillis = 20 * 1000; // sirf 10 sec ke liye testing
//
//        autoTriggerRunnable = new Runnable() {
//            @Override
//            public void run() {
////                sendMyCommand(); // Actual command
//                serialPort.write(DataParser.CMD_START_NIBP);
//                autoTriggerHandler.postDelayed(this, intervalInMillis);
//            }
//        };
//
//        autoTriggerHandler.post(autoTriggerRunnable);
//    }
//
//    // To stop auto trigger (optional cancel button ke liye)
//    private void stopAutoTrigger() {
//        if (autoTriggerRunnable != null) {
//            autoTriggerHandler.removeCallbacks(autoTriggerRunnable);
//            autoTriggerRunnable = null;
//        }
//    }
//}


//---------------------------------------------


//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.Dialog;
//import android.app.ProgressDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.Typeface;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.Looper;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.ImageView;
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
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.Deque;
//import java.util.List;
//
//import android.graphics.pdf.PdfDocument;
//
//public class BerryDeviceActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener, DataParser.onPackageReceivedListener {
//
//    private static final String TAG = "BerryDeviceActivity";
//    private static final int MAX_BUFFER_SIZE = 1000; // Limit buffer size to prevent overflow
//
//    private Button btnSerialCtr;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfECG4;
//    private WaveformView wfResp;
//    private Spinner spinnerECG3;
//    private Button btnGenerateReport;
//    private Button chestoConnet;
//    private ImageView btnShowAllLeads;
//    private Dialog ecgDialog;
//    private WaveformView wfLeadI, wfLeadII, wfLeadIII, wfLeadAVR, wfLeadAVL, wfLeadAVF, wfLeadV;
//
//    private String[] ecgOptions = {"ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2, 3};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//    private BluetoothService bluetoothService;
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    private BroadcastReceiver vitalStatusReceiver;
//
//    private Deque<int[]> ecgDataBuffers = new ArrayDeque<>(); // Store 10 seconds data for 7 leads
//    private static final int DATA_COLLECTION_DURATION_MS = 10000; // 10 seconds
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//        bluetoothService = new BluetoothService();
//        initData();
//        initView();
//        initPermissions();
//        setupVitalStatusReceiver();
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//        checkVitalStatus();
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
//        backgroundHandler = new Handler(backgroundThread.getLooper()) {
//            @Override
//            public void handleMessage(android.os.Message msg) {
//                if (!serialPort.isConnected()) {
//                    Log.d(TAG, "Ignoring message: Device disconnected");
//                    return;
//                }
//                super.handleMessage(msg);
//            }
//        };
//        mDataParser.start();
//        serialPort = new BerrySerialPort(this);
//        serialPort.setOnDataReceivedListener(this);
//    }
//
//    private void initView() {
//        btnSerialCtr = findViewById(R.id.btnBtCtr);
//        btnSerialCtr.setText("Connect Vitals");
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//        wfECG4 = findViewById(R.id.wfECG4);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//        btnGenerateReport = findViewById(R.id.btnGenerateReport);
//        btnShowAllLeads = findViewById(R.id.btnShowAllLeads);
//        chestoConnet = findViewById(R.id.chesto_connect_vt);
//        spinnerECG3 = findViewById(R.id.spinnerECG4);
//
//        setupDropdown(spinnerECG3, 3);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
//        btnGenerateReport.setOnClickListener(v -> {
//            backgroundHandler.post(() -> generateReport());
//        });
//
//        chestoConnet.setOnClickListener(v -> {
//            if (chestoConnet.getText().toString().equals("Connect")) {
//                connectToChesto();
//            } else {
//                try {
//                    bluetoothService.disconnect();
//                } catch (Exception e) {
//                    Log.d(TAG, "Disconnect failed: " + e.getMessage());
//                    Toast.makeText(this, "Disconnect failed", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//
//        btnShowAllLeads.setOnClickListener(v -> showEcgDialog());
//    }
//
//    private void setupVitalStatusReceiver() {
//        vitalStatusReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                long vitalStatus = intent.getLongExtra("vital_status", 0);
//                if (vitalStatus == 1) {
//                    GlobalVars.setVitalOn(true);
//                    connectSerialPort();
//                } else {
//                    GlobalVars.setVitalOn(false);
//                    disconnectSerialPort();
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(vitalStatusReceiver, filter);
//    }
//
//    private void checkVitalStatus() {
//        if (GlobalVars.isVitalOn) {
//            connectSerialPort();
//        } else {
//            disconnectSerialPort();
//        }
//    }
//
//    private void connectSerialPort() {
//        if (!serialPort.isConnected()) {
//            runOnUiThread(() -> mConnectingDialog.show());
//            serialPort.connect();
//        }
//    }
//
//    private void disconnectSerialPort() {
//        if (serialPort.isConnected()) {
//            Log.d(TAG, "Disconnecting serial port...");
//            serialPort.disconnect();
//            backgroundHandler.removeCallbacksAndMessages(null);
//            ecgDataBuffers.clear();
//            clearWaveformViews();
//            Log.d(TAG, "Cleared buffers and handler tasks");
//        }
//    }
//
//    private void clearWaveformViews() {
//        runOnUiThread(() -> {
//            wfECG1.clear();
//            wfECG2.clear();
//            wfECG3.clear();
//            wfECG4.clear();
//            wfSpO2.clear(); // Ensure SpO2 is cleared
//            wfResp.clear();
//            if (ecgDialog != null && ecgDialog.isShowing()) {
//                wfLeadI.clear();
//                wfLeadII.clear();
//                wfLeadIII.clear();
//                wfLeadAVR.clear();
//                wfLeadAVL.clear();
//                wfLeadAVF.clear();
//                wfLeadV.clear();
//            }
//            Log.d(TAG, "Cleared all waveform views including SpO2");
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
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//        if (index == 3) wfECG4.clear();
//    }
//
//    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btnBtCtr:
//                if (!serialPort.isConnected()) {
//                    mConnectingDialog.show();
//                    serialPort.connect();
//                } else {
//                    disconnectSerialPort();
//                }
//              break;
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
//        disconnectSerialPort();
//        mDataParser.stop();
//        if (backgroundThread != null) {
//            backgroundThread.quitSafely();
//            backgroundThread = null;
//        }
//        if (vitalStatusReceiver != null) {
//            unregisterReceiver(vitalStatusReceiver);
//            vitalStatusReceiver = null;
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
//        if (!serialPort.isConnected()) {
//            Log.d(TAG, "Ignoring data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (serialPort.isConnected()) {
//                mDataParser.add(data);
//            } else {
//                Log.d(TAG, "Dropped data: Device disconnected");
//            }
//        });
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
//        runOnUiThread(() -> {
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect Vitals");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//                ecgDataBuffers.clear();
//                clearWaveformViews(); // Ensure SpO2 is cleared here
//            }
//            Log.d(TAG, "Connection status: " + status);
//        });
//    }
//
//    @Override
//    public void onSpO2WaveReceived(int dat) {
//        if (!serialPort.isConnected()) {
//            Log.d(TAG, "Ignoring SpO2 data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Dropped SpO2 data: Device disconnected");
//                return;
//            }
//            wfSpO2.addAmp(dat);
//            runOnUiThread(() -> wfSpO2.postInvalidate());
//        });
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Ignoring SpO2 data: Device disconnected");
//                return;
//            }
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
//            spannable.setSpan(new RelativeSizeSpan(2.8f), spo2ValueStart, spo2ValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.setSpan(new RelativeSizeSpan(2.8f), pulseValueStart, pulseValueEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            runOnUiThread(() -> {
//                tvSPO2info.setText(spannable);
//                tvSPO2info.setTypeface(Typeface.DEFAULT_BOLD);
//                tvSPO2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
//                wfSpO2.postInvalidate();
//            });
//        });
//    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        if (!serialPort.isConnected()) {
//            Log.d(TAG, "Ignoring ECG data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Dropped ECG data: Device disconnected");
//                return;
//            }
//
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            if (ecgDataBuffers.size() >= MAX_BUFFER_SIZE) {
//                ecgDataBuffers.removeFirst();
//            }
//            ecgDataBuffers.addLast(ecgData.clone());
//
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//            wfECG4.addAmp(ecgData[selectedECG[3]]);
//
//            if (ecgDialog != null && ecgDialog.isShowing()) {
//                if (wfLeadI != null) wfLeadI.addAmp(leadI);
//                if (wfLeadII != null) wfLeadII.addAmp(leadII);
//                if (wfLeadIII != null) wfLeadIII.addAmp(leadIII);
//                if (wfLeadAVR != null) wfLeadAVR.addAmp(aVR);
//                if (wfLeadAVL != null) wfLeadAVL.addAmp(aVL);
//                if (wfLeadAVF != null) wfLeadAVF.addAmp(aVF);
//                if (wfLeadV != null) wfLeadV.addAmp(vLead);
//            }
//
//            runOnUiThread(() -> {
//                wfECG1.postInvalidate();
//                wfECG2.postInvalidate();
//                wfECG3.postInvalidate();
//                wfECG4.postInvalidate();
//                if (ecgDialog != null && ecgDialog.isShowing()) {
//                    if (wfLeadI != null) wfLeadI.postInvalidate();
//                    if (wfLeadII != null) wfLeadII.postInvalidate();
//                    if (wfLeadIII != null) wfLeadIII.postInvalidate();
//                    if (wfLeadAVR != null) wfLeadAVR.postInvalidate();
//                    if (wfLeadAVL != null) wfLeadAVL.postInvalidate();
//                    if (wfLeadAVF != null) wfLeadAVF.postInvalidate();
//                    if (wfLeadV != null) wfLeadV.postInvalidate();
//                }
//            });
//        });
//    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
//        if (!serialPort.isConnected()) {
//            Log.d(TAG, "Ignoring Resp data: Device disconnected");
//            return;
//        }
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Dropped Resp data: Device disconnected");
//                return;
//            }
//            for (int i = 0; i < 3; i++) wfResp.addAmp(dat);
//            runOnUiThread(() -> wfResp.postInvalidate());
//        });
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Ignoring ECG data: Device disconnected");
//                return;
//            }
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
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Ignoring Temp data: Device disconnected");
//                return;
//            }
//            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
//        backgroundHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                Log.d(TAG, "Ignoring NIBP data: Device disconnected");
//                return;
//            }
//            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//        });
//    }
//
//    private void generateReport() {
//        final Handler handler = new Handler(Looper.getMainLooper());
//        final Runnable stopRecording = () -> {
//            int[][] allData = new int[7][];
//            if (!ecgDataBuffers.isEmpty()) {
//                int dataPoints = Math.min(500, ecgDataBuffers.size());
//                allData = new int[7][dataPoints];
//                for (int i = 0; i < 7; i++) {
//                    int[] tempData = new int[dataPoints];
//                    for (int j = 0; j < dataPoints; j++) {
//                        tempData[j] = ecgDataBuffers.toArray(new int[0][])[ecgDataBuffers.size() - 1 - j][i];
//                    }
//                    allData[i] = tempData;
//                }
//            } else {
//                for (int i = 0; i < 7; i++) allData[i] = new int[0];
//            }
//            generatePdfFromData(allData);
//        };
//
//        ecgDataBuffers.clear();
//        backgroundHandler.post(() -> {
//            handler.postDelayed(stopRecording, 10000);
//        });
//
//        runOnUiThread(() -> {
//            ProgressDialog progressDialog = new ProgressDialog(this);
//            progressDialog.setMessage("Generating PDF (10 seconds)...");
//            progressDialog.setCancelable(false);
//            progressDialog.show();
//            handler.postDelayed(() -> {
//                if (progressDialog.isShowing()) {
//                    progressDialog.dismiss();
//                }
//            }, 10000);
//        });
//    }
//
//    private void generatePdfFromData(int[][] ecgData) {
//        PdfDocument pdfDocument = new PdfDocument();
//        Paint paint = new Paint();
//        Paint title = new Paint();
//
//        PdfDocument.PageInfo myPageInfo = new PdfDocument.PageInfo.Builder(842, 595, 1).create();
//        PdfDocument.Page myPage = pdfDocument.startPage(myPageInfo);
//        Canvas canvas = myPage.getCanvas();
//
//        String patientName = "DixaMomo";
//        int patientAge = 40;
//        String gender = "Male";
//        String dateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
//        String hospital = "City hospital";
//        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? parseHeartRate(tvECGinfo.getText().toString()) : 87;
//        String pQRSAxis = "(66)(-249)(59) deg";
//        String qtC = "360 ms";
//        String prInterval = "148 ms";
//        String rrInterval = "996 ms";
//        String qrsDuration = "84 ms";
//        String spO2Value = (tvSPO2info != null && tvSPO2info.getText() != null) ? tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim() : "--";
//        String interpretation = "Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
//        String doctor = "Dr. Mangeshkar";
//
//        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        title.setTextSize(20);
//        title.setTextAlign(Paint.Align.CENTER);
//        title.setColor(Color.BLACK);
//        float headerX = 842 / 2;
//        canvas.drawText(hospital, headerX, 30, title);
//
//        title.setTextSize(12);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("Patient Name: " + patientName + "  Age: " + patientAge + "  Gender: " + gender, 20, 50, title);
//        canvas.drawText("Date: " + dateTime.split(" ")[0] + "  Time: " + dateTime.split(" ")[1], 20, 70, title);
//
//        title.setTextSize(24);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("HR: " + hr, 20, 100, title);
//
//        title.setTextSize(12);
//        canvas.drawText("P-QRS-T Axis: " + pQRSAxis + "  QTc: " + qtC + "  PR Interval: " + prInterval + "  RR Interval: " + rrInterval + "  QRS Duration: " + qrsDuration + "  SpO2: " + spO2Value + "%", 100, 100, title);
//
//        paint.setStrokeWidth(0.5f);
//        paint.setColor(Color.RED);
//        for (int y = 120; y < 595 - 20; y += 10) {
//            canvas.drawLine(20, y, 842 - 20, y, paint);
//        }
//        for (int x = 20; x < 842 - 20; x += 10) {
//            canvas.drawLine(x, 120, x, 595 - 20, paint);
//        }
//
//        int startY = 140;
//        int graphWidth = 140;
//        int graphHeight = 80;
//        String[] leadPairs = {"I-II", "III-aVR", "aVL-aVF", "V1", "V2", "V3"};
//        int[][] leadIndices = {{0, 1}, {2, 3}, {4, 5}, {6, -1}, {6, -1}, {6, -1}};
//
//        for (int row = 0; row < 6; row++) {
//            int[] indices = leadIndices[row];
//            for (int col = 0; col < 2; col++) {
//                if (indices[col] != -1 && ecgData[indices[col]].length > 0) {
//                    Bitmap bitmap = Bitmap.createBitmap(graphWidth, graphHeight, Bitmap.Config.ARGB_8888);
//                    Canvas bitmapCanvas = new Canvas(bitmap);
//                    bitmapCanvas.drawColor(Color.WHITE);
//
//                    Paint graphPaint = new Paint();
//                    graphPaint.setColor(Color.BLACK);
//                    graphPaint.setStrokeWidth(1);
//                    Path path = new Path();
//                    float pointSpacing = graphWidth / (float) Math.min(ecgData[indices[col]].length, 500);
//                    path.moveTo(0, graphHeight / 2);
//
//                    for (int j = 0; j < ecgData[indices[col]].length && j < 500; j++) {
//                        float x = j * pointSpacing;
//                        float y = graphHeight / 2 - (ecgData[indices[col]][j] * graphHeight / (2.0f * 500));
//                        if (j == 0) path.moveTo(x, y);
//                        else path.lineTo(x, y);
//                    }
//
//                    bitmapCanvas.drawPath(path, graphPaint);
//                    bitmapCanvas.drawText(leadPairs[row].split("-")[col], 5, graphHeight - 5, graphPaint);
//
//                    int xPos = 20 + col * (graphWidth + 10);
//                    int yPos = startY + row * (graphHeight + 10);
//                    canvas.drawBitmap(bitmap, xPos, yPos, null);
//                }
//            }
//        }
//
//        int finalY = startY + 6 * (graphHeight + 10);
//        title.setTextSize(12);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("Interpretation: " + interpretation, 20, finalY, title);
//        canvas.drawText("Dr. " + doctor, 20, finalY + 20, title);
//
//        pdfDocument.finishPage(myPage);
//
//        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "7_Lead_ECG_Report.pdf");
//        try {
//            pdfDocument.writeTo(new FileOutputStream(file));
//            Toast.makeText(this, "PDF file generated successfully.", Toast.LENGTH_SHORT).show();
//            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to generate PDF file.", Toast.LENGTH_SHORT).show();
//            Log.e(TAG, "Error saving PDF: " + e.getMessage());
//        }
//        pdfDocument.close();
//    }
//
//    private int parseHeartRate(String text) {
//        try {
//            return Integer.parseInt(text.split("\n")[0].replace("Heart Rate: ", "").trim());
//        } catch (Exception e) {
//            return 87;
//        }
//    }
//
//    private void connectToChesto() {
//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (bluetoothAdapter == null) {
//            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, 1);
//        }
//
//        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
//            if (device.getName() != null && device.getName().contains("Chesto")) {
//                bluetoothService.connectToDevice(device);
//                return;
//            }
//        }
//        Toast.makeText(this, "Chesto device not found", Toast.LENGTH_SHORT).show();
//    }
//
//    private void showEcgDialog() {
//        if (ecgDialog != null && ecgDialog.isShowing()) {
//            return;
//        }
//
//        ecgDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
//        ecgDialog.setContentView(R.layout.layout_seven_lead_popup);
//
//        wfLeadI = ecgDialog.findViewById(R.id.wfLeadI);
//        wfLeadII = ecgDialog.findViewById(R.id.wfLeadII);
//        wfLeadIII = ecgDialog.findViewById(R.id.wfLeadIII);
//        wfLeadAVR = ecgDialog.findViewById(R.id.wfLeadAVR);
//        wfLeadAVL = ecgDialog.findViewById(R.id.wfLeadAVL);
//        wfLeadAVF = ecgDialog.findViewById(R.id.wfLeadAVF);
//        wfLeadV = ecgDialog.findViewById(R.id.wfLeadV);
//
//        ImageView ivClosePopup = ecgDialog.findViewById(R.id.ivClosePopup);
//        ivClosePopup.setOnClickListener(v -> ecgDialog.dismiss());
//
//        ecgDialog.show();
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
//import android.app.Dialog;
//import android.app.ProgressDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.Typeface;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.Looper;
//import android.text.Spannable;
//import android.text.SpannableString;
//import android.text.style.RelativeSizeSpan;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.ImageView;
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
//import org.jetbrains.annotations.NotNull;
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
////    private TextView tvSerialInfo;
//    private TextView tvECGinfo;
//    private TextView tvSPO2info;
//    private TextView tvTEMPinfo;
//    private TextView tvNIBPinfo;
//    private TextView tvRespRate;
//    private LinearLayout llAbout;
////    private TextView tvFWVersion;
////    private TextView tvHWVersion;
//    private WaveformView wfSpO2;
//    private WaveformView wfECG1;
//    private WaveformView wfECG2;
//    private WaveformView wfECG3;
//    private WaveformView wfECG4;
//    private WaveformView wfResp;
//    private Spinner spinnerECG3;
//    private Button btnGenerateReport;
//    private Button chestoConnet;
//
//    private ImageView btnShowAllLeads;
//
//    private Dialog ecgDialog;
//    private WaveformView wfLeadI, wfLeadII, wfLeadIII, wfLeadAVR, wfLeadAVL, wfLeadAVF, wfLeadV;
//
//
////        private String[] ecgOptions = {"ECG I", "ECG II", "ECG III", "ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private String[] ecgOptions = {"ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
//    private int[] selectedECG = {0, 1, 2, 3};
//
//    private BerrySerialPort serialPort;
//    private DataParser mDataParser;
//    private ProgressDialog mConnectingDialog;
//    private HandlerThread backgroundThread;
//    private Handler backgroundHandler;
//
//
//    private BluetoothService bluetoothService;
//
//
//    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//    private static final int REQUEST_CODE = 100;
//
//    private BroadcastReceiver vitalStatusReceiver;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        bluetoothService = new BluetoothService();
//
//        initData();
//        initView();
//        initPermissions();
//        setupVitalStatusReceiver();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//
//        checkVitalStatus();
//
////        startGraphResetTimer();
//
//
//    }
//
//    private void startGraphResetTimer() {
//        Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                wfECG1.clear();
//                wfECG2.clear();
//                wfECG3.clear();
//                wfECG4.clear();
//                wfSpO2.clear();
//                wfResp.clear();
//                handler.postDelayed(this, 10000); // Repeat every 5 seconds
//            }
//        }, 5000);
//    }
//
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
//        btnSerialCtr.setText("Connect Vitals");
////        tvSerialInfo = findViewById(R.id.tvbtinfo);
//        tvECGinfo = findViewById(R.id.tvECGinfo);
//        tvSPO2info = findViewById(R.id.tvSPO2info);
//        tvTEMPinfo = findViewById(R.id.tvTEMPinfo);
//        tvNIBPinfo = findViewById(R.id.tvNIBPinfo);
//        llAbout = findViewById(R.id.llAbout);
////        tvFWVersion = findViewById(R.id.tvFWverison);
////        tvHWVersion = findViewById(R.id.tvHWverison);
//        wfECG1 = findViewById(R.id.wfECG1);
//        wfECG2 = findViewById(R.id.wfECG2);
//        wfECG3 = findViewById(R.id.wfECG3);
//
//        wfECG4 = findViewById(R.id.wfECG4);
//
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
//        tvRespRate = findViewById(R.id.tvRespRate);
//        btnGenerateReport = findViewById(R.id.btnGenerateReport);
//
//        btnShowAllLeads = findViewById(R.id.btnShowAllLeads);
//
//        chestoConnet = findViewById(R.id.chesto_connect_vt);
//
////        spinnerECG1 = findViewById(R.id.spinnerECG1);
////        spinnerECG2 = findViewById(R.id.spinnerECG2);
//        spinnerECG3 = findViewById(R.id.spinnerECG4);
//
////        setupDropdown(spinnerECG1, 0);
////        setupDropdown(spinnerECG2, 1);
//        setupDropdown(spinnerECG3, 3);
//
////        spinnerECG1.bringToFront();
////        spinnerECG1.setZ(100);
////        wfECG1.setZ(1);
//
//        mConnectingDialog = new ProgressDialog(this);
//        mConnectingDialog.setMessage("Connecting to Berry PM6750 (COM7/J42)...");
//        mConnectingDialog.setCancelable(false);
//
////        llAbout.setOnClickListener(v -> {
////            byte[] fwCmd = {0x05, 0x0A, 0x04, (byte) 0xFC, 0x00, (byte) 0xFD};
////            byte[] hwCmd = {0x05, 0x0A, 0x04, (byte) 0xFD, 0x00, (byte) 0xFE};
////            serialPort.write(fwCmd);
////            serialPort.write(hwCmd);
////        });
//
//        btnGenerateReport.setOnClickListener(v -> {
//            backgroundHandler.post(() -> generateReport());
//        });
//
//        chestoConnet.setOnClickListener(v -> {
//            if (chestoConnet.getText().toString().equals("Connect")) {
//                connectToChesto();
////                bluetoothService.connectToDevice();
//            } else {
//                try {
//                    bluetoothService.disconnect();
//                } catch (Exception e) {
//                    Log.d("ChestoDeviceActivity", "Disconnect failed: " + e.getMessage());
//                    Toast.makeText(this, "Disconnect failed", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
////
//
//        btnShowAllLeads.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                showEcgDialog();
//            }
//        });
//    }
//
//
//    private void setupVitalStatusReceiver() {
//        vitalStatusReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                long vitalStatus = intent.getLongExtra("vital_status", 0);
//                if (vitalStatus == 1) {
//                    GlobalVars.setVitalOn(true);
//                    connectSerialPort();
//                } else {
//                    GlobalVars.setVitalOn(false);
//                    disconnectSerialPort();
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(vitalStatusReceiver, filter);
//    }
//
//    private void checkVitalStatus() {
//        if (GlobalVars.isVitalOn) {
//            connectSerialPort();
//        } else {
//            disconnectSerialPort();
//        }
//    }
//
//    private void connectSerialPort() {
//        if (!serialPort.isConnected()) {
//            runOnUiThread(() -> mConnectingDialog.show());
//            serialPort.connect();
//        }
//    }
//
//    private void disconnectSerialPort() {
//        if (serialPort.isConnected()) {
//            serialPort.disconnect();
//        }
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
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
//    }
//
//    private void clearGraph(int index) {
//        if (index == 0) wfECG1.clear();
//        if (index == 1) wfECG2.clear();
//        if (index == 2) wfECG3.clear();
//        if (index == 3) wfECG4.clear();
////        if (index == 4) wfaVR.clear();
////        if (index == 5) wfaVL.clear();
////        if (index == 6) wfaVF.clear();
////        if (index == 7) wfaV.clear();
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
//        if (vitalStatusReceiver != null) {
//            unregisterReceiver(vitalStatusReceiver);
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
////            tvSerialInfo.setText(status);
//            if (status.startsWith("Connected")) {
//                btnSerialCtr.setText("Disconnect");
//                if (mConnectingDialog.isShowing()) {
//                    mConnectingDialog.dismiss();
//                }
//            } else {
//                btnSerialCtr.setText("Connect Vitals");
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
//                wfSpO2.postInvalidate();
//            });
//        });
//    }
//
//    private List<int[]> ecgDataBuffers = new ArrayList<>(); // Store 10 seconds data for 7 leads
//    private static final int DATA_COLLECTION_DURATION_MS = 10000; // 10 seconds
//
////    @Override
////    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//////        Log.d(TAG, "📈 ECG Wave: I=" + leadI + ", II=" + leadII + ", III=" + leadIII);
////        backgroundHandler.post(() -> {
////            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
////            ecgDataBuffers.add(ecgData.clone()); // Store all 7 leads
////            wfECG1.addAmp(ecgData[selectedECG[0]]);
////            wfECG2.addAmp(ecgData[selectedECG[1]]);
////            wfECG3.addAmp(ecgData[selectedECG[2]]);
////            wfECG4.addAmp(ecgData[selectedECG[3]]);
////            runOnUiThread(() -> {
////                wfECG1.postInvalidate();
////                wfECG2.postInvalidate();
////                wfECG3.postInvalidate();
////                wfECG4.postInvalidate();
////            });
////        });
////    }
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        backgroundHandler.post(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            ecgDataBuffers.add(ecgData.clone());
//
//            // ✅ Main screen graph updates (4 leads)
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//            wfECG4.addAmp(ecgData[selectedECG[3]]);
//
//            // ✅ Popup screen graph updates (all 7 leads)
//            if (ecgDialog != null && ecgDialog.isShowing()) {
//                if (wfLeadI != null) wfLeadI.addAmp(leadI);
//                if (wfLeadII != null) wfLeadII.addAmp(leadII);
//                if (wfLeadIII != null) wfLeadIII.addAmp(leadIII);
//                if (wfLeadAVR != null) wfLeadAVR.addAmp(aVR);
//                if (wfLeadAVL != null) wfLeadAVL.addAmp(aVL);
//                if (wfLeadAVF != null) wfLeadAVF.addAmp(aVF);
//                if (wfLeadV != null) wfLeadV.addAmp(vLead);
//            }
//
//            runOnUiThread(() -> {
//                wfECG1.postInvalidate();
//                wfECG2.postInvalidate();
//                wfECG3.postInvalidate();
//                wfECG4.postInvalidate();
//
//                // ✅ Invalidate popup graphs too
//                if (ecgDialog != null && ecgDialog.isShowing()) {
//                    if (wfLeadI != null) wfLeadI.postInvalidate();
//                    if (wfLeadII != null) wfLeadII.postInvalidate();
//                    if (wfLeadIII != null) wfLeadIII.postInvalidate();
//                    if (wfLeadAVR != null) wfLeadAVR.postInvalidate();
//                    if (wfLeadAVL != null) wfLeadAVL.postInvalidate();
//                    if (wfLeadAVF != null) wfLeadAVF.postInvalidate();
//                    if (wfLeadV != null) wfLeadV.postInvalidate();
//                }
//            });
//        });
//    }
//
//
//
////    @Override
////    public void onECGAllWaveReceived(int leadI) {
////        wfLeadI.addAmp(leadI);
////    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
////        backgroundHandler.post(() -> wfResp.addAmp(dat));
//        backgroundHandler.post(() -> {
//            // 3x repeat for smooth scroll
//            for (int i = 0; i < 3; i++) wfResp.addAmp(dat);
////            wfResp.addAmp(dat);
////            wfResp.addAmp(dat);
//            wfResp.postInvalidate();
//        });
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
////    @Override
////    public void onFirmwareReceived(String str) {
////        backgroundHandler.post(() -> {
////            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
////        });
////    }
////
////    @Override
////    public void onHardwareReceived(String str) {
////        backgroundHandler.post(() -> {
////            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
////        });
////    }
//
//    private void generateReport() {
//        final Handler handler = new Handler(Looper.getMainLooper());
//        final Runnable stopRecording = () -> {
//            int[][] allData = new int[7][];
//            if (!ecgDataBuffers.isEmpty()) {
//                int dataPoints = Math.min(500, ecgDataBuffers.size()); // 500 points for 10 seconds
//                allData = new int[7][dataPoints];
//                for (int i = 0; i < 7; i++) {
//                    int[] tempData = new int[dataPoints];
//                    for (int j = 0; j < dataPoints; j++) {
//                        tempData[j] = ecgDataBuffers.get(ecgDataBuffers.size() - 1 - j)[i];
//                    }
//                    allData[i] = tempData;
//                }
//            } else {
//                for (int i = 0; i < 7; i++) allData[i] = new int[0];
//            }
//            generatePdfFromData(allData);
//        };
//
//        // Clear previous data and collect 10 seconds
//        ecgDataBuffers.clear();
//        backgroundHandler.post(() -> {
//            handler.postDelayed(stopRecording, 10000); // 10 seconds delay
//        });
//
//        runOnUiThread(() -> {
//            ProgressDialog progressDialog = new ProgressDialog(this);
//            progressDialog.setMessage("Generating PDF (10 seconds)...");
//            progressDialog.setCancelable(false);
//            progressDialog.show();
//            handler.postDelayed(() -> {
//                if (progressDialog.isShowing()) {
//                    progressDialog.dismiss();
//                }
//            }, 10000);
//        });
//    }
//
//    private void generatePdfFromData(int[][] ecgData) {
//        PdfDocument pdfDocument = new PdfDocument();
//        Paint paint = new Paint();
//        Paint title = new Paint();
//
//        PdfDocument.PageInfo myPageInfo = new PdfDocument.PageInfo.Builder(842, 595, 1).create(); // Landscape (A4)
//        PdfDocument.Page myPage = pdfDocument.startPage(myPageInfo);
//        Canvas canvas = myPage.getCanvas();
//
//        // Patient and measurement data from UI
//        String patientName = "DixaMomo"; // Replace with actual patient data
//        int patientAge = 40; // Replace with actual data
//        String gender = "Male"; // Replace with actual data
//        String dateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
//        String hospital = "City hospital";
//        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? Integer.parseInt(tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim()) : 87; // Updated from latest image
//        String pQRSAxis = "(66)(-249)(59) deg"; // Replace with actual data
//        String qtC = "360 ms"; // Replace with actual data
//        String prInterval = "148 ms"; // Replace with actual data
//        String rrInterval = "996 ms"; // Replace with actual data
//        String qrsDuration = "84 ms"; // Replace with actual data
//        String spO2Value = (tvSPO2info != null && tvSPO2info.getText() != null) ? tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim() : "--";
//        String interpretation = "Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
//        String doctor = "Dr. Mangeshkar";
//
//        // Header (Centered)
//        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        title.setTextSize(20);
//        title.setTextAlign(Paint.Align.CENTER);
//        title.setColor(Color.BLACK);
//        float headerX = 842 / 2;
//        canvas.drawText(hospital, headerX, 30, title);
//
//        title.setTextSize(12);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("Patient Name: " + patientName + "  Age: " + patientAge + "  Gender: " + gender, 20, 50, title);
//        canvas.drawText("Date: " + dateTime.split(" ")[0] + "  Time: " + dateTime.split(" ")[1], 20, 70, title);
//
//        // Measurements (Single line with HR large)
//        title.setTextSize(24);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("HR: " + hr, 20, 100, title);
//
//        title.setTextSize(12);
//        canvas.drawText("P-QRS-T Axis: " + pQRSAxis + "  QTc: " + qtC + "  PR Interval: " + prInterval + "  RR Interval: " + rrInterval + "  QRS Duration: " + qrsDuration + "  SpO2: " + spO2Value + "%", 100, 100, title);
//
//        // Dense Grid lines (5mm spacing)
//        paint.setStrokeWidth(0.5f);
//        paint.setColor(Color.RED);
//        for (int y = 120; y < 595 - 20; y += 10) {
//            canvas.drawLine(20, y, 842 - 20, y, paint);
//        }
//        for (int x = 20; x < 842 - 20; x += 10) {
//            canvas.drawLine(x, 120, x, 595 - 20, paint); // Corrected vertical lines
//        }
//
//        // Graphs for 7 leads in 6 rows, 2 columns
//        int startY = 140;
//        int graphWidth = 140;
//        int graphHeight = 80;
//        String[] leadPairs = {"I-II", "III-aVR", "aVL-aVF", "V1", "V2", "V3"};
//        int[][] leadIndices = {{0, 1}, {2, 3}, {4, 5}, {6, -1}, {6, -1}, {6, -1}}; // Adjusted for 7th lead
//
//        for (int row = 0; row < 6; row++) {
//            int[] indices = leadIndices[row];
//            for (int col = 0; col < 2; col++) {
//                if (indices[col] != -1 && ecgData[indices[col]].length > 0) {
//                    Bitmap bitmap = Bitmap.createBitmap(graphWidth, graphHeight, Bitmap.Config.ARGB_8888);
//                    Canvas bitmapCanvas = new Canvas(bitmap);
//                    bitmapCanvas.drawColor(Color.WHITE);
//
//                    Paint graphPaint = new Paint();
//                    graphPaint.setColor(Color.BLACK);
//                    graphPaint.setStrokeWidth(1);
//                    Path path = new Path();
//                    float pointSpacing = graphWidth / (float) Math.min(ecgData[indices[col]].length, 500);
//                    path.moveTo(0, graphHeight / 2);
//
//                    for (int j = 0; j < ecgData[indices[col]].length && j < 500; j++) {
//                        float x = j * pointSpacing;
//                        float y = graphHeight / 2 - (ecgData[indices[col]][j] * graphHeight / (2.0f * 500)); // Normalize
//                        if (j == 0) path.moveTo(x, y);
//                        else path.lineTo(x, y);
//                    }
//
//                    bitmapCanvas.drawPath(path, graphPaint);
//                    bitmapCanvas.drawText(leadPairs[row].split("-")[col], 5, graphHeight - 5, graphPaint);
//
//                    int xPos = 20 + col * (graphWidth + 10);
//                    int yPos = startY + row * (graphHeight + 10);
//                    canvas.drawBitmap(bitmap, xPos, yPos, null);
//                }
//            }
//        }
//
//        // Interpretation
//        int finalY = startY + 6 * (graphHeight + 10);
//        title.setTextSize(12);
//        title.setTextAlign(Paint.Align.LEFT);
//        canvas.drawText("Interpretation: " + interpretation, 20, finalY, title);
//        canvas.drawText("Dr. " + doctor, 20, finalY + 20, title);
//
//        pdfDocument.finishPage(myPage);
//
//        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "7_Lead_ECG_Report.pdf");
//        try {
//            pdfDocument.writeTo(new FileOutputStream(file));
//            Toast.makeText(this, "PDF file generated successfully.", Toast.LENGTH_SHORT).show();
//            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to generate PDF file.", Toast.LENGTH_SHORT).show();
//            Log.e(TAG, "Error saving PDF: " + e.getMessage());
//        }
//        pdfDocument.close();
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02X ", b));
//        }
//        return sb.toString().trim();
//    }
//
//
//    private void connectToChesto() {
//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (bluetoothAdapter == null) {
//            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, 1);
//        }
//
//        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
//            if (device.getName() != null && device.getName().contains("Chesto")) {
//                bluetoothService.connectToDevice(device);
//                return;
//            }
//        }
//        Toast.makeText(this, "Chesto device not found", Toast.LENGTH_SHORT).show();
//    }
//
//
////
//
//
//    private void showEcgDialog() {
//        if (ecgDialog != null && ecgDialog.isShowing()) {
//            return; // already showing
//        }
//
//        ecgDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
//        ecgDialog.setContentView(R.layout.layout_seven_lead_popup);
//
//        // Bind all 7 waveform views
//        wfLeadI = ecgDialog.findViewById(R.id.wfLeadI);
//        wfLeadII = ecgDialog.findViewById(R.id.wfLeadII);
//        wfLeadIII = ecgDialog.findViewById(R.id.wfLeadIII);
//        wfLeadAVR = ecgDialog.findViewById(R.id.wfLeadAVR);
//        wfLeadAVL = ecgDialog.findViewById(R.id.wfLeadAVL);
//        wfLeadAVF = ecgDialog.findViewById(R.id.wfLeadAVF);
//        wfLeadV = ecgDialog.findViewById(R.id.wfLeadV);
//
//        // Close icon
//        ImageView ivClosePopup = ecgDialog.findViewById(R.id.ivClosePopup);
//        ivClosePopup.setOnClickListener(v -> ecgDialog.dismiss());
//
//        ecgDialog.show();
//    }
//
//}


//package com.lztek.api.demo;
//
//import android.Manifest;
//import android.app.ProgressDialog;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.Typeface;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.Looper;
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
//    private WaveformView wfaVR,wfaVL,wfaVF,wfaV;
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
//    private BroadcastReceiver vitalStatusReceiver;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_berry_device);
//
//        initData();
//        initView();
//        initPermissions();
//        setupVitalStatusReceiver();
//
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//
//        // Check initial vital status
//        checkVitalStatus();
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
////            Log.d("hum", "Generate Report button clicked");
//            backgroundHandler.post(() -> generateReport());
//        });
//    }
//
//    private void setupVitalStatusReceiver() {
//        vitalStatusReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                long vitalStatus = intent.getLongExtra("vital_status", 0);
////                Log.d(TAG, "📡 Received VITAL status: " + vitalStatus);
//                if (vitalStatus == 1) {
//                    GlobalVars.setVitalOn(true);
//                    connectSerialPort();
//                } else {
//                    GlobalVars.setVitalOn(false);
//                    disconnectSerialPort();
//                }
//            }
//        };
//        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
//        registerReceiver(vitalStatusReceiver, filter);
//    }
//
//    private void checkVitalStatus() {
//        if (GlobalVars.isVitalOn) {
////            Log.d(TAG, "🌡️ Initial check: isVitalOn=true, connecting...");
//            connectSerialPort();
//        } else {
////            Log.d(TAG, "🌡️ Initial check: isVitalOn=false, disconnecting...");
//            disconnectSerialPort();
//        }
//    }
//
//    private void connectSerialPort() {
//        if (!serialPort.isConnected()) {
////            Log.d(TAG, "🔌 Attempting to connect BerrySerialPort");
//            runOnUiThread(() -> mConnectingDialog.show());
//            serialPort.connect();
//        } else {
////            Log.d(TAG, "🔌 BerrySerialPort already connected");
//        }
//    }
//
//    private void disconnectSerialPort() {
//        if (serialPort.isConnected()) {
////            Log.d(TAG, "🔌 Attempting to disconnect BerrySerialPort");
//            serialPort.disconnect();
//        } else {
////            Log.d(TAG, "🔌 BerrySerialPort already disconnected");
//        }
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
//        if (index == 3) wfaVR.clear();
//        if (index == 4) wfaVL.clear();
//        if (index == 5) wfaVF.clear();
//        if (index == 6) wfaV.clear();
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
//        if (vitalStatusReceiver != null) {
//            unregisterReceiver(vitalStatusReceiver);
//        }
//        finish();
//    }
//
//    @Override
//    public void onDataReceived(byte[] data) {
////        Log.d(TAG, "📡 onDataReceived: " + bytesToHex(data));
//        backgroundHandler.post(() -> mDataParser.add(data));
//    }
//
//    @Override
//    public void onConnectionStatusChanged(String status) {
////        Log.d(TAG, "🔌 Connection Status: " + status);
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
////        Log.d(TAG, "📈 SpO2 Wave: " + dat);
//        backgroundHandler.post(() -> wfSpO2.addAmp(dat));
//    }
//
//    @Override
//    public void onSpO2Received(SpO2 spo2) {
////        Log.d(TAG, "📊 SpO2 Data: " + spo2.toString());
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
//                wfSpO2.postInvalidate(); // Force redraw
//            });
//        });
//    }
//
//    private List<int[]> ecgDataBuffers = new ArrayList<>(); // Store 10 seconds data for 7 leads
//    private static final int DATA_COLLECTION_DURATION_MS = 10000; // 10 seconds
//
//    @Override
//    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
//        Log.d(TAG, "📈 ECG Wave: I=" + leadI + ", II=" + leadII + ", III=" + leadIII);
//        backgroundHandler.post(() -> {
//            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
//            // Store all 7 leads data
//            ecgDataBuffers.add(ecgData.clone()); // Clone to avoid reference issues
//            // Map to UI WaveformViews based on dropdown
//            wfECG1.addAmp(ecgData[selectedECG[0]]);
//            wfECG2.addAmp(ecgData[selectedECG[1]]);
//            wfECG3.addAmp(ecgData[selectedECG[2]]);
//            runOnUiThread(() -> {
//                wfECG1.postInvalidate();
//                wfECG2.postInvalidate();
//                wfECG3.postInvalidate();
//            });
//        });
//    }
//
////    @Override
////    public void onECGWaveReceived(int leadI, int leadII, int leadIII, int aVR, int aVL, int aVF, int vLead) {
////        Log.d(TAG, "📈 ECG Wave: I=" + leadI + ", II=" + leadII + ", III=" + leadIII);
////        backgroundHandler.post(() -> {
////            int[] ecgData = {leadI, leadII, leadIII, aVR, aVL, aVF, vLead};
////            wfECG1.addAmp(ecgData[selectedECG[0]]);
////            wfECG2.addAmp(ecgData[selectedECG[1]]);
////            wfECG3.addAmp(ecgData[selectedECG[2]]);
////            runOnUiThread(() -> {
////                wfECG1.postInvalidate(); // Force redraw
////                wfECG2.postInvalidate();
////                wfECG3.postInvalidate();
////            });
////        });
////    }
//
//    @Override
//    public void onRespWaveReceived(int dat) {
////        Log.d(TAG, "📈 Resp Wave: " + dat);
//        backgroundHandler.post(() -> wfResp.addAmp(dat));
//    }
//
//    @Override
//    public void onECGReceived(ECG ecg) {
////        Log.d(TAG, "📊 ECG Data: " + ecg.toString());
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
////        Log.d(TAG, "🌡️ Temp Data: " + temp.toString());
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvTEMPinfo.setText(temp.toString()));
//        });
//    }
//
//    @Override
//    public void onNIBPReceived(NIBP nibp) {
////        Log.d(TAG, "🩺 NIBP Data: " + nibp.toString());
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvNIBPinfo.setText(nibp.toString()));
//        });
//    }
//
//    @Override
//    public void onFirmwareReceived(String str) {
////        Log.d(TAG, "📟 Firmware Version: " + str);
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvFWVersion.setText("Firmware Version: " + str));
//        });
//    }
//
//    @Override
//    public void onHardwareReceived(String str) {
////        Log.d(TAG, "🔧 Hardware Version: " + str);
//        backgroundHandler.post(() -> {
//            runOnUiThread(() -> tvHWVersion.setText("Hardware Version: " + str));
//        });
//    }
//
//
//    private void generateReport() {
//        Log.d(TAG, "Starting PDF generation with PdfDocument");
//
//        // Clear previous data
//        ecgDataBuffers.clear();
//
//        // Start data collection for 10 seconds
//        final Handler handler = new Handler(Looper.getMainLooper());
//        final Runnable stopRecording = () -> {
//            // Get the last 10 seconds of data
//            int[][] allData = new int[7][];
//            if (!ecgDataBuffers.isEmpty()) {
//                int dataPoints = Math.min(500, ecgDataBuffers.size()); // 500 points for 10 seconds (adjust as per sampling rate)
//                for (int i = 0; i < 7; i++) {
//                    int[] tempData = new int[dataPoints];
//                    for (int j = 0; j < dataPoints; j++) {
//                        tempData[j] = ecgDataBuffers.get(ecgDataBuffers.size() - 1 - j)[i];
//                    }
//                    allData[i] = tempData;
//                }
//            } else {
//                for (int i = 0; i < 7; i++) allData[i] = new int[0];
//            }
//            generatePdfFromData(allData);
//        };
//
//        // Collect data for 10 seconds
//        backgroundHandler.post(() -> {
//            wfECG1.postInvalidate();
//            wfECG2.postInvalidate();
//            wfECG3.postInvalidate();
//            handler.postDelayed(stopRecording, 10000); // 10 seconds delay
//        });
//
//        // Show progress dialog (non-blocking)
//        runOnUiThread(() -> {
//            ProgressDialog progressDialog = new ProgressDialog(this);
//            progressDialog.setMessage("Generating PDF (10 seconds)...");
//            progressDialog.setCancelable(false);
//            progressDialog.show();
//            handler.postDelayed(() -> {
//                if (progressDialog.isShowing()) {
//                    progressDialog.dismiss();
//                }
//            }, 10000);
//        });
//    }
//
//    private void generatePdfFromData(int[][] ecgData) {
//        // Debug logs
//        Log.d(TAG, "ECG1 data size: " + ecgData[0].length + ", Values: " + java.util.Arrays.toString(ecgData[0]));
//        Log.d(TAG, "ECG7 (V1) data size: " + ecgData[6].length + ", Values: " + java.util.Arrays.toString(ecgData[6]));
//
//        // Sample patient and measurement data (replace with actual data if available)
//        String patientName = "prosim";
//        int patientAge = 40;
//        String gender = "Male";
//        String dateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
//        String hospital = "City Hospital";
//        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? Integer.parseInt(tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim()) : 60;
//        String spO2Value = (tvSPO2info != null && tvSPO2info.getText() != null) ? tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim() : "N/A";
//        String pQRSAxis = "(66)(-249)(59) deg";
//        String qtC = "360 ms";
//        String prInterval = "148 ms";
//        String rrInterval = "996 ms";
//        String qrsDuration = "84 ms";
//        String interpretation = "Sinus Rhythm. PR is normal. Normal QRS Width. Normal QT Interval. QRS Axis is indeterminate.";
//        String doctor = "Dr. Mangeshkar";
//
//        // Create Bitmap for each graph with higher resolution
//        int bitmapWidth = 700; // Adjusted for grid
//        int bitmapHeight = 80; // Adjusted for grid
//        Bitmap[] bitmaps = new Bitmap[7];
//        Canvas[] canvases = new Canvas[7];
//        Paint paint = new Paint();
//        paint.setColor(Color.BLACK);
//        paint.setStrokeWidth(1);
//        paint.setStyle(Paint.Style.STROKE);
//
//        String[] leadTitles = {"I", "II", "III", "aVR", "aVL", "aVF", "V1"};
//        WaveformView[] waveformViews = {wfECG1, wfECG2, wfECG3, null, null, null, null};
//
//        for (int i = 0; i < 7; i++) {
//            bitmaps[i] = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
//            canvases[i] = new Canvas(bitmaps[i]);
//            canvases[i].drawColor(Color.WHITE);
//
//            if (ecgData[i].length > 0) {
//                Path path = new Path();
//                float pointSpacing = bitmapWidth / (float) Math.max(1, Math.min(ecgData[i].length, 500));
//                path.moveTo(0, bitmapHeight / 2);
//
//                for (int j = 0; j < ecgData[i].length && j < 500; j++) {
//                    float x = j * pointSpacing;
//                    float y = bitmapHeight / 2 - (ecgData[i][j] * bitmapHeight / (2.0f * (waveformViews[0] != null ? waveformViews[0].getMaxValue() : 1000)));
//                    if (j == 0) path.moveTo(x, y);
//                    else path.lineTo(x, y);
//                }
//
//                canvases[i].drawPath(path, paint);
//                paint.setTextSize(12);
//                canvases[i].drawText(leadTitles[i], 5, bitmapHeight - 5, paint); // Label at bottom
//            }
//        }
//
//        // Create PDF with high resolution
//        PdfDocument document = new PdfDocument();
//        int pageWidth = 842; // A4 height for landscape
//        int pageHeight = 595; // A4 width for landscape
//        int margin = 20;
//        int startY = margin;
//
//        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageHeight, pageWidth, 1).create();
//        PdfDocument.Page page = document.startPage(pageInfo);
//        Canvas pdfCanvas = page.getCanvas();
//
//        // Header
//        paint.setTextSize(16);
//        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
//        pdfCanvas.drawText(hospital, margin, startY, paint);
//        startY += 20;
//
//        paint.setTextSize(12);
//        pdfCanvas.drawText("Patient Name: " + patientName + "  Age: " + patientAge + "  Gender: " + gender, margin, startY, paint);
//        pdfCanvas.drawText("Date: " + dateTime.split(" ")[0] + "  Time: " + dateTime.split(" ")[1], margin + 300, startY, paint);
//        startY += 20;
//
//        // Measurements
//        paint.setTextSize(12);
//        pdfCanvas.drawText("HR: " + hr + "  P-QRS-T Axis: " + pQRSAxis + "  QTc: " + qtC, margin, startY, paint);
//        pdfCanvas.drawText("PR Interval: " + prInterval + "  RR Interval: " + rrInterval + "  QRS Duration: " + qrsDuration, margin + 300, startY, paint);
//        startY += 20;
//        pdfCanvas.drawText("SpO2: " + spO2Value + "%", margin, startY, paint);
//        startY += 30;
//
//        // Draw grid lines (ECG paper effect)
//        paint.setStrokeWidth(0.5f);
//        for (int y = startY; y < pageHeight - margin; y += 20) {
//            pdfCanvas.drawLine(margin, y, pageHeight - margin, y, paint);
//        }
//        for (int x = margin; x < pageWidth - margin; x += 20) {
//            pdfCanvas.drawLine(x, startY, x, pageHeight - margin, paint);
//        }
//
//        // Graphs in grid layout (2-2-2-1)
//        int graphsPerRow = 2;
//        int currentRow = 0;
//        int currentCol = 0;
//        startY += 10; // Start below grid lines
//        for (int i = 0; i < 7; i++) {
//            if (bitmaps[i] != null) {
//                int xPos = margin + currentCol * (bitmapWidth / 2 + 10); // Half width for scaling
//                int yPos = startY + currentRow * (bitmapHeight + 10);
//                if (xPos + bitmapWidth / 2 > pageHeight - margin) {
//                    currentCol = 0;
//                    currentRow++;
//                    yPos = startY + currentRow * (bitmapHeight + 10);
//                }
//                // Scale bitmap to fit grid
//                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmaps[i], bitmapWidth / 2, bitmapHeight, true);
//                pdfCanvas.drawBitmap(scaledBitmap, xPos, yPos, null);
//
//                currentCol++;
//                if (currentCol >= graphsPerRow) {
//                    currentCol = 0;
//                    currentRow++;
//                }
//            }
//        }
//
//        // Interpretation
//        startY = startY + (currentRow + 1) * (bitmapHeight + 10);
//        paint.setTextSize(12);
//        pdfCanvas.drawText("Interpretation: " + interpretation, margin, startY, paint);
//        startY += 20;
//        pdfCanvas.drawText(doctor, margin, startY, paint);
//
//        document.finishPage(page);
//
//        // Save PDF with higher quality
//        File file;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
//                    "7_Lead_ECG_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
//        } else {
//            file = new File(Environment.getExternalStorageDirectory(),
//                    "7_Lead_ECG_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
//        }
//        try (FileOutputStream fos = new FileOutputStream(file)) {
//            document.writeTo(fos);
//            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
//        } catch (IOException e) {
//            Log.e(TAG, "Error saving PDF: " + e.getMessage());
//            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show());
//        } finally {
//            document.close();
//        }
//    }
//
//
////    private void generateReport() {
////        Log.d(TAG, "Starting PDF generation with PdfDocument");
////
////        // Clear previous data
////        ecgDataBuffers.clear();
////
////        // Start data collection for 10 seconds
////        final Handler handler = new Handler(Looper.getMainLooper());
////        final Runnable stopRecording = () -> {
////            // Get the last 10 seconds of data
////            int[][] allData = new int[7][];
////            if (!ecgDataBuffers.isEmpty()) {
////                int dataPoints = Math.min(500, ecgDataBuffers.size());
////                for (int i = 0; i < 7; i++) {
////                    int[] tempData = new int[dataPoints];
////                    for (int j = 0; j < dataPoints; j++) {
////                        tempData[j] = ecgDataBuffers.get(ecgDataBuffers.size() - 1 - j)[i];
////                    }
////                    allData[i] = tempData;
////                }
////            } else {
////                for (int i = 0; i < 7; i++) allData[i] = new int[0];
////            }
////            generatePdfFromData(allData);
////        };
////
////        // Collect data for 10 seconds
////        backgroundHandler.post(() -> {
////            wfECG1.postInvalidate();
////            wfECG2.postInvalidate();
////            wfECG3.postInvalidate();
////            handler.postDelayed(stopRecording, DATA_COLLECTION_DURATION_MS);
////        });
////
////        // Show progress dialog (non-blocking)
////        runOnUiThread(() -> {
////            ProgressDialog progressDialog = new ProgressDialog(this);
////            progressDialog.setMessage("Generating PDF (10 seconds)...");
////            progressDialog.setCancelable(false);
////            progressDialog.show();
////            handler.postDelayed(() -> {
////                if (progressDialog.isShowing()) {
////                    progressDialog.dismiss();
////                }
////            }, DATA_COLLECTION_DURATION_MS);
////        });
////    }
////
////    private void generatePdfFromData(int[][] ecgData) {
////        // Debug logs
////        Log.d(TAG, "ECG1 data size: " + ecgData[0].length + ", Values: " + java.util.Arrays.toString(ecgData[0]));
////        Log.d(TAG, "ECG4 (aVR) data size: " + ecgData[3].length + ", Values: " + java.util.Arrays.toString(ecgData[3]));
////
////        // Get values for labels
////        String spO2Value = (tvSPO2info != null && tvSPO2info.getText() != null)
////                ? tvSPO2info.getText().toString().split("\n")[0].replace("SpO2: ", "").trim()
////                : "N/A";
////        String heartRateValue = (tvECGinfo != null && tvECGinfo.getText() != null)
////                ? tvECGinfo.getText().toString().split("\n")[0].replace("Heart Rate: ", "").trim()
////                : "N/A";
////        String dateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a").format(new Date());
////
////        // Create Bitmap for each graph
////        int bitmapWidth = 500;
////        int bitmapHeight = 80;
////        Bitmap[] bitmaps = new Bitmap[7];
////        Canvas[] canvases = new Canvas[7];
////        Paint paint = new Paint();
////        paint.setColor(Color.BLACK);
////        paint.setStrokeWidth(1);
////        paint.setStyle(Paint.Style.STROKE);
////
////        String[] leadTitles = {"Lead I", "Lead II", "Lead III", "aVR", "aVL", "aVF", "V1"};
////        WaveformView[] waveformViews = {wfECG1, wfECG2, wfECG3, null, null, null, null};
////
////        for (int i = 0; i < 7; i++) {
////            bitmaps[i] = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
////            canvases[i] = new Canvas(bitmaps[i]);
////            canvases[i].drawColor(Color.WHITE);
////
////            if (ecgData[i].length > 0) {
////                Path path = new Path();
////                float pointSpacing = bitmapWidth / (float) Math.max(1, Math.min(ecgData[i].length, 500));
////                path.moveTo(0, bitmapHeight / 2);
////
////                for (int j = 0; j < ecgData[i].length && j < 500; j++) {
////                    float x = j * pointSpacing;
////                    float y = bitmapHeight / 2 - (ecgData[i][j] * bitmapHeight / (2.0f * (waveformViews[0] != null ? waveformViews[0].getMaxValue() : 1000)));
////                    if (j == 0) path.moveTo(x, y);
////                    else path.lineTo(x, y);
////                }
////
////                canvases[i].drawPath(path, paint);
////                paint.setTextSize(12);
////                canvases[i].drawText(leadTitles[i], 0, 15, paint);
////            }
////        }
////
////        // Create PDF
////        PdfDocument document = new PdfDocument();
////        int pageWidth = 595; // A4 width in points
////        int pageHeight = 842; // A4 height in points
////        int margin = 50;
////        int startY = 80;
////
////        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
////        PdfDocument.Page page = document.startPage(pageInfo);
////        Canvas pdfCanvas = page.getCanvas();
////        paint.setTextSize(18);
////        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
////        pdfCanvas.drawText("7-Lead ECG Report", margin, 30, paint);
////        paint.setTextSize(12);
////        pdfCanvas.drawText(dateTime, margin, 50, paint);
////        pdfCanvas.drawText("SpO2: " + spO2Value + "%  Heart Rate: " + heartRateValue + " bpm", pageWidth - margin - 200, 50, paint);
////
////        // Draw bitmaps on PDF
////        for (int i = 0; i < 7; i++) {
////            if (bitmaps[i] != null) {
////                pdfCanvas.drawBitmap(bitmaps[i], margin, startY, null);
////                startY += bitmapHeight + 10;
////            }
////        }
////
////        paint.setTextSize(8);
////        pdfCanvas.drawText("Scale: 25 mm/s, 10 mm/mV", margin, startY + 30, paint);
////
////        document.finishPage(page);
////
////        // Save PDF
////        File file;
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
////            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
////                    "7_Lead_ECG_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
////        } else {
////            file = new File(Environment.getExternalStorageDirectory(),
////                    "7_Lead_ECG_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
////        }
////        try (FileOutputStream fos = new FileOutputStream(file)) {
////            document.writeTo(fos);
////            Log.d(TAG, "PDF saved at: " + file.getAbsolutePath());
////            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "PDF saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
////        } catch (IOException e) {
////            Log.e(TAG, "Error saving PDF: " + e.getMessage());
////            runOnUiThread(() -> Toast.makeText(BerryDeviceActivity.this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show());
////        } finally {
////            document.close();
////        }
////    }
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
