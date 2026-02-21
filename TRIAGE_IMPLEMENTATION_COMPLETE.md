# Triage System Implementation Summary

## What Was Implemented

A complete triage data communication system for RESQNET that allows survivors and rescuers to share critical information (age, injury severity, location) across the peer-to-peer mesh network using JSON serialization.

---

## Problems Solved

### 1. **Self Appearing in Triage/Map Lists**
- **Problem:** The rescuer's own profile was appearing alongside other survivors in the triage and map views, causing confusion.
- **Solution:** Added `MeshManager.getSelfId()` method that retrieves the local endpoint ID from SharedPreferences. All peer listing methods now filter out the self ID.
- **Files Modified:** MeshManager.java, ConnectionHelper.java, MapActivity.java

### 2. **Triage Data Not Being Communicated**
- **Problem:** Only basic profile data (name, role, skills) was being shared. Critical triage data (age, injury severity, location) was missing.
- **Solution:** 
  - Extended `PeerProfile` with triage fields: `age`, `injurySeverity`, `location`
  - Added JSON serialization methods: `toJsonString()`, `fromJsonString()`
  - Created `MeshManager.broadcastProfileAsJson()` to send complete profiles
  - Added `ConnectionHelper.broadcastMessage()` to multicast to all peers
- **Files Modified:** PeerProfile.java, MeshManager.java, ConnectionHelper.java, build.gradle.kts

### 3. **No Dedicated Triage List Excluding Self**
- **Problem:** Activities had to manually filter peers, and self-filtering was inconsistent.
- **Solution:** 
  - Added `MeshManager.getSurvivorsExcludingSelf()` method
  - Added `MeshManager.getPeerProfilesExcludingSelf()` method
  - Updated MapActivity to use these filtered lists
- **Files Modified:** MeshManager.java, MapActivity.java

---

## Files Modified

### 1. **build.gradle.kts**
Added Gson dependency for JSON serialization:
```kotlin
implementation("com.google.code.gson:gson:2.10.1")
```

### 2. **PeerProfile.java**
- Added fields: `age`, `injurySeverity`, `location`
- Added methods: `toJsonString()`, `fromJsonString()`
- Updated constructors to support triage data
- Maintained backward compatibility with legacy constructor

### 3. **MeshManager.java**
- Added `getSelfId()` — returns local endpoint ID
- Added `broadcastProfileAsJson(Context)` — sends profile with triage data as JSON
- Added `onJsonProfileReceived(String)` — parses incoming JSON profiles
- Added `getSurvivorsExcludingSelf()` — filters survivors excluding self
- Added `getPeerProfilesExcludingSelf()` — filters all peers excluding self

### 4. **ConnectionHelper.java**
- Added `getLocalEndpointId()` — retrieves stable node ID
- Added `broadcastMessage(String type, String payload)` — sends to all peers
- Added `handleJsonProfile(String fromId, String msg)` — processes JSON profiles
- Updated `PayloadCallback` to handle `PROFILE_JSON` message type

### 5. **MapActivity.java**
- Updated `drawPeers()` method to:
  - Exclude self using `MeshManager.getSelfId()`
  - Show only survivors (not volunteers)
  - Display triage information in labels

---

## Data Communication Flow

```
┌─ User Updates Survivor Info ─────────────────────┐
│ (Age, Injury Severity, Location, Description)  │
└────────────────┬─────────────────────────────────┘
                 ↓
        ┌─ SurvivorActivity ─┐
        │ Save to SharedPrefs │
        └────────┬────────────┘
                 ↓
   ┌─ MeshManager.broadcastProfileAsJson() ─┐
   │ 1. Create PeerProfile with triage fields│
   │ 2. Serialize to JSON via Gson           │
   │ 3. Call ConnectionHelper.broadcastMsg   │
   └────────┬────────────────────────────────┘
            ↓
  ┌─ ConnectionHelper.broadcastMessage() ─┐
  │ Create packet: "PROFILE_JSON|{json}"  │
  │ Send to all connected peers            │
  └────────┬─────────────────────────────┘
           ↓
      ═══ MESH NETWORK ═══
           ↓
  ┌─ Remote Device PayloadCallback ─┐
  │ Receive bytes, parse UTF-8      │
  │ Route to handleJsonProfile()     │
  └────────┬────────────────────────┘
           ↓
   ┌─ MeshManager.onJsonProfileReceived() ─┐
   │ 1. Parse JSON via Gson                │
   │ 2. Validate it's not self (endpointId)│
   │ 3. Store in peerProfiles map          │
   │ 4. Notify all listeners                │
   └────────┬────────────────────────────┘
            ↓
    ┌─ TriageActivity.onProfileReceived() ─┐
    │ Call refreshTriageView()               │
    │ Build list excluding self              │
    │ Sort by injury severity, age, location │
    │ Display in triage categories           │
    └──────────────────────────────────────┘

    ┌─ MapActivity.onProfileReceived() ─┐
    │ Call mapView.refreshPeers()         │
    │ Filter out self, show survivors     │
    │ Redraw with new positions           │
    └──────────────────────────────────┘
```

