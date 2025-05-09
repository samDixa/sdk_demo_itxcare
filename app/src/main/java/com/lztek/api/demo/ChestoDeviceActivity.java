package com.lztek.api.demo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class ChestoDeviceActivity extends AppCompatActivity {
    private BluetoothService bluetoothService;
    private TextView statusText;
    private Button connectButton, recordButton, playPauseButton, chestoButton;
    private boolean isRecording = false;
    private boolean isListening = false;
    private boolean isPlaying = false;
    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private LineChart audioGraph;
    private Handler graphUpdateHandler = new Handler(Looper.getMainLooper());
    private AudioTrack audioTrack;
    private List<Byte> plotBuffer = new ArrayList<>(); // Local buffer for graphing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chesto_device);

        audioGraph = findViewById(R.id.audioGraph);
        setupGraph();

        bluetoothService = new BluetoothService();

        statusText = findViewById(R.id.statusTextView);
        connectButton = findViewById(R.id.connectButton);
        recordButton = findViewById(R.id.recordButton);
        playPauseButton = findViewById(R.id.playPauseButton);
        chestoButton = findViewById(R.id.chestoButton);

        chestoButton.setEnabled(false);
        playPauseButton.setEnabled(false);

        bluetoothService.setCallback(new BluetoothService.BluetoothCallback() {
            @Override
            public void onDataReceived(byte[] data) {
                runOnUiThread(() -> statusText.setText("Receiving Data..."));
                if (isRecording) {
                    try {
                        audioBuffer.write(data);
                    } catch (IOException e) {
                        Log.d("ChestoDeviceActivity", "Write failed: " + e.getMessage());
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
                    statusText.setText("Connected to Chesto");
                    connectButton.setText("Disconnect");
                    chestoButton.setEnabled(true);
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    statusText.setText("Disconnected");
                    connectButton.setText("Connect");
                    chestoButton.setEnabled(false);
                    playPauseButton.setEnabled(false);
                    isListening = false;
                    chestoButton.setText("Start Chesto");
                    graphUpdateHandler.removeCallbacks(graphUpdater);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ChestoDeviceActivity.this, error, Toast.LENGTH_SHORT).show());
            }

        });

        connectButton.setOnClickListener(v -> {
            if (connectButton.getText().toString().equals("Connect")) {
                connectToChesto();
            } else {
                try {
                    bluetoothService.disconnect();
                } catch (Exception e) {
                    Log.d("ChestoDeviceActivity", "Disconnect failed: " + e.getMessage());
                    Toast.makeText(this, "Disconnect failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        chestoButton.setOnClickListener(v -> {
            if (!isListening) {
                if (bluetoothService.isConnected()) {
                    bluetoothService.startListening();
                    chestoButton.setText("Stop Chesto");
                    isListening = true;
                    Toast.makeText(this, "Started Listening", Toast.LENGTH_SHORT).show();
                    graphUpdateHandler.post(graphUpdater);

//                    recordButton.setEnabled(false);
//                    playPauseButton.setEnabled(false);
                } else {
                    Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show();
                }
            } else {
                bluetoothService.stopListening();
                chestoButton.setText("Start Chesto");
                isListening = false;
                Toast.makeText(this, "Stopped Listening", Toast.LENGTH_SHORT).show();
                graphUpdateHandler.removeCallbacks(graphUpdater);

//                recordButton.setEnabled(true);
//                playPauseButton.setEnabled(true);
            }
        });

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                audioBuffer.reset();
                isRecording = true;
                bluetoothService.startRecording();
                recordButton.setText("Stop Recording");
                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();

//                chestoButton.setEnabled(false);
//                playPauseButton.setEnabled(false);
            } else {
                isRecording = false;
                recordButton.setText("Start Recording");

//                chestoButton.setEnabled(true);

                byte[] recordedData = audioBuffer.toByteArray();
                if (recordedData.length == 0) {
                    Toast.makeText(this, "Recording Failed: No Data", Toast.LENGTH_SHORT).show();
                    return;
                }
                new Thread(() -> saveAudioToFile(recordedData)).start();
                playPauseButton.setEnabled(true);
            }
        });

        playPauseButton.setOnClickListener(v -> {
            if (!isPlaying) {
                playRecordedAudio();
                playPauseButton.setText("Pause Audio");
                isPlaying = true;
            } else {
                if (audioTrack != null) {
                    audioTrack.pause();
                    playPauseButton.setText("Play Audio");
                    isPlaying = false;
                    Toast.makeText(this, "Audio Paused", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupGraph() {
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "Audio Signal");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        LineData lineData = new LineData(dataSet);
        audioGraph.setData(lineData);
        audioGraph.getXAxis().setDrawLabels(false);
        audioGraph.getAxisLeft().setDrawLabels(false);
        audioGraph.getAxisRight().setDrawLabels(false);
        audioGraph.setHardwareAccelerationEnabled(true);
        audioGraph.invalidate();
    }

    private Runnable graphUpdater = new Runnable() {
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
                LineData data = audioGraph.getData();
                if (data != null && data.getDataSetCount() > 0) {
                    LineDataSet dataSet = (LineDataSet) data.getDataSetByIndex(0);
                    dataSet.setValues(newEntries);
                    data.notifyDataChanged();
                    audioGraph.notifyDataSetChanged();
                    audioGraph.invalidate();
                }
            } catch (Exception e) {
                Log.d("GraphUpdate", "Update failed: " + e.getMessage());
            }
        });
    }

//

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

    private void saveAudioToFile(byte[] recordedData) {
        try {
            File file = new File(getExternalFilesDir(null), "recorded_audio.wav");
            FileOutputStream fos = new FileOutputStream(file);

            // 6000 Hz sample rate, 16-bit PCM mono
            byte[] header = getWavHeader(recordedData.length, 6000);
            fos.write(header);
            fos.write(recordedData);  // direct write, no conversion
            fos.flush();
            fos.close();

            final String filePath = file.getAbsolutePath();
            runOnUiThread(() ->
                    Toast.makeText(this, "Audio saved at: " + filePath, Toast.LENGTH_LONG).show()
            );
        } catch (IOException e) {
            Log.d("ChestoDeviceActivity", "Save failed: " + e.getMessage());
            runOnUiThread(() ->
                    Toast.makeText(this, "Failed to save audio: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void playRecordedAudio() {
        new Thread(() -> {
            try {
                File file = new File(getExternalFilesDir(null), "recorded_audio.wav");
                if (!file.exists()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "No Audio Found!", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                FileInputStream fis = new FileInputStream(file);
                fis.skip(44);  // skip WAV header

                byte[] audioData = new byte[(int) (file.length() - 44)];
                fis.read(audioData);
                fis.close();

                AudioTrack audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        6000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        audioData.length,
                        AudioTrack.MODE_STREAM
                );

                audioTrack.play();
                audioTrack.write(audioData, 0, audioData.length);
                runOnUiThread(() ->
                        Toast.makeText(this, "Playing Audio...", Toast.LENGTH_SHORT).show()
                );
            } catch (IOException e) {
                Log.d("ChestoDeviceActivity", "Play failed: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Play failed", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private byte[] getWavHeader(int totalAudioLen, int sampleRate) {
        int channels = 1;
        int byteRate = sampleRate * 2;  // 16-bit = 2 bytes * 1 channel

        int totalDataLen = totalAudioLen + 36;

        byte[] header = new byte[44];
        header[0] = 'R';  header[1] = 'I';  header[2] = 'F';  header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';  header[9] = 'A';  header[10] = 'V';  header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16;  header[17] = 0;   header[18] = 0;   header[19] = 0;   // Subchunk1Size
        header[20] = 1;   header[21] = 0;   // PCM format
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
        header[32] = 2;   header[33] = 0;   // Block align = 2 bytes
        header[34] = 16;  header[35] = 0;   // Bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        return header;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
            graphUpdateHandler.removeCallbacksAndMessages(null);
            bluetoothService.disconnect();
        } catch (Exception e) {
            Log.d("ChestoDeviceActivity", "Destroy failed: " + e.getMessage());
        }
    }
}