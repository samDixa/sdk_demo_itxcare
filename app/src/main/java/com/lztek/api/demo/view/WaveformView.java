package com.lztek.api.demo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lztek.api.demo.R;

public class WaveformView extends SurfaceView implements SurfaceHolder.Callback {
    private Paint mWavePaintECG, mWavePaintSpO2, mBackgroundPaint;
    private SurfaceHolder mSurfaceHolder;
    private Canvas mCanvas;

    private Point mLastPointECG, mLastPointSpO2;
    private float pointStep;
    private float mLineWidth;

    private int[] mDataBufferECG, mDataBufferSpO2;
    private int mDataBufferIndexECG, mDataBufferIndexSpO2;
    private int mBufferSize, mMaxValue;
    private boolean isSurfaceViewAvailable;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        TypedArray arr = context.getTheme().obtainStyledAttributes(attrs, R.styleable.WaveformView, 0, 0);

        int waveColorECG = arr.getColor(R.styleable.WaveformView_waveColor, Color.GREEN);
        int waveColorSpO2 = arr.getColor(R.styleable.WaveformView_waveColor, Color.RED);
        mLineWidth = arr.getDimension(R.styleable.WaveformView_lineWidth, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics));
        pointStep = arr.getDimension(R.styleable.WaveformView_pointStep, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.4f, metrics));
        mBufferSize = arr.getInt(R.styleable.WaveformView_bufferSize, 5);
        mMaxValue = arr.getInteger(R.styleable.WaveformView_maxValue, 100);

        mWavePaintECG = new Paint();
        mWavePaintECG.setColor(waveColorECG);
        mWavePaintECG.setStrokeWidth(mLineWidth);
        mWavePaintECG.setStyle(Paint.Style.STROKE);

        mWavePaintSpO2 = new Paint();
        mWavePaintSpO2.setColor(waveColorSpO2);
        mWavePaintSpO2.setStrokeWidth(mLineWidth);
        mWavePaintSpO2.setStyle(Paint.Style.STROKE);

        int backgroundColor = arr.getColor(R.styleable.WaveformView_backgroundColor, Color.BLACK);
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(backgroundColor);

        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);

        mDataBufferECG = new int[mBufferSize];
        mDataBufferSpO2 = new int[mBufferSize];
        mDataBufferIndexECG = 0;
        mDataBufferIndexSpO2 = 0;

        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    public void addAmpECG(int amp) {
        if (!isSurfaceViewAvailable) return;
        drawWaveform(amp, mDataBufferECG, mDataBufferIndexECG, mWavePaintECG, mLastPointECG);
        mDataBufferIndexECG = (mDataBufferIndexECG + 1) % mBufferSize;
    }

    public void addAmpSpO2(int amp) {
        if (!isSurfaceViewAvailable) return;
        drawWaveform(amp, mDataBufferSpO2, mDataBufferIndexSpO2, mWavePaintSpO2, mLastPointSpO2);
        mDataBufferIndexSpO2 = (mDataBufferIndexSpO2 + 1) % mBufferSize;
    }

    private void drawWaveform(int amp, int[] buffer, int index, Paint paint, Point lastPoint) {
        buffer[index] = amp;
        if (lastPoint == null) {
            lastPoint = new Point(0, getHeight() - (int) ((getHeight() / (float) mMaxValue) * amp));
            return;
        }

        int xRight = (int) (lastPoint.x + pointStep);
        mCanvas = mSurfaceHolder.lockCanvas(new Rect(lastPoint.x, 0, xRight, getHeight()));
        if (mCanvas == null) return;

        mCanvas.drawRect(new Rect(lastPoint.x, 0, xRight, getHeight()), mBackgroundPaint);
        Point newPoint = new Point(xRight, getHeight() - (int) ((getHeight() / (float) mMaxValue) * amp));
        mCanvas.drawLine(lastPoint.x, lastPoint.y, newPoint.x, newPoint.y, paint);
        lastPoint.set(newPoint.x, newPoint.y);

        mSurfaceHolder.unlockCanvasAndPost(mCanvas);
        postInvalidate();
    }

    public void reset() {
        mDataBufferIndexECG = 0;
        mDataBufferIndexSpO2 = 0;
        mLastPointECG = null;
        mLastPointSpO2 = null;
        Canvas c = mSurfaceHolder.lockCanvas();
        if (c != null) {
            c.drawRect(new Rect(0, 0, getWidth(), getHeight()), mBackgroundPaint);
            mSurfaceHolder.unlockCanvasAndPost(c);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceViewAvailable = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        reset();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isSurfaceViewAvailable = false;
    }
}
