package com.example.myapplication;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.PermissionHelper;

/**
 * ResqnetApp — Application class
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHERE THIS FILE GOES:
 *   app/src/main/java/com/resqnet/ResqnetApp.java
 *
 * WHY IT MUST EXIST:
 *   AndroidManifest.xml declares android:name=".ResqnetApp" on the
 *   <application> tag. If this class doesn't exist, the app crashes
 *   immediately on launch with:
 *     java.lang.ClassNotFoundException: com.resqnet.ResqnetApp
 *
 * WHAT IT DOES:
 *   1. Creates the notification channel required for the mesh foreground
 *      service (mandatory on Android 8.0+, otherwise the service crashes).
 *   2. Provides a static app-wide context accessor so helpers like
 *      PermissionHelper can reference the Application without needing
 *      an Activity passed in.
 *   3. Acts as the single place for any future app-level initialisation
 *      (e.g. crash reporting, offline database warm-up).
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ResqnetApp extends Application {

    private static final String TAG = "ResqnetApp";

    // ── Notification channel ID ───────────────────────────────────────────────
    // Used by MeshForegroundService to keep the mesh alive in the background.
    // Must be created here in Application.onCreate() before the service starts.
    public static final String CHANNEL_ID_MESH    = "resqnet_mesh_channel";
    public static final String CHANNEL_ID_ALERTS  = "resqnet_alerts_channel";

    // ── Static application reference ─────────────────────────────────────────
    // Allows non-Activity classes to access application context safely.
    private static ResqnetApp instance;

    public static ResqnetApp getInstance() {
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.i(TAG, "RESQNET starting — offline mesh mode. No internet required.");

        createNotificationChannels();
    }

    // ── Notification channels ─────────────────────────────────────────────────

    /**
     * Creates the notification channels required for Android 8.0+ (API 26+).
     *
     * Channel 1 — MESH SERVICE (low priority, persistent)
     *   Shows a silent persistent notification while the mesh is running
     *   in the background. Required by Android to keep the foreground
     *   service alive — without it the OS kills the mesh after ~1 minute.
     *
     * Channel 2 — ALERTS (high priority, sound + vibration)
     *   Used for SOS received alerts and volunteer-nearby notifications.
     *   These must break through Do Not Disturb in an emergency.
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // Channels not needed below API 26
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        // ── Channel 1: Mesh foreground service ───────────────────────────────
        NotificationChannel meshChannel = new NotificationChannel(
                CHANNEL_ID_MESH,
                "Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW  // Silent — no sound/vibration
        );
        meshChannel.setDescription(
                "Keeps the RESQNET peer-to-peer mesh active in the background");
        meshChannel.setShowBadge(false);
        manager.createNotificationChannel(meshChannel);

        // ── Channel 2: Emergency alerts ──────────────────────────────────────
        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH  // Sound + vibration + heads-up
        );
        alertChannel.setDescription(
                "SOS received alerts and nearby volunteer notifications");
        alertChannel.enableVibration(true);
        alertChannel.setVibrationPattern(new long[]{0, 300, 200, 300, 200, 300});
        alertChannel.enableLights(true);
        alertChannel.setLightColor(0xFFFF4F00); // International Orange
        manager.createNotificationChannel(alertChannel);

        Log.d(TAG, "Notification channels created.");
    }
}