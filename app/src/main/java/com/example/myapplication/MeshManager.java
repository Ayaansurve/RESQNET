package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.MainActivity;
import com.example.myapplication.PeerProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeshManager — Application-wide singleton for the P2P mesh.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ROOT CAUSE FIX for "mesh not formed" in SurvivorActivity:
 *
 * Previously every Activity created its own NEW ConnectionHelper instance.
 * Each new instance has an empty connectedPeers map — so getPeerCount() always
 * returned 0 even when MainActivity's mesh had live connections.
 *
 * The fix: ONE ConnectionHelper instance lives here in a singleton.
 * MainActivity starts it. SurvivorActivity, VolunteerActivity, and
 * MapActivity all call MeshManager.getInstance() to get the same instance.
 * They register/unregister themselves as listeners to receive callbacks.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class MeshManager {

    private static MeshManager instance;

    private ConnectionHelper connectionHelper;

    // All currently connected peer profiles, keyed by endpointId
    private final Map<String, PeerProfile> peerProfiles =
            new ConcurrentHashMap<>();

    // All registered listeners (one per active Activity)
    private final List<ConnectionHelper.ConnectionStatusListener> listeners =
            Collections.synchronizedList(new ArrayList<>());

    // ── Singleton access ──────────────────────────────────────────────────────

    public static synchronized MeshManager getInstance() {
        if (instance == null) instance = new MeshManager();
        return instance;
    }

    private MeshManager() {}

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Called ONCE from MainActivity.onCreate() after permissions are granted.
     * Creates the ConnectionHelper and starts the mesh.
     */
    public void init(Context context, String initialRole) {
        if (connectionHelper != null) return; // already initialised

        connectionHelper = new ConnectionHelper(
                context,
                masterListener,
                initialRole
        );
    }

    public void startMesh() {
        if (connectionHelper != null) connectionHelper.startMesh();
    }

    public void stopMesh() {
        if (connectionHelper != null) connectionHelper.stopMesh();
    }

    public void updateRole(String role) {
        if (connectionHelper != null) connectionHelper.updateRoleAndRestart(role);
    }

    // ── Peer data ─────────────────────────────────────────────────────────────

    /** Returns a snapshot of all currently connected peer profiles. */
    public List<PeerProfile> getPeerProfiles() {
        return new ArrayList<>(peerProfiles.values());
    }

    /** Returns only volunteer profiles. */
    public List<PeerProfile> getVolunteers() {
        List<PeerProfile> result = new ArrayList<>();
        for (PeerProfile p : peerProfiles.values()) {
            if (p.isVolunteer()) result.add(p);
        }
        return result;
    }

    /** Returns only survivor profiles. */
    public List<PeerProfile> getSurvivors() {
        List<PeerProfile> result = new ArrayList<>();
        for (PeerProfile p : peerProfiles.values()) {
            if (p.isSurvivor()) result.add(p);
        }
        return result;
    }

    public int getPeerCount() {
        return connectionHelper != null ? connectionHelper.getPeerCount() : 0;
    }

    public void broadcastSOS() {
        if (connectionHelper != null) connectionHelper.broadcastSOS();
    }

    /**
     * Send this device's own profile to a specific peer after connecting.
     * Called by ConnectionHelper after a successful connection.
     */
    public void sendProfileTo(Context context, String endpointId) {
        if (connectionHelper == null) return;
        SharedPreferences prefs = context.getSharedPreferences(
                MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String role      = prefs.getString(MainActivity.KEY_USER_ROLE, MainActivity.ROLE_SURVIVOR);
        String name      = prefs.getString(MainActivity.KEY_USER_NAME, "Unknown");
        String skills    = prefs.getString("vol_medical_skills", "");
        String equipment = prefs.getString("vol_equipment", "");
        String situation = prefs.getString("survivor_description", "");

        PeerProfile myProfile = new PeerProfile(
                "self", role, name, skills, equipment, 0.0, 0.0, situation);

        connectionHelper.sendProfilePayload(endpointId, myProfile.toWireFormat());
    }

    // ── Listener registration ─────────────────────────────────────────────────

    /**
     * Activities register on onResume() and unregister on onPause()
     * so they receive mesh callbacks only when visible.
     */
    public void addListener(ConnectionHelper.ConnectionStatusListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(ConnectionHelper.ConnectionStatusListener l) {
        listeners.remove(l);
    }

    // ── Master listener — relays events to all registered activities ──────────

    private final ConnectionHelper.ConnectionStatusListener masterListener =
            new ConnectionHelper.ConnectionStatusListener() {

                @Override
                public void onPeerCountChanged(int peerCount) {
                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onPeerCountChanged(peerCount);
                    }
                }

                @Override
                public void onPeerConnected(String endpointName) {
                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onPeerConnected(endpointName);
                    }
                }

                @Override
                public void onPeerDisconnected(String endpointId) {
                    peerProfiles.remove(endpointId);
                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onPeerDisconnected(endpointId);
                    }
                }

                @Override
                public void onSosReceived(String fromNodeId) {
                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onSosReceived(fromNodeId);
                    }
                }

                @Override
                public void onProfileReceived(PeerProfile profile) {
                    // Store the peer's profile so any Activity can query it
                    peerProfiles.put(profile.endpointId, profile);
                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onProfileReceived(profile);
                    }
                }
            };
}