package com.lztek.api.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

public class FooterFragment extends Fragment {

    private TextView batteryPercentageTextView;
    private TextView chargingStatusTextView;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Receive battery and charging status from service
            long batteryPercentage = intent.getLongExtra("battery_percentage", -1);
            long chargingStatus = intent.getLongExtra("charging_status", -1);

            // Update UI
            if (batteryPercentageTextView != null) {
                batteryPercentageTextView.setText("Battery: " + batteryPercentage + "%");
            }
            if (chargingStatusTextView != null) {
                chargingStatusTextView.setText("Charging: " + (chargingStatus == 1 ? "Yes" : "No"));
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the footer layout
        View view = inflater.inflate(R.layout.footer_bar, container, false);

        // Initialize views
        batteryPercentageTextView = view.findViewById(R.id.battery_percentage);
        chargingStatusTextView = view.findViewById(R.id.charging_status);

        // Register BroadcastReceiver to receive updates from service
        IntentFilter filter = new IntentFilter("com.lztek.api.demo.STATUS_UPDATE");
        requireActivity().registerReceiver(statusReceiver, filter);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unregister the receiver to avoid leaks
        requireActivity().unregisterReceiver(statusReceiver);
    }
}