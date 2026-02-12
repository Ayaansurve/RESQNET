package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextView statusLabel;
    private EditText messageInput;
    private RecyclerView recyclerView;
    private ImageButton sendButton; // Changed to ImageButton to match the new UI
    private SosFlashlight sosFlashlight;

    // Adapters & Logic
    private ChatAdapter chatAdapter;
    private BleLinkManager bleManager;

    // Permission Code
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize UI Elements
        statusLabel = findViewById(R.id.statusLabel);
        messageInput = findViewById(R.id.messageInput);
        recyclerView = findViewById(R.id.recyclerView);
        sendButton = findViewById(R.id.sendButton);

        // 2. Setup the Chat List (RecyclerView)
        chatAdapter = new ChatAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // 3. Initialize the Offline Mesh Manager
        bleManager = new BleLinkManager(this, new BleLinkManager.MessageListener() {
            @Override
            public void onMessageReceived(String message) {
                // Must run on UI thread to update the screen
                runOnUiThread(() -> {
                    chatAdapter.addMessage(message, false); // false = received from others
                    scrollToBottom();
                });
            }

            @Override
            public void onStatusUpdate(String status) {
                runOnUiThread(() -> {
                    // Check if status contains signal strength
                    if (status.contains("dBm")) {
                        // Extract number (e.g., -60)
                        // Logic: -40 is CLOSE, -90 is FAR
                        statusLabel.setText(status);

                        // Visual Color Coding
                        if (status.contains("-4") || status.contains("-5")) {
                            statusLabel.setTextColor(getColor(android.R.color.holo_green_light)); // Very Close
                        } else if (status.contains("-9")) {
                            statusLabel.setTextColor(getColor(android.R.color.holo_red_light)); // Far away
                        } else {
                            statusLabel.setTextColor(getColor(R.color.safety_orange)); // Medium
                        }
                    } else {
                        statusLabel.setText(status.toUpperCase());
                    }
                });
            }
        });

        // 4. Check Permissions & Start Automatically
        if (checkAndRequestPermissions()) {
            startMeshNetwork();
        }

        // 5. Send Button Logic
        sendButton.setOnClickListener(v -> {
            String msg = messageInput.getText().toString().trim();
            if (!msg.isEmpty()) {
                // Send via Bluetooth
                bleManager.sendMessage(msg);

                // Show on my screen immediately
                chatAdapter.addMessage(msg, true); // true = sent by me
                scrollToBottom();

                // Clear input
                messageInput.setText("");
            }
        });
        sosFlashlight = new SosFlashlight(this);
        ImageButton btnSos = findViewById(R.id.btnSos);

        btnSos.setOnClickListener(v -> {
            // Toggle logic
            if (btnSos.isSelected()) {
                sosFlashlight.stopSos();
                btnSos.setSelected(false);
                Toast.makeText(this, "SOS Stopped", Toast.LENGTH_SHORT).show();
            } else {
                sosFlashlight.startSos();
                btnSos.setSelected(true);
                Toast.makeText(this, "SOS Broadcasting!", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Helper to auto-scroll to the newest message
    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void startMeshNetwork() {
        // Start acting as both a Server (Receiver) and Client (Scanner)
        bleManager.startServer();
        bleManager.startScanning();
        Toast.makeText(this, "RESQNET Started: Scanning...", Toast.LENGTH_SHORT).show();
    }

    // --- PERMISSION HANDLING (Crucial for Android 12+) ---

    private boolean checkAndRequestPermissions() {
        List<String> neededPermissions = new ArrayList<>();

        // Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        }
        // Android 11 and below (Legacy)
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    neededPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startMeshNetwork();
            } else {
                Toast.makeText(this, "Permissions Missing. App cannot function.", Toast.LENGTH_LONG).show();
                statusLabel.setText("PERMISSION DENIED");
            }
        }
    }


}