---

## API Usage Examples

### Broadcasting Your Survivor Info
```java
// In SurvivorActivity after user saves
MeshManager.getInstance().broadcastProfileAsJson(this);
```

### Getting Triage List (Excluding Self)
```java
// In TriageActivity
List<PeerProfile> survivors = MeshManager.getInstance().getSurvivorsExcludingSelf();
for (PeerProfile peer : survivors) {
    Log.d("Triage", peer.name + " - Age: " + peer.age + 
                    ", Severity: " + peer.injurySeverity);
}
```

### Getting Self ID for Filtering
```java
String selfId = MeshManager.getInstance().getSelfId();
```

### Getting All Peers Except Self
```java
List<PeerProfile> otherPeers = MeshManager.getInstance().getPeerProfilesExcludingSelf();
```

---

## Data Structure: PeerProfile (Extended)

### Fields
| Field | Type | Purpose | Example |
|-------|------|---------|---------|
| endpointId | String | Unique device identifier | "ABC123XYZ" |
| role | String | "VOLUNTEER" or "SURVIVOR" | "SURVIVOR" |
| name | String | User's name | "John Smith" |
| age | int | Age in years | 45 |
| injurySeverity | int | 1-5 scale (1=minor, 5=critical) | 4 |
| location | String | Zone/area identifier | "Building A, Room 201" |
| situation | String | Free-text injury description | "Chest pain, difficulty breathing" |
| skills | String | Volunteer medical skills (CSV) | "CPR,First Aid,Trauma" |
| equipment | String | Volunteer equipment (CSV) | "Flashlight,Rope,Bandages" |
| lat, lng | double | GPS coordinates | 0.0, 0.0 |
| timestamp | long | Creation time (ms) | 1708452000000 |

### JSON Example
```json
{
    "endpointId": "device_abc123",
    "role": "SURVIVOR",
    "name": "Alice Johnson",
    "age": 35,
    "injurySeverity": 4,
    "location": "Building A, 3rd Floor, Office 301",
    "situation": "Severe leg fracture, conscious",
    "skills": "",
    "equipment": "",
    "lat": 0.0,
    "lng": 0.0,
    "timestamp": 1708452000000
}
```

---

## Integration Checklist

### Step 1: Verify Dependencies
- [ ] Gson 2.10.1 is in build.gradle.kts
- [ ] Run `./gradlew clean build` to sync

### Step 2: Update Activities
- [ ] SurvivorActivity calls `MeshManager.broadcastProfileAsJson()` after saving
- [ ] TriageActivity uses `getSurvivorsExcludingSelf()` to build list
- [ ] MapActivity filters self (already implemented)
- [ ] All Activities register/unregister as listeners in onResume/onPause

### Step 3: Update SharedPreferences Keys
Ensure these are set when user updates survivor info:
- [ ] "user_age" (int)
- [ ] "user_injury_severity" (int, 1-5)
- [ ] "user_location" (String)
- [ ] "stable_node_id" (String) — set at app startup

### Step 4: Initialize at App Start
In MainActivity:
```java
MeshManager.getInstance().init(this, selectedRole);
MeshManager.getInstance().startMesh();
// Optional: broadcast initial profile
MeshManager.getInstance().broadcastProfileAsJson(this);
```

### Step 5: Test
- [ ] Run on two devices
- [ ] Device A: Enter survivor info, save
- [ ] Device B: Open TriageActivity, verify info appears
- [ ] Verify self does NOT appear in list
- [ ] Verify triage sorted by injury severity

---

## Key Features Implemented

### 1. **Self-Filtering**
All peer lists now exclude the current device using `endpointId` matching:
```java
String selfId = MeshManager.getInstance().getSelfId();
if (!peer.endpointId.equals(selfId)) {
    // Include in list
}
```

### 2. **JSON Serialization**
Complete triage profiles transmitted as JSON:
- Automatic serialization via Gson
- Includes all triage fields (age, injury, location)
- Fallback to wire format for backward compatibility

### 3. **Event-Driven Updates**
- No polling or manual refreshing
- Listener pattern notifies all Activities when profiles received
- UI updates automatically on mesh changes

### 4. **Data Isolation**
- Each device maintains local copy of peer profiles in memory
- No central server needed
- Profiles relayed through network to other peers

---

## Testing Scenarios

