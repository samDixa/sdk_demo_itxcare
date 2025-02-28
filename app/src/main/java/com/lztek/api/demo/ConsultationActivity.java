package com.lztek.api.demo;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class ConsultationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root layout with a black background
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.BLACK);
        scrollView.addView(mainLayout);

        // SPO2 Section
        CardView spO2Card = createParameterCard("SPO2", new String[]{"Status", "SPO2%", "Pulse Rate"},
                new String[]{"Normal", "98%", "75 bpm"});
        mainLayout.addView(spO2Card);

        // NIBP Section
        CardView nibpCard = createParameterCard("NIBP", new String[]{"Status", "Mode", "SYS mmHg", "DIA mmHg", "MEAN mmHg", "CUFF mmHg"},
                new String[]{"Active", "Auto", "120", "80", "100", "5"});
        mainLayout.addView(nibpCard);

        // Temperature Section
        CardView tempCard = createParameterCard("TEMPERATURE", new String[]{"Status", "Temp °C"},
                new String[]{"Normal", "36.6°C"});
        mainLayout.addView(tempCard);

        // Respiration Section
        CardView respirationCard = createParameterCard("RESPIRATION", new String[]{"Respiration Rate"},
                new String[]{"16 bpm"});
        mainLayout.addView(respirationCard);

        // ECG Section
        CardView ecgCard = createParameterCard("ECG", new String[]{"Lead", "Signal", "Filter Mode", "Gain", "Heart Rate", "ST Level", "Arrhythmia"},
                new String[]{"Lead I", "Normal", "Auto", "1x", "72 bpm", "0.2", "None"});
        mainLayout.addView(ecgCard);

        // Set the main layout to the activity
        setContentView(scrollView);
    }

    // Utility method to create each parameter section as a CardView
    private CardView createParameterCard(String header, String[] labels, String[] values) {
        CardView cardView = new CardView(this);
        cardView.setCardBackgroundColor(Color.DKGRAY);
        cardView.setRadius(8);
        cardView.setContentPadding(16, 16, 16, 16);
        cardView.setCardElevation(8);
        cardView.setUseCompatPadding(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(8, 8, 8, 8);
        cardView.setLayoutParams(cardParams);

        // Title text for CardView
        TextView headerTextView = new TextView(this);
        headerTextView.setText(header);
        headerTextView.setTextColor(Color.WHITE);
        headerTextView.setTextSize(18);
        headerTextView.setGravity(Gravity.CENTER);
        headerTextView.setPadding(0, 0, 0, 8);

        // Add title to card
        LinearLayout cardContentLayout = new LinearLayout(this);
        cardContentLayout.setOrientation(LinearLayout.VERTICAL);
        cardContentLayout.addView(headerTextView);

        // Grid-like structure for labels and values
        for (int i = 0; i < labels.length; i++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setPadding(0, 8, 0, 8);

            // Label
            TextView labelTextView = new TextView(this);
            labelTextView.setText(labels[i] + ": ");
            labelTextView.setTextColor(Color.LTGRAY);
            labelTextView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            // Value
            TextView valueTextView = new TextView(this);
            valueTextView.setText(values[i]);
            valueTextView.setTextColor(Color.WHITE);
            valueTextView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            // Add label and value to row
            rowLayout.addView(labelTextView);
            rowLayout.addView(valueTextView);
            cardContentLayout.addView(rowLayout);
        }

        // Add content to card
        cardView.addView(cardContentLayout);

        return cardView;
    }
}
