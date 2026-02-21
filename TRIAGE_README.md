# Triage System Implementation

## Overview

The RESQNET Triage System prioritizes survivors in disaster response scenarios based on:
1. **Injury Severity** (Primary Factor)
2. **Age Group** (Secondary Factor)
3. **Location** (Reference)

## Key Features

### 1. **Survivor Data Model (SurvivorInfo.java)**
- Extends PeerProfile data with triage-specific information
- Defines injury levels: NONE, MINOR, SERIOUS, CRITICAL
- Defines age groups: CHILD, ADOLESCENT, ADULT, ELDERLY
- Calculates priority scores for automated sorting

```java
public enum InjuryLevel {
    NONE(0, "No injury", 0xFF00C853),           // Green
    MINOR(1, "Minor injury", 0xFFFFD600),       // Amber
    SERIOUS(2, "Serious injury", 0xFFFF6F00),   // Orange
    CRITICAL(3, "Critical", 0xFFD32F2F)         // Red
}

public enum AgeGroup {
    CHILD(0, "Child (0-12)", 1),
    ADOLESCENT(1, "Teen (13-17)", 2),
    ADULT(2, "Adult (18-64)", 3),
    ELDERLY(3, "Elderly (65+)", 1)
}
```

### 2. **Triage Calculator (TriageCalculator.java)**
Provides utility methods for:
- **calculateTriage()** - Sorts survivors by priority
- **groupByTriageCategory()** - Groups by injury level
- **getTriageRecommendation()** - Generates action recommendations
- **getRescueSequence()** - Creates rescue priority list

**Triage Categories:**
- 🔴 **IMMEDIATE (Red)** - Critical injuries, life-threatening
- 🟠 **URGENT (Orange)** - Serious injuries, but stable
- 🟡 **DELAYED (Yellow)** - Minor injuries, mobile
- 🟢 **MINOR (Green)** - No injuries or very minor

### 3. **Triage Activity (TriageActivity.java)**
Main UI for viewing and managing survivor triage:
- Displays all connected survivors
- Groups by triage category
- Shows priority ranking
- Provides rescue sequence recommendations
- Real-time updates via mesh listener

**Features:**
- 📋 Category-based display
- 🔄 Refresh button for manual updates
- 📊 Rescue sequence recommendation
- 👤 Age and people count tracking
- 📍 Location-based organization

### 4. **Data Capture (SurvivorActivity.java)**
Enhanced survivor form to collect:
- **Age** (slider 0-100 years) - NEW
- **Location** (text field)
- **People Count** (slider 1-20)
- **Injury Level** (radio buttons: None, Minor, Serious)
- **Description** (free text)

All data persists in SharedPreferences and broadcasts to volunteers via mesh.

## Data Flow

```
SurvivorActivity (Collect Data)
    ↓ saves to SharedPreferences
    ↓ broadcasts via PeerProfile
    ↓
Mesh (Nearby Connections)
    ↓
MeshManager (Aggregates Peers)
    ↓
TriageActivity (Displays & Prioritizes)
    ↓
TriageCalculator (Sorts & Analyzes)
    ↓
UI (Categorized Survivor List + Recommendations)
```

## Priority Scoring Algorithm

```
Score = (InjuryScore × 100) + AgeScore

Where:
  InjuryScore = 0 (CRITICAL) | 1 (SERIOUS) | 2 (MINOR) | 3 (NONE)
  AgeScore = ageGroup.triagePriority (1-3)

Lower scores = Higher priority (sorted ascending)

Example:
  CRITICAL injury + CHILD age = (0 × 100) + 1 = 1 (Highest priority)
  MINOR injury + ADULT age = (2 × 100) + 3 = 203 (Lower priority)
```

## Integration with Existing Code

### MainActivity
- Triage button opens TriageActivity
- Integrated with mesh connection lifecycle

### SurvivorActivity
- Added age slider to form
- Saves age to SharedPreferences: `survivor_age`
- Broadcasts updated profile with age info

