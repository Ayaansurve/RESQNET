package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import com.example.myapplication.MainActivity;
import com.example.myapplication.PeerProfile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConnectionHelper {

    private static final String TAG        = "ConnectionHelper";
    private static final String SERVICE_ID = "com.resqnet.mesh.v1";
    private static final String SEP        = "|";
    private static final String PREFIX     = "RESQNET";

    private final Context context;
    private final ConnectionsClient nearbyClient;
    private final ConnectionStatusListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private final Map<String, String> connectedPeers =
            Collections.synchronizedMap(new HashMap<>());
    private final Set<String> pendingEndpoints =
            Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> pendingEndpointNames =
            Collections.synchronizedMap(new HashMap<>());

    private String currentRole;
    private boolean isRunning = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ConnectionHelper(@NonNull Context context,
                            @NonNull ConnectionStatusListener listener,
                            @NonNull String initialRole) {
        this.context      = context.getApplicationContext();
        this.listener     = listener;
        this.nearbyClient = Nearby.getConnectionsClient(this.context);
        this.prefs        = context.getSharedPreferences(
                MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.currentRole  = initialRole;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startMesh() {
        isRunning = true;
        startAdvertising();
        startDiscovery();
        Log.i(TAG, "Mesh started. EndpointName=" + buildEndpointName(currentRole));
    }

    public void stopMesh() {
        isRunning = false;
        nearbyClient.stopAdvertising();
        nearbyClient.stopDiscovery();
        nearbyClient.stopAllEndpoints();
        connectedPeers.clear();
        pendingEndpoints.clear();
        pendingEndpointNames.clear();
    }

    public void updateRoleAndRestart(String newRole) {
        this.currentRole = newRole;
        if (isRunning) {
            nearbyClient.stopAdvertising();
            startAdvertising();
        }
    }

    public int getPeerCount() {
        return connectedPeers.size();
    }

    /**
     * Get the local endpoint ID (stable node identifier).
     * Used to filter self out from peer lists.
     */
    public String getLocalEndpointId() {
        return prefs.getString("stable_node_id", "UNKNOWN");
    }

    public void broadcastSOS() {
        if (connectedPeers.isEmpty()) {
            Log.w(TAG, "SOS: no peers.");
            return;
        }
        String nodeId = prefs.getString("stable_node_id", "UNKNOWN");
        String packet = "SOS" + SEP + nodeId + SEP + System.currentTimeMillis();
        byte[] bytes  = packet.getBytes(StandardCharsets.UTF_8);
        for (String id : connectedPeers.keySet()) {
            nearbyClient.sendPayload(id, Payload.fromBytes(bytes));
        }
        Log.i(TAG, "SOS sent to " + connectedPeers.size() + " peers.");
    }

    /**
     * Send this device's profile string to one specific peer.
     * Called by MeshManager right after a connection is established.
     */
    public void sendProfilePayload(String endpointId, String profileWire) {
        byte[] bytes = profileWire.getBytes(StandardCharsets.UTF_8);
        nearbyClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Profile send failed: " + e.getMessage()));
        Log.d(TAG, "Profile sent to " + endpointId);
    }

    /**
     * Broadcast a message to all connected peers.
     * Used for JSON profile exchange and other protocol messages.
     */
    public void broadcastMessage(String messageType, String payload) {
        if (connectedPeers.isEmpty()) {
            Log.w(TAG, messageType + ": no peers to broadcast to.");
            return;
        }
        String packet = messageType + SEP + payload;
        byte[] bytes = packet.getBytes(StandardCharsets.UTF_8);
        for (String id : connectedPeers.keySet()) {
            nearbyClient.sendPayload(id, Payload.fromBytes(bytes))
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Broadcast failed to " + id + ": " + e.getMessage()));
        }
        Log.d(TAG, messageType + " broadcast to " + connectedPeers.size() + " peers.");
    }

    // ── EndpointName helpers ──────────────────────────────────────────────────

    public String buildEndpointName(String role) {
        String name = prefs.getString(MainActivity.KEY_USER_NAME, "Unknown");
        return PREFIX + SEP + role + SEP + name;
    }

    public static String parseRoleFromEndpointName(String n) {
        if (n == null) return "SURVIVOR";
        String[] p = n.split("\\" + SEP);
        return p.length >= 2 ? p[1] : "SURVIVOR";
    }

    public static String parseNameFromEndpointName(String n) {
        if (n == null) return "Unknown";
        String[] p = n.split("\\" + SEP);
        return p.length >= 3 ? p[2] : "Unknown";
    }

    public static boolean isVolunteer(String endpointName) {
        return "VOLUNTEER".equals(parseRoleFromEndpointName(endpointName));
    }

    // ── Advertising / Discovery ───────────────────────────────────────────────

    private void startAdvertising() {
        nearbyClient.startAdvertising(
                buildEndpointName(currentRole),
                SERVICE_ID,
                connectionLifecycleCallback,
                new AdvertisingOptions.Builder()
                        .setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener(e ->
                Log.w(TAG, "Advertising failed: " + e.getMessage()));
    }

    private void startDiscovery() {
        nearbyClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                new DiscoveryOptions.Builder()
                        .setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener(e ->
                Log.w(TAG, "Discovery failed: " + e.getMessage()));
    }

    // ── Nearby callbacks ──────────────────────────────────────────────────────

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId,
                                            @NonNull DiscoveredEndpointInfo info) {
                    if (!info.getEndpointName().startsWith(PREFIX)) return;
                    if (connectedPeers.containsKey(endpointId)) return;
                    if (pendingEndpoints.contains(endpointId)) return;
                    pendingEndpoints.add(endpointId);
                    nearbyClient.requestConnection(
                            buildEndpointName(currentRole), endpointId,
                            connectionLifecycleCallback
                    ).addOnFailureListener(e -> {
                        pendingEndpoints.remove(endpointId);
                        Log.w(TAG, "Request failed: " + e.getMessage());
                    });
                }
                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    pendingEndpoints.remove(endpointId);
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId,
                                                  @NonNull ConnectionInfo info) {
                    pendingEndpointNames.put(endpointId, info.getEndpointName());
                    nearbyClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                               @NonNull ConnectionResolution result) {
                    pendingEndpoints.remove(endpointId);
                    String name = pendingEndpointNames.remove(endpointId);
                    if (name == null) name = "";

                    if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                        connectedPeers.put(endpointId, name);
                        final String finalName = name;

                        // Notify UI
                        mainHandler.post(() -> {
                            listener.onPeerConnected(finalName);
                            listener.onPeerCountChanged(connectedPeers.size());
                        });

                        // Immediately send our own profile to the new peer
                        // so they know our role, name, skills etc.
                        MeshManager.getInstance().sendProfileTo(context, endpointId);

                        Log.i(TAG, "Connected: " + endpointId + " total=" + connectedPeers.size());
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    connectedPeers.remove(endpointId);
                    pendingEndpointNames.remove(endpointId);
                    mainHandler.post(() -> {
                        listener.onPeerDisconnected(endpointId);
                        listener.onPeerCountChanged(connectedPeers.size());
                    });
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String fromEndpointId,
                                      @NonNull Payload payload) {
            if (payload.getType() != Payload.Type.BYTES
                    || payload.asBytes() == null) return;

            String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);

            if (msg.startsWith("SOS")) {
                handleSos(fromEndpointId, msg, payload.asBytes());
            } else if (msg.startsWith("PROFILE_JSON")) {
                handleJsonProfile(fromEndpointId, msg);
            } else if (msg.startsWith(PeerProfile.TYPE)) {
                handleProfile(fromEndpointId, msg);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String e,
                                            @NonNull PayloadTransferUpdate u) {}
    };

    // ── Payload handlers ──────────────────────────────────────────────────────

    private void handleSos(String fromId, String msg, byte[] raw) {
        String[] parts  = msg.split("\\" + SEP);
        String fromNode = parts.length >= 2 ? parts[1] : fromId;
        mainHandler.post(() -> listener.onSosReceived(fromNode));
        // Relay to all other peers
        for (String id : connectedPeers.keySet()) {
            if (!id.equals(fromId)) {
                nearbyClient.sendPayload(id, Payload.fromBytes(raw));
            }
        }
    }

    private void handleProfile(String fromId, String msg) {
        PeerProfile profile = PeerProfile.fromWireFormat(fromId, msg);
        if (profile == null) {
            Log.w(TAG, "Failed to parse profile from " + fromId);
            return;
        }
        Log.i(TAG, "Profile received: " + profile);
        mainHandler.post(() -> listener.onProfileReceived(profile));
    }

    /**
     * Handle incoming JSON profile from a peer.
     * Includes triage data: age, injury severity, location.
     */
    private void handleJsonProfile(String fromId, String msg) {
        try {
            // Remove "PROFILE_JSON|" prefix
            int separatorIndex = msg.indexOf(SEP);
            if (separatorIndex < 0) return;
            String jsonPayload = msg.substring(separatorIndex + 1);

            // Delegate to MeshManager for parsing and storage
            MeshManager.getInstance().onJsonProfileReceived(jsonPayload);

            // Relay to all other peers (flood the network with updated profiles)
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            for (String id : connectedPeers.keySet()) {
                if (!id.equals(fromId)) {
                    nearbyClient.sendPayload(id, Payload.fromBytes(bytes));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to handle JSON profile: " + e.getMessage());
        }
    }

    // ── Listener interface ────────────────────────────────────────────────────

    public interface ConnectionStatusListener {
        void onPeerCountChanged(int peerCount);
        void onPeerConnected(String endpointName);
        void onPeerDisconnected(String endpointId);
        void onSosReceived(String fromNodeId);
        /** Called when a connected peer sends us their full profile. */
        void onProfileReceived(PeerProfile profile);
    }
}