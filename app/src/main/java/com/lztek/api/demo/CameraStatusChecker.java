package com.lztek.api.demo;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants; // Import UsbConstants
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class CameraStatusChecker {

    private static final String TAG = "CameraStatusChecker";
    private HandlerThread handlerThread;
    private Handler handler;
    private GlobalVars globalVars;
    private boolean isRunning = false;
    private Context context;

    public CameraStatusChecker(Context context) {
        this.context = context;
        globalVars = GlobalVars.getInstance();
        handlerThread = new HandlerThread("CameraStatusThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void startChecking() {
        if (isRunning) return;
        isRunning = true;
        handler.postDelayed(statusCheckerRunnable, 0); // Start immediately
    }

    public void stopChecking() {
        isRunning = false;
        handler.removeCallbacks(statusCheckerRunnable);
        handlerThread.quitSafely();
    }

    private final Runnable statusCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning || globalVars.isCameraPreviewActive()) {
                handler.postDelayed(this, 5000);
                return;
            }

            checkCameraStatus();
            handler.postDelayed(this, 1000); // Check every 1 second
        }
    };

    private void checkCameraStatus() {
        // Check internal (front) camera
        int frontCameraId = -1;
        Camera tempCamera = null;
        try {
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }
            if (frontCameraId != -1) {
                tempCamera = Camera.open(frontCameraId);
                globalVars.setInternalCameraConnected(tempCamera != null);
            } else {
                globalVars.setInternalCameraConnected(false);
            }
        } catch (Exception e) {
//            Log.e(TAG, "Internal Camera Check Error: " + e.getMessage());
            globalVars.setInternalCameraConnected(false);
        } finally {
            if (tempCamera != null) {
                tempCamera.release();
            }
        }

        // Check USB/external camera
        int usbCameraId = -1;
        tempCamera = null;
        try {
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    usbCameraId = i;
                    break;
                }
            }
            if (usbCameraId != -1) {
                tempCamera = Camera.open(usbCameraId);
                globalVars.setUSBCameraConnected(tempCamera != null);
            } else {
                globalVars.setUSBCameraConnected(false);
            }
        } catch (Exception e) {
//            Log.e(TAG, "USB Camera Check Error: " + e.getMessage());
            globalVars.setUSBCameraConnected(false);
        } finally {
            if (tempCamera != null) {
                tempCamera.release();
            }
        }

        // Check USB keyboard dongle
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            boolean keyboardConnected = false;
            if (usbManager != null) {
                for (UsbDevice device : usbManager.getDeviceList().values()) {
                    // Check if the device is an HID device (keyboard/mouse)
                    if (device.getDeviceClass() == UsbConstants.USB_CLASS_HID ||
                            (device.getInterfaceCount() > 0 &&
                                    device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_HID)) {
                        keyboardConnected = true;
                        break;
                    }
                }
            }
//            Log.d(TAG, "USB Keyboard dongle connected: " + keyboardConnected);
            globalVars.setKeyboardConnected(keyboardConnected);
        } catch (Exception e) {
//            Log.e(TAG, "Keyboard Check Error: " + e.getMessage());
            globalVars.setKeyboardConnected(false);
        }
    }
}