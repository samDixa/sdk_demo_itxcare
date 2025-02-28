package com.lztek.api.demo;


import com.lztek.api.demo.R;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.app.Activity;
import android.widget.Toast;

public class HdmiInActivity extends BaseActivity {
    private com.lztek.toolkit.Lztek mLztek;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hdmiin_layout);
        setTitle(R.string.hdmiin_demo);

        mLztek = com.lztek.toolkit.Lztek.create(this);
        mHandler = new Handler();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ((TextView) findViewById(R.id.hdmiin_status)).setText("" + mLztek.getHdmiinStatus());
                    ((TextView) findViewById(R.id.hdmiin_resolution)).setText("" + mLztek.getHdmiinResolution());
                    mHandler.postDelayed(this, 5000);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mHandler.removeCallbacksAndMessages(null);
    }
}
