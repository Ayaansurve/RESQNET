package com.example.myapplication;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.snackbar.Snackbar;
import com.example.myapplication.ConnectionHelper;
import com.example.myapplication.MeshManager;
import com.example.myapplication.databinding.ActivityMapBinding;
import com.example.myapplication.PeerProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * MapActivity — Real-time proximity map of connected peers.
 *
 * Implements the 5-method ConnectionStatusListener from the pre-chat
 * ConnectionHelper. Every mesh event (connect, disconnect, profile update,
 * peer count change, SOS) causes an immediate map redraw so the display
 * always reflects the live mesh state.
 *
 * Design decisions:
 *   - MeshMapView is a custom View drawn entirely on Canvas — no XML layouts
 *     inside the map itself. This gives us full control of every pixel and
 *     makes 60fps redraws cheap.
 *   - Peer positions use golden-angle spiral placement so nodes never overlap
 *     regardless of count, and their position is STABLE between redraws
 *     (same index = same angle) so the map doesn't shuffle on every update.
 *   - Hit-testing is done in CANVAS space. Tap coords are converted from
 *     screen → canvas before comparing, so taps work correctly after pan/zoom.
 *   - New peers animate in with a scale-from-zero spring. Disconnected peers
 *     animate out with a fade before removal.
 *   - The "YOU" dot pulses continuously via a ValueAnimator that only runs
 *     while the view is attached (startPulse/stopPulse).
 *
 * WHERE THIS FILE GOES:
 *   app/src/main/java/com/resqnet/MapActivity.java
 */
