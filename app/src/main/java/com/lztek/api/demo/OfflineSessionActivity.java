package com.lztek.api.demo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.lztek.toolkit.Lztek;
import com.lztek.toolkit.SerialPort;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OfflineSessionActivity extends AppCompatActivity implements BerrySerialPort.OnDataReceivedListener,DataParser.onPackageReceivedListener {

    private static final String TAG = "BerryDeviceActivity";
    private static final int MAX_BUFFER_SIZE = 5000; // 10 seconds at 500 Hz for ECG
    private static final int SPO2_BUFFER_MAX_SIZE = 1000; // 10 seconds at 100 Hz for SpO2
    private static final int REQUEST_CODE = 100;
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 999;
    private static final long BUFFER_CLEANUP_INTERVAL_MS = 10_000; // 10 seconds

    private static final String[] TEMP_SERIAL_PORTS = {"/dev/ttyS9", "/dev/ttyUSB0", "/dev/ttyACM0"};
    private static final int TEMP_BAUD_RATE = 115200;
    private static final int TEMP_STRUCT_SIZE = 40; // Assumed packet size
    private static final long TEMP_UPDATE_INTERVAL_MS = 500; // Update UI every 500ms
    private static final long TEMP_DATA_TIMEOUT_MS = 2000; // Data older than 2s is stale
    private static final float TEMP_MIN_CELSIUS = 20.0f; // Min valid temperature
    private static final float TEMP_MAX_CELSIUS = 45.0f; // Max valid temperature
    private static final int TEMP_SMOOTHING_WINDOW = 5; // Smooth over 5 readings


    private static final int SAMPLE_RATE = 6000; // Chesto ke specs ke hisaab se 6000 Hz
    private int audioPosition = 0; // Track playback position for resume
    private byte[] audioData = null; // Store audio data for multiple playbacks
    private long lastClickTime = 0; // For debouncing play/pause clicks
    private static final long DEBOUNCE_DELAY = 300; // 300ms debounce delay

    private ActivityResultLauncher<Intent> audioPickerLauncher;


//    json
    private final List<NIBP> nibpBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> respRateBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Float> tempBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> pulseRateBuffer = Collections.synchronizedList(new ArrayList<>());
    private AppDatabase database;
    private SessionDao sessionDao;
    private long sessionId;


    // UI Elements
    private Button btnSerialCtr;
    private TextView tvECGinfo;
    private TextView tvSPO2info;
    private TextView tvTEMPinfo;
    private TextView tvTEMP2info; // External temperature TextView
    private TextView tvNIBPinfo;
    private TextView tvRespRate;
    private WaveformView wfSpO2;
    private WaveformView wfECG1;
    private WaveformView wfECG2;
    private WaveformView wfECG3;
    private WaveformView wfECG4;
//    private WaveformView wfResp;
//    private Spinner spinnerECG3;
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

//
    private CheckBox CbCaptureTempData;
    private CheckBox CbCaptureNiBpData;
    private CheckBox CbCaptureSpo2Data;
    private CheckBox CbCaptureRespData;
    private CheckBox CbCaptureBpmPluseData;

    private Button cam1Button;
    private Button cam2Button;

    private TextView hrDrate, respDrate, stDlevel, arDcode;

    private ImageButton vitalScreenRecording;
    private TextView vitalScreenTimmerRecording;

    // Thread Handling
    private HandlerThread vitalThread;
    private Handler vitalHandler;
    private HandlerThread generalThread;
    private Handler generalHandler;
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService;
    private final Handler bufferCleanupHandler = new Handler(Looper.getMainLooper());
    private HandlerThread tempThread; // Thread for temperature device
    private Handler tempHandler; // Handler for temperature device
    private HandlerThread audioThread;
    private Handler audioHandler;

    // Screen Recording
    private MediaProjectionManager projectionManager;
    private boolean isScreenRecording = false;
    private Handler screenTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable screenTimerRunnable;
    private int screenRecordSeconds = 0;

    // Timer
    private Runnable timerRunnable;
    private int secondsElapsed = 0;

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
    private Lztek mLztek; // Lztek instance for temperature device
    private SerialPort mTempSerialPort; // Serial port for temperature device
    private volatile boolean isTempRunning = false; // Control temperature reading loop
    private final List<BerryDeviceActivity.TemperatureReading> tempSmoothingBuffer = Collections.synchronizedList(new ArrayList<>()); // Smoothing buffer with timestamps
    private volatile boolean isTempConnected = false; // Track sensor connection status
    private final Handler tempUpdateHandler = new Handler(Looper.getMainLooper()); // For periodic UI updates

    // NIBP Auto Trigger
    private Handler autoTriggerHandler = new Handler();
    private Runnable autoTriggerRunnable;
    private int intervalInMillis = 0;

    // ECG Options
    private String[] ecgOptions = {"ECG aVR", "ECG aVL", "ECG aVF", "ECG V"};
    private int[] selectedECG = {0, 1, 2, 3};

    private String pateintName = "DixaMomo";
    private String monitorName = "CNRGI Remote Patient Monitoring Solution";

    // Permissions
    private String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

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

    // Periodic Temperature UI Update
    private final Runnable tempUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (tempSmoothingBuffer) {
                // Remove stale readings (older than 2 seconds)
                long currentTime = System.currentTimeMillis();
                tempSmoothingBuffer.removeIf(reading -> (currentTime - reading.timestamp) > TEMP_DATA_TIMEOUT_MS);

                if (!isTempConnected || tempSmoothingBuffer.isEmpty()) {
                    tvTEMP2info.setText("-- °C / -- °F");
                    Log.d(TAG, "No live temperature data or sensor disconnected");
                } else {
                    float sum = 0.0f;
                    for (BerryDeviceActivity.TemperatureReading reading : tempSmoothingBuffer) {
                        sum += reading.temperature;
                    }
                    float avgCelsius = sum / tempSmoothingBuffer.size();
                    float fahrenheit = avgCelsius * 9 / 5 + 32;
                    tvTEMP2info.setText(String.format("%.1f °C / %.1f °F", avgCelsius, fahrenheit));
                    tvTEMP2info.setTypeface(Typeface.DEFAULT_BOLD);
                    tvTEMP2info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    Log.d(TAG, "UI Updated - Temperature: " + avgCelsius + " °C / " + fahrenheit + " °F");
                }
            }
            tempUpdateHandler.postDelayed(this, TEMP_UPDATE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_session);
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

        audioThread = new HandlerThread("AudioThread", Thread.NORM_PRIORITY);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        // Initialize temperature serial port
        initTempSerialPort();

        // Initialize MediaProjectionManager for screen recording
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedAudioUri = result.getData().getData();
                        if (selectedAudioUri != null) {
                            Log.d(TAG, "Selected audio URI: " + selectedAudioUri);
                            Toast.makeText(this, "Audio selected: " + selectedAudioUri, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
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
                runOnUiThread(() -> Toast.makeText(OfflineSessionActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void initTempSerialPort() {
        mLztek = Lztek.create(getApplicationContext());
        tempThread = new HandlerThread("TempThread", Thread.NORM_PRIORITY);
        tempThread.start();
        tempHandler = new Handler(tempThread.getLooper());

        isTempConnected = false;
        for (String port : TEMP_SERIAL_PORTS) {
            try {
                mTempSerialPort = mLztek.openSerialPort(port, TEMP_BAUD_RATE, 8, 0, 1, 0);
                InputStream tempInputStream = mTempSerialPort.getInputStream();
                isTempRunning = true;
                isTempConnected = true;
                tempHandler.post(() -> readTempSerialData(tempInputStream));
                Log.d(TAG, "Temperature Serial Port " + port + " Opened Successfully");
                break;
            } catch (Exception e) {
                Log.w(TAG, "Failed to open port " + port + ": " + e.getMessage());
            }
        }

        if (!isTempConnected) {
            Log.e(TAG, "No valid temperature serial port found");
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to open temperature device", Toast.LENGTH_LONG).show();
                tvTEMP2info.setText("-- °C / -- °F");
            });
            return;
        }

        tempUpdateHandler.post(tempUpdateRunnable);
    }

    private void readTempSerialData(InputStream inputStream) {
        byte[] buffer = new byte[1024]; // Larger buffer for robustness
        ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream();

        while (isTempRunning && inputStream != null) {
            try {
                int availableBytes = inputStream.available();
                if (availableBytes > 0) {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        packetBuffer.write(buffer, 0, bytesRead);
//                        Log.d(TAG, "Received " + bytesRead + " bytes from temperature port");

                        // Process complete packets
                        byte[] accumulatedData = packetBuffer.toByteArray();
                        while (accumulatedData.length >= TEMP_STRUCT_SIZE) {
                            byte[] packet = new byte[TEMP_STRUCT_SIZE];
                            System.arraycopy(accumulatedData, 0, packet, 0, TEMP_STRUCT_SIZE);
                            processTempPacket(packet);

                            // Remove processed packet
                            byte[] remaining = new byte[accumulatedData.length - TEMP_STRUCT_SIZE];
                            if (remaining.length > 0) {
                                System.arraycopy(accumulatedData, TEMP_STRUCT_SIZE, remaining, 0, remaining.length);
                            }
                            packetBuffer.reset();
                            packetBuffer.write(remaining);
                            accumulatedData = packetBuffer.toByteArray();
                        }
                    }
                } else {
                    Thread.sleep(50); // Match SerialPortActivity efficiency
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading temperature serial port: " + e.getMessage());
                isTempConnected = false;
                runOnUiThread(() -> tvTEMP2info.setText("-- °C / -- °F"));
                break;
            } catch (InterruptedException e) {
                Log.e(TAG, "Temperature reading interrupted: " + e.getMessage());
                isTempConnected = false;
                runOnUiThread(() -> tvTEMP2info.setText("-- °C / -- °F"));
                break;
            }
        }
        Log.d(TAG, "Temperature reading loop stopped");
        isTempConnected = false;
        runOnUiThread(() -> tvTEMP2info.setText("-- °C / -- °F"));
    }

    private void processTempPacket(byte[] packet) {
        // Log raw packet for debugging
        StringBuilder hexString = new StringBuilder();
        for (byte b : packet) {
            hexString.append(String.format("%02X ", b));
        }
//        Log.d(TAG, "Temperature Packet: " + hexString.toString());

        try {
            BerryDeviceActivity.TemperatureData tempData = new BerryDeviceActivity.TemperatureData(packet);
            float celsius = tempData.temperature / 100.0f; // Scale by 100 for hundredths

            // Validate and add to smoothing buffer
            if (celsius >= TEMP_MIN_CELSIUS && celsius <= TEMP_MAX_CELSIUS) {
                synchronized (tempSmoothingBuffer) {
                    tempSmoothingBuffer.add(new BerryDeviceActivity.TemperatureReading(celsius, System.currentTimeMillis()));
                    if (tempSmoothingBuffer.size() > TEMP_SMOOTHING_WINDOW) {
                        tempSmoothingBuffer.remove(0);
                    }
//                    Log.d(TAG, "Valid Temperature: " + celsius + " °C (Raw: " + tempData.temperature + ")");
                }
            } else {
//                Log.w(TAG, "Invalid Temperature: " + celsius + " °C (Raw: " + tempData.temperature + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing temperature packet: " + e.getMessage());
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

    private void initView() {
        btnSerialCtr = findViewById(R.id.off_btnBtCtr);
        btnSerialCtr.setText("Connect Vitals");
        tvECGinfo = findViewById(R.id.off_tvECGinfo);
        tvSPO2info = findViewById(R.id.off_tvSPO2info);
        tvTEMPinfo = findViewById(R.id.off_tvTEMPinfo);
        tvTEMP2info = findViewById(R.id.off_tvTEMP2info);
        tvNIBPinfo = findViewById(R.id.off_tvNIBPinfo);
        wfECG1 = findViewById(R.id.wfECG1);
        wfECG2 = findViewById(R.id.wfECG2);
        wfECG3 = findViewById(R.id.wfECG3);
        wfECG4 = findViewById(R.id.wfECG4);
//        wfSpO2 = findViewById(R.id.wfSpO2);
//        wfResp = findViewById(R.id.wfResp);
        tvRespRate = findViewById(R.id.off_tvRespRate);
        btnGenerateReport = findViewById(R.id.off_btnGenerateReport);
        btnShowAllLeads = findViewById(R.id.off_btnShowAllLeads);
//        spinnerECG3 = findViewById(R.id.off_spinnerECG4);
        nibpStopButton = findViewById(R.id.off_btnNIBPStop);
        nibp5MinButton = findViewById(R.id.off_nibp5minbtn);
        nibp15MinButton = findViewById(R.id.off_nibp15minbtn);
        nibp30MinButton = findViewById(R.id.off_nibp30minbtn);

        chestoConnett = findViewById(R.id.off_chesto_connect_vt);
        liveChesto = findViewById(R.id.off_live_chesto);
        recordChestoo = findViewById(R.id.off_record_chesto);
        recordingTimer = findViewById(R.id.off_recording_timer);
        playOrstop = findViewById(R.id.off_play_or_stop);
        refreshAudio = findViewById(R.id.off_refresh_audio);
//        uploadAudio = findViewById(R.id.upload_audio);

        audioGrpahContainer = findViewById(R.id.off_audio_graph_container);
        cAudioGraph = findViewById(R.id.off_AudioGraph);

        vitalScreenRecording = findViewById(R.id.off_vitalScreenRecording);
        vitalScreenTimmerRecording = findViewById(R.id.off_vitalRecoTimmer);

//
        cam1Button = findViewById(R.id.off_cam1_button);
        cam2Button = findViewById(R.id.off_cam2_button);
//
        CbCaptureTempData = findViewById(R.id.cbCaptureTempData);
        CbCaptureNiBpData = findViewById(R.id.cbCaptureNiBpData);
        CbCaptureSpo2Data = findViewById(R.id.cbCaptureSpo2Data);
        CbCaptureRespData = findViewById(R.id.cbCaptureRespData);
        CbCaptureBpmPluseData = findViewById(R.id.cbCaptureBpmPluseData);

        setupGraph();
        requestUsbPermission();
//        setupDropdown(spinnerECG3, 3);
//        String dateTimeNow = getCurrentDateTime();

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
                synchronized (plotBuffer) {
                    plotBuffer.clear();
                }
                Toast.makeText(this, "Stopped Listening", Toast.LENGTH_SHORT).show();
                graphUpdateHandler.removeCallbacks(graphUpdater);
            }
        });


        // Cam1 Button (Rear Camera)
        cam1Button.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineSessionActivity.this, CameraActivity.class);
            intent.putExtra("camera_id", "0");
            intent.putExtra("camera_name", "Rear Camera");
            startActivity(intent);
        });

        // Cam2 Button (Front Camera)
        cam2Button.setOnClickListener(v -> {
            Intent intent = new Intent(OfflineSessionActivity.this, CameraActivity.class);
            intent.putExtra("camera_id", "1");
            intent.putExtra("camera_name", "Front Camera");
            startActivity(intent);
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
                startTimer();
            } else {
                isRecording = false;
                recordChestoo.setBackgroundResource(R.drawable.play);
                stopTimer();
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

        playOrstop.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < DEBOUNCE_DELAY) {
                return; // Ignore rapid clicks
            }
            lastClickTime = currentTime;

            if (!isPlaying) {
                playRecordedAudio();
            } else {
                pauseRecordedAudio();
            }
        });

        refreshAudio.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < DEBOUNCE_DELAY) {
                return; // Ignore rapid clicks
            }
            lastClickTime = currentTime;

            executorService.execute(() -> {
                try {
                    File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recorded_audio.wav");
                    if (file.exists()) {
                        if (!file.delete()) {
                            Log.w(TAG, "Failed to delete audio file: " + file.getAbsolutePath());
                            uiUpdateHandler.post(() ->
                                    Toast.makeText(this, "Failed to delete audio file", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        Log.d(TAG, "Audio file deleted: " + file.getAbsolutePath());
                    }

                    audioHandler.post(() -> {
                        synchronized (this) {
                            if (audioTrack != null) {
                                try {
                                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ||
                                            audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                                        audioTrack.stop();
                                    }
                                    audioTrack.release();
                                    audioTrack = null;
                                    audioPosition = 0; // Reset playback position
                                    audioData = null; // Clear audio data
                                } catch (IllegalStateException e) {
                                    Log.e(TAG, "AudioTrack release failed: " + e.getMessage());
                                }
                                isPlaying = false;
                                uiUpdateHandler.post(() -> {
                                    playOrstop.setBackgroundResource(R.drawable.play);
                                    Toast.makeText(this, "Audio refreshed", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });

                    synchronized (audioBuffer) {
                        audioBuffer.reset();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Refresh failed: " + e.getMessage());
                    uiUpdateHandler.post(() ->
                            Toast.makeText(this, "Refresh failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        vitalScreenRecording.setOnClickListener(v -> {
            if (!isScreenRecording) {
                Intent captureIntent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
            } else {
                stopScreenRecording();
            }
        });

//

        // Initialize Room database
        database = AppDatabase.getDatabase(this);
        sessionDao = database.sessionDao();

        // Get session ID from intent as Long
        sessionId = getIntent().getLongExtra("session_id", -1L); // Retrieve as Long and cast to int if needed
//        if (sessionId == -1) {
//            Log.e(TAG, "Invalid session ID");
//            Toast.makeText(this, "Invalid session ID", Toast.LENGTH_SHORT).show();
//            finish();
//            return;
//        }

        // Set checkbox listeners
        CbCaptureTempData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                saveVitalsToJson();
            }
        });

        CbCaptureNiBpData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                saveVitalsToJson();
            }
        });

        CbCaptureSpo2Data.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                saveVitalsToJson();
            }
        });

        CbCaptureRespData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                saveVitalsToJson();
            }
        });

        CbCaptureBpmPluseData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                saveVitalsToJson();
            }
        });

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    // Method to extract last 10 seconds of data and save as JSON
    private void saveVitalsToJson() {
        executorService.execute(() -> {
            int heartRate = -1;
            int spo2 = -1;
            int respRate = -1;
            int pulseRate = -1;
            float tempCelsius = -1.0f;
            float tempFahrenheit = -1.0f;
            int nibpSystolic = -1;
            int nibpDiastolic = -1;

            // Heart Rate (from ecgDataBuffers indirectly via tvECGinfo)
            String heartRateText = tvECGinfo.getText().toString();
            try {
                heartRate = Integer.parseInt(heartRateText.split("\n")[0].replace("Heart Rate: ", "").trim());
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse heart rate: " + e.getMessage());
            }

            // SpO2 and Pulse Rate
            synchronized (spo2Buffer) {
                if (!spo2Buffer.isEmpty()) {
                    spo2 = spo2Buffer.get(spo2Buffer.size() - 1); // Latest value
                }
            }
            synchronized (pulseRateBuffer) {
                if (!pulseRateBuffer.isEmpty()) {
                    pulseRate = pulseRateBuffer.get(pulseRateBuffer.size() - 1); // Latest value
                }
            }

            // Resp Rate
            synchronized (respRateBuffer) {
                if (!respRateBuffer.isEmpty()) {
                    respRate = respRateBuffer.get(respRateBuffer.size() - 1); // Latest value
                }
            }

            // Temperature
            synchronized (tempBuffer) {
                if (!tempBuffer.isEmpty()) {
                    tempCelsius = tempBuffer.get(tempBuffer.size() - 1); // Latest value
                    tempFahrenheit = tempCelsius * 9 / 5 + 32;
                }
            }

            // NIBP
            synchronized (nibpBuffer) {
                if (!nibpBuffer.isEmpty()) {
                    NIBP latestNIBP = nibpBuffer.get(nibpBuffer.size() - 1);
                    nibpSystolic = latestNIBP.getSystolic();
                    nibpDiastolic = latestNIBP.getDiastolic();
                }
            }

            // Create JSON
            StringBuilder jsonBuilder = new StringBuilder("{");
            jsonBuilder.append("\"heart_rate\":").append(heartRate != -1 ? heartRate : "null").append(",");
            jsonBuilder.append("\"spo2\":").append(spo2 != -1 ? spo2 : "null").append(",");
            jsonBuilder.append("\"resp_rate\":").append(respRate != -1 ? respRate : "null").append(",");
            jsonBuilder.append("\"pulse_rate\":").append(pulseRate != -1 ? pulseRate : "null").append(",");
            jsonBuilder.append("\"temp_celsius\":").append(tempCelsius != -1.0f ? tempCelsius : "null").append(",");
            jsonBuilder.append("\"temp_fahrenheit\":").append(tempFahrenheit != -1.0f ? tempFahrenheit : "null").append(",");
            jsonBuilder.append("\"nibp_systolic\":").append(nibpSystolic != -1 ? nibpSystolic : "null").append(",");
            jsonBuilder.append("\"nibp_diastolic\":").append(nibpDiastolic != -1 ? nibpDiastolic : "null");
            jsonBuilder.append("}");

            String vitalJson = jsonBuilder.toString();
            Log.d(TAG, "Vitals JSON: " + vitalJson);

            // Save to Room database
            Session session = sessionDao.getSessionById(sessionId);
            if (session != null) {
                session.vitalJson = vitalJson;
                sessionDao.update(session);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Vitals saved to database", Toast.LENGTH_SHORT).show();
                });
            } else {
                Log.e(TAG, "Session not found for ID: " + sessionId);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: Session not found", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent serviceIntent = new Intent(this, ScreenRecordingService.class);
            serviceIntent.setAction("START_RECORDING");
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            startForegroundService(serviceIntent);
            isScreenRecording = true;
            vitalScreenRecording.setImageResource(R.drawable.stop_recording);
            startScreenTimer();
            Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String filePath = getFilePathFromUri(uri);
                uiUpdateHandler.post(() ->
                        Toast.makeText(this, "Audio uploading: " + filePath, Toast.LENGTH_LONG).show());
                Log.d(TAG, "Selected audio file: " + filePath);
            } else {
                uiUpdateHandler.post(() ->
                        Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show());
            }
        } else if (requestCode == 1 && resultCode == RESULT_OK) {
            connectToChesto();
        }
    }

    private void openCameraPreview(String cameraId, String cameraName) {
        CameraPreviewDialogFragment dialogFragment = CameraPreviewDialogFragment.newInstance(cameraId, cameraName);
        dialogFragment.show(getSupportFragmentManager(), "CameraPreviewDialog");
    }

    private String findUsbCameraId() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                // Camera ID "0" is usually the back camera, "1" is front, and higher IDs might be external (USB)
                // This is a simple heuristic; adjust based on your device
                if (!id.equals("0") && !id.equals("1")) {
                    return id; // Assume this is the USB camera
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding USB camera: " + e.getMessage());
        }
        return null;
    }

    private void startScreenTimer() {
        screenRecordSeconds = 0;
        screenTimerRunnable = new Runnable() {
            @Override
            public void run() {
                int mins = screenRecordSeconds / 60;
                int secs = screenRecordSeconds % 60;
                vitalScreenTimmerRecording.setText(String.format("%02d:%02d", mins, secs));
                screenRecordSeconds++;
                screenTimerHandler.postDelayed(this, 1000);
            }
        };
        screenTimerHandler.post(screenTimerRunnable);
    }

    private void requestUsbPermission() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice device = null;
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            if (usbDevice.getInterfaceCount() > 0 && usbDevice.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_VIDEO) {
                device = usbDevice;
                break;
            }
        }
        if (device != null) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.lztek.api.demo.USB_PERMISSION"), 0);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    private void stopScreenTimer() {
        screenTimerHandler.removeCallbacks(screenTimerRunnable);
        vitalScreenTimmerRecording.setText("00:00");
    }

    private void stopScreenRecording() {
        Intent serviceIntent = new Intent(this, ScreenRecordingService.class);
        serviceIntent.setAction("STOP_RECORDING");
        startService(serviceIntent);
        isScreenRecording = false;
        vitalScreenRecording.setImageResource(R.drawable.vital_screen_recorder);
        stopScreenTimer();
        Toast.makeText(this, "Screen recording stopped", Toast.LENGTH_SHORT).show();
    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = "";
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                ContentResolver resolver = getContentResolver();
                Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        filePath = cursor.getString(index);
                    }
                    cursor.close();
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                filePath = new File(uri.getPath()).getAbsolutePath();
            }
            if (filePath.isEmpty()) {
                filePath = uri.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get file path: " + e.getMessage());
            filePath = "Unknown path";
        }
        return filePath;
    }

    private void startTimer() {
        secondsElapsed = 0;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                int hours = secondsElapsed / 3600;
                int minutes = (secondsElapsed % 3600) / 60;
                int seconds = secondsElapsed % 60;
                String timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                recordingTimer.setText(timeFormatted);
                secondsElapsed++;
                uiUpdateHandler.postDelayed(this, 1000);
            }
        };
        uiUpdateHandler.post(timerRunnable);
    }

    private void stopTimer() {
        uiUpdateHandler.removeCallbacks(timerRunnable);
    }

    private void playRecordedAudio() {
        if (audioData == null) {
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recorded_audio.wav");
            if (!file.exists()) {
                uiUpdateHandler.post(() ->
                        Toast.makeText(this, "No audio file found!", Toast.LENGTH_SHORT).show());
                return;
            }

            executorService.execute(() -> {
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.skip(44); // Skip WAV header
                    audioData = new byte[(int) (file.length() - 44)];
                    fis.read(audioData);

                    playAudioData();
                } catch (IOException e) {
                    Log.e(TAG, "Play failed: " + e.getMessage());
                    uiUpdateHandler.post(() ->
                            Toast.makeText(this, "Playback failed: Unable to read audio file", Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            playAudioData();
        }
    }

    private void playAudioData() {
        audioHandler.post(() -> {
            synchronized (this) {
                try {
                    // Reinitialize AudioTrack if null, uninitialized, or playback is complete
                    if (audioTrack == null || audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED || audioPosition >= audioData.length) {
                        if (audioTrack != null) {
                            audioTrack.release();
                            audioTrack = null;
                        }
                        audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                audioData.length,
                                AudioTrack.MODE_STREAM
                        );
                        audioPosition = 0; // Reset position for new playback
                    }

                    if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PAUSED) {
                            audioTrack.play();
                        }

                        if (audioPosition < audioData.length) {
                            audioTrack.write(audioData, audioPosition, audioData.length - audioPosition);
                        }

                        isPlaying = true;
                        uiUpdateHandler.post(() -> {
                            playOrstop.setBackgroundResource(R.drawable.pause);
                            Toast.makeText(this, "Playing audio...", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "AudioTrack play failed: " + e.getMessage());
                    uiUpdateHandler.post(() ->
                            Toast.makeText(this, "Playback failed: Audio device may be busy", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void pauseRecordedAudio() {
        audioHandler.post(() -> {
            synchronized (this) {
                if (audioTrack != null) {
                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioPosition = audioTrack.getPlaybackHeadPosition() * 2; // Bytes for 16-bit mono
                        audioTrack.pause();
                        isPlaying = false;
                        uiUpdateHandler.post(() -> {
                            playOrstop.setBackgroundResource(R.drawable.play);
                            Toast.makeText(this, "Audio paused", Toast.LENGTH_SHORT).show();
                        });
                    } else if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED || audioTrack.getPlayState() == AudioTrack.PLAYSTATE_STOPPED) {
                        isPlaying = false;
                        uiUpdateHandler.post(() -> {
                            playOrstop.setBackgroundResource(R.drawable.play);
                            Toast.makeText(this, "Audio paused", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        uiUpdateHandler.post(() ->
                                Toast.makeText(this, "No audio available", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    uiUpdateHandler.post(() ->
                            Toast.makeText(this, "No audio available", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void saveAudioToFile(byte[] recordedData) {
        try {
            // Save to public Music folder via MediaStore
            ContentValues values = new ContentValues();
            String fileName = "recorded_audio_" + System.currentTimeMillis() + ".wav";
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Failed to create MediaStore entry");
            }

            try (OutputStream os = resolver.openOutputStream(uri)) {
                byte[] header = getWavHeader(recordedData.length, SAMPLE_RATE);
                os.write(header);
                os.write(recordedData);
                os.flush();
            }

            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);

            // Save a copy to app-specific storage for playback
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recorded_audio.wav");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] header = getWavHeader(recordedData.length, SAMPLE_RATE);
                fos.write(header);
                fos.write(recordedData);
                fos.flush();
            }

            uiUpdateHandler.post(() ->
                    Toast.makeText(this, "Audio saved to Music folder and app storage", Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save audio: " + e.getMessage());
            uiUpdateHandler.post(() ->
                    Toast.makeText(this, "Failed to save audio: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
        header[30] = (byte) ((byte) (byteRate >> 16) & 0xff);
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
        // Create a LineDataSet for the audio signal
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), ""); // Label hata diya
        dataSet.setDrawCircles(false);  // Circles pehle se disabled hain, rakh rahe hain
        dataSet.setDrawValues(false);   // Values pehle se disabled hain, rakh rahe hain
        dataSet.setColor(Color.BLUE);   // Line ka color blue hi rakhenge
        dataSet.setLineWidth(1f);       // Line ki width same rakhi

        // Create LineData with the dataset
        LineData lineData = new LineData(dataSet);
        cAudioGraph.setData(lineData);

        // Disable all labels and grid lines for a clean look
        cAudioGraph.getXAxis().setEnabled(false);  // X-axis completely disable (no labels, no grid)
        cAudioGraph.getAxisLeft().setEnabled(false);  // Left Y-axis disable (no labels, no grid)
        cAudioGraph.getAxisRight().setEnabled(false);  // Right Y-axis disable (no labels, no grid)
        cAudioGraph.getDescription().setEnabled(false);  // Graph description disable
        cAudioGraph.getLegend().setEnabled(false);  // Legend (label "Audio Signal") disable

        // Enable hardware acceleration for smoother rendering
        cAudioGraph.setHardwareAccelerationEnabled(true);

        // Refresh the graph
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
//            wfECG1.clear();
//            wfECG2.clear();
//            wfECG3.clear();
//            wfECG4.clear();
////            wfResp.clear();
////            wfSpO2.clear();
//            wfECG1.postInvalidate();
//            wfECG2.postInvalidate();
//            wfECG3.postInvalidate();
//            wfECG4.postInvalidate();
//            wfResp.postInvalidate();
//            wfSpO2.postInvalidate();
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
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
            case R.id.off_btnBtCtr:
                if (!serialPort.isConnected()) {
                    mConnectingDialog.show();
                    connectSerialPort();
                } else {
                    disconnectSerialPort();
                }
                break;
            case R.id.off_btnNIBPStart:
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
        if (tempThread != null) {
            isTempRunning = false;
            tempThread.quitSafely();
            tempThread = null;
        }
        if (mTempSerialPort != null) {
            try {
                mTempSerialPort.close();
                mTempSerialPort = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing temperature serial port", e);
            }
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
        tempUpdateHandler.removeCallbacksAndMessages(null);
        tempSmoothingBuffer.clear();
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
//                            wfSpO2.addAmp(dat);
                        }
                        spo2Buffer.clear();
//                        wfSpO2.postInvalidate();
                        Log.d(TAG, "SpO2 batch update: added " + spo2Buffer.size() + " points");
                    }
                }
                uiUpdateHandler.postDelayed(this, 3000);
            }
        }, 3000);
    }

    private void startECGBatchUpdates() {
        uiUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (ecgBatchBuffer) {
                    if (!ecgBatchBuffer.isEmpty()) {
                        for (int[] ecgData : ecgBatchBuffer) {
//                            wfECG1.addAmp(ecgData[selectedECG[0]]);
//                            wfECG2.addAmp(ecgData[selectedECG[1]]);
//                            wfECG3.addAmp(ecgData[selectedECG[2]]);
//                            wfECG4.addAmp(ecgData[selectedECG[3]]);
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
//                            wfECG1.postInvalidate();
//                            wfECG2.postInvalidate();
//                            wfECG3.postInvalidate();
//                            wfECG4.postInvalidate();
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
                uiUpdateHandler.postDelayed(this, 3000);
            }
        }, 3000);
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

            // Add to buffers
            synchronized (spo2Buffer) {
                if (spo2.getSpO2() != SpO2.SPO2_INVALID) {
                    spo2Buffer.add(spo2.getSpO2());
                    if (spo2Buffer.size() > SPO2_BUFFER_MAX_SIZE) {
                        spo2Buffer.subList(0, spo2Buffer.size() - SPO2_BUFFER_MAX_SIZE).clear();
                    }
                }
            }
            synchronized (pulseRateBuffer) {
                if (spo2.getPulseRate() != SpO2.PULSE_RATE_INVALID) {
                    pulseRateBuffer.add(spo2.getPulseRate());
                    if (pulseRateBuffer.size() > SPO2_BUFFER_MAX_SIZE) {
                        pulseRateBuffer.subList(0, pulseRateBuffer.size() - SPO2_BUFFER_MAX_SIZE).clear();
                    }
                }
            }

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
//        if (!serialPort.isConnected()) {
//            return;
//        }
//        vitalHandler.post(() -> {
//            if (!serialPort.isConnected()) {
//                return;
//            }
//            for (int i = 0; i < 3; i++) wfResp.addAmp(dat);
//            runOnUiThread(() -> {
//                wfResp.postInvalidate();
//                Log.d(TAG, "Resp data received: " + dat);
//            });
//        });
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

            // Add to buffers
            synchronized (respRateBuffer) {
                if (ecg.getRestRate() != ecg.RESP_RATE_INVALID) {
                    respRateBuffer.add(ecg.getRestRate());
                    if (respRateBuffer.size() > 1000) { // 10 seconds at 100 Hz
                        respRateBuffer.subList(0, respRateBuffer.size() - 1000).clear();
                    }
                }
            }

            runOnUiThread(() -> {
                tvECGinfo.setText(heartRateSpannable);
                tvECGinfo.setTypeface(Typeface.DEFAULT_BOLD);
                tvECGinfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvRespRate.setText(respRateSpannable);
                tvRespRate.setTypeface(Typeface.DEFAULT_BOLD);
                tvRespRate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                if (ecgDialog != null && ecgDialog.isShowing()) {
                    hrDrate.setText(heartRateValue);
                    hrDrate.setTypeface(Typeface.DEFAULT_BOLD);
                    hrDrate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                    respDrate.setText(respRateValue);
                    respDrate.setTypeface(Typeface.DEFAULT_BOLD);
                    respDrate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                    stDlevel.setText(stLevelValue);
                    stDlevel.setTypeface(Typeface.DEFAULT_BOLD);
                    stDlevel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                    arDcode.setText(arrhyValue);
                    arDcode.setTypeface(Typeface.DEFAULT_BOLD);
                    arDcode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                }
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
            // Add to nibpBuffer
            synchronized (nibpBuffer) {
                nibpBuffer.add(nibp);
                if (nibpBuffer.size() > 100) { // Keep last 100 readings (adjust as needed)
                    nibpBuffer.subList(0, nibpBuffer.size() - 100).clear();
                }
            }

            runOnUiThread(() -> {
                tvNIBPinfo.setText(nibp.toString());
            });
        });
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure to end session?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    super.onBackPressed(); // Close the activity
                    finish(); // Ensure activity is finished
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss()) // Keep the activity open
                .setCancelable(false) // Prevent back press to dismiss dialog
                .show();
    }

    private void generateReport() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable stopRecording = () -> {
            final int[][] allData;
            synchronized (ecgDataBuffers) {
                if (!ecgDataBuffers.isEmpty()) {
                    int dataPoints = Math.min(2500, ecgDataBuffers.size()); // 10 sec at 250 Hz
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

        String patientName = pateintName;
        int patientAge = 40;
        String reportNumber = "ECG-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String deviceName = monitorName;
        String dateTime = "06 Jun 2025 03:00 PM"; // Updated to match the given date and time
        int hr = (tvECGinfo != null && tvECGinfo.getText() != null) ? parseHeartRate(tvECGinfo.getText().toString()) : 60;
        String pQRSAxis = "(66)-(249)-(59) deg";
        String qtC = "360 ms";
        String prInterval = "200 ms";
        String rrInterval = "996 ms";
        String qrsDuration = "40 ms";
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
        canvas.drawText("Patient Name: " + patientName, leftX, y, title);
        y += lineGap;
        canvas.drawText("Age: " + patientAge, leftX, y, title);
        canvas.drawText("Gender: Male", leftX + 120, y, title);
        y += lineGap;
        canvas.drawText("P-QRS-T Axis: " + pQRSAxis, leftX, y, title);
        y += lineGap;
        canvas.drawText("QTc: " + qtC, leftX, y, title);

        y = startY;
        canvas.drawText("Report Number: " + reportNumber, centerX, y, title);
        y += lineGap;
        canvas.drawText("Date: " + dateTime.split(" ")[0] + " " + dateTime.split(" ")[1] + " " + dateTime.split(" ")[2], centerX, y, title);
        y += lineGap;
        canvas.drawText("PR Interval: " + prInterval, centerX, y, title);
        y += lineGap;
        canvas.drawText("QRS Duration: " + qrsDuration, centerX, y, title);

        y = startY;
        canvas.drawText("Name of Device: " + deviceName, rightX, y, title);
        y += lineGap;
        canvas.drawText("Time: " + dateTime.split(" ")[3] + " " + dateTime.split(" ")[4], rightX, y, title);
        y += lineGap;
        canvas.drawText("RR Interval: " + rrInterval, rightX, y, title);
        y += lineGap + 10;
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(14);
        canvas.drawText("HR: " + hr, rightX, y, title);

        drawECGSection(canvas, ecgData);
        drawFooterSection(canvas);

        pdfDocument.finishPage(page);
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ECG_Report_" + reportNumber + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            runOnUiThread(() -> Toast.makeText(this, "ECG PDF generated successfully at: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to generate ECG PDF: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
        pdfDocument.close();
    }

    private void drawECGSection(Canvas canvas, int[][] ecgData) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.argb(255, 255, 150, 150));
        gridPaint.setStrokeWidth(1);
        Paint wavePaint = new Paint();
        wavePaint.setColor(Color.BLACK);
        wavePaint.setStrokeWidth(3);
        wavePaint.setAntiAlias(true);
        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(14);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        String[][] rows = {{"I", "aVR"}, {"II", "aVL"}, {"III", "aVF"}, {"V"}, {"II (Long)"}}; // Added long lead
        int[] leadIndices = {0, 3, 1, 4, 2, 5, 6, 1}; // II for long lead
        int startX = 20;
        int startY = 220;
        int totalWidth = 842;
        int boxHeight = 80;
        int horizontalGap = 1;
        int boxWidth = (totalWidth - horizontalGap) / 2;
        int verticalGap = 10;

        float samplingRate = 250; // Hz
        float pixelsPerSample = 0.64f; // 32 pixels per 50 samples (200 msec)
        float samplesPerGridBlock = 50; // 200 msec at 250 Hz

        for (int row = 0; row < rows.length; row++) {
            String[] leads = rows[row];
            int samplesToDraw = (row == rows.length - 1) ? 2500 : 625; // 10 sec for long lead, 2.5 sec for others
            int currentBoxWidth = (row == rows.length - 1) ? (boxWidth * 2 + horizontalGap) : boxWidth;

            for (int col = 0; col < leads.length; col++) {
                int x = startX + col * (boxWidth + horizontalGap);
                int y = startY + row * (boxHeight + verticalGap);

                if (row == rows.length - 1) {
                    currentBoxWidth = boxWidth * 2 + horizontalGap; // Full width for long lead
                } else {
                    currentBoxWidth = boxWidth;
                }

                drawGrid(canvas, x, y, currentBoxWidth, boxHeight, gridPaint);

                int leadIndex = leadIndices[row * (row == rows.length - 1 ? 1 : 2) + col];
                int[] leadData = ecgData[leadIndex];
                if (leadData.length > 0) {
                    int minValue = leadData[0];
                    int maxValue = leadData[0];
                    for (int i = 0; i < Math.min(samplesToDraw, leadData.length); i++) {
                        int value = leadData[i];
                        if (value < minValue) minValue = value;
                        if (value > maxValue) maxValue = value;
                    }
                    int dataRange = Math.max(Math.abs(minValue), Math.abs(maxValue)) * 2;
                    if (dataRange == 0) dataRange = 1;

                    // Scale to 1 mV = 80 pixels
                    float yMid = y + boxHeight / 2;
                    float yScale = 80.0f / 1000; // Assuming data is in microvolts, 1000 uV = 1 mV = 80 pixels
                    for (int i = 0; i < Math.min(samplesToDraw, leadData.length) - 1; i++) {
                        float x1 = x + i * pixelsPerSample;
                        float y1 = yMid - (leadData[i] * yScale);
                        float x2 = x + (i + 1) * pixelsPerSample;
                        float y2 = yMid - (leadData[i + 1] * yScale);
                        y1 = Math.max(y + 5, Math.min(y + boxHeight - 5, y1));
                        y2 = Math.max(y + 5, Math.min(y + boxHeight - 5, y2));
                        canvas.drawLine(x1, y1, x2, y2, wavePaint);
                    }

                    if (row == rows.length - 1) { // Draw markers on long lead
                        drawIntervalMarkers(canvas, x, y, boxHeight, samplesToDraw, pixelsPerSample, wavePaint);
                    }
                } else {
                    int midY = y + boxHeight / 2;
                    canvas.drawLine(x, midY, x + currentBoxWidth, midY, wavePaint);
                }

                canvas.drawText(leads[col], x + 5, y + 15, labelPaint);
            }
        }
    }

    private void drawGrid(Canvas canvas, int x, int y, int width, int height, Paint paint) {
        float pixelsPerGridBlock = 32; // 200 msec = 32 pixels
        int smallGridBlocks = 5; // 200 msec = 5 small grid blocks (40 msec each)
        float smallGrid = pixelsPerGridBlock / smallGridBlocks; // 6.4 pixels per small grid block

        for (int i = 0; i <= height / smallGrid; i++) {
            paint.setStrokeWidth(i % smallGridBlocks == 0 ? 1.5f : 0.8f);
            canvas.drawLine(x, y + i * smallGrid, x + width, y + i * smallGrid, paint);
        }

        for (int i = 0; i <= width / smallGrid; i++) {
            paint.setStrokeWidth(i % smallGridBlocks == 0 ? 1.5f : 0.8f);
            canvas.drawLine(x + i * smallGrid, y, x + i * smallGrid, y + height, paint);
        }
    }

    private void drawIntervalMarkers(Canvas canvas, int x, int y, int boxHeight, int dataLength, float pixelsPerSample, Paint paint) {
        float pixelsPerGridBlock = 32; // 200 msec = 32 pixels
        float samplesPerGridBlock = 50; // 200 msec at 250 Hz
        int prSamples = 50; // PR interval: 200 msec = 50 samples
        int qrsSamples = 10; // QRS duration: 40 msec = 10 samples

        float prPixels = prSamples * pixelsPerSample; // 32 pixels
        float qrsPixels = qrsSamples * pixelsPerSample; // 6.4 pixels

        float yMid = y + boxHeight / 2;
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(3);

        float startX = x + 3 * pixelsPerGridBlock; // Start at 3rd grid block
        if (startX + prPixels < x + dataLength * pixelsPerSample) {
            float markerY = y + boxHeight - 10;
            canvas.drawLine(startX, markerY, startX + prPixels, markerY, paint);
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLUE);
            textPaint.setTextSize(12);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("PR (200 ms)", startX + prPixels / 2 - 30, markerY - 5, textPaint);
        }

        startX += prPixels + 10;
        if (startX + qrsPixels < x + dataLength * pixelsPerSample) {
            float markerY = y + boxHeight - 10;
            canvas.drawLine(startX, markerY, startX + qrsPixels, markerY, paint);
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLUE);
            textPaint.setTextSize(12);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("QRS (40 ms)", startX + qrsPixels / 2 - 30, markerY - 5, textPaint);
        }

        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(3);
    }

    private void drawFooterSection(Canvas canvas) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.BLACK);
        footerPaint.setTextSize(12);
        footerPaint.setTypeface(Typeface.DEFAULT);
        int startX = 20;
        int startY = 560;
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

    private int parseHeartRate(String text) {
        try {
            return Integer.parseInt(text.split("\n")[0].replace("Heart Rate: ", "").trim());
        } catch (Exception e) {
            return 60;
        }
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

        hrDrate = ecgDialog.findViewById(R.id.hrD_rate);
        respDrate = ecgDialog.findViewById(R.id.respDrate);
        stDlevel = ecgDialog.findViewById(R.id.stDlevel);
        arDcode = ecgDialog.findViewById(R.id.arDcode);

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

    public static class TemperatureData {
        public float temperature; // Temperature in hundredths of a degree Celsius

        public TemperatureData(byte[] buffer) {
            try {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                temperature = byteBuffer.getShort(4);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing temperature data: " + e.getMessage());
                temperature = -1.0f;
            }
        }
    }

    public static class TemperatureReading {
        public float temperature;
        public long timestamp;

        public TemperatureReading(float temperature, long timestamp) {
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
}