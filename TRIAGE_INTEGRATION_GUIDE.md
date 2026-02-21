# Triage Integration Guide

This guide shows how to integrate the new triage data communication system into your Activities.

## Quick Start

### 1. SurvivorActivity - Broadcasting Triage Data

When the user updates their survivor information, broadcast it to all peers:

```java
@Override
protected void onSurvivorInfoSaved() {
    // Your existing code to save to SharedPreferences
    prefs.edit()
        .putInt("user_age", ageValue)
        .putInt("user_injury_severity", injurySeverityValue)
        .putString("user_location", locationValue)
        .apply();
    
    // NEW: Broadcast updated profile as JSON to all peers
    MeshManager.getInstance().broadcastProfileAsJson(this);
    
    Toast.makeText(this, "Profile shared with team", Toast.LENGTH_SHORT).show();
}
```

### 2. TriageActivity - Using Filtered Survivors

Instead of getting all survivors, get survivors excluding self:

```java
private List<SurvivorInfo> buildSurvivorList() {
    List<SurvivorInfo> survivors = new ArrayList<>();

    // Get all survivors EXCEPT self
    for (PeerProfile peer : MeshManager.getInstance().getSurvivorsExcludingSelf()) {
        SurvivorInfo info = createSurvivorInfoFromPeerProfile(peer);
        if (info != null) {
            survivors.add(info);
        }
    }

    // Note: Do NOT add self to list in triage view
    // Self is shown in a different section if needed

    return survivors;
}
```

### 3. MapActivity - Excluding Self from Map

The MapActivity already has this implemented. It now:
- Excludes self using `MeshManager.getInstance().getSelfId()`
- Shows only survivors (not volunteers) on the map
- Displays injury details in the triage view

### 4. MainActivity - Initialize and Broadcast Initial Profile

