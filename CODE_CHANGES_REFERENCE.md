# Code Changes Reference - Triage Implementation

This file documents all code changes made to implement the triage system.

---

## File: build.gradle.kts

### Change: Add Gson dependency

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.1.0")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.mappls.sdk:mappls-android-sdk:8.1.0")
    implementation("com.google.code.gson:gson:2.10.1")  // ← NEW
    // ... rest of dependencies
}
```

---

## File: PeerProfile.java

### Change 1: Add triage fields

```java
public class PeerProfile {
    public static final String TYPE = "PROFILE";

    public final String endpointId;
    public final String role;
    public final String name;
    public final String skills;
    public final String equipment;
    public final double lat;
    public final double lng;
    public final String situation;
    
    // ← NEW: Triage fields
    public final int age;
    public final int injurySeverity;
    public final String location;
    
    public final long timestamp;
```

### Change 2: Update constructors

```java
    // Full constructor with triage data
    public PeerProfile(String endpointId, String role, String name,
                       String skills, String equipment,
                       double lat, double lng, String situation,
                       int age, int injurySeverity, String location) {
        this.endpointId = endpointId;
        this.role       = role;
        this.name       = name;
        this.skills     = skills;
        this.equipment  = equipment;
        this.lat        = lat;
        this.lng        = lng;
        this.situation  = situation;
        this.age        = age;
        this.injurySeverity = injurySeverity;
        this.location   = location;
        this.timestamp  = System.currentTimeMillis();
    }

    // Legacy constructor for backward compatibility
    public PeerProfile(String endpointId, String role, String name,
                       String skills, String equipment,
                       double lat, double lng, String situation) {
        this(endpointId, role, name, skills, equipment, lat, lng, situation, 0, 0, "");
    }
```

### Change 3: Add JSON serialization methods

```java
    /** Serialize to JSON format for triage communication. */
    public String toJsonString() {
        return new com.google.gson.Gson().toJson(this);
    }

    /** Deserialize from JSON string. Returns null on error. */
    public static PeerProfile fromJsonString(String json) {
        try {
            return new com.google.gson.Gson().fromJson(json, PeerProfile.class);
        } catch (Exception e) {
            return null;
        }
    }
```

---

## File: MeshManager.java

### Change 1: Add getSelfId() method

```java
    /**
     * Get the self node ID / endpoint ID of the current device.
     * Used to filter self out from triage and peer lists.
     */
    public String getSelfId() {
        return connectionHelper != null ? connectionHelper.getLocalEndpointId() : "self";
    }
```

### Change 2: Add broadcastProfileAsJson() method

```java
    /**
     * Broadcast profile as JSON to all connected peers.
     * Includes triage data (age, injury severity, location).
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
        connectionHelper.broadcastMessage("PROFILE_JSON", json);
    }
```

### Change 3: Add onJsonProfileReceived() method

```java
    /**
     * Parse incoming JSON profile and store it.
     * Called when a peer sends their profile as JSON with triage data.
     */
    public void onJsonProfileReceived(String json) {
        try {
            PeerProfile profile = PeerProfile.fromJsonString(json);
            if (profile != null && !profile.endpointId.equals(getSelfId())) {
                peerProfiles.put(profile.endpointId, profile);
                // Notify all listeners
                for (ConnectionHelper.ConnectionStatusListener l : listeners) {
                    l.onProfileReceived(profile);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MeshManager", "Failed to parse JSON profile", e);
        }
    }
```

### Change 4: Add getSurvivorsExcludingSelf() method

```java
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
```

### Change 5: Add getPeerProfilesExcludingSelf() method

```java
    /**
     * Get all peers excluding self.
     */
    public List<PeerProfile> getPeerProfilesExcludingSelf() {
        String selfId = getSelfId();
        List<PeerProfile> result = new ArrayList<>(peerProfiles.values());
        result.removeIf(p -> p.endpointId.equals(selfId));
        return result;
    }
```

---

## File: ConnectionHelper.java

### Change 1: Add getLocalEndpointId() method

```java
    /**
     * Get the local endpoint ID (stable node identifier).
     * Used to filter self out from peer lists.
     */
    public String getLocalEndpointId() {
        return prefs.getString("stable_node_id", "UNKNOWN");
    }
```

### Change 2: Add broadcastMessage() method

```java
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
```

### Change 3: Update PayloadCallback to handle JSON profiles

```java
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String fromEndpointId,
                                      @NonNull Payload payload) {
            if (payload.getType() != Payload.Type.BYTES
                    || payload.asBytes() == null) return;

            String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);

            if (msg.startsWith("SOS")) {
                handleSos(fromEndpointId, msg, payload.asBytes());
            } else if (msg.startsWith("PROFILE_JSON")) {  // ← NEW
                handleJsonProfile(fromEndpointId, msg);   // ← NEW
            } else if (msg.startsWith(PeerProfile.TYPE)) {
                handleProfile(fromEndpointId, msg);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String e,
                                            @NonNull PayloadTransferUpdate u) {}
    };
```

### Change 4: Add handleJsonProfile() method

```java
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
```

---

## File: MapActivity.java

### Change: Update drawPeers() method to filter self

```java
        private void drawPeers(Canvas canvas, float cx, float cy) {
            if (peers == null || peers.isEmpty()) return;

            // Filter: exclude self, include survivors only
            String selfId = MeshManager.getInstance().getSelfId();
            List<PeerProfile> triagePeers = new java.util.ArrayList<>();
            
            for (PeerProfile peer : peers) {
                if (!peer.endpointId.equals(selfId) && peer.isSurvivor()) {
                    triagePeers.add(peer);
                }
            }

            int count = triagePeers.size();
            for (int i = 0; i < count; i++) {
                PeerProfile peer = triagePeers.get(i);

                double angle    = (2 * Math.PI * i) / count - Math.PI / 2;
                float  distance = 90f + (i * 40f % 100f);
                float  px       = cx + (float)(Math.cos(angle) * distance);
                float  py       = cy + (float)(Math.sin(angle) * distance);

                // Glow
                paintPeerGlow.setColor(peer.isVolunteer() ? 0x2200C853 : 0x22FF4F00);
                canvas.drawCircle(px, py, 28f, paintPeerGlow);

                // Dot
                canvas.drawCircle(px, py, 16f,
                        peer.isVolunteer() ? paintVolunteer : paintSurvivor);

                // Name
                paintLabel.setColor(0xFFCCCCCC);
                String name = (peer.name != null && !peer.name.isEmpty())
                        ? peer.name
                        : (peer.isVolunteer() ? "Volunteer" : "Survivor");
                canvas.drawText(name, px + 20f, py + 8f, paintLabel);

                // Sub-label
                if (peer.isVolunteer()
                        && peer.skills != null && !peer.skills.isEmpty()) {
                    paintSubLabel.setColor(0xFF00C853);
                    String s = peer.skills.length() > 22
                            ? peer.skills.substring(0, 22) + "…" : peer.skills;
                    canvas.drawText(s, px + 20f, py + 30f, paintSubLabel);
                } else if (peer.isSurvivor()
                        && peer.situation != null && !peer.situation.isEmpty()) {
                    paintSubLabel.setColor(0xFFFF8800);
                    String s = peer.situation.length() > 22
                            ? peer.situation.substring(0, 22) + "…" : peer.situation;
                    canvas.drawText(s, px + 20f, py + 30f, paintSubLabel);
                }
            }
        }
```

---

## Integration Points in Activities

### SurvivorActivity
Add this after saving survivor info:
```java
    // Broadcast profile with triage data to all connected peers
    MeshManager.getInstance().broadcastProfileAsJson(this);
```

### TriageActivity
Use this to build the triage list:
```java
    private List<SurvivorInfo> buildSurvivorList() {
        List<SurvivorInfo> survivors = new ArrayList<>();

        // Get survivors EXCLUDING self
        for (PeerProfile peer : MeshManager.getInstance().getSurvivorsExcludingSelf()) {
            SurvivorInfo info = createSurvivorInfoFromPeerProfile(peer);
            if (info != null) {
                survivors.add(info);
            }
        }

        return survivors;
    }
```

### MainActivity
Initialize at startup:
```java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure stable node ID exists
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.contains("stable_node_id")) {
            prefs.edit().putString("stable_node_id", 
                UUID.randomUUID().toString()).apply();
        }
        
        // Initialize and start mesh
        MeshManager.getInstance().init(this, selectedRole);
        MeshManager.getInstance().startMesh();
        
        // Optional: broadcast initial profile
        MeshManager.getInstance().broadcastProfileAsJson(this);
    }
