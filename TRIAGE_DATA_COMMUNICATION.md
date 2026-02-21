# Triage Data Communication System

## Overview

The RESQNET triage system now properly communicates survivor data between peers using JSON serialization. This document explains the data flow, fields, and filtering mechanisms.

## Problem Solved

### Issue 1: Self Appearing in Peer Lists
**Before:** The map and triage view displayed the rescuer (self) along with other survivors, which was confusing.
**After:** Self is now excluded from all peer lists using `MeshManager.getSelfId()` to identify the local device.

### Issue 2: Data Not Communicated Between Devices
**Before:** Only basic profile data was shared (name, role, skills).
**After:** Complete triage data is now transmitted as JSON, including age, injury severity, and location.

### Issue 3: No Dedicated Triage List
**Before:** Survivors were drawn on a map in arbitrary circular arrangement.
**After:** Dedicated filtering methods (`getSurvivorsExcludingSelf()`) allow proper triage prioritization.

---

## Data Structure

### PeerProfile (Extended)
The `PeerProfile` class now includes triage fields:

```java
public class PeerProfile {
    // Basic info
    public final String endpointId;      // Unique node ID
    public final String role;            // "VOLUNTEER" or "SURVIVOR"
    public final String name;            // User's name
    public final String skills;          // Volunteer: "CPR,First Aid"
    public final String equipment;       // Volunteer: "Flashlight,Rope"
    public final String situation;       // Survivor: free-text description
    
    // Location
    public final double lat;             // GPS latitude (0.0 if unavailable)
    public final double lng;             // GPS longitude (0.0 if unavailable)
    
    // Triage Priority Fields (NEW)
    public final int age;                // Age in years
    public final int injurySeverity;     // 1=minor, 2=moderate, 3=serious, 4=critical, 5=life-threatening
    public final String location;        // Zone/area identifier (e.g., "Building A, 3rd Floor")
    
    // Metadata
    public final long timestamp;         // When profile was created
}
```

### JSON Format Example
Profiles are transmitted as JSON between peers:

```json
{
    "endpointId": "ABC123XYZ",
    "role": "SURVIVOR",
    "name": "John Smith",
    "skills": "",
    "equipment": "",
    "situation": "Chest pain, difficulty breathing",
    "lat": 0.0,
    "lng": 0.0,
    "age": 45,
    "injurySeverity": 4,
    "location": "Building A, Office 201",
    "timestamp": 1708452000000
}
```

---

## Communication Flow

### 1. Peer Connection
```
Device A connects to Device B
           ↓
ConnectionHelper.onConnectionResult() triggered
           ↓
Sends basic profile via wire format
           ↓
Waits for peer to acknowledge
```

### 2. Triage Data Broadcasting (NEW)
```
User updates survivor info in SurvivorActivity
           ↓
Saves to SharedPreferences (age, injury, location)
           ↓
MeshManager.broadcastProfileAsJson() called
           ↓
Converts PeerProfile to JSON with all triage fields
           ↓
ConnectionHelper.broadcastMessage("PROFILE_JSON", json)
           ↓
Sends to all connected peers as UTF-8 bytes
           ↓
Peers receive via PayloadCallback
           ↓
ConnectionHelper.handleJsonProfile() routes to MeshManager
           ↓
MeshManager.onJsonProfileReceived() parses JSON
           ↓
Stores in peerProfiles map
           ↓
Notifies all listeners (MapActivity, TriageActivity)
           ↓
UI updates with latest triage data
```

### 3. Self Filtering
```
Activities request peer list
           ↓
MeshManager.getPeerProfilesExcludingSelf() 
           ↓
Gets selfId from ConnectionHelper.getLocalEndpointId()
           ↓
Filters out any peer matching selfId
           ↓
Returns only OTHER peers
           ↓
UI displays list without self
```

---

## API Reference

### MeshManager Methods

#### Broadcasting Profile with Triage Data
```java
public void broadcastProfileAsJson(Context context)
```
Broadcasts the user's full profile (including age, injury severity, location) as JSON to all connected peers.

#### Getting Self ID
```java
public String getSelfId()
```
Returns the stable endpoint ID of the current device. Used for self-filtering.

#### Getting Survivors Excluding Self
```java
public List<PeerProfile> getSurvivorsExcludingSelf()
```
Returns all survivor profiles EXCEPT the current user.

#### Getting All Peers Excluding Self
```java
public List<PeerProfile> getPeerProfilesExcludingSelf()
```
Returns all peer profiles EXCEPT the current user.

#### Parsing Incoming JSON Profile
```java
public void onJsonProfileReceived(String json)
```
Called by ConnectionHelper when a JSON profile is received from a peer. Parses, stores, and notifies listeners.

### PeerProfile Methods

#### Serialize to JSON
```java
public String toJsonString()
```
Converts the entire PeerProfile (including triage data) to JSON string.

