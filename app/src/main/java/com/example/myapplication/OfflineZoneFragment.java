package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OfflineZoneFragment extends Fragment {
    private static final String TAG = "RESQNET_OFFLINE";
    private static final String SERVICE_ID = "com.example.wordwave.OFFLINE_CHAT";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private ConnectionsClient connectionsClient;
    private final Set<String> connectedEndpoints = new HashSet<>();

    private RecyclerView recyclerView;
    private com.example.wordwave.ChatAdapterBlue adapter;
    private List<Message> messages = new ArrayList<>();
    private EditText editMessage;
    private FloatingActionButton btnSend;
    private Button btnAdvertise, btnDiscover;

    private Animation pulseAnimation;
    private boolean permissionsGranted = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offline_zone, container, false);

        initViews(view);
        pulseAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.pulse);
        connectionsClient = Nearby.getConnectionsClient(requireContext());
        requestNearbyPermissions();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewOffline);
        editMessage = view.findViewById(R.id.editTextMessage);
        btnSend = view.findViewById(R.id.buttonSend);
        btnAdvertise = view.findViewById(R.id.buttonAdvertise);
        btnDiscover = view.findViewById(R.id.buttonDiscover);

        adapter = new com.example.wordwave.ChatAdapterBlue(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        btnAdvertise.setOnClickListener(v -> {
            if (permissionsGranted) startAdvertising();
            else requestNearbyPermissions();
        });

        btnDiscover.setOnClickListener(v -> {
            if (permissionsGranted) startDiscovery();
            else requestNearbyPermissions();
        });
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        if (connectedEndpoints.isEmpty()) {
            Toast.makeText(getContext(), "No one nearby to receive message!", Toast.LENGTH_SHORT).show();
            return;
        }

        Payload payload = Payload.fromBytes(text.getBytes(StandardCharsets.UTF_8));
        for (String endpoint : connectedEndpoints) {
            connectionsClient.sendPayload(endpoint, payload);
        }

        messages.add(new Message(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        editMessage.setText("");
    }

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising("User", SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(unused -> {
                    btnAdvertise.setText("Visible");
                    btnAdvertise.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF8F00"))); // Orange
                    btnAdvertise.startAnimation(pulseAnimation);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Advertising failed", e));
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(unused -> {
                    btnDiscover.setText("Scanning...");
                    btnDiscover.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF8F00")));
                    btnDiscover.startAnimation(pulseAnimation);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Discovery failed", e));
    }

    // --- CALLBACKS ---

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                connectedEndpoints.add(endpointId);
                updateUIForConnection();
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            connectedEndpoints.remove(endpointId);
            if (connectedEndpoints.isEmpty()) resetUI();
        }
    };

    private void updateUIForConnection() {
        btnDiscover.clearAnimation();
        btnAdvertise.clearAnimation();
        btnDiscover.setText("Connected (" + connectedEndpoints.size() + ")");
        btnDiscover.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2E7D32"))); // Success Green
    }

    private void resetUI() {
        btnDiscover.setText("Scan Area");
        btnDiscover.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        btnAdvertise.setText("Be Visible");
        btnAdvertise.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6200EE")));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            connectionsClient.requestConnection("User", endpointId, connectionLifecycleCallback);
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {}
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String received = new String(payload.asBytes(), StandardCharsets.UTF_8);
                messages.add(new Message(received, false));
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String id, @NonNull PayloadTransferUpdate update) {}
    };

    // --- PERMISSIONS ---

    private void requestNearbyPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        permissionLauncher.launch(perms.toArray(new String[0]));
    }

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                permissionsGranted = !result.containsValue(false);
            });

    @Override
    public void onStop() {
        super.onStop();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
    }
}