### MeshManager
- No changes needed (already provides getSurvivors())
- Listeners receive real-time updates

### TriageActivity
- Listens to ConnectionStatusListener events
- Auto-refreshes on peer connect/disconnect
- Displays both mesh peers and local survivor data

## SharedPreferences Keys

```
SURVIVOR DATA (SurvivorActivity):
  survivor_location      → String (e.g., "Building A, Floor 3")
  survivor_age           → int (e.g., 28)
  survivor_people_count  → int (e.g., 3)
  survivor_injury_level  → int (0=None, 1=Minor, 2=Serious, 3=Critical)
  survivor_description   → String (free text)
```

## UI Color Scheme

- **Green (#00C853)** - No injury / Minor
- **Amber (#FFD600)** - Minor injury
- **Orange (#FF6F00)** - Serious injury
- **Red (#D32F2F)** - Critical injury

## Testing Scenarios

### Scenario 1: Single Survivor
1. Open SurvivorActivity
2. Fill form: Age=12, Location="Room 5", Injury=Serious
3. Click "Save & Broadcast"
4. Open TriageActivity
5. Should show one card in URGENT category

### Scenario 2: Multiple Survivors (Mesh)
1. Connect two devices via mesh
2. Each device fills SurvivorActivity form differently
3. Open TriageActivity
4. Should show multiple survivors sorted by triage priority

### Scenario 3: Dynamic Updates
1. Open TriageActivity
2. Modify SurvivorActivity form on another device
3. Triage should refresh automatically within seconds

## Standard Triage Protocols (START Method)

RESQNET's triage implementation is based on the **START** (Simple Triage And Rapid Treatment) protocol:

- **Respiratory rate** → (Not directly captured, but could be added)
- **Perfusion/Pulse** → (Not directly captured, but injury level serves as proxy)
- **Mental status** → (Not directly captured, description field can note)
- **Age + Injury** → Primary factors in RESQNET

## Future Enhancements

1. **Vital Signs Integration**
   - Heart rate, breathing rate, blood pressure
   - Oxygen saturation level

2. **Medical History**
   - Chronic conditions
   - Current medications
   - Allergies

3. **GPS-Based Priority**
   - Distance from nearest volunteer
   - Accessibility score

4. **Voice Notes**
   - Audio description of injuries
   - Real-time communication

5. **Analytics Dashboard**
   - Triage statistics
   - Resource utilization
   - Response time tracking

## Files Modified/Created

### New Files:
- `SurvivorInfo.java` - Survivor triage data model
- `TriageCalculator.java` - Triage utility methods
- `TriageActivity.java` - Triage UI activity
- `activity_triage.xml` - Triage layout
- `btn_refresh_bg.xml` - Refresh button style

### Modified Files:
- `MainActivity.java` - Added Triage button navigation
- `SurvivorActivity.java` - Added age slider and saving
- `AndroidManifest.xml` - Registered TriageActivity

## API Level

Minimum API: 24 (Android 7.0)
- Uses Collections.sort() instead of List.sort()
- Compatible with older devices

## Performance Considerations

- **Sorting**: O(n log n) using Collections.sort()
- **UI Updates**: Synchronized list listeners prevent race conditions
- **Memory**: Survivor list held in memory (acceptable for < 100 survivors)
- **Network**: Updates broadcast on every peer connection

## Error Handling

- Null-safe parsing of PeerProfile situation field
- Default values if age not captured
- Graceful handling of missing location data
- Continues operation if some peers disconnect

## Testing Checklist

- [ ] Triage button opens TriageActivity from MainActivity
- [ ] Age slider appears in SurvivorActivity form
- [ ] Age value persists across app closes
- [ ] Survivors display in correct triage category
- [ ] Color indicators match injury level
- [ ] Rescue sequence generates in priority order
- [ ] Dynamic refresh on peer connect/disconnect
- [ ] Multiple survivors sort correctly by age within category
- [ ] No survivors message displays when list is empty
- [ ] Age slider ranges from 0-100