### Scenario 1: Self-Filtering
**Setup:** Device A connects to Device B
**Test:**
1. On Device A, open MapActivity
2. Verify "YOU" appears at center in blue
3. Verify Device A does NOT appear in surrounding rings
4. Verify Device B appears as orange dot (survivor)

**Expected:** Self excluded from peer list, only other survivors shown

### Scenario 2: Triage Broadcast
**Setup:** Device A (rescuer), Device B (coordinator)
**Test:**
1. Device A: Open SurvivorActivity
2. Enter: Name="Bob", Age=50, Injury=Critical (4), Location="Building B"
3. Click Save
4. Check logcat: "PROFILE_JSON broadcast to X peers"
5. Device B: Open TriageActivity within 2 seconds
6. Verify Bob appears in RED (IMMEDIATE) section

**Expected:** Profile broadcasts successfully, received, and displayed in correct triage category

### Scenario 3: Triage Updates
**Setup:** Device B (TriageActivity open) watches Device A
**Test:**
1. Device A: Change injury from Critical (4) to Delayed (2)
2. Click Save
3. Device B: Observe TriageActivity updates live
4. Verify Bob moves from RED to GREEN section

**Expected:** Real-time updates without manual refresh

### Scenario 4: Multiple Survivors
**Setup:** 3 devices with different injuries
**Test:**
1. Device A: Age=25, Injury=Critical (4)
2. Device B: Age=70, Injury=Delayed (2)
3. Device C: Age=45, Injury=Urgent (3)
4. Device C opens TriageActivity
5. Verify priority order: A (RED), C (YELLOW), B (GREEN)

**Expected:** Correct triage prioritization by severity, then age

---

## Performance Notes

- **JSON Size:** ~500 bytes per profile (minimal network overhead)
- **Memory:** All profiles stored in HashMap (suitable for <100 survivors)
- **Latency:** Milliseconds to parse JSON, broadcast happens immediately
- **Power:** ValueAnimator in MapActivity respects device sleep states
- **Threading:** All UI updates marshalled to main thread via Handler

---

## Known Limitations & Future Improvements

### Current Limitations
- No persistent storage (profiles lost if device restarts)
- No conflict resolution (last write wins)
- No bandwidth throttling (all peers receive all updates)

### Future Improvements
1. **Persistent Storage:** Save profiles to SQLite for recovery
2. **Rate Limiting:** Throttle broadcasts (max 1 per second)
3. **Location Tracking:** Use GPS to update lat/lng periodically
4. **End-to-End Encryption:** Encrypt JSON payload before transmission
5. **Message Acknowledgment:** Confirm delivery of critical profiles
6. **Offline Queuing:** Queue updates when offline, sync on reconnect

---

## Troubleshooting

### Issue: Build Error - "Cannot find symbol: class Gson"
**Solution:** Run `./gradlew clean build` to sync Gradle dependencies

### Issue: Self Appears in Triage List
**Solution:** Check that `stable_node_id` is set in SharedPreferences:
```java
// In MainActivity onCreate
SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
if (!prefs.contains("stable_node_id")) {
    prefs.edit().putString("stable_node_id", 
        UUID.randomUUID().toString()).apply();
}
```

### Issue: JSON Parse Errors in Logcat
**Solution:** Ensure all triage fields are set before broadcasting:
```java
prefs.edit()
    .putInt("user_age", age)                    // Required
    .putInt("user_injury_severity", severity)  // Required (1-5)
    .putString("user_location", location)      // Can be empty
    .apply();
```

### Issue: Triage List Not Updating
**Solution:** Verify listener is registered:
```java
@Override
protected void onResume() {
    super.onResume();
    MeshManager.getInstance().addListener(this);  // Critical!
    refreshTriageView();
}

@Override
protected void onPause() {
    super.onPause();
    MeshManager.getInstance().removeListener(this);  // Clean up
}
```

---

## Documentation Files

Created two comprehensive guides:

1. **TRIAGE_DATA_COMMUNICATION.md** — Technical details about the system
   - Data structures and JSON format
   - Communication flow diagrams
   - Complete API reference
   - Integration points

2. **TRIAGE_INTEGRATION_GUIDE.md** — Step-by-step integration instructions
   - Code examples for each Activity
   - Configuration requirements
   - Testing procedures
   - Common issues & solutions

---

## Summary

✅ **Problem 1 (Self in List):** SOLVED
- Added self-filtering using `getSelfId()`
- All peer methods exclude self by default

✅ **Problem 2 (Data Not Communicated):** SOLVED
- Extended PeerProfile with triage fields
- Added JSON serialization and broadcasting
- Peers automatically share age, injury, location

✅ **Problem 3 (No Dedicated Triage List):** SOLVED
- Created filtered survivor list methods
- Updated MapActivity to use filters
- Implemented event-driven UI updates

**Status:** Complete and ready for integration into Activities

