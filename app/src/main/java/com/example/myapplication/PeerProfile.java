package com.example.myapplication;

/**
 * PeerProfile — data model for a connected mesh peer.
 *
 * When two devices connect, they immediately exchange a PROFILE payload
 * containing this information. This is what powers:
 *   - "Volunteer nearby: Maria K. (CPR, First Aid)" on the survivor screen
 *   - Peer markers on the map (role determines icon colour)
 *   - Volunteer skills display on the volunteer dashboard
 *
 * Wire format (pipe-delimited string for simplicity):
 *   "PROFILE|<role>|<name>|<skills>|<equipment>|<lat>|<lng>|<situation>"
 *
 * lat/lng are 0.0 when GPS is unavailable (offline mode default).
 * situation is the survivor's free-text description, empty for volunteers.
 */
public class PeerProfile {

    public static final String TYPE = "PROFILE";

    public final String endpointId;  // Nearby Connections session ID
    public final String role;        // "VOLUNTEER" or "SURVIVOR"
    public final String name;
    public final String skills;      // CSV e.g. "CPR,First Aid"
    public final String equipment;   // CSV e.g. "Flashlight,Rope"
    public final double lat;
    public final double lng;
    public final String situation;   // Survivor's free-text description

    // Triage fields — prioritization for survivors
    public final int age;            // Age in years
    public final int injurySeverity; // 1=minor, 5=critical
    public final String location;    // Zone or location identifier

    public final long timestamp;

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

    public boolean isVolunteer() {
        return "VOLUNTEER".equals(role);
    }

    public boolean isSurvivor() {
        return "SURVIVOR".equals(role);
    }

    /** Serialise to wire format for Nearby Connections BYTES payload. */
    public String toWireFormat() {
        return TYPE + "|" +
                role      + "|" +
                safe(name)      + "|" +
                safe(skills)    + "|" +
                safe(equipment) + "|" +
                lat        + "|" +
                lng        + "|" +
                safe(situation);
    }

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

    /** Parse a wire-format string back into a PeerProfile. Returns null on error. */
    public static PeerProfile fromWireFormat(String endpointId, String raw) {
        try {
            String[] p = raw.split("\\|", -1);
            if (p.length < 8 || !TYPE.equals(p[0])) return null;
            return new PeerProfile(
                    endpointId,
                    p[1],
                    p[2],
                    p[3],
                    p[4],
                    Double.parseDouble(p[5]),
                    Double.parseDouble(p[6]),
                    p[7]
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** Replaces pipe characters in user text to avoid breaking the wire format. */
    private static String safe(String s) {
        return s == null ? "" : s.replace("|", "/");
    }

    @Override
    public String toString() {
        return "PeerProfile{role=" + role + ", name=" + name +
                ", skills=" + skills + ", lat=" + lat + ", lng=" + lng + "}";
    }
}