In MainActivity onCreate/onResume, initialize the mesh and optionally broadcast first profile:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... existing code ...
    
    // Initialize MeshManager with initial role
    MeshManager.getInstance().init(this, selectedRole);
    MeshManager.getInstance().startMesh();
    
    // Optional: Send initial profile with triage data to all peers
    MeshManager.getInstance().broadcastProfileAsJson(this);
}
```

---

## Complete Integration Example

### SurvivorActivity Integration

```java
public class SurvivorActivity extends AppCompatActivity {
    private ActivitySurvivorBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySurvivorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        
        binding.btnSaveInfo.setOnClickListener(v -> saveSurvivorInfo());
    }

    private void saveSurvivorInfo() {
        int age = Integer.parseInt(binding.editAge.getText().toString());
        int injurySeverity = binding.spinnerInjury.getSelectedItemPosition() + 1;
        String location = binding.editLocation.getText().toString();
        String situation = binding.editSituation.getText().toString();
        int peopleCount = Integer.parseInt(binding.editPeople.getText().toString());

        // Save to local SharedPreferences
        prefs.edit()
            .putInt("user_age", age)
            .putInt("user_injury_severity", injurySeverity)
            .putString("user_location", location)
            .putString("survivor_description", situation)
            .putInt("survivor_people_count", peopleCount)
            .apply();

        // NEW: Broadcast profile with triage data to all connected peers
        MeshManager.getInstance().broadcastProfileAsJson(this);

        Toast.makeText(this, "Information saved and shared with team", 
                Toast.LENGTH_SHORT).show();
        
        // Optional: Show confirmation that broadcast succeeded
        int peerCount = MeshManager.getInstance().getPeerCount();
        Log.d("SurvivorActivity", "Broadcasted to " + peerCount + " peers");
    }
}
```

### TriageActivity Integration

```java
public class TriageActivity extends AppCompatActivity 
        implements ConnectionHelper.ConnectionStatusListener {
    
    private ActivityTriageBinding binding;
    
    @Override
    protected void onResume() {
        super.onResume();
        // Register to receive mesh updates
        MeshManager.getInstance().addListener(this);
        refreshTriageView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister when not visible
        MeshManager.getInstance().removeListener(this);
    }

    private void refreshTriageView() {
        if (binding == null) return;

        binding.layoutTriageList.removeAllViews();

        // Get survivors EXCLUDING self
        List<SurvivorInfo> survivorList = buildSurvivorList();

        if (survivorList.isEmpty()) {
            binding.tvNoSurvivors.setVisibility(View.VISIBLE);
            binding.layoutTriageList.setVisibility(View.GONE);
            return;
        }

        binding.tvNoSurvivors.setVisibility(View.GONE);
        binding.layoutTriageList.setVisibility(View.VISIBLE);

        // Prioritize by injury severity, age, location
        List<SurvivorInfo> triaged = TriageCalculator.calculateTriage(survivorList);

        // Group by triage category (RED, YELLOW, GREEN, WHITE)
        List<List<SurvivorInfo>> grouped = TriageCalculator.groupByTriageCategory(triaged);

        String[] categoryLabels = {"IMMEDIATE (RED)", "URGENT (YELLOW)", 
                                  "DELAYED (GREEN)", "MINOR (WHITE)"};
        int[] categoryColors = {0xFFD32F2F, 0xFFFF6F00, 0xFFFFD600, 0xFF00C853};

        int categoryIndex = 0;
        for (List<SurvivorInfo> category : grouped) {
            if (!category.isEmpty()) {
                binding.layoutTriageList.addView(
                    buildCategoryHeader(categoryLabels[categoryIndex], 
                                       categoryColors[categoryIndex]));

                for (SurvivorInfo survivor : category) {
                    binding.layoutTriageList.addView(buildSurvivorCard(survivor));
                }
            }
            categoryIndex++;
        }

        binding.tvRescueSequence.setText(TriageCalculator.getRescueSequence(triaged));
    }

    private List<SurvivorInfo> buildSurvivorList() {
        List<SurvivorInfo> survivors = new ArrayList<>();

        // Get survivors from mesh EXCLUDING self
        for (PeerProfile peer : MeshManager.getInstance().getSurvivorsExcludingSelf()) {
            SurvivorInfo info = createSurvivorInfoFromPeerProfile(peer);
            if (info != null) {
                survivors.add(info);
            }
        }

        return survivors;
    }

    private SurvivorInfo createSurvivorInfoFromPeerProfile(PeerProfile peer) {
        try {
            return new SurvivorInfo(
                peer.endpointId,
                peer.name,
                peer.location != null ? peer.location : "Unknown",
                SurvivorInfo.InjuryLevel.values()[Math.min(peer.injurySeverity, 3)],
                peer.age,
                1, // default people count
                peer.situation,
                peer.lat,
                peer.lng
            );
        } catch (Exception e) {
            return null;
        }
    }

    // Listener callbacks from MeshManager
    @Override public void onPeerCountChanged(int c) { refreshTriageView(); }
    @Override public void onPeerConnected(String n) { refreshTriageView(); }
    @Override public void onPeerDisconnected(String id) { refreshTriageView(); }
    @Override public void onProfileReceived(PeerProfile p) { refreshTriageView(); }
    @Override public void onSosReceived(String nodeId) { 
        showSosAlert(nodeId);
    }

    private void showSosAlert(String nodeId) {
        // Find the survivor with this ID and highlight them
        for (PeerProfile peer : MeshManager.getInstance().getPeerProfiles()) {
            if (peer.endpointId.equals(nodeId)) {
                Toast.makeText(this, "🚨 SOS from " + peer.name, 
                        Toast.LENGTH_LONG).show();
                break;
            }
        }
    }
}
```

### MapActivity - Already Updated

MapActivity already implements the filtering correctly:

```java
private void drawPeers(Canvas canvas, float cx, float cy) {
    if (peers == null || peers.isEmpty()) return;

    // Get self ID for filtering
    String selfId = MeshManager.getInstance().getSelfId();
    List<PeerProfile> triagePeers = new java.util.ArrayList<>();
    
    // Filter: exclude self, include survivors only
    for (PeerProfile peer : peers) {
        if (!peer.endpointId.equals(selfId) && peer.isSurvivor()) {
            triagePeers.add(peer);
        }
    }

    // Draw filtered list
    int count = triagePeers.size();
    for (int i = 0; i < count; i++) {
        PeerProfile peer = triagePeers.get(i);
        // ... drawing code ...
    }
}
```

---

## Configuration Requirements

### SharedPreferences Keys

Ensure the following keys are used consistently:

```
User Profile:
- "stable_node_id"           → String (endpoint ID)
- "user_age"                 → int (age in years)
- "user_injury_severity"     → int (1-5)
- "user_location"            → String (zone/area)
- "user_role"                → String ("VOLUNTEER" or "SURVIVOR")

Survivor-Specific:
- "survivor_description"     → String (free-text)
- "survivor_people_count"    → int

