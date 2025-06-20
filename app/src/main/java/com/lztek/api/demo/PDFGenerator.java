package com.lztek.api.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PDFGenerator {
    private static final String TAG = "PDFGenerator";
    private static final int PAGE_WIDTH = 842; // A4 width in points
    private static final int PAGE_HEIGHT = 595; // A4 height in points
    private static final float MM_TO_PX = 3.78f; // Conversion factor for mm to pixels
    private static final int MAJOR_GRID_MM = 5;
    private static final int MINOR_GRID_MM = 1;
    private static final float LINE_WIDTH_MM = 0.1f;
    private static final float PIXELS_PER_SAMPLE = 0.84f; // 420 pixels for 500 samples
    private static final float SAMPLES_PER_GRID_BLOCK = 100; // 200 msec at 500 Hz

    private final Context context;

    public PDFGenerator(Context context) {
        this.context = context;
    }

    public boolean generateECGReport(int[][] ecgData, String patientName, int patientAge, String gender,
                                     int heartRate, String reportNumber, String dateTime, String deviceName,
                                     String pQRSAxis, String qtC, String prInterval, String rrInterval,
                                     String qrsDuration, String interpretation, String doctor, String calibration,
                                     String outputFileName) {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        Paint title = new Paint();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Header section
        int leftMargin = 20, topMargin = 20, lineSpacing = 20, sectionSpacing = 25;
        int leftX = 40;
        int centerX = 300;
        int rightX = 560;
        int startY = 60;
        int lineGap = 20;

        title.setTextSize(16);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ECG and Vitals with Measurement and Interpretation", PAGE_WIDTH / 2, startY, title);

        startY += 30;
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT);
        title.setTextAlign(Paint.Align.LEFT);

        int y = startY;
        canvas.drawText("Patient Name: " + patientName, leftX, y, title);
        y += lineGap;
        canvas.drawText("Age: " + patientAge, leftX, y, title);
        canvas.drawText("Gender: " + gender, leftX + 120, y, title);
        y += lineGap;
        canvas.drawText("P-QRS-T Axis: " + pQRSAxis, leftX, y, title);
        y += lineGap;
        canvas.drawText("QTc: " + qtC, leftX, y, title);

        y = startY;
        canvas.drawText("Report Number: " + reportNumber, centerX, y, title);
        y += lineGap;
        canvas.drawText("Date: " + dateTime.split(" ")[0], centerX, y, title);
        y += lineGap;
        canvas.drawText("PR Interval: " + prInterval, centerX, y, title);
        y += lineGap;
        canvas.drawText("QRS Duration: " + qrsDuration, centerX, y, title);

        y = startY;
        canvas.drawText("Name of Device: " + deviceName, rightX, y, title);
        y += lineGap;
        canvas.drawText("Time: " + dateTime.split(" ")[1], rightX, y, title);
        y += lineGap;
        canvas.drawText("RR Interval: " + rrInterval, rightX, y, title);
        y += lineGap + 10;
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(14);
        canvas.drawText("HR: " + heartRate, rightX, y, title);

        // ECG section
        drawECGSection(canvas, ecgData);
        drawFooterSection(canvas, interpretation, doctor);

        pdfDocument.finishPage(page);
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                outputFileName);
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            showToast("ECG PDF generated successfully at: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            showToast("Failed to generate ECG PDF: " + e.getMessage());
            return false;
        } finally {
            pdfDocument.close();
        }
    }

    private void drawECGSection(Canvas canvas, int[][] ecgData) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.argb(255, 255, 150, 150)); // Lighter red for grid
        gridPaint.setStrokeWidth(1);
        Paint wavePaint = new Paint();
        wavePaint.setColor(Color.BLACK);
        wavePaint.setStrokeWidth(3);
        wavePaint.setAntiAlias(true);
        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(14);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        String[][] rows = {{"I", "aVR"}, {"II", "aVL"}, {"III", "aVF"}, {"V"}};
        int[] leadIndices = {0, 3, 1, 4, 2, 5, 6};
        int startX = 20;
        int startY = 220;
        int totalWidth = PAGE_WIDTH;
        int boxHeight = 80;
        int horizontalGap = 1;
        int boxWidth = (totalWidth - horizontalGap) / 2;
        int verticalGap = 10;

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

                    float xStep = PIXELS_PER_SAMPLE;
                    float yMid = y + boxHeight / 2;
                    float yScale = (float) (boxHeight * 0.6) / dataRange;
                    for (int i = 0; i < leadData.length - 1; i++) {
                        float x1 = x + i * xStep;
                        float y1 = yMid - (leadData[i] * yScale);
                        float x2 = x + (i + 1) * xStep;
                        float y2 = yMid - (leadData[i + 1] * yScale);
                        y1 = Math.max(y + 5, Math.min(y + boxHeight - 5, y1));
                        y2 = Math.max(y + 5, Math.min(y + boxHeight - 5, y2));
                        canvas.drawLine(x1, y1, x2, y2, wavePaint);
                    }

                    drawIntervalMarkers(canvas, x, y, boxHeight, leadData.length, PIXELS_PER_SAMPLE, wavePaint);
                } else {
                    int midY = y + boxHeight / 2;
                    canvas.drawLine(x, midY, x + currentBoxWidth, midY, wavePaint);
                }

                canvas.drawText(leads[col], x + 5, y + 15, labelPaint);
            }
        }
    }

    private void drawGrid(Canvas canvas, int x, int y, int width, int height, Paint paint) {
        float pixelsPerGridBlock = 84; // 200 msec = 84 pixels
        int smallGridBlocks = 5; // 200 msec = 5 small grid blocks (40 msec each)
        float smallGrid = pixelsPerGridBlock / smallGridBlocks; // 16.8 pixels per small grid block

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
        float pixelsPerGridBlock = 84; // 200 msec = 84 pixels
        float samplesPerGridBlock = SAMPLES_PER_GRID_BLOCK; // 200 msec at 500 Hz
        int prSamples = 100; // PR interval: 200 msec = 100 samples
        int qrsSamples = 20; // QRS duration: 40 msec = 20 samples

        float prPixels = prSamples * pixelsPerSample; // 84 pixels
        float qrsPixels = qrsSamples * pixelsPerSample; // 16.8 pixels

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

    private void drawFooterSection(Canvas canvas, String interpretation, String doctor) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.BLACK);
        footerPaint.setTextSize(12);
        footerPaint.setTypeface(Typeface.DEFAULT);
        int startX = 20;
        int startY = 560;
        canvas.drawText("Interpretation: " + interpretation, startX, startY, footerPaint);
        int lineGap = 20;
        String unconfirmedText = "Unconfirmed ECG Report. Please refer Physician";
        String doctorLabel = "Name of Doctor: " + doctor;
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

    private void showToast(String message) {
        if (context != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
    }
}