#### Deserialize from JSON
```java
public static PeerProfile fromJsonString(String json)
```
Parses a JSON string back into a PeerProfile object. Returns null on error.

### ConnectionHelper Methods

#### Get Local Endpoint ID
```java
public String getLocalEndpointId()
```
Returns the stable node ID stored in SharedPreferences.

#### Broadcast Message to All Peers
```java
public void broadcastMessage(String messageType, String payload)
```
Sends a message to all connected peers. Used for PROFILE_JSON and other protocol messages.

---

## Integration Points

### SurvivorActivity
Should call `MeshManager.broadcastProfileAsJson(context)` after user updates:
- Age
- Injury level
- Location
- Description

### TriageActivity
Uses `MeshManager.getSurvivorsExcludingSelf()` to build prioritized triage list.

### MapActivity
Uses `MeshManager.getPeerProfilesExcludingSelf()` to draw only other peers on the map.

### MainActivity
Should initialize MeshManager and optionally broadcast initial profile:
```java
MeshManager.getInstance().init(context, role);
MeshManager.getInstance().startMesh();
// Optional: Send first profile
MeshManager.getInstance().broadcastProfileAsJson(context);
```

---

## Injury Severity Scale

```
1 = Minor        (cuts, minor bruises, mild sprain)
2 = Moderate     (deeper cuts, moderate sprain, minor fracture)
3 = Serious      (significant fracture, head injury, internal bleeding risk)
4 = Critical     (severe bleeding, difficulty breathing, unconscious)
5 = Life-threatening (cardiac event, severe trauma, immediate intervention needed)
```

## Priority Algorithm

Triage prioritization in TriageActivity:

1. **Primary:** Injury Severity (descending: 5→1)
2. **Secondary:** Age (children and elderly prioritized)
3. **Tertiary:** Location (alphabetical for tie-breaking)

---

## Example: Complete Workflow

1. **Rescue Worker** opens app and selects "SURVIVOR"
2. **Enters information:**
   - Name: "Alice"
   - Age: 35
   - Injury: "Severe leg fracture"
   - Injury Severity: 4
   - Location: "Building A, Room 205"

3. **SurvivorActivity saves** to SharedPreferences and calls `broadcastProfileAsJson()`

4. **MeshManager converts** to JSON:
   ```json
   {
     "endpointId": "LOCAL_DEVICE_ID",
     "role": "SURVIVOR",
     "name": "Alice",
     "age": 35,
     "injurySeverity": 4,
     "location": "Building A, Room 205",
     "situation": "Severe leg fracture"
   }
   ```

5. **ConnectionHelper broadcasts** "PROFILE_JSON|{json}" to all peers

6. **Other devices receive** via PayloadCallback

7. **handleJsonProfile()** routes to MeshManager

8. **onJsonProfileReceived()** stores in peerProfiles map

9. **Listeners notified** → TriageActivity refreshes

10. **Triage list updated** showing Alice at top (severity 4)

---

## Key Changes Summary

| Component | Change | Reason |
|-----------|--------|--------|
| PeerProfile | Added age, injurySeverity, location | Triage prioritization |
| PeerProfile | Added toJsonString(), fromJsonString() | JSON communication |
| MeshManager | Added getSelfId() | Self-filtering |
| MeshManager | Added getSurvivorsExcludingSelf() | Triage list building |
| MeshManager | Added broadcastProfileAsJson() | Send triage data |
| MeshManager | Added onJsonProfileReceived() | Receive & parse triage data |
| ConnectionHelper | Added getLocalEndpointId() | Self identification |
| ConnectionHelper | Added broadcastMessage() | Multi-peer messaging |
| ConnectionHelper | Added handleJsonProfile() | Route JSON profiles |
| MapActivity | Updated drawPeers() | Exclude self, show survivors only |
| build.gradle | Added Gson 2.10.1 | JSON serialization library |

---

## Testing Checklist

- [ ] Self does not appear in peer list on map
- [ ] Self does not appear in triage list
- [ ] Survivors appear on map with orange dot
- [ ] Volunteers appear on map with green dot
- [ ] JSON profile successfully broadcasts to peers
- [ ] Incoming JSON profiles parse without errors
- [ ] Triage list updates when new survivor info received
- [ ] Triage list sorted by injury severity (high→low)
- [ ] Age and location used for tie-breaking
- [ ] Map displays peer names and injury descriptions

---

## Troubleshooting

### Self Appearing in List
Check that `MeshManager.getSelfId()` returns correct endpoint ID from SharedPreferences.

### JSON Parse Errors
Verify that Gson 2.10.1 is in dependencies. Check logcat for stack trace.

### Profiles Not Broadcasting
Ensure `MeshManager.broadcastProfileAsJson()` is called after user updates survivor info. Verify peers have active connections.

### Triage List Not Updating
Check that listeners are properly registered in onResume() and removed in onPause().

