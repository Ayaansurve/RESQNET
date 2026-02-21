package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
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
 * TriageActivity — Displays survivors in priority order.
 *
 * This activity shows:
 * 1. All connected survivors grouped by triage category
 * 2. Priority ranking based on injury severity and age
 * 3. Rescue sequence recommendations
 * 4. Detailed survivor information (age, injury, location, people count)
 *
 * The triage data is populated from:
 * - MeshManager.getSurvivors() — survivors connected via mesh
 * - Local SharedPreferences — current user's own survivor info (if applicable)
 */
public class TriageActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    private ActivityTriageBinding binding;
    private SharedPreferences prefs;

    private List<SurvivorInfo> survivorList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTriageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

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

    // ── Setup ─────────────────────────────────────────────────────────────────

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

    // ── Triage View ───────────────────────────────────────────────────────────

    private void refreshTriageView() {
        if (binding == null) return;

        // Clear all views
        binding.layoutTriageList.removeAllViews();

        // Build survivor list from mesh and local data
        survivorList = buildSurvivorList();

        if (survivorList.isEmpty()) {
            binding.tvNoSurvivors.setVisibility(View.VISIBLE);
            binding.layoutTriageList.setVisibility(View.GONE);
            binding.layoutRescueSequence.setVisibility(View.GONE);
            return;
        }

        binding.tvNoSurvivors.setVisibility(View.GONE);
        binding.layoutTriageList.setVisibility(View.VISIBLE);
        binding.layoutRescueSequence.setVisibility(View.VISIBLE);

        // Calculate triage priority
        List<SurvivorInfo> triaged = TriageCalculator.calculateTriage(survivorList);

        // Display survivors by category
        List<List<SurvivorInfo>> grouped = TriageCalculator.groupByTriageCategory(triaged);

        String[] categoryLabels = {"IMMEDIATE (RED)", "URGENT (YELLOW)", "DELAYED (GREEN)", "MINOR (WHITE)"};
        int[] categoryColors = {0xFFD32F2F, 0xFFFF6F00, 0xFFFFD600, 0xFF00C853};

        int categoryIndex = 0;
        for (List<SurvivorInfo> category : grouped) {
            if (!category.isEmpty()) {
                // Add category header
                View header = buildCategoryHeader(categoryLabels[categoryIndex], categoryColors[categoryIndex]);
                binding.layoutTriageList.addView(header);

                // Add survivors in this category
                for (SurvivorInfo survivor : category) {
                    binding.layoutTriageList.addView(buildSurvivorCard(survivor));
                }
            }
            categoryIndex++;
        }

        // Update rescue sequence
        binding.tvRescueSequence.setText(TriageCalculator.getRescueSequence(triaged));
    }

    private List<SurvivorInfo> buildSurvivorList() {
        List<SurvivorInfo> survivors = new ArrayList<>();

        // Add survivors from mesh
        for (PeerProfile peer : MeshManager.getInstance().getSurvivors()) {
            SurvivorInfo info = createSurvivorInfoFromPeerProfile(peer);
            if (info != null) {
                survivors.add(info);
            }
        }

        // Add self if user is a survivor
        String userRole = prefs.getString(MainActivity.KEY_USER_ROLE, MainActivity.ROLE_VOLUNTEER);
        if (MainActivity.ROLE_SURVIVOR.equals(userRole)) {
            SurvivorInfo selfInfo = createSelfSurvivorInfo();
            if (selfInfo != null) {
                survivors.add(selfInfo);
            }
        }

        return survivors;
    }

    private SurvivorInfo createSurvivorInfoFromPeerProfile(PeerProfile peer) {
        try {
            // Parse injury level from situation string
            // Format: "age:XX,injury:Y,people:Z" or similar
            SurvivorInfo.InjuryLevel injuryLevel = SurvivorInfo.InjuryLevel.NONE;
            int age = 30; // default

            if (peer.situation != null && !peer.situation.isEmpty()) {
                // Try to parse structured format
                String[] parts = peer.situation.split(",");
                for (String part : parts) {
                    if (part.startsWith("injury:")) {
                        int injuryCode = Integer.parseInt(part.substring(7));
                        injuryLevel = SurvivorInfo.InjuryLevel.values()[Math.min(injuryCode, 3)];
                    } else if (part.startsWith("age:")) {
                        age = Integer.parseInt(part.substring(4));
                    }
                }
            }

            return new SurvivorInfo(
                    peer.endpointId,
                    peer.name,
                    "", // location not in PeerProfile
                    injuryLevel,
                    age,
                    1, // default people count
                    peer.situation,
                    peer.lat,
                    peer.lng
            );
        } catch (Exception e) {
            return null;
        }
    }

    private SurvivorInfo createSelfSurvivorInfo() {
        try {
            String name = prefs.getString(MainActivity.KEY_USER_NAME, "Self");
            String location = prefs.getString(SurvivorActivity.KEY_SURVIVOR_LOCATION, "Unknown");
            int injuryCode = prefs.getInt(SurvivorActivity.KEY_SURVIVOR_INJURY, 0);
            int age = prefs.getInt("survivor_age", 30); // will be added
            int people = prefs.getInt(SurvivorActivity.KEY_SURVIVOR_PEOPLE, 1);
            String description = prefs.getString(SurvivorActivity.KEY_SURVIVOR_DESCRIPTION, "");

            SurvivorInfo.InjuryLevel injuryLevel = SurvivorInfo.InjuryLevel.values()[Math.min(injuryCode, 3)];

            return new SurvivorInfo(
                    "self",
                    name,
                    location,
                    injuryLevel,
                    age,
                    people,
                    description,
                    0.0,
                    0.0
            );
        } catch (Exception e) {
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
        card.setCardBackgroundColor(0xFF0D0D0D);  // surface_2
        card.setRadius(12);
        card.setCardElevation(4);
        card.setContentPadding(20, 16, 20, 16);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        // Name + injury badge
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Injury color indicator dot
        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(16, 16);
        dotLp.setMargins(0, 0, 12, 0);
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(survivor.injuryLevel.color);
        nameRow.addView(dot);

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(survivor.name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameRow.addView(tvName, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Injury badge
        TextView tvInjury = new TextView(this);
        tvInjury.setText(survivor.injuryLevel.label);
        tvInjury.setTextColor(survivor.injuryLevel.color);
        tvInjury.setTextSize(11);
        tvInjury.setTypeface(null, android.graphics.Typeface.BOLD);
        tvInjury.setLetterSpacing(0.1f);
        nameRow.addView(tvInjury);

        inner.addView(nameRow);

        // Age and people count
        LinearLayout detailsRow = new LinearLayout(this);
        detailsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailsLp.setMargins(0, 8, 0, 0);
        detailsRow.setLayoutParams(detailsLp);

        TextView tvAge = new TextView(this);
        tvAge.setText("👤 Age: " + survivor.age + " (" + survivor.ageGroup.label + ")");
        tvAge.setTextColor(0xFF999999);
        tvAge.setTextSize(12);
        detailsRow.addView(tvAge, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPeople = new TextView(this);
        tvPeople.setText("👥 " + survivor.peopleCount);
        tvPeople.setTextColor(0xFF999999);
        tvPeople.setTextSize(12);
        detailsRow.addView(tvPeople);

        inner.addView(detailsRow);

        // Location
        if (survivor.location != null && !survivor.location.isEmpty()) {
            TextView tvLocation = new TextView(this);
            tvLocation.setText("📍 " + survivor.location);
            tvLocation.setTextColor(0xFF777777);
            tvLocation.setTextSize(12);
            LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            locLp.setMargins(0, 6, 0, 0);
            tvLocation.setLayoutParams(locLp);
            inner.addView(tvLocation);
        }

        // Description
        if (survivor.description != null && !survivor.description.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText("📝 " + survivor.description);
            tvDesc.setTextColor(0xFF666666);
            tvDesc.setTextSize(11);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            descLp.setMargins(0, 6, 0, 0);
            tvDesc.setLayoutParams(descLp);
            tvDesc.setMaxLines(2);
            tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            inner.addView(tvDesc);
        }

        // Recommendation
        TextView tvRec = new TextView(this);
        tvRec.setText(TriageCalculator.getTriageRecommendation(survivor));
        tvRec.setTextColor(survivor.injuryLevel.color);
        tvRec.setTextSize(10);
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        recLp.setMargins(0, 10, 0, 0);
        tvRec.setLayoutParams(recLp);
        tvRec.setMaxLines(3);
        tvRec.setEllipsize(android.text.TextUtils.TruncateAt.END);
        inner.addView(tvRec);

        card.addView(inner);
        return card;
    }

    // ── ConnectionStatusListener ──────────────────────────────────────────────

    @Override
    public void onPeerCountChanged(int peerCount) {
        refreshTriageView();
    }

    @Override
    public void onPeerConnected(String endpointName) {
        refreshTriageView();
    }

    @Override
    public void onPeerDisconnected(String endpointId) {
        refreshTriageView();
    }

    @Override
    public void onSosReceived(String fromNodeId) {
        refreshTriageView();
    }

    @Override
    public void onProfileReceived(PeerProfile profile) {
        refreshTriageView();
    }
}


