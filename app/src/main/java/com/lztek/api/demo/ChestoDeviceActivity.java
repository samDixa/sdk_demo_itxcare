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

            @Override
            public void onBatteryUpdate(String batteryLevel) {
                runOnUiThread(() -> {
                    statusText.setText("Connected to Chesto | Battery: " + batteryLevel + "%");
                    Log.d("ChestoDeviceActivity", "Battery Level: " + batteryLevel + "%");
                });
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
                } else {
                    Toast.makeText(this, "Not Connected!", Toast.LENGTH_SHORT).show();
                }
            } else {
                bluetoothService.stopListening();
                chestoButton.setText("Start Chesto");
                isListening = false;
                Toast.makeText(this, "Stopped Listening", Toast.LENGTH_SHORT).show();
                graphUpdateHandler.removeCallbacks(graphUpdater);
            }
        });

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                audioBuffer.reset();
                isRecording = true;
                bluetoothService.startRecording();
                recordButton.setText("Stop Recording");
                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
            } else {
                isRecording = false;
                recordButton.setText("Start Recording");
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
            byte[] pcmData = convertUint8ToPCM16(recordedData);
            byte[] header = getWavHeader(pcmData.length, 16000);
            fos.write(header);
            fos.write(pcmData);
            fos.close();
            runOnUiThread(() -> Toast.makeText(this, "Audio Saved!", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            Log.d("ChestoDeviceActivity", "Save failed: " + e.getMessage());
        }
    }

    private void playRecordedAudio() {
        new Thread(() -> {
            try {
                File file = new File(getExternalFilesDir(null), "recorded_audio.wav");
                if (!file.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "No Audio Found!", Toast.LENGTH_SHORT).show());
                    return;
                }

                FileInputStream fis = new FileInputStream(file);
                byte[] wavHeader = new byte[44];
                fis.read(wavHeader, 0, 44);

                byte[] audioData = new byte[(int) (file.length() - 44)];
                fis.read(audioData);
                fis.close();

                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        16000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        audioData.length,
                        AudioTrack.MODE_STREAM
                );

                audioTrack.write(audioData, 0, audioData.length);
                audioTrack.play();
                runOnUiThread(() -> Toast.makeText(this, "Playing Audio...", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                Log.d("ChestoDeviceActivity", "Play failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Play failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private byte[] convertUint8ToPCM16(byte[] uint8Data) {
        byte[] pcm16Data = new byte[uint8Data.length * 2];
        for (int i = 0; i < uint8Data.length; i++) {
            short sample = (short) ((uint8Data[i] & 0xFF) - 128);
            pcm16Data[i * 2] = (byte) (sample & 0xFF);
            pcm16Data[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm16Data;
    }

    private byte[] getWavHeader(int dataLength, int sampleRate) {
        byte[] header = new byte[44];
        String[] riff = {"RIFF", "WAVEfmt ", "data"};
        System.arraycopy(riff[0].getBytes(), 0, header, 0, 4);
        System.arraycopy(BitConverter.getBytes(dataLength + 36), 0, header, 4, 4);
        System.arraycopy(riff[1].getBytes(), 0, header, 8, 8);
        System.arraycopy(BitConverter.getBytes(16), 0, header, 16, 4);
        System.arraycopy(BitConverter.getBytes((short) 1), 0, header, 20, 2);
        System.arraycopy(BitConverter.getBytes((short) 1), 0, header, 22, 2);
        System.arraycopy(BitConverter.getBytes(sampleRate), 0, header, 24, 4);
        System.arraycopy(BitConverter.getBytes(sampleRate * 2), 0, header, 28, 4);
        System.arraycopy(BitConverter.getBytes((short) 2), 0, header, 32, 2);
        System.arraycopy(BitConverter.getBytes((short) 16), 0, header, 34, 2);
        System.arraycopy(riff[2].getBytes(), 0, header, 36, 4);
        System.arraycopy(BitConverter.getBytes(dataLength), 0, header, 40, 4);
        return header;
    }

    public static class BitConverter {
        public static byte[] getBytes(int value) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(value);
            return buffer.array();
        }
        public static byte[] getBytes(short value) {
            ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putShort(value);
            return buffer.array();
        }
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