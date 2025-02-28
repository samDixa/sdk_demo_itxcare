package com.lztek.api.demo;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.cardview.widget.CardView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ParameterCardView extends CardView {

    private Context context;

    public ParameterCardView(Context context, String header, String[] labels, String[] values) {
        super(context);
        this.context = context;

        // CardView setup
        this.setCardElevation(4); // minimal elevation
        this.setRadius(8); // minimal corner radius
        this.setUseCompatPadding(true);
        this.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        this.setContentPadding(8, 8, 8, 8); // minimal padding

        // Inner LinearLayout to contain all elements
        LinearLayout innerLayout = new LinearLayout(context);
        innerLayout.setOrientation(LinearLayout.VERTICAL);

        // Header TextView
        TextView headerTextView = new TextView(context);
        headerTextView.setText(header);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTextView.setTextColor(Color.WHITE);
        headerTextView.setBackgroundColor(Color.DKGRAY);
        headerTextView.setPadding(10, 10, 10, 10);
        headerTextView.setGravity(Gravity.CENTER);

        // Add the header to the layout
        innerLayout.addView(headerTextView);

        // Add parameter rows
        for (int i = 0; i < labels.length; i++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setPadding(4, 4, 4, 4); // minimal padding for rows

            // Label TextView
            TextView labelTextView = new TextView(context);
            labelTextView.setText(labels[i]);
            labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            labelTextView.setTextColor(Color.DKGRAY);
            labelTextView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            // Value TextView
            TextView valueTextView = new TextView(context);
            valueTextView.setText(values[i]);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            valueTextView.setTextColor(Color.BLACK);
            valueTextView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            // Add the label and value to the row layout
            rowLayout.addView(labelTextView);
            rowLayout.addView(valueTextView);

            // Add the row layout to the inner layout
            innerLayout.addView(rowLayout);
        }

        // Add the inner layout to the CardView
        this.addView(innerLayout);
    }
}