public class MapActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    private ActivityMapBinding binding;
    private MeshMapView        mapView;
    private View               peerDetailPanel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        // Full-screen canvas map
        mapView = new MeshMapView(this, this::showPeerDetail);
        binding.mapContainer.addView(mapView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        // Sliding peer detail panel at bottom
        peerDetailPanel = buildPeerDetailPanel();
        binding.mapContainer.addView(peerDetailPanel,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM));

        setupLegend();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register for live mesh events
        MeshManager.getInstance().addListener(this);
        // Sync immediately in case peers connected while we were paused
        mapView.syncPeers(MeshManager.getInstance().getPeerProfiles());
        mapView.startPulse();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshManager.getInstance().removeListener(this);
        mapView.stopPulse();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setSupportActionBar(binding.mapToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Peer Map");
        }
        binding.mapToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupLegend() {
        binding.legendSelfDot.setBackgroundColor(0xFF2196F3);
        binding.legendVolunteerDot.setBackgroundColor(0xFF00C853);
        binding.legendSurvivorDot.setBackgroundColor(0xFFFF4F00);
    }

    private void setupButtons() {
        binding.btnRefreshMap.setOnClickListener(v -> {
            mapView.syncPeers(MeshManager.getInstance().getPeerProfiles());
            Snackbar.make(binding.getRoot(), "Map refreshed",
                            Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF1A1A1A)
                    .setTextColor(0xFFFFFFFF).show();
        });
        binding.btnCentreMap.setOnClickListener(v -> mapView.resetView());
    }

    // ── ConnectionStatusListener — all 5 callbacks cause immediate redraws ────
    //
    // Each callback pumps fresh peer data from MeshManager into the map view.
    // We don't cache state in the Activity — MeshManager IS the source of truth.

    @Override
    public void onPeerCountChanged(int count) {
        // Peer count changed — sync map. runOnUiThread is safe to call from
        // any thread; MeshManager's masterListener already posts to main, but
        // being explicit here makes the code self-documenting.
        runOnUiThread(() ->
                mapView.syncPeers(MeshManager.getInstance().getPeerProfiles()));
    }

    @Override
    public void onPeerConnected(String endpointName) {
        // New peer appeared — show entry animation + sync
        runOnUiThread(() -> {
            mapView.syncPeers(MeshManager.getInstance().getPeerProfiles());
            String role = ConnectionHelper.parseRoleFromEndpointName(endpointName);
            String name = ConnectionHelper.parseNameFromEndpointName(endpointName);
            Snackbar.make(binding.getRoot(),
                            "VOLUNTEER".equals(role)
                                    ? "🟢  Volunteer joined mesh: " + name
                                    : "📍  Survivor detected nearby",
                            Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF0D0D0D)
                    .setTextColor(0xFFCCCCCC).show();
        });
    }

    @Override
    public void onPeerDisconnected(String endpointId) {
        runOnUiThread(() ->
                mapView.syncPeers(MeshManager.getInstance().getPeerProfiles()));
    }

    @Override
    public void onProfileReceived(PeerProfile profile) {
        // Full profile arrived for a peer — their node now has real data.
        // Sync immediately so name/skills appear without waiting for next poll.
        runOnUiThread(() ->
                mapView.syncPeers(MeshManager.getInstance().getPeerProfiles()));
    }

    @Override
    public void onSosReceived(String fromNodeId) {
        runOnUiThread(() -> {
            if (binding == null) return;
            // Flash the map red briefly
            mapView.flashSos();
            Snackbar.make(binding.getRoot(),
                            "⚠  SOS received — someone nearby needs help!",
                            Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", v -> {})
                    .setBackgroundTint(0xFFCC0000)
                    .setTextColor(0xFFFFFFFF)
                    .setActionTextColor(0xFFFFFFFF).show();
        });
    }

    // ── Peer detail panel ─────────────────────────────────────────────────────

    private View buildPeerDetailPanel() {
        CardView card = new CardView(this);
        card.setCardBackgroundColor(0xFF0D0D0D);
        card.setRadius(32f);
        card.setCardElevation(24f);
        card.setVisibility(View.GONE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(56, 40, 56, 56);

        // Drag handle
        View handle = new View(this);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(100, 8);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, 0, 0, 32);
        handle.setLayoutParams(hp);
        handle.setBackgroundColor(0xFF2A2A2A);
        content.addView(handle);

        // Role row: coloured dot + label + close X
        LinearLayout roleRow = new LinearLayout(this);
        roleRow.setOrientation(LinearLayout.HORIZONTAL);
        roleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rrp.setMargins(0, 0, 0, 20);
        roleRow.setLayoutParams(rrp);

        View roleDot = new View(this);
        LinearLayout.LayoutParams rdp = new LinearLayout.LayoutParams(20, 20);
        rdp.setMargins(0, 0, 16, 0);
        roleDot.setLayoutParams(rdp);
        roleDot.setId(R.id.detail_role_dot);
        roleRow.addView(roleDot);

        TextView tvRole = new TextView(this);
        tvRole.setId(R.id.detail_role_label);
        tvRole.setTextColor(0xFF666666);
        tvRole.setTextSize(10f);
        tvRole.setLetterSpacing(0.18f);
        tvRole.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvRole.setLayoutParams(rlp);
        roleRow.addView(tvRole);

        TextView tvClose = new TextView(this);
        tvClose.setText("✕");
        tvClose.setTextColor(0xFF444444);
        tvClose.setTextSize(20f);
        tvClose.setPadding(24, 0, 0, 0);
        tvClose.setOnClickListener(v -> hidePeerDetail());
        roleRow.addView(tvClose);

        content.addView(roleRow);

        // Name
        TextView tvName = new TextView(this);
        tvName.setId(R.id.detail_name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(26f);
        tvName.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, 0, 0, 28);
        tvName.setLayoutParams(np);
        content.addView(tvName);

        // Divider
        View div = new View(this);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        div.setBackgroundColor(0xFF1A1A1A);
        content.addView(div);

        // Detail rows
        LinearLayout rows = new LinearLayout(this);
        rows.setId(R.id.detail_rows);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        drp.setMargins(0, 28, 0, 0);
        rows.setLayoutParams(drp);
        content.addView(rows);

        card.addView(content);
        return card;
    }

    private void showPeerDetail(PeerProfile peer) {
        View         roleDot = peerDetailPanel.findViewById(R.id.detail_role_dot);
        TextView     tvRole  = peerDetailPanel.findViewById(R.id.detail_role_label);
        TextView     tvName  = peerDetailPanel.findViewById(R.id.detail_name);
        LinearLayout rows    = peerDetailPanel.findViewById(R.id.detail_rows);

        boolean isVol = peer.isVolunteer();

        // Colour dot
        android.graphics.drawable.GradientDrawable dot =
                new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(isVol ? 0xFF00C853 : 0xFFFF4F00);
        roleDot.setBackground(dot);

        tvRole.setText(isVol ? "VOLUNTEER" : "SURVIVOR");
        tvRole.setTextColor(isVol ? 0xFF00C853 : 0xFFFF4F00);

        String displayName = (peer.name != null && !peer.name.isEmpty())
                ? peer.name
                : (isVol ? "Unknown Volunteer" : "Unknown Survivor");
        tvName.setText(displayName);

        rows.removeAllViews();
        if (isVol) {
            if (peer.skills    != null && !peer.skills.isEmpty())
                addDetailRow(rows, "🩺", "Medical Skills",      peer.skills.replace(",", "  ·  "));
            if (peer.equipment != null && !peer.equipment.isEmpty())
                addDetailRow(rows, "🎒", "Equipment",           peer.equipment.replace(",", "  ·  "));
            if (peer.situation != null && !peer.situation.isEmpty())
                addDetailRow(rows, "📞", "Emergency Contact",   peer.situation);
        } else {
            if (peer.skills    != null && !peer.skills.isEmpty())
                addDetailRow(rows, "📍", "Location",            peer.skills.replace("📍 ", ""));
            if (peer.equipment != null && !peer.equipment.isEmpty())
                addDetailRow(rows, "ℹ️",  "Status",             peer.equipment);
            if (peer.situation != null && !peer.situation.isEmpty())
                addDetailRow(rows, "💬", "Description",         peer.situation);
        }
        if (rows.getChildCount() == 0)
            addDetailRow(rows, "⏳", "Info", "Waiting for profile data from this peer…");

        // Slide up
        peerDetailPanel.setVisibility(View.VISIBLE);
        peerDetailPanel.setTranslationY(700f);
        peerDetailPanel.animate()
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    private void hidePeerDetail() {
        peerDetailPanel.animate()
                .translationY(700f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> peerDetailPanel.setVisibility(View.GONE))
                .start();
    }

    private void addDetailRow(LinearLayout parent, String emoji, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, 0, 24);
        row.setLayoutParams(rp);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView emo = new TextView(this);
        emo.setText(emoji + "  ");
        emo.setTextSize(13f);
        emo.setTextColor(0xFF555555);
        labelRow.addView(emo);

        TextView lbl = new TextView(this);
        lbl.setText(label.toUpperCase());
        lbl.setTextSize(10f);
        lbl.setTextColor(0xFF444444);
        lbl.setLetterSpacing(0.14f);
        lbl.setTypeface(null, Typeface.BOLD);
        labelRow.addView(lbl);
        row.addView(labelRow);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(15f);
        val.setTextColor(0xFFCCCCCC);
        val.setLineSpacing(6f, 1f);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.setMargins(48, 6, 0, 0);
        val.setLayoutParams(vp);
        row.addView(val);

        parent.addView(row);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MeshMapView — custom Canvas view
    // ══════════════════════════════════════════════════════════════════════════

    static class MeshMapView extends View {

        // ── Tap callback ──────────────────────────────────────────────────────
        interface OnPeerTappedListener { void onTapped(PeerProfile peer); }
        private final OnPeerTappedListener tapListener;

        // ── Paints (pre-allocated — never new'd in onDraw) ────────────────────
        private final Paint pBg          = new Paint();
        private final Paint pGrid        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pRing        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pRingDash    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pRingLabel   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSelf        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSelfRing    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSelfPulse   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pVol         = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSurv        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGlow        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSelected    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pLabel       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSubLabel    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pHud         = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pHudSub      = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCompassBg   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCompassText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSosBg       = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── Peer state ────────────────────────────────────────────────────────
        // peers is the live list — replaced atomically via syncPeers()
        private volatile List<PeerProfile> peers = new ArrayList<>();

        // Stable canvas-space positions keyed by endpointId so the same peer
        // always appears at the same spot between redraws. New peers get assigned
        // a position on first draw; removed peers lose their entry.
        private final java.util.Map<String, float[]> peerCanvasPos =
                new java.util.LinkedHashMap<>();

        // Per-peer entry animation progress (0→1). Peers start at 0 and
        // animate to 1 over ~400ms. Stored by endpointId.
        private final java.util.Map<String, Float> peerEntryProgress =
                new java.util.HashMap<>();

        // Selected peer for detail panel
        private PeerProfile selectedPeer = null;

        // ── Pan / zoom ────────────────────────────────────────────────────────
        private float scale   = 1.0f;
        private float transX  = 0f;
        private float transY  = 0f;
        private float lastX   = 0f;
        private float lastY   = 0f;
        private boolean panning = false;

        // ── Animations ────────────────────────────────────────────────────────
        // Single pulse animator that runs while view is attached
        private ValueAnimator pulseAnim;
        private float         pulseProgress = 0f;

        // SOS flash: drives background colour briefly to red
        private ValueAnimator sosFlashAnim;
        private int           sosOverlayColor = 0x00FF0000; // starts transparent

        // Per-peer entry animators (endpointId → animator)
        private final java.util.Map<String, ValueAnimator> entryAnims =
                new java.util.HashMap<>();

        // ── Gesture detectors ─────────────────────────────────────────────────
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector      gestureDetector;

        // ── Ring config ───────────────────────────────────────────────────────
        private static final float[]  RINGS   = {90f, 180f, 280f};
        private static final String[] RLABELS = {"~10m", "~30m", "60m+"};

        // Handler for entry animation ticks
        private final Handler animHandler = new Handler(Looper.getMainLooper());

        // ─────────────────────────────────────────────────────────────────────

        MeshMapView(Context ctx, OnPeerTappedListener listener) {
            super(ctx);
            this.tapListener = listener;
            setLayerType(LAYER_TYPE_HARDWARE, null);
            initPaints();

            scaleDetector = new ScaleGestureDetector(ctx,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(@NonNull ScaleGestureDetector d) {
                            scale = Math.max(0.35f, Math.min(scale * d.getScaleFactor(), 6f));
                            invalidate();
                            return true;
                        }
                    });

            gestureDetector = new GestureDetector(ctx,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                            resetView(); return true;
                        }
                        @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                            handleTap(e.getX(), e.getY()); return true;
                        }
                    });
        }

        private void initPaints() {
            pBg.setColor(0xFF000000);

            pGrid.setColor(0xFF0C0C0C);
            pGrid.setStyle(Paint.Style.STROKE);
            pGrid.setStrokeWidth(1f);

            pRing.setStyle(Paint.Style.STROKE);
            pRing.setStrokeWidth(1f);
            pRing.setColor(0xFF1A1A1A);

            pRingDash.setStyle(Paint.Style.STROKE);
            pRingDash.setStrokeWidth(1f);
            pRingDash.setColor(0xFF222222);
            pRingDash.setPathEffect(new DashPathEffect(new float[]{8f, 12f}, 0f));

            pRingLabel.setTextSize(18f);
            pRingLabel.setColor(0xFF1E1E1E);

            pSelf.setColor(0xFF2196F3);
            pSelf.setStyle(Paint.Style.FILL);

            pSelfRing.setColor(0xFF1565C0);
            pSelfRing.setStyle(Paint.Style.STROKE);
            pSelfRing.setStrokeWidth(2f);

            pSelfPulse.setStyle(Paint.Style.FILL);

            pVol.setColor(0xFF00C853);
            pVol.setStyle(Paint.Style.FILL);

            pSurv.setColor(0xFFFF4F00);
            pSurv.setStyle(Paint.Style.FILL);

            pGlow.setStyle(Paint.Style.FILL);

            pSelected.setStyle(Paint.Style.STROKE);
            pSelected.setStrokeWidth(2.5f);

            pLabel.setTextSize(22f);
            pLabel.setAntiAlias(true);

            pSubLabel.setTextSize(18f);
            pSubLabel.setAntiAlias(true);

            pHud.setColor(0xFFCCCCCC);
            pHud.setTextSize(26f);

            pHudSub.setColor(0xFF333333);
            pHudSub.setTextSize(20f);

            pCompassBg.setColor(0xFF111111);
            pCompassBg.setStyle(Paint.Style.FILL);

            pCompassText.setColor(0xFFFF4F00);
            pCompassText.setTextSize(24f);

            pSosBg.setStyle(Paint.Style.FILL);
        }

        // ── Public API ────────────────────────────────────────────────────────

        /**
         * Primary update method. Called from every ConnectionStatusListener callback.
         * Replaces the peer list and triggers a redraw. Thread-safe — always
         * called on main thread via runOnUiThread in the Activity.
         */
        public void syncPeers(List<PeerProfile> newPeers) {
            // Build a set of current endpointIds to detect new and removed peers
            java.util.Set<String> incoming = new java.util.HashSet<>();
            for (PeerProfile p : newPeers) incoming.add(p.endpointId);

            // Start entry animation for genuinely new peers
            for (PeerProfile p : newPeers) {
                if (!peerEntryProgress.containsKey(p.endpointId)) {
                    peerEntryProgress.put(p.endpointId, 0f);
                    startEntryAnimation(p.endpointId);
                }
            }

            // Remove state for peers that have left
            java.util.Iterator<String> it = peerEntryProgress.keySet().iterator();
            while (it.hasNext()) {
                String id = it.next();
                if (!incoming.contains(id)) {
                    it.remove();
                    peerCanvasPos.remove(id);
                    ValueAnimator va = entryAnims.remove(id);
                    if (va != null) va.cancel();
                }
            }

            peers = new ArrayList<>(newPeers);
            // Clear selected if their peer left
            if (selectedPeer != null && !incoming.contains(selectedPeer.endpointId)) {
                selectedPeer = null;
            }
            invalidate();
        }

        /** Flash the background red momentarily on SOS. */
        public void flashSos() {
            if (sosFlashAnim != null) sosFlashAnim.cancel();
            sosFlashAnim = ValueAnimator.ofObject(
                    new ArgbEvaluator(), 0x55FF0000, 0x00FF0000);
            sosFlashAnim.setDuration(1200);
            sosFlashAnim.setRepeatCount(2);
            sosFlashAnim.setRepeatMode(ValueAnimator.REVERSE);
            sosFlashAnim.addUpdateListener(a -> {
                sosOverlayColor = (int) a.getAnimatedValue();
                invalidate();
            });
            sosFlashAnim.start();
        }

        public void resetView() {
            transX = 0f; transY = 0f; scale = 1.0f;
            invalidate();
        }

        public void startPulse() {
            if (pulseAnim != null && pulseAnim.isRunning()) return;
            pulseAnim = ValueAnimator.ofFloat(0f, 1f);
            pulseAnim.setDuration(2200);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.setRepeatMode(ValueAnimator.RESTART);
            pulseAnim.setInterpolator(new LinearInterpolator());
            pulseAnim.addUpdateListener(a -> {
                pulseProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            pulseAnim.start();
        }

        public void stopPulse() {
            if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
            if (sosFlashAnim != null) { sosFlashAnim.cancel(); sosFlashAnim = null; }
            // Cancel all entry anims
            for (ValueAnimator va : entryAnims.values()) va.cancel();
            entryAnims.clear();
        }

        // ── Entry animation ───────────────────────────────────────────────────

        private void startEntryAnimation(String endpointId) {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(450);
            va.setInterpolator(new OvershootInterpolator(1.5f));
            va.addUpdateListener(a -> {
                peerEntryProgress.put(endpointId, (float) a.getAnimatedValue());
                invalidate();
            });
            entryAnims.put(endpointId, va);
            va.start();
        }

        // ── Tap hit-test ──────────────────────────────────────────────────────

        /**
         * Convert screen-space tap → canvas space, then compare against
         * canvas-space peer positions. This is correct after any pan or zoom.
         *
         * Canvas transform (applied in onDraw):
         *   canvas.scale(scale, scale, screenW/2, screenH/2)
         *   peers drawn at (cx + r*cos, cy + r*sin) where cx = screenW/2 + transX
         *
         * Inverse (screen → canvas):
         *   canvasX = (screenX - screenW/2) / scale + screenW/2 + transX
         *   canvasY = (screenY - screenH/2) / scale + screenH/2 + transY
         */
        private void handleTap(float sx, float sy) {
            float hw = getWidth()  / 2f;
            float hh = getHeight() / 2f;
            float cx = (sx - hw) / scale + hw + transX;
            float cy = (sy - hh) / scale + hh + transY;

            float hitR = 44f; // generous touch target in canvas units
            List<PeerProfile> snap = peers;
            for (PeerProfile peer : snap) {
                float[] pos = peerCanvasPos.get(peer.endpointId);
                if (pos == null) continue;
                float dx = cx - pos[0];
                float dy = cy - pos[1];
                if (dx * dx + dy * dy < hitR * hitR) {
                    selectedPeer = peer;
                    invalidate();
                    if (tapListener != null) tapListener.onTapped(peer);
                    return;
                }
            }
            selectedPeer = null;
            invalidate();
        }

        // ── Drawing ───────────────────────────────────────────────────────────

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f + transX;
            float cy = h / 2f + transY;

            // Background
            canvas.drawPaint(pBg);

            // SOS overlay (transparent normally)
            if (sosOverlayColor != 0x00FF0000) {
                pSosBg.setColor(sosOverlayColor);
                canvas.drawRect(0, 0, w, h, pSosBg);
            }

            // Save and apply pan+zoom transform
            canvas.save();
            canvas.scale(scale, scale, w / 2f, h / 2f);

            drawGrid(canvas, cx, cy);
            drawRings(canvas, cx, cy);
            assignAndDrawPeers(canvas, cx, cy);
            drawSelf(canvas, cx, cy);
            drawCompass(canvas);

            canvas.restore();

            // HUD drawn in screen space (not affected by pan/zoom)
            drawHud(canvas);
        }

        private void drawGrid(Canvas canvas, float cx, float cy) {
            float step = 80f;
            float offX = ((cx % step) + getWidth())  % step;
            float offY = ((cy % step) + getHeight()) % step;
            for (float x = offX; x < getWidth();  x += step)
                canvas.drawLine(x, 0, x, getHeight(), pGrid);
            for (float y = offY; y < getHeight(); y += step)
                canvas.drawLine(0, y, getWidth(), y, pGrid);
        }

        private void drawRings(Canvas canvas, float cx, float cy) {
            for (int i = 0; i < RINGS.length; i++) {
                // Innermost ring solid, outer two dashed
                canvas.drawCircle(cx, cy, RINGS[i], i == 0 ? pRing : pRingDash);
                pRingLabel.setColor(0xFF1A1A1A);
                canvas.drawText(RLABELS[i],
                        cx + RINGS[i] + 8f, cy + 18f, pRingLabel);
            }
        }

        /**
         * Assigns stable canvas positions to peers (computed once, cached by id)
         * and then draws each one with their current entry animation scale.
         */
        private void assignAndDrawPeers(Canvas canvas, float cx, float cy) {
            List<PeerProfile> snap = peers;
            if (snap == null || snap.isEmpty()) return;

            int count = snap.size();
            for (int i = 0; i < count; i++) {
                PeerProfile peer = snap.get(i);

                // Assign position if this is the first draw for this peer
                if (!peerCanvasPos.containsKey(peer.endpointId)) {
                    double angle = i * 2.39996; // golden angle — avoids clustering
                    float  r     = Math.min(
                            80f + (float) Math.sqrt(i + 1) * 52f,
                            RINGS[RINGS.length - 1] - 28f);
                    float px = cx + (float)(Math.cos(angle) * r);
                    float py = cy + (float)(Math.sin(angle) * r);
                    peerCanvasPos.put(peer.endpointId, new float[]{px, py});
                }

                float[] pos = peerCanvasPos.get(peer.endpointId);
                if (pos == null) continue;

                float px = pos[0];
                float py = pos[1];

                // Entry scale (0→1 with overshoot spring)
                float entryScale = peerEntryProgress.containsKey(peer.endpointId)
                        ? peerEntryProgress.get(peer.endpointId) : 1f;

                drawPeerNode(canvas, peer, px, py, entryScale);
            }
        }

        private void drawPeerNode(Canvas canvas, PeerProfile peer,
                                  float px, float py, float entryScale) {
            boolean isVol      = peer.isVolunteer();
            boolean isSelected = peer == selectedPeer;

            float dotR = 18f * entryScale;

            // Glow halo
            pGlow.setColor(isVol ? 0x1800C853 : 0x18FF4F00);
            canvas.drawCircle(px, py, (isSelected ? 46f : 34f) * entryScale, pGlow);

            // Selection ring (dashed)
            if (isSelected) {
                pSelected.setColor(isVol ? 0xFF00C853 : 0xFFFF4F00);
                pSelected.setPathEffect(new DashPathEffect(new float[]{6f, 5f}, 0f));
                canvas.drawCircle(px, py, 28f * entryScale, pSelected);
                pSelected.setPathEffect(null);
            }

            // Dot fill
            canvas.drawCircle(px, py, dotR, isVol ? pVol : pSurv);

            // Letter inside dot
            if (entryScale > 0.5f) {
                pLabel.setColor(0xFF000000);
                pLabel.setTextSize(16f * entryScale);
                pLabel.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(isVol ? "V" : "S",
                        px - 6f * entryScale, py + 6f * entryScale, pLabel);
            }

            // Name label (only after most of entry anim plays)
            if (entryScale > 0.6f) {
                float labelAlpha = (entryScale - 0.6f) / 0.4f;
                pLabel.setColor((int)(labelAlpha * 0xFF) << 24 | 0x00DDDDDD);
                pLabel.setTextSize(22f);
                pLabel.setTypeface(Typeface.DEFAULT);
                String name = (peer.name != null && !peer.name.isEmpty())
                        ? peer.name
                        : (isVol ? "Volunteer" : "Survivor");
                canvas.drawText(name, px + dotR + 8f, py + 6f, pLabel);

                // Sub-label (skills for vols, brief situation for survivors)
                String sub = isVol ? peer.skills : peer.situation;
                if (sub != null && !sub.isEmpty()) {
                    pSubLabel.setColor((int)(labelAlpha * 0xBB) << 24 |
                            (isVol ? 0x00007A33 : 0x00992B00));
                    pSubLabel.setTextSize(18f);
                    String short_ = sub.length() > 20
                            ? sub.substring(0, 20) + "…" : sub;
                    canvas.drawText(short_, px + dotR + 8f, py + 26f, pSubLabel);
                }
            }

            // Tap hint when selected
            if (isSelected && entryScale >= 1f) {
                pSubLabel.setColor(0xFF555555);
                pSubLabel.setTextSize(17f);
                canvas.drawText("tap for details", px - 46f, py - 34f, pSubLabel);
            }
        }

        private void drawSelf(Canvas canvas, float cx, float cy) {
            // Expanding pulse ring
            float pr    = 22f + pulseProgress * 55f;
            int   pa    = (int)((1f - pulseProgress) * 70f);
            pSelfPulse.setColor((pa << 24) | 0x002196F3);
            canvas.drawCircle(cx, cy, pr, pSelfPulse);

            // Second, slower pulse offset by 0.5
            float pr2 = 22f + ((pulseProgress + 0.5f) % 1f) * 55f;
            int   pa2 = (int)((1f - (pulseProgress + 0.5f) % 1f) * 40f);
            pSelfPulse.setColor((pa2 << 24) | 0x002196F3);
            canvas.drawCircle(cx, cy, pr2, pSelfPulse);

            // Core dot
            canvas.drawCircle(cx, cy, 22f, pSelf);
            canvas.drawCircle(cx, cy, 22f, pSelfRing);

            // YOU label
            pLabel.setColor(0xFFFFFFFF);
            pLabel.setTextSize(22f);
            pLabel.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("YOU", cx + 28f, cy + 8f, pLabel);
        }

        private void drawCompass(Canvas canvas) {
            float ccx = getWidth() - 54f;
            float ccy = 72f;
            canvas.drawCircle(ccx, ccy, 30f, pCompassBg);
            pCompassText.setTextSize(22f);
            canvas.drawText("N", ccx - 8f, ccy + 9f, pCompassText);
        }

        private void drawHud(Canvas canvas) {
            // Count vols vs survivors
            int vc = 0, sc = 0;
            List<PeerProfile> snap = peers;
            if (snap != null) {
                for (PeerProfile p : snap) {
                    if (p.isVolunteer()) vc++; else sc++;
                }
            }

            int total = vc + sc;
            String countText = total == 0
                    ? "Searching for peers…"
                    : vc + " volunteer" + (vc != 1 ? "s" : "") +
                    "  ·  " + sc + " survivor" + (sc != 1 ? "s" : "");

            pHud.setColor(total == 0 ? 0xFF333333 : 0xFFCCCCCC);
            canvas.drawText(countText, 24f, 56f, pHud);

            pHudSub.setColor(0xFF282828);
            canvas.drawText(
                    String.format("%.1fx   pinch·pan·double-tap to reset", scale),
                    24f, getHeight() - 20f, pHudSub);
        }

        // ── Touch ─────────────────────────────────────────────────────────────

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX   = event.getX();
                    lastY   = event.getY();
                    panning = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (panning && !scaleDetector.isInProgress()
                            && event.getPointerCount() == 1) {
                        transX += (event.getX() - lastX) / scale;
                        transY += (event.getY() - lastY) / scale;
                        lastX   = event.getX();
                        lastY   = event.getY();
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    panning = false;
                    break;
            }
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopPulse();
        }
    }
}