package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.example.myapplication.ConnectionHelper;
import com.example.myapplication.MeshManager;
import com.example.myapplication.databinding.ActivitySurvivorBinding;
import com.example.myapplication.PeerProfile;

import java.util.List;

public class SurvivorActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    public static final String KEY_SURVIVOR_LOCATION    = "survivor_location";
    public static final String KEY_SURVIVOR_AGE         = "survivor_age";
    public static final String KEY_SURVIVOR_PEOPLE      = "survivor_people_count";
    public static final String KEY_SURVIVOR_INJURY      = "survivor_injury_level";
    public static final String KEY_SURVIVOR_DESCRIPTION = "survivor_description";

    private ActivitySurvivorBinding binding;
    private SharedPreferences prefs;

    private final Handler pulseHandler   = new Handler(Looper.getMainLooper());
    private Runnable pulseRunnable;
    private boolean isPulseRunning = false; // FIX: guard against double-start

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySurvivorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        setupToolbar();
        restoreSavedInfo();
        setupSosButton();
        setupSaveButton();
        setupMapButton();
        refreshVolunteerCards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshManager.getInstance().addListener(this);
        updatePeerUI(MeshManager.getInstance().getPeerCount());
        refreshVolunteerCards();
        // FIX: always stop before starting so we never have two loops running
        stopPulseAnimation();
        startPulseAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshManager.getInstance().removeListener(this);
        stopPulseAnimation();
        // FIX: cancel any in-flight ViewPropertyAnimator so withEndAction
        // doesn't fire after the view is detached
        if (binding != null) {
            binding.tvBroadcastingLabel.animate().cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // prevent pending Runnables from touching dead views
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupMapButton() {
        binding.btnOpenMap.setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));
    }

    private void restoreSavedInfo() {
        String loc  = prefs.getString(KEY_SURVIVOR_LOCATION, "");
        String desc = prefs.getString(KEY_SURVIVOR_DESCRIPTION, "");
        int people  = prefs.getInt(KEY_SURVIVOR_PEOPLE, 1);
        int injury  = prefs.getInt(KEY_SURVIVOR_INJURY, 0);
        int age     = prefs.getInt(KEY_SURVIVOR_AGE, 30);

        if (!loc.isEmpty())  binding.etLocation.setText(loc);
        if (!desc.isEmpty()) binding.etDescription.setText(desc);

        binding.sliderPeopleCount.setValue(Math.max(1, Math.min(20, people)));
        binding.tvPeopleCount.setText(people + (people == 1 ? " person" : " people"));
        binding.sliderPeopleCount.addOnChangeListener((s, v, f) -> {
            int c = (int) v;
            binding.tvPeopleCount.setText(c + (c == 1 ? " person" : " people"));
        });

        // Restore age slider
        if (binding.sliderAge != null) {
            binding.sliderAge.setValue(age);
            binding.tvAge.setText("Age: " + age + " years");
            binding.sliderAge.addOnChangeListener((s, v, f) -> {
                int a = (int) v;
                binding.tvAge.setText("Age: " + a + " years");
            });
        }

        switch (injury) {
            case 1:  binding.rbMinor.setChecked(true);   break;
            case 2:  binding.rbSerious.setChecked(true); break;
            default: binding.rbNone.setChecked(true);    break;
        }
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    private void setupSosButton() {
        binding.btnSos.setOnClickListener(v -> {
            binding.btnSos.setAlpha(0.6f);
            binding.btnSos.postDelayed(() -> {
                if (binding != null) binding.btnSos.setAlpha(1f);
            }, 200);

            int peers = MeshManager.getInstance().getPeerCount();
            if (peers == 0) {
                showSosMessage("⚠ No rescuers in range yet — SOS will send when mesh forms",
                        0xFFFF8800);
            } else {
                MeshManager.getInstance().broadcastSOS();
                showSosMessage("⚠ SOS SENT to " + peers + " rescuer"
                        + (peers == 1 ? "" : "s") + " — Help is coming", 0xFFFF4F00);
            }
        });
    }

    private void showSosMessage(String msg, int color) {
        if (binding == null) return;
        binding.tvSosConfirmation.setVisibility(View.VISIBLE);
        binding.tvSosConfirmation.setTextColor(color);
        binding.tvSosConfirmation.setText(msg);
        binding.tvSosConfirmation.postDelayed(() -> {
            if (binding != null)
                binding.tvSosConfirmation.setVisibility(View.GONE);
        }, 8000);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        binding.btnSaveInfo.setOnClickListener(v -> {
            String loc  = binding.etLocation.getText() != null
                    ? binding.etLocation.getText().toString().trim() : "";
            String desc = binding.etDescription.getText() != null
                    ? binding.etDescription.getText().toString().trim() : "";
            int people  = (int) binding.sliderPeopleCount.getValue();
            int age     = binding.sliderAge != null ? (int) binding.sliderAge.getValue() : 30;
            int injury  = binding.rbMinor.isChecked() ? 1
                    : binding.rbSerious.isChecked() ? 2 : 0;

            // Save to local SharedPreferences
            prefs.edit()
                    .putString(KEY_SURVIVOR_LOCATION, loc)
                    .putInt(KEY_SURVIVOR_AGE, age)
                    .putString(KEY_SURVIVOR_DESCRIPTION, desc)
                    .putInt(KEY_SURVIVOR_PEOPLE, people)
                    .putInt(KEY_SURVIVOR_INJURY, injury)
                    .apply();

            // NEW: Broadcast updated profile as JSON to ALL connected peers immediately
            // This ensures real-time updates (not just on reconnect)
            MeshManager.getInstance().broadcastProfileAsJson(this);

            if (binding == null) return;
            binding.tvSavedConfirmation.setVisibility(View.VISIBLE);
            binding.tvSavedConfirmation.setText("✓ Info saved and broadcast to "
                    + MeshManager.getInstance().getPeerCount() + " rescuer(s)");
            binding.tvSavedConfirmation.postDelayed(() -> {
                if (binding != null)
                    binding.tvSavedConfirmation.setVisibility(View.GONE);
            }, 5000);
        });
    }

    // ── Volunteer cards ───────────────────────────────────────────────────────

    private void refreshVolunteerCards() {
        if (binding == null) return;
        binding.layoutVolunteerCards.removeAllViews();
        List<PeerProfile> volunteers = MeshManager.getInstance().getVolunteers();

        if (volunteers.isEmpty()) {
            binding.tvNoVolunteers.setVisibility(View.VISIBLE);
            binding.layoutVolunteerCards.setVisibility(View.GONE);
        } else {
            binding.tvNoVolunteers.setVisibility(View.GONE);
            binding.layoutVolunteerCards.setVisibility(View.VISIBLE);
            for (PeerProfile v : volunteers) {
                binding.layoutVolunteerCards.addView(buildVolunteerCard(v));
            }
        }
    }

    private View buildVolunteerCard(PeerProfile v) {
        androidx.cardview.widget.CardView card =
                new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cp);
        card.setCardBackgroundColor(0xFF0D0D0D);
        card.setRadius(12);
        card.setCardElevation(0);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(40, 32, 40, 32);

        // Name + dot
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(20, 20);
        dp.setMargins(0, 0, 16, 0);
        dot.setLayoutParams(dp);
        dot.setBackground(getDrawable(R.drawable.dot_active));
        nameRow.addView(dot);

        TextView tvName = new TextView(this);
        tvName.setText(v.name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameRow.addView(tvName);

        TextView badge = new TextView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(16, 0, 0, 0);
        badge.setLayoutParams(bp);
        badge.setText("VOLUNTEER");
        badge.setTextColor(0xFF00C853);
        badge.setTextSize(9);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setLetterSpacing(0.1f);
        nameRow.addView(badge);
        inner.addView(nameRow);

        // Skills
        if (v.skills != null && !v.skills.isEmpty()) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 12, 0, 0);
            tv.setLayoutParams(lp);
            tv.setText("🩺 " + v.skills.replace(",", "  ·  "));
            tv.setTextColor(0xFF888888);
            tv.setTextSize(12);
            inner.addView(tv);
        }

        // Equipment
        if (v.equipment != null && !v.equipment.isEmpty()) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 6, 0, 0);
            tv.setLayoutParams(lp);
            tv.setText("🎒 " + v.equipment.replace(",", "  ·  "));
            tv.setTextColor(0xFF666666);
            tv.setTextSize(12);
            inner.addView(tv);
        }

        card.addView(inner);
        return card;
    }

    // ── Pulse animation — fixed ───────────────────────────────────────────────

    private void startPulseAnimation() {
        if (isPulseRunning) return; // guard — never start two loops
        isPulseRunning = true;
        pulseRunnable  = new Runnable() {
            @Override public void run() {
                // FIX: null-check at every step of the chain
                if (binding == null || !isPulseRunning) return;
                binding.tvBroadcastingLabel.animate()
                        .alpha(0.2f)
                        .setDuration(700)
                        .withEndAction(() -> {
                            if (binding == null || !isPulseRunning) return;
                            binding.tvBroadcastingLabel.animate()
                                    .alpha(1f)
                                    .setDuration(700)
                                    .withEndAction(() -> {
                                        if (isPulseRunning) {
                                            pulseHandler.postDelayed(
                                                    pulseRunnable, 300);
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

    // ── Peer UI ───────────────────────────────────────────────────────────────

    private void updatePeerUI(int count) {
        if (binding == null) return;
        if (count > 0) {
            binding.tvPeerStatus.setText(count + " rescuer"
                    + (count == 1 ? "" : "s") + " in range");
            binding.tvPeerStatus.setTextColor(getColor(R.color.rescue_green));
            binding.tvBroadcastingLabel.setText("RESCUERS NEARBY");
            binding.tvBroadcastingLabel.setTextColor(getColor(R.color.rescue_green));
        } else {
            binding.tvPeerStatus.setText("No rescuers detected yet");
            binding.tvPeerStatus.setTextColor(getColor(R.color.text_tertiary));
            binding.tvBroadcastingLabel.setText("BROADCASTING FOR HELP");
            binding.tvBroadcastingLabel.setTextColor(
                    getColor(R.color.international_orange));
        }
    }

    // ── ConnectionStatusListener ──────────────────────────────────────────────

    @Override public void onPeerCountChanged(int c) { updatePeerUI(c); }

    @Override
    public void onPeerConnected(String endpointName) {
        if (binding == null) return;
        boolean isVol = ConnectionHelper.isVolunteer(endpointName);
        String  name  = ConnectionHelper.parseNameFromEndpointName(endpointName);
        Snackbar.make(binding.getRoot(),
                        isVol ? "🟢 Volunteer connected: " + name
                                : "📍 Another survivor nearby",
                        Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.snackbar_bg))
                .setTextColor(getColor(R.color.text_primary)).show();
    }

    @Override public void onPeerDisconnected(String id) { refreshVolunteerCards(); }

    @Override
    public void onSosReceived(String fromNodeId) {
        if (binding == null) return;
        Snackbar.make(binding.getRoot(),
                        "⚠ SOS received from another survivor nearby",
                        Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.triage_critical))
                .setTextColor(0xFFFFFFFF).show();
    }

    @Override public void onProfileReceived(PeerProfile p) { refreshVolunteerCards(); }
}