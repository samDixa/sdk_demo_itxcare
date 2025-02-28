package com.lztek.api.demo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lztek.api.demo.bluetooth.BTController;
import com.lztek.api.demo.data.DataParser;
import com.lztek.api.demo.data.ECG;
import com.lztek.api.demo.data.NIBP;
import com.lztek.api.demo.data.SpO2;
import com.lztek.api.demo.data.Temp;
import com.lztek.api.demo.dialog.BluetoothDeviceAdapter;
import com.lztek.api.demo.dialog.SearchDevicesDialog;
import com.lztek.api.demo.view.WaveformView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BerryDeviceActivity extends AppCompatActivity implements BTController.Listener, DataParser.onPackageReceivedListener {

    private BTController mBtController;


    // ui
    private Button btnBtCtr;
    private TextView tvBtinfo;
    private TextView tvECGinfo;
    private TextView tvSPO2info;
    private TextView tvTEMPinfo;
    private TextView tvNIBPinfo;
    private LinearLayout llAbout;
    private TextView tvFWVersion;
    private TextView tvHWVersion;
    private WaveformView wfSpO2;
    private WaveformView wfECG;

    //Bluetooth
    BluetoothDeviceAdapter mBluetoothDeviceAdapter;
    SearchDevicesDialog mSearchDialog;
    ProgressDialog mConnectingDialog;
    ArrayList<BluetoothDevice> mBluetoothDevices;

    //data
    DataParser mDataParser;

    private String[] permissions = {Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION};

    List<String> mPermissionList = new ArrayList<>();
    private final int mRequestCode = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_berry_device);

        initData();
        initView();
        initPermission();


        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void initPermission() {
        mPermissionList.clear();
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[1]);
            }
        }
        if (mPermissionList.size() > 0) {
            ActivityCompat.requestPermissions(this, permissions, mRequestCode);
        }
    }

    private void initData() {
//        enable the bt adapter
        mBtController = BTController.getDefaultBTController(this);
        mBtController.registerBroadcastReceiver(this);
        mBtController.enableBtAdpter();

        mDataParser = new DataParser(this);
        mDataParser.start();
    }

    private void initView() {
        //ui screen widgets
        btnBtCtr = (Button) findViewById(R.id.btnBtCtr);
        tvBtinfo = (TextView) findViewById(R.id.tvbtinfo);
        tvECGinfo = (TextView) findViewById(R.id.tvECGinfo);
        tvSPO2info = (TextView) findViewById(R.id.tvSPO2info);
        tvTEMPinfo = (TextView) findViewById(R.id.tvTEMPinfo);
        tvNIBPinfo = (TextView) findViewById(R.id.tvNIBPinfo);
        llAbout = (LinearLayout) findViewById(R.id.llAbout);
        tvFWVersion = (TextView) findViewById(R.id.tvFWverison);
        tvHWVersion = (TextView) findViewById(R.id.tvHWverison);

//        bluetooth search dialog
        mBluetoothDevices = new ArrayList<>();
        mBluetoothDeviceAdapter = new BluetoothDeviceAdapter(BerryDeviceActivity.this, mBluetoothDevices);
        mSearchDialog = new SearchDevicesDialog(BerryDeviceActivity.this, mBluetoothDeviceAdapter) {
            @Override
            public void onStartSearch() {
                mBtController.startScan(true);
            }

            @Override
            public void onClickDeviceItem(int pos) {
                BluetoothDevice device = mBluetoothDevices.get(pos);
                mBtController.startScan(false);
                mBtController.connect(BerryDeviceActivity.this, device);
                tvBtinfo.setText(device.getName() + ": " + device.getAddress());
                mConnectingDialog.show();
                mSearchDialog.dismiss();
            }
        };
        mSearchDialog.setOnDismissListener((dialog) -> {
            mBtController.startScan(false);
        });

        mConnectingDialog = new ProgressDialog(BerryDeviceActivity.this);
        mConnectingDialog.setMessage("Connecting...");

//        about information
        llAbout.setOnClickListener((v) -> {
            mBtController.write(DataParser.CMD_FW_VERSION);
            mBtController.write(DataParser.CMD_HW_VERSION);
        });
//        spo2 & ecg waveform

        wfSpO2 = (WaveformView) findViewById(R.id.wfSpO2);
        wfECG = (WaveformView) findViewById(R.id.wfSpO2);

    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnBtCtr:
                if (!mBtController.isBTConnected()) {
                    mSearchDialog.show();
                    mSearchDialog.startSearch();
                    mBtController.startScan(true);
                } else {
                    mBtController.disconnect();
                    tvBtinfo.setText("");
                }
                break;
            case R.id.btnNIBPStart:
                mBtController.write(DataParser.CMD_START_NIBP);
                break;
            case R.id.btnNIBPStop:
                mBtController.write(DataParser.CMD_STOP_NIBP);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finish();
//        System.exit(0); //for release "mBluetoothDevices" on key_back down
        mBtController.unregisterBroadcastReceiver(this);
    }


    //    btcontroller implements
    @Override
    public void onFoundDevice(BluetoothDevice device) {
        if (mBluetoothDevices.contains(device))
            return;
        mBluetoothDevices.add(device);
        mBluetoothDeviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStopScan() {
        mSearchDialog.startSearch();
    }

    @Override
    public void onStartScan() {
        mBluetoothDevices.clear();
        mBluetoothDeviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConnected() {
        mConnectingDialog.setMessage("Connected..");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectingDialog.dismiss();
                    }
                });
            }
        }, 800);

        btnBtCtr.setText("Disconnect");
    }

    @Override
    public void onDisconnected() {
        btnBtCtr.setText("Search Devices");
    }

    @Override
    public void onReceiveData(byte[] dat) {
        mDataParser.add(dat);
    }

//    data parser impl

    @Override
    public void onSpO2WaveReceived(int dat) {
        wfSpO2.addAmp(dat);
    }

    @Override
    public void onSpO2Received(SpO2 spo2) {
        runOnUiThread(() -> {
            tvSPO2info.setText(spo2.toString());
        });
    }

    @Override
    public void onECGWaveReceived(int dat) {
        wfECG.addAmp(dat);
    }

    @Override
    public void onECGReceived(ECG ecg) {
        runOnUiThread(() -> {
            tvECGinfo.setText(ecg.toString());
        });
    }

    @Override
    public void onTempReceived(Temp temp) {
        runOnUiThread(() -> {
            tvTEMPinfo.setText(temp.toString());
        });
    }

    @Override
    public void onNIBPReceived(NIBP nibp) {
        runOnUiThread(() -> {
            tvNIBPinfo.setText(nibp.toString());
        });
    }

    @Override
    public void onFirmwareReceived(String str) {
        runOnUiThread(() -> {
            tvFWVersion.setText("Firmaware Version" + str);
        });
    }

    @Override
    public void onHardwareReceived(String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvHWVersion.setText("Hardware Version:" + str);
            }
        });
    }
}