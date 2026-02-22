package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.myapplication.databinding.ActivityTriageBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * TriageActivity — Displays survivors in priority order (excluding self).
 *
 * This activity shows:
 * 1. All connected survivors EXCEPT the current user, grouped by triage category
 * 2. Priority ranking based on injury severity and age
 * 3. Rescue sequence recommendations
 * 4. Detailed survivor information (age, injury severity, location, people count)
 *
 * Data Flow:
 * - SurvivorActivity broadcasts PeerProfile as JSON with triage data
 * - ConnectionHelper receives PROFILE_JSON messages and routes to MeshManager
 * - MeshManager parses JSON and stores in peerProfiles map
 * - TriageActivity listens for onProfileReceived() callbacks
 * - buildSurvivorList() uses getSurvivorsExcludingSelf() to filter out self
 * - Triage data includes: age, injurySeverity (1-5), location
 */
public class TriageActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    private ActivityTriageBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTriageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupRefreshButton();
        refreshTriageView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshManager.getInstance().addListener(this);
        refreshTriageView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshManager.getInstance().removeListener(this);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Survivor Triage");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRefreshButton() {
        binding.btnRefresh.setOnClickListener(v -> refreshTriageView());
    }

    private void refreshTriageView() {
        if (binding == null) return;

        binding.layoutTriageList.removeAllViews();
        List<SurvivorInfo> survivorList = buildSurvivorList();

        if (survivorList.isEmpty()) {
            binding.tvNoSurvivors.setVisibility(View.VISIBLE);
            binding.layoutTriageList.setVisibility(View.GONE);
            binding.layoutRescueSequence.setVisibility(View.GONE);
            return;
        }

        binding.tvNoSurvivors.setVisibility(View.GONE);
        binding.layoutTriageList.setVisibility(View.VISIBLE);
        binding.layoutRescueSequence.setVisibility(View.VISIBLE);

        List<SurvivorInfo> triaged = TriageCalculator.calculateTriage(survivorList);
        List<List<SurvivorInfo>> grouped = TriageCalculator.groupByTriageCategory(triaged);

        String[] categoryLabels = {"IMMEDIATE (RED)", "URGENT (YELLOW)", "DELAYED (GREEN)", "MINOR (WHITE)"};
        int[] categoryColors = {0xFFD32F2F, 0xFFFF6F00, 0xFFFFD600, 0xFF00C853};

        int categoryIndex = 0;
        for (List<SurvivorInfo> category : grouped) {
            if (!category.isEmpty()) {
                binding.layoutTriageList.addView(buildCategoryHeader(categoryLabels[categoryIndex], categoryColors[categoryIndex]));
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
            SurvivorInfo.InjuryLevel injuryLevel;
            if (peer.injurySeverity <= 0) {
                injuryLevel = SurvivorInfo.InjuryLevel.NONE;
            } else if (peer.injurySeverity == 1) {
                injuryLevel = SurvivorInfo.InjuryLevel.MINOR;
            } else if (peer.injurySeverity == 2) {
                injuryLevel = SurvivorInfo.InjuryLevel.SERIOUS;
            } else {
                injuryLevel = SurvivorInfo.InjuryLevel.CRITICAL;
            }

            String location = (peer.location != null && !peer.location.isEmpty())
                    ? peer.location
                    : "Unknown Location";

            return new SurvivorInfo(
                    peer.deviceId,
                    peer.endpointId,
                    peer.name != null ? peer.name : "Unknown",
                    location,
                    injuryLevel,
                    peer.age > 0 ? peer.age : 30,
                    1,
                    peer.situation != null ? peer.situation : "",
                    peer.lat,
                    peer.lng,
                    false
            );
        } catch (Exception e) {
            android.util.Log.w("TriageActivity", "Failed to create SurvivorInfo from peer: " + e.getMessage());
            return null;
        }
    }

    private View buildCategoryHeader(String label, int color) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 24, 16, 12);
        tv.setLayoutParams(lp);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    private View buildSurvivorCard(SurvivorInfo survivor) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(16, 0, 16, 12);
        card.setLayoutParams(cp);
        card.setCardBackgroundColor(0xFF0D0D0D);
        card.setRadius(12);
        card.setCardElevation(4);
        card.setContentPadding(20, 16, 20, 16);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(16, 16);
        dotLp.setMargins(0, 0, 12, 0);
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(survivor.injuryLevel.color);
        nameRow.addView(dot);

        TextView tvName = new TextView(this);
        tvName.setText(survivor.name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameRow.addView(tvName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvInjury = new TextView(this);
        tvInjury.setText(survivor.injuryLevel.label);
        tvInjury.setTextColor(survivor.injuryLevel.color);
        tvInjury.setTextSize(11);
        tvInjury.setTypeface(null, android.graphics.Typeface.BOLD);
        nameRow.addView(tvInjury);
        inner.addView(nameRow);

        TextView tvDetails = new TextView(this);
        tvDetails.setText("👤 Age: " + survivor.age + " (" + survivor.ageGroup.label + ")  👥 " + survivor.peopleCount);
        tvDetails.setTextColor(0xFF999999);
        tvDetails.setTextSize(12);
        inner.addView(tvDetails);

        if (survivor.location != null) {
            TextView tvLoc = new TextView(this);
            tvLoc.setText("📍 " + survivor.location);
            tvLoc.setTextColor(0xFF777777);
            tvLoc.setTextSize(12);
            inner.addView(tvLoc);
        }

        if (survivor.description != null && !survivor.description.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText("📝 " + survivor.description);
            tvDesc.setTextColor(0xFF666666);
            tvDesc.setTextSize(11);
            inner.addView(tvDesc);
        }

        card.addView(inner);
        return card;
    }

    @Override
    public void onConnectionStatusChanged(String endpointId, ConnectionHelper.Status status) {}

    @Override
    public void onProfileReceived(String endpointId, PeerProfile profile) {
        runOnUiThread(this::refreshTriageView);
    }
}
