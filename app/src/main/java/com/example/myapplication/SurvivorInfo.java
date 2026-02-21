package com.example.myapplication;

/**
 * SurvivorInfo — Extended data model for survivor triage.
 *
 * This class represents a survivor with all information needed for triage prioritization:
 * - Age (child, adult, elderly)
 * - Injury level (none, minor, serious, critical)
 * - Location
 * - Name and other identifying info
 *
 * This data is captured from PeerProfile and local SharedPreferences.
 */
public class SurvivorInfo {

    public enum InjuryLevel {
        NONE(0, "No injury", 0xFF00C853),      // Green
        MINOR(1, "Minor injury", 0xFFFFD600),  // Amber
        SERIOUS(2, "Serious injury", 0xFFFF6F00), // Orange
        CRITICAL(3, "Critical", 0xFFD32F2F);   // Red

        public final int code;
        public final String label;
        public final int color;

        InjuryLevel(int code, String label, int color) {
            this.code = code;
            this.label = label;
            this.color = color;
        }
    }

    public enum AgeGroup {
        CHILD(0, "Child (0-12)", 1),
        ADOLESCENT(1, "Teen (13-17)", 2),
        ADULT(2, "Adult (18-64)", 3),
        ELDERLY(3, "Elderly (65+)", 1);  // Higher priority in some systems

        public final int code;
        public final String label;
        public final int triagePriority; // Lower = higher priority

        AgeGroup(int code, String label, int triagePriority) {
            this.code = code;
            this.label = label;
            this.triagePriority = triagePriority;
        }

        public static AgeGroup fromAge(int age) {
            if (age <= 12) return CHILD;
            if (age <= 17) return ADOLESCENT;
            if (age <= 64) return ADULT;
            return ELDERLY;
        }
    }

    public final String endpointId;      // Unique identifier from PeerProfile
    public final String name;
    public final String location;        // GPS or text location
    public final InjuryLevel injuryLevel;
    public final int age;
    public final AgeGroup ageGroup;
    public final int peopleCount;        // Number of people at this location
    public final String description;     // Free-text situation description
    public final long timestamp;         // When this info was last updated
    public final double lat;
    public final double lng;

    public SurvivorInfo(String endpointId, String name, String location,
                        InjuryLevel injuryLevel, int age,
                        int peopleCount, String description,
                        double lat, double lng) {
        this.endpointId = endpointId;
        this.name = name;
        this.location = location;
        this.injuryLevel = injuryLevel;
        this.age = age;
        this.ageGroup = AgeGroup.fromAge(age);
        this.peopleCount = peopleCount;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
        this.lat = lat;
        this.lng = lng;
    }

    public int getTriagePriority() {
        // Priority scoring (lower score = higher priority):
        // Injury level is the primary factor
        int injuryScore;
        if (injuryLevel == InjuryLevel.CRITICAL) {
            injuryScore = 0;    // Immediate
        } else if (injuryLevel == InjuryLevel.SERIOUS) {
            injuryScore = 1;    // Urgent
        } else if (injuryLevel == InjuryLevel.MINOR) {
            injuryScore = 2;    // Delayed
        } else {
            injuryScore = 3;    // Minor
        }

        // Age is a secondary factor
        int ageScore = ageGroup.triagePriority;

        // Calculate combined priority (injury weighted more heavily)
        return (injuryScore * 100) + ageScore;
    }

    @Override
    public String toString() {
        return "SurvivorInfo{" +
                "name='" + name + '\'' +
                ", age=" + age + " (" + ageGroup.label + ")" +
                ", injury=" + injuryLevel.label +
                ", location='" + location + '\'' +
                ", people=" + peopleCount +
                ", priority=" + getTriagePriority() +
                '}';
    }
}