Volunteer-Specific:
- "vol_medical_skills"       → String (CSV)
- "vol_equipment"            → String (CSV)
```

### Dependencies

Ensure `build.gradle.kts` includes:

```kotlin
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    // ... other dependencies ...
}
```

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Device A (Rescuer)                       │
│                                                                 │
│  SurvivorActivity                                               │
│  ├─ User fills: age, injury, location                          │
│  └─ Calls: MeshManager.broadcastProfileAsJson()                │
│      ↓                                                           │
│  MeshManager                                                    │
│  ├─ Gets data from SharedPreferences                           │
│  ├─ Creates PeerProfile with triage fields                     │
│  └─ Converts to JSON via Gson                                  │
│      ↓                                                           │
│  ConnectionHelper                                              │
│  ├─ Calls: broadcastMessage("PROFILE_JSON", json)             │
│  └─ Sends to all connected peers as UTF-8 bytes               │
│      ↓                                                           │
│  ━━━━━━━━━━━━━━━━ MESH NETWORK ━━━━━━━━━━━━━━━━               │
│      ↓                                                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Device B (Coordinator)                   │
│                                                                 │
│  ConnectionHelper (PayloadCallback)                             │
│  ├─ Receives bytes                                              │
│  └─ Calls: handleJsonProfile()                                 │
│      ↓                                                           │
│  MeshManager                                                    │
│  ├─ Calls: onJsonProfileReceived(json)                         │
│  ├─ Parses JSON via Gson                                       │
│  ├─ Stores in peerProfiles map                                 │
│  └─ Notifies listeners                                         │
│      ↓                                                           │
│  TriageActivity (listener)                                      │
│  ├─ Calls: onProfileReceived(profile)                          │
│  ├─ Calls: refreshTriageView()                                 │
│  └─ Updates triage list with new survivor                      │
│                                                                 │
│  MapActivity (listener)                                         │
│  ├─ Calls: onProfileReceived(profile)                          │
│  ├─ Calls: mapView.refreshPeers()                              │
│  └─ Redraws map with updated peer positions                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Testing the Integration

### Manual Test Steps

1. **Setup:**
   - Run app on Device A (rescuer)
   - Run app on Device B (coordinator)
   - Both devices connect via Bluetooth/WiFi Direct

2. **Test Self-Filtering:**
   - On Device A, navigate to MapActivity
   - Verify Device A (YOU) is at center with blue dot
   - Verify Device A does NOT appear in the surrounding peer rings
   - Verify Device B appears as orange dot (survivor)

3. **Test Triage Broadcast:**
   - On Device A, open SurvivorActivity
   - Enter: Age=35, Injury=Critical (level 4), Location="Building A"
   - Click Save
   - Check Android Studio logcat: "PROFILE_JSON broadcast to 1 peers"

4. **Test Triage Reception:**
   - On Device B, open TriageActivity
   - Verify Device A appears in IMMEDIATE (RED) section
   - Verify age, injury level, and location are displayed

5. **Test Triage Prioritization:**
   - On Device A, change injury to DELAYED (level 2)
   - Click Save (broadcasts updated profile)
   - On Device B, TriageActivity updates immediately
   - Device A should move to DELAYED (GREEN) section

### Debug Logging

Add logging to track data flow:

```java
// In MeshManager
public void broadcastProfileAsJson(Context context) {
    // ... existing code ...
    String json = myProfile.toJsonString();
    Log.d("MeshManager", "Broadcasting profile JSON: " + json);
    connectionHelper.broadcastMessage("PROFILE_JSON", json);
}

public void onJsonProfileReceived(String json) {
    Log.d("MeshManager", "Received JSON profile: " + json);
    try {
        PeerProfile profile = PeerProfile.fromJsonString(json);
        Log.d("MeshManager", "Parsed: " + profile.name + 
              " (severity=" + profile.injurySeverity + ")");
        // ... rest of code ...
    } catch (Exception e) {
        Log.e("MeshManager", "JSON parse error: " + e.getMessage());
    }
}
```

---

## Common Issues & Solutions

### Issue: Self Still Appears in List
**Solution:** Check that `stable_node_id` is set in SharedPreferences at app startup.

```java
// In MainActivity
private void ensureStableNodeId() {
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    if (!prefs.contains("stable_node_id")) {
        String nodeId = UUID.randomUUID().toString();
        prefs.edit().putString("stable_node_id", nodeId).apply();
    }
}
```

### Issue: JSON Parse Errors
**Solution:** Verify all triage fields are set before broadcasting:

```java
// Before broadcasting, ensure these are set:
prefs.edit()
    .putInt("user_age", age)                     // Must be set
    .putInt("user_injury_severity", severity)   // Must be set (1-5)
    .putString("user_location", location)       // Can be empty
    .apply();
```

### Issue: Triage List Not Updating
**Solution:** Ensure listener is registered:

```java
@Override
protected void onResume() {
    super.onResume();
    MeshManager.getInstance().addListener(this);  // Add listener
    refreshTriageView();  // Initial refresh
}

@Override
protected void onPause() {
    super.onPause();
    MeshManager.getInstance().removeListener(this);  // Remove listener
}
```

---

## Performance Considerations

- JSON serialization is lightweight (~500 bytes per profile)
- Each broadcast sends to all connected peers (flooding)
- Consider rate-limiting broadcasts (e.g., max 1 per second)
- No database needed; all data in memory (fine for <100 survivors)

---

## Next Steps

1. Integrate this code into your Activities
2. Run the manual tests above
3. Monitor logcat for errors
4. Deploy and test with real devices
5. Gather feedback from rescue teams

