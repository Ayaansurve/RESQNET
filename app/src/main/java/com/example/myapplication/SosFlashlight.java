package com.example.myapplication;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;

public class SosFlashlight {
    private final CameraManager cameraManager;
    private final String cameraId;
    private boolean isFlashing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Morse Code Timing (in milliseconds)
    // DOT = 200, DASH = 600, GAP = 200
    private final long[] sosPattern = {
            200, 200, 200, // ... (S)
            200, 600, 200, 600, 200, 600, // --- (O)
            200, 200, 200, 200, 200 // ... (S) and pause
    };

    private int patternIndex = 0;

    public SosFlashlight(Context context) {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0]; // Usually the back camera
        } catch (Exception e) {
            throw new RuntimeException("No camera found");
        }
    }

    public void startSos() {
        if (isFlashing) return;
        isFlashing = true;
        patternIndex = 0;
        runFlashLoop();
    }

    public void stopSos() {
        isFlashing = false;
        handler.removeCallbacksAndMessages(null);
        turnTorch(false);
    }

    private void runFlashLoop() {
        if (!isFlashing) return;

        // Toggle logic: If index is even, turn ON. If odd, turn OFF.
        boolean turnOn = (patternIndex % 2 == 0);
        turnTorch(turnOn);

        // Calculate delay for next step
        // Just a simple fast strobe for demo purposes is often more stable than complex Morse
        long delay = 500; // Flash every half second

        handler.postDelayed(this::runFlashLoop, delay);
    }

    private void turnTorch(boolean on) {
        try {
            cameraManager.setTorchMode(cameraId, on);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}