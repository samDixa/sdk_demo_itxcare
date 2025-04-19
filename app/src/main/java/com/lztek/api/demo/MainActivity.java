package com.lztek.api.demo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements
        View.OnClickListener {

    private android.util.SparseArray<Intent> mBtnIdMap;

//    @TargetApi(Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
//        startForegroundService(new Intent(this, SerialPortService.class));
        initview();
        mBtnIdMap = new android.util.SparseArray<Intent>();
        mBtnIdMap.put(R.id.power_demo, new Intent(this, PowerActivity.class));
        mBtnIdMap.put(R.id.serialport_demo, new Intent(this, SerialPortActivity.class));
        mBtnIdMap.put(R.id.gpio_demo, new Intent(this, GPIOActivity.class));
        mBtnIdMap.put(R.id.watchdog_demo, new Intent(this, WatchDogActivity.class));
        mBtnIdMap.put(R.id.screen_demo, new Intent(this, ScreenActivity.class));
        mBtnIdMap.put(R.id.ethernet_demo, new Intent(this, EthernetActivity.class));
        mBtnIdMap.put(R.id.density_demo, new Intent(this, DensityActivity.class));
        mBtnIdMap.put(R.id.apk_demo, new Intent(this, ApkActivity.class));
        mBtnIdMap.put(R.id.system_demo, new Intent(this, SystemActivity.class));
        mBtnIdMap.put(R.id.hdmiin_demo, new Intent(this, HdmiInActivity.class));
        mBtnIdMap.put(R.id.ota_demo, new Intent(this, OtaUpdateActivity.class));
        mBtnIdMap.put(R.id.autoinstall, new Intent(this, AutoInstallActivity.class));
        for (int i = 0; i < mBtnIdMap.size(); ++i) {
            ((Button) findViewById(mBtnIdMap.keyAt(i))).setOnClickListener(this);
        }
        ((Button) findViewById(R.id.exit_app)).setOnClickListener(this);
        findViewById(R.id.testlayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), NewMainActivity.class));
            }
        });


//        // Service Start

        Intent intent = new Intent(this, SerialPortService.class);
        startService(intent);
        Log.d("tag", "🚀 SerialPortService Started");
//
//        // Register Broadcast Receiver
//        registerReceiver(serialReceiver, new IntentFilter("SerialDataReceived"));
    }

    private Button navigation, openslide, navigationautohidden, silentinstallation;
    private com.lztek.toolkit.Lztek mLztek;
    AlertDialog alertDialog1;

    private void initview() {
        mLztek = com.lztek.toolkit.Lztek.create(this);
        alertDialog1 = new AlertDialog.Builder(getApplicationContext()).create();
        findViewById(R.id.screenshot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mLztek.screenCapture(Environment.getExternalStorageDirectory() + "/" + "screenshot.png");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "screenshot.png" + "保存成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        }


                    }
                }).start();
            }
        });
        navigation = findViewById(R.id.navigation);
        navigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (navigation.getText().toString().equals(getResources().getString(R.string.shownavigation))) {
                    navigation.setText(getResources().getString(R.string.hidenavigation));
                    mLztek.showNavigationBar();
                } else {
                    navigation.setText(getResources().getString(R.string.shownavigation));
                    mLztek.hideNavigationBar();
                }
            }
        });

        openslide = findViewById(R.id.openslide);
        openslide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openslide.getText().toString().equals(getResources().getString(R.string.openslide))) {
                    openslide.setText(getResources().getString(R.string.closeslide));
                    mLztek.navigationBarSlideShow(true);
                } else {
                    openslide.setText(getResources().getString(R.string.openslide));
                    mLztek.navigationBarSlideShow(false);
                }
            }
        });
        navigationautohidden = findViewById(R.id.navigationautohidden);
        navigationautohidden.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLztek.navigationBarMaxIdle(5);
                mLztek.installApplication(Environment.getExternalStorageDirectory() + "/" + "via.apk");
            }
        });

        silentinstallation = findViewById(R.id.silentinstallation);
        silentinstallation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (silentinstallation.getText().toString().equals(getResources().getString(R.string.silentinstallation))) {
                    silentinstallation.setText(getResources().getString(R.string.silentuninstallation));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mLztek.installApplication(Environment.getExternalStorageDirectory() + "/" + "via.apk");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "via.apk" + "安装成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();

                } else {
                    silentinstallation.setText(getResources().getString(R.string.silentinstallation));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mLztek.uninstallApplication("mark.via");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "via.apk" + "卸载成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();

                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        unregisterReceiver(serialReceiver);
        System.exit(0);

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.exit_app) {
            finish();
        } else {
            Intent intent = mBtnIdMap.get(v.getId(), null);
            if (null != intent) {
                startActivity(intent);
            }
        }
    }
}
