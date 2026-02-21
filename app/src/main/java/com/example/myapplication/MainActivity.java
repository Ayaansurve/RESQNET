package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.example.myapplication.ConnectionHelper;
import com.example.myapplication.MeshManager;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.PeerProfile;
import com.example.myapplication.PermissionHelper;

public class MainActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    public static final String PREFS_NAME     = "resqnet_prefs";
    public static final String KEY_USER_ROLE  = "user_role";
    public static final String KEY_USER_NAME  = "user_name";
    public static final String ROLE_SURVIVOR  = "SURVIVOR";
    public static final String ROLE_VOLUNTEER = "VOLUNTEER";

    private ActivityMainBinding binding;
    private SharedPreferences prefs;

    // FIX: single Handler + Runnable reference so we can always cancel
    // before restarting. Previously if onResume fired twice (back navigation,
    // screen rotation) two loops ran simultaneously causing flicker.
    private final Handler pulseHandler = new Handler(Looper.getMainLooper());
    private Runnable pulseRunnable;
    private boolean isPulseRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        MeshManager.getInstance().init(this, getSavedRole());

        setupStatusBar();
        setupRoleCards();
        setupQuickActionButtons();
        setupSosButton();
        restoreRoleUI(getSavedRole());

        if (PermissionHelper.allGranted(this)) {
            MeshManager.getInstance().startMesh();
        } else {
            PermissionHelper.requestAll(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshManager.getInstance().addListener(this);
        restoreRoleUI(getSavedRole());
        // FIX: stop any existing pulse before starting a new one
        stopPulseAnimation();
        startPulseAnimation();
        onPeerCountChanged(MeshManager.getInstance().getPeerCount());
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshManager.getInstance().removeListener(this);
        stopPulseAnimation();
        // FIX: cancel any in-progress view animations so they don't
        // fire withEndAction() after the view is detached
        if (binding != null) {
            binding.viewStatusDot.animate().cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // prevent any pending Runnables from accessing views
        if (isFinishing()) {
            MeshManager.getInstance().stopMesh();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQUEST_CODE) {
            if (PermissionHelper.allGranted(this)) {
                MeshManager.getInstance().startMesh();
            } else {
                if (binding == null) return;
                binding.tvMeshStatus.setText("PERMISSIONS DENIED — Mesh disabled");
                binding.tvMeshStatus.setTextColor(getColor(R.color.triage_critical));
                Toast.makeText(this,
                        "Permissions required. Please grant them in Settings.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private void setupStatusBar() {
        binding.tvMeshStatus.setText("SCANNING FOR PEERS…");
        binding.tvPeerCount.setText("0 nearby");
        binding.viewStatusDot.setBackgroundResource(R.drawable.dot_scanning);
    }

    // ── Role cards ────────────────────────────────────────────────────────────

    private void setupRoleCards() {
        binding.cardSurvivor.setOnClickListener(v -> {
            animateCardPress(binding.cardSurvivor);
            setRole(ROLE_SURVIVOR);
            startActivity(new Intent(this, SurvivorActivity.class));
        });
        binding.cardVolunteer.setOnClickListener(v -> {
            animateCardPress(binding.cardVolunteer);
            setRole(ROLE_VOLUNTEER);
            startActivity(new Intent(this, VolunteerActivity.class));
        });
    }

    // ── Quick actions ─────────────────────────────────────────────────────────

    private void setupQuickActionButtons() {
        binding.btnTriage.setOnClickListener(v ->
                startActivity(new Intent(this, TriageActivity.class)));
        binding.btnSupply.setOnClickListener(v -> showComingSoon("Supply Log"));
        binding.btnMap.setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));
    }

    private void showComingSoon(String name) {
        Snackbar.make(binding.getRoot(), name + " — coming soon",
                        Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.snackbar_bg))
                .setTextColor(getColor(R.color.text_primary)).show();
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    private void setupSosButton() {
        binding.btnSos.setOnClickListener(v -> {
            binding.btnSos.startAnimation(
                    AnimationUtils.loadAnimation(this, R.anim.anim_sos_pulse));
            int peers = MeshManager.getInstance().getPeerCount();
            if (peers == 0) {
                Snackbar.make(binding.getRoot(),
                                "No peers connected yet — keep device visible",
                                Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.triage_critical))
                        .setTextColor(0xFFFFFFFF).show();
            } else {
                MeshManager.getInstance().broadcastSOS();
                Snackbar.make(binding.getRoot(),
                                "⚠ SOS SENT to " + peers + " peer(s)",
                                Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.triage_critical))
                        .setTextColor(0xFFFFFFFF).show();
            }
        });
    }

    // ── Role ──────────────────────────────────────────────────────────────────

    private void setRole(String role) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply();
        MeshManager.getInstance().updateRole(role);
        restoreRoleUI(role);
    }

    private String getSavedRole() {
        return prefs.getString(KEY_USER_ROLE, ROLE_SURVIVOR);
    }

    private void restoreRoleUI(String role) {
        if (binding == null) return;
        boolean isSurvivor  = ROLE_SURVIVOR.equals(role);
        boolean isVolunteer = ROLE_VOLUNTEER.equals(role);

        binding.cardSurvivor.setStrokeColor(isSurvivor
                ? getColor(R.color.international_orange)
                : getColor(R.color.card_border_inactive));
        binding.cardVolunteer.setStrokeColor(isVolunteer
                ? getColor(R.color.international_orange)
                : getColor(R.color.card_border_inactive));

        binding.tvActiveBadgeSurvivor.setVisibility(
                isSurvivor ? View.VISIBLE : View.GONE);
        binding.tvActiveBadgeVolunteer.setVisibility(
                isVolunteer ? View.VISIBLE : View.GONE);

        String savedName = prefs.getString(KEY_USER_NAME, "");
        binding.tvVolunteerSubtitle.setText(
                (isVolunteer && !savedName.isEmpty())
                        ? "Active as: " + savedName
                        : "Register your skills & equipment");
    }

    // ── ConnectionStatusListener ──────────────────────────────────────────────

    @Override
    public void onPeerCountChanged(int peerCount) {
        if (binding == null) return;
        binding.tvPeerCount.setText(peerCount + " nearby");
        if (peerCount > 0) {
            binding.tvMeshStatus.setText("MESH ACTIVE");
            binding.tvMeshStatus.setTextColor(getColor(R.color.mesh_active_green));
            binding.viewStatusDot.setBackgroundResource(R.drawable.dot_active);
        } else {
            binding.tvMeshStatus.setText("SCANNING…");
            binding.tvMeshStatus.setTextColor(getColor(R.color.text_secondary));
            binding.viewStatusDot.setBackgroundResource(R.drawable.dot_scanning);
        }
    }

    @Override
    public void onPeerConnected(String endpointName) {
        if (binding == null) return;
        String role = ConnectionHelper.parseRoleFromEndpointName(endpointName);
        String name = ConnectionHelper.parseNameFromEndpointName(endpointName);
        Snackbar.make(binding.getRoot(),
                        "VOLUNTEER".equals(role)
                                ? "🟢 Volunteer nearby: " + name
                                : "📍 Survivor detected nearby",
                        Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.snackbar_bg))
                .setTextColor(getColor(R.color.text_primary)).show();
    }

    @Override public void onPeerDisconnected(String id) {}
    @Override public void onProfileReceived(PeerProfile p) {}

    @Override
    public void onSosReceived(String fromNodeId) {
        if (binding == null) return;
        binding.getRoot().setBackgroundColor(0x33FF0000);
        binding.getRoot().postDelayed(
                () -> { if (binding != null)
                    binding.getRoot().setBackgroundColor(
                            getColor(R.color.true_black)); }, 400);
        Snackbar.make(binding.getRoot(),
                        "⚠ SOS RECEIVED — Someone nearby needs help!",
                        Snackbar.LENGTH_INDEFINITE)
                .setAction("OK", v -> {})
                .setBackgroundTint(getColor(R.color.triage_critical))
                .setTextColor(0xFFFFFFFF)
                .setActionTextColor(0xFFFFFFFF).show();
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private void animateCardPress(MaterialCardView card) {
        card.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.anim_card_press));
    }

    private void startPulseAnimation() {
        if (isPulseRunning) return; // guard against double-start
        isPulseRunning = true;
        pulseRunnable  = new Runnable() {
            @Override public void run() {
                // FIX: null-check binding before every access
                if (binding == null || !isPulseRunning) return;
                binding.viewStatusDot.animate()
                        .alpha(0.3f)
                        .setDuration(600)
                        .withEndAction(() -> {
                            if (binding == null || !isPulseRunning) return;
                            binding.viewStatusDot.animate()
                                    .alpha(1f)
                                    .setDuration(600)
                                    .withEndAction(() -> {
                                        if (isPulseRunning) {
                                            pulseHandler.postDelayed(
                                                    pulseRunnable, 200);
                                        }
                                    }).start();
                        }).start();
            }
        };
        pulseHandler.post(pulseRunnable);
    }

    private void stopPulseAnimation() {
        isPulseRunning = false;
        if (pulseRunnable != null) {
            pulseHandler.removeCallbacks(pulseRunnable);
            pulseRunnable = null;
        }
    }
}