package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * PermissionHelper — Runtime Permission Manager
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHERE THIS FILE GOES:
 *   app/src/main/java/com/resqnet/util/PermissionHelper.java
 *
 * WHY THIS IS NEEDED:
 *   Nearby Connections uses Bluetooth + Wi-Fi scanning internally.
 *   On Android 6.0+ (API 23+) dangerous permissions must be requested
 *   at runtime — listing them in the manifest alone is not enough.
 *
 *   If you skip this step:
 *     - On Android 12+: SecurityException crash when startAdvertising() is called
 *     - On Android 6–11: Nearby silently fails to discover any peers
 *     - The mesh appears to "start" but never finds anyone
 *
 * HOW TO USE:
 *   In MainActivity.onCreate(), BEFORE calling connectionHelper.startMesh():
 *
 *     if (!PermissionHelper.allGranted(this)) {
 *         PermissionHelper.requestAll(this);
 *         return; // startMesh() will be called from onRequestPermissionsResult
 *     }
 *     connectionHelper.startMesh();
 *
 *   Then in MainActivity.onRequestPermissionsResult():
 *
 *     @Override
 *     public void onRequestPermissionsResult(int requestCode,
 *             @NonNull String[] permissions, @NonNull int[] grantResults) {
 *         super.onRequestPermissionsResult(requestCode, permissions, grantResults);
 *         if (requestCode == PermissionHelper.REQUEST_CODE) {
 *             if (PermissionHelper.allGranted(this)) {
 *                 connectionHelper.startMesh();
 *             } else {
 *                 Toast.makeText(this,
 *                     "Permissions required for mesh networking",
 *                     Toast.LENGTH_LONG).show();
 *             }
 *         }
 *     }
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PermissionHelper {

    public static final int REQUEST_CODE = 1001;

    /**
     * Builds the correct permission list for the device's Android version.
     *
     * Android added new Bluetooth permissions in API 31 (Android 12) and
     * a new Wi-Fi permission in API 33 (Android 13). We must request the
     * right set for each version — requesting API 31 permissions on API 28
     * causes an IllegalArgumentException crash.
     */
    private static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // ── Location (all API levels) ────────────────────────────────────────
        // Required by Nearby Connections for BLE scanning on all versions.
        // On Android 13+ this is still needed alongside NEARBY_WIFI_DEVICES.
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        // ── Bluetooth (Android 12 / API 31+) ─────────────────────────────────
        // Pre-API 31 Bluetooth was granted at install time via the manifest.
        // From API 31 onward these three must be requested at runtime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        // ── Nearby Wi-Fi Devices (Android 13 / API 33+) ──────────────────────
        // Replaces the need for fine location just for Wi-Fi peer discovery.
        // Still need ACCESS_FINE_LOCATION alongside it for BLE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Returns true only if every required permission is already granted.
     * Call this before startMesh() to decide whether to request permissions first.
     */
    public static boolean allGranted(@NonNull Activity activity) {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests only the permissions that haven't been granted yet.
     * The system dialog will appear for each un-granted permission.
     * Result comes back in Activity.onRequestPermissionsResult().
     */
    public static void requestAll(@NonNull Activity activity) {
        List<String> missing = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    missing.toArray(new String[0]),
                    REQUEST_CODE
            );
        }
    }

    /**
     * Checks if the user permanently denied a permission ("Don't ask again").
     * If true, you should show a dialog explaining why it's needed and
     * direct the user to Settings to grant it manually.
     *
     * Usage:
     *   if (PermissionHelper.isPermanentlyDenied(this, Manifest.permission.BLUETOOTH_SCAN)) {
     *       showGoToSettingsDialog();
     *   }
     */
    public static boolean isPermanentlyDenied(@NonNull Activity activity,
                                              @NonNull String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                != PackageManager.PERMISSION_GRANTED
                && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}