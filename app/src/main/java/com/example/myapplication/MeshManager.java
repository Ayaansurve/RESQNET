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

    /**
     * Get the self node ID / endpoint ID of the current device.
     * Used to filter self out from triage and peer lists.
     */
    public String getSelfId() {
        return connectionHelper != null ? connectionHelper.getLocalEndpointId() : "self";
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

    /**
     * Broadcast profile as JSON to all connected peers.
     * Includes triage data (age, injury severity, location).
     * Logs the broadcast with profile details.
     */
    public void broadcastProfileAsJson(Context context) {
        if (connectionHelper == null) return;
        SharedPreferences prefs = context.getSharedPreferences(
                MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String role      = prefs.getString(MainActivity.KEY_USER_ROLE, MainActivity.ROLE_SURVIVOR);
        String name      = prefs.getString(MainActivity.KEY_USER_NAME, "Unknown");
        String skills    = prefs.getString("vol_medical_skills", "");
        String equipment = prefs.getString("vol_equipment", "");
        String situation = prefs.getString("survivor_description", "");
        int age          = prefs.getInt("user_age", 30);
        int injurySeverity = prefs.getInt("user_injury_severity", 0);
        String location  = prefs.getString("user_location", "");

        PeerProfile myProfile = new PeerProfile(
                getSelfId(), role, name, skills, equipment, 0.0, 0.0, situation,
                age, injurySeverity, location);

        String json = myProfile.toJsonString();
        int peerCount = connectionHelper.getPeerCount();

        // Log the broadcast
        android.util.Log.i("MeshManager",
            "=== BROADCASTING PROFILE ===\n" +
            "Role: " + role + "\n" +
            "Name: " + name + "\n" +
            "Age: " + age + "\n" +
            "InjurySeverity: " + injurySeverity + "\n" +
            "Location: " + (location != null && !location.isEmpty() ? location : "not set") + "\n" +
            "Skills: " + (skills != null && !skills.isEmpty() ? skills : "none") + "\n" +
            "Equipment: " + (equipment != null && !equipment.isEmpty() ? equipment : "none") + "\n" +
            "Situation: " + (situation != null && !situation.isEmpty() ? situation : "none") + "\n" +
            "Peers: " + peerCount + "\n" +
            "JSON Size: " + json.length() + " bytes");

        connectionHelper.broadcastMessage("PROFILE_JSON", json);
    }

    /**
     * Parse incoming JSON profile and store it.
     * Called when a peer sends their profile as JSON with triage data.
     * Logs the update with before/after comparison.
     */
    public void onJsonProfileReceived(String json) {
        try {
            PeerProfile profile = PeerProfile.fromJsonString(json);
            if (profile != null && !profile.endpointId.equals(getSelfId())) {
                // Check if this is an update to an existing profile
                PeerProfile oldProfile = peerProfiles.get(profile.endpointId);
                boolean isUpdate = oldProfile != null;

                // Store the new profile
                peerProfiles.put(profile.endpointId, profile);

                // Log the profile update with details
                if (isUpdate) {
                    android.util.Log.i("MeshManager",
                        "=== PROFILE UPDATED ===\n" +
                        "EndpointId: " + profile.endpointId + "\n" +
                        "Name: " + oldProfile.name + " → " + profile.name + "\n" +
                        "Age: " + oldProfile.age + " → " + profile.age + "\n" +
                        "InjurySeverity: " + oldProfile.injurySeverity + " → " + profile.injurySeverity + "\n" +
                        "Location: " + (oldProfile.location != null ? oldProfile.location : "null") +
                        " → " + (profile.location != null ? profile.location : "null") + "\n" +
                        "Skills: " + (oldProfile.skills != null ? oldProfile.skills : "none") +
                        " → " + (profile.skills != null ? profile.skills : "none") + "\n" +
                        "Situation: " + (oldProfile.situation != null ? oldProfile.situation : "none") +
                        " → " + (profile.situation != null ? profile.situation : "none") + "\n" +
                        "Timestamp: " + profile.timestamp);
                } else {
                    android.util.Log.i("MeshManager",
                        "=== NEW PROFILE RECEIVED ===\n" +
                        "EndpointId: " + profile.endpointId + "\n" +
                        "Name: " + profile.name + "\n" +
                        "Role: " + profile.role + "\n" +
                        "Age: " + profile.age + "\n" +
                        "InjurySeverity: " + profile.injurySeverity + "\n" +
                        "Location: " + (profile.location != null ? profile.location : "not set") + "\n" +
                        "Skills: " + (profile.skills != null && !profile.skills.isEmpty() ? profile.skills : "none") + "\n" +
                        "Timestamp: " + profile.timestamp);
                }

                // Notify all listeners
                for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                    l.onProfileReceived(profile);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MeshManager", "Failed to parse JSON profile: " + e.getMessage(), e);
        }
    }

    /**
     * Get all survivors excluding self.
     */
    public List<PeerProfile> getSurvivorsExcludingSelf() {
        String selfId = getSelfId();
        List<PeerProfile> result = new ArrayList<>();
        for (PeerProfile p : peerProfiles.values()) {
            if (p.isSurvivor() && !p.endpointId.equals(selfId)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Get all peers excluding self.
     */
    public List<PeerProfile> getPeerProfilesExcludingSelf() {
        String selfId = getSelfId();
        List<PeerProfile> result = new ArrayList<>(peerProfiles.values());
        result.removeIf(p -> p.endpointId.equals(selfId));
        return result;
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
                    // Check if this is an update to an existing profile
                    PeerProfile oldProfile = peerProfiles.get(profile.endpointId);
                    boolean isUpdate = oldProfile != null;

                    // Store the peer's profile so any Activity can query it
                    peerProfiles.put(profile.endpointId, profile);

                    // Log the profile reception
                    if (isUpdate) {
                        android.util.Log.i("MeshManager",
                            ">>> PROFILE UPDATED (wire format) <<<\n" +
                            "EndpointId: " + profile.endpointId + "\n" +
                            "Name: " + oldProfile.name + " → " + profile.name + "\n" +
                            "Role: " + profile.role + "\n" +
                            "Skills: " + (oldProfile.skills != null ? oldProfile.skills : "none") +
                            " → " + (profile.skills != null ? profile.skills : "none"));
                    } else {
                        android.util.Log.i("MeshManager",
                            ">>> NEW PROFILE RECEIVED (wire format) <<<\n" +
                            "EndpointId: " + profile.endpointId + "\n" +
                            "Name: " + profile.name + "\n" +
                            "Role: " + profile.role + "\n" +
                            "Skills: " + (profile.skills != null && !profile.skills.isEmpty() ? profile.skills : "none") + "\n" +
                            "Equipment: " + (profile.equipment != null && !profile.equipment.isEmpty() ? profile.equipment : "none"));
                    }

                    for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                        l.onProfileReceived(profile);
                    }
                }
            };
}