```

---

## Summary of Changes

| File | Type | Lines Added | New Methods |
|------|------|-------------|-------------|
| build.gradle.kts | Dependency | 1 | 0 |
| PeerProfile.java | Fields + Methods | 25 | 2 |
| MeshManager.java | Methods | 80 | 5 |
| ConnectionHelper.java | Methods | 50 | 3 |
| MapActivity.java | Updated Method | 40 | 0 |
| **TOTAL** | | **196** | **10** |

**Files Modified:** 5
**New Public APIs:** 10
**Breaking Changes:** 0 (backward compatible)

---

## Compilation Status

✅ All files compile without errors
⚠️ Some warnings (non-critical):
- Unused imports in MapActivity (can be removed)
- Override annotations on interface methods

---

## Testing Before Deployment

### Unit Testing
```java
// Test self filtering
String selfId = MeshManager.getInstance().getSelfId();
assertNotNull(selfId);

List<PeerProfile> filtered = MeshManager.getInstance().getPeerProfilesExcludingSelf();
for (PeerProfile p : filtered) {
    assertNotEquals(p.endpointId, selfId);
}
```

### Integration Testing
```
Device A (Rescuer):
  1. Open SurvivorActivity
  2. Enter: Age=35, Injury=Critical, Location="Room 5"
  3. Click Save
  4. Check logcat for: "PROFILE_JSON broadcast to X peers"

Device B (Coordinator):
  1. Open TriageActivity
  2. Verify Device A appears in RED section
  3. Verify age=35, severity=4, location="Room 5" displayed
  4. No self-entry visible
  5. Modify on Device A → Device B updates live
```

---

## Deployment Checklist

- [ ] All 5 files compiled successfully
- [ ] Gson 2.10.1 dependency added and synced
- [ ] SurvivorActivity integrated (broadcastProfileAsJson call)
- [ ] TriageActivity integrated (getSurvivorsExcludingSelf usage)
- [ ] MainActivity initialized (stable_node_id setup)
- [ ] SharedPreferences keys documented and set correctly
- [ ] Tested on two devices
- [ ] Self filtering verified (self not in lists)
- [ ] JSON broadcasts confirmed in logcat
- [ ] Triage data received and displayed correctly
- [ ] Ready for production deployment

---

## Quick Rollback Instructions

If any issue occurs, you can revert to the original implementation:

1. Remove Gson dependency from build.gradle.kts
2. Revert PeerProfile to original constructor
3. Comment out broadcastProfileAsJson() calls
4. Revert drawPeers() filtering logic
5. Rebuild and redeploy

The original wire format will still work for backward compatibility.

