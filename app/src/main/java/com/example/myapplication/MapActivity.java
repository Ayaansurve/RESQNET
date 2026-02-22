package com.example.myapplication;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import com.example.myapplication.ConnectionHelper;
import com.example.myapplication.MeshManager;
import com.example.myapplication.databinding.ActivityMapBinding;
import com.example.myapplication.PeerProfile;

import java.util.List;

/**
 * MapActivity — Visualises nearby mesh peers on a radar-style map.
 * 
 * Features:
 * - Radar-style "MeshMapView" for offline proximity tracking.
 * - Support for toggling to an online map mode (placeholder).
 * - Real-time updates when peers connect/disconnect or send data.
 * - Interaction via pinch-to-zoom and panning.
 */
public class MapActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    private ActivityMapBinding binding;
    private MeshMapView mapView;

    private boolean isOnlineMode = false;
    private View onlineMapView; // placeholder for Google/Mapbox view

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        // Initialize the custom radar view and add it to the container
        mapView = new MeshMapView(this);
        binding.mapContainer.addView(mapView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        // Setup placeholder for online map (e.g. Google Maps)
        onlineMapView = new View(this);
        binding.onlineMapContainer.addView(
                onlineMapView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setupLegend();
        setupRefreshButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start listening for mesh network updates
        MeshManager.getInstance().addListener(this);
        mapView.refreshPeers();

        // Resume radar pulse animation if in mesh mode
        if (!isOnlineMode) {
            mapView.startPulse();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop updates and animations when activity is not in foreground
        MeshManager.getInstance().removeListener(this);
        mapView.stopPulse();
    }

    /** Configures the action bar with a back button and title. */
    private void setupToolbar() {
        setSupportActionBar(binding.mapToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Nearby Peers");
        }
        binding.mapToolbar.setNavigationOnClickListener(v -> finish());
    }

    /** Sets colors for the map legend dots to match the radar markers. */
    private void setupLegend() {
        binding.legendVolunteerDot.setBackgroundColor(0xFF00C853);
        binding.legendSurvivorDot.setBackgroundColor(0xFFFF4F00);
        binding.legendSelfDot.setBackgroundColor(0xFF2196F3);
    }

    /** Configures tap (refresh) and long-press (toggle mode) behavior for the refresh button. */
    private void setupRefreshButton() {
        binding.btnRefreshMap.setOnClickListener(v -> {
            mapView.refreshPeers();
            Snackbar.make(binding.getRoot(), "Map refreshed",
                            Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF1A1A1A)
                    .setTextColor(0xFFFFFFFF).show();
        });

        binding.btnRefreshMap.setOnLongClickListener(v -> {
            setMapMode(!isOnlineMode);

            Snackbar.make(binding.getRoot(),
                    isOnlineMode ? "Online map enabled" : "Offline mesh map enabled",
                    Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF1A1A1A)
                    .setTextColor(0xFFFFFFFF).show();

            return true;
        });
    }

    /** Toggles visibility between the custom MeshMapView and the online map container. */
    private void setMapMode(boolean online) {
        isOnlineMode = online;

        binding.mapContainer.setVisibility(online ? View.GONE : View.VISIBLE);
        binding.onlineMapContainer.setVisibility(online ? View.VISIBLE : View.GONE);

        if (online) {
            mapView.stopPulse();
        } else {
            mapView.startPulse();
        }
    }

    // ── Mesh Network Callbacks ────────────────────────────────────────────────

    @Override public void onPeerCountChanged(int c)    { mapView.refreshPeers(); }
    @Override public void onPeerConnected(String n)    { mapView.refreshPeers(); }
    @Override public void onPeerDisconnected(String id){ mapView.refreshPeers(); }
    @Override public void onProfileReceived(PeerProfile p) { mapView.refreshPeers(); }

    /** Displays a high-priority alert when an SOS signal is detected nearby. */
    @Override public void onSosReceived(String nodeId) {
        runOnUiThread(() ->
                Snackbar.make(binding.getRoot(),
                                "⚠ SOS received from nearby peer!",
                                Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFFFF0000)
                        .setTextColor(0xFFFFFFFF).show());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MeshMapView — A custom hardware-accelerated radar view
    // ══════════════════════════════════════════════════════════════════════════

    static class MeshMapView extends View {

        // Pre-allocated Paint objects to avoid allocations during onDraw()
        private final Paint paintBackground = new Paint();
        private final Paint paintGrid       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintRing       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintSelf       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintSelfGlow   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintVolunteer  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintSurvivor   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintLabel      = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintSubLabel   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintHud        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintHint       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintPeerGlow   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintRingLabel  = new Paint(Paint.ANTI_ALIAS_FLAG);

        private List<PeerProfile> peers;

        // Viewport state for panning and zooming
        private float scaleFactor = 1.0f;
        private float translateX  = 0f;
        private float translateY  = 0f;
        private float lastTouchX  = 0f;
        private float lastTouchY  = 0f;
        private boolean isPanning = false;

        // Hardware-accelerated pulse animation tied to display refresh rate
        private ValueAnimator pulseAnimator;
        private float pulseProgress = 0f; // 0.0 → 1.0

        private static final float[] RING_RADII = {60f, 120f, 200f};
        private static final String[] RING_LABELS = {"CLOSE", "NEAR", "FAR"};

        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector      gestureDetector;

        public MeshMapView(Context ctx) {
            super(ctx);
            // Enable hardware layer for smoother scaling/panning performance
            setLayerType(LAYER_TYPE_HARDWARE, null);

            // ── Paint setup ──────────────────────────────────────────────
            paintBackground.setColor(0xFF000000);

            paintGrid.setColor(0xFF141414);
            paintGrid.setStyle(Paint.Style.STROKE);
            paintGrid.setStrokeWidth(1f);

            paintRing.setColor(0xFF1F1F1F);
            paintRing.setStyle(Paint.Style.STROKE);
            paintRing.setStrokeWidth(1.5f);

            paintRingLabel.setColor(0xFF2A2A2A);
            paintRingLabel.setTextSize(22f);
            paintRingLabel.setAntiAlias(true);

            paintSelf.setColor(0xFF2196F3);
            paintSelf.setStyle(Paint.Style.FILL);

            paintSelfGlow.setStyle(Paint.Style.FILL);

            paintVolunteer.setColor(0xFF00C853);
            paintVolunteer.setStyle(Paint.Style.FILL);

            paintSurvivor.setColor(0xFFFF4F00);
            paintSurvivor.setStyle(Paint.Style.FILL);

            paintPeerGlow.setStyle(Paint.Style.FILL);

            paintLabel.setColor(0xFFCCCCCC);
            paintLabel.setTextSize(26f);
            paintLabel.setAntiAlias(true);

            paintSubLabel.setTextSize(20f);
            paintSubLabel.setAntiAlias(true);

            paintHud.setColor(0xFFFFFFFF);
            paintHud.setTextSize(30f);
            paintHud.setAntiAlias(true);

            paintHint.setColor(0xFF444444);
            paintHint.setTextSize(22f);
            paintHint.setAntiAlias(true);

            // Setup gesture detection for pinch-zoom
            scaleDetector = new ScaleGestureDetector(ctx,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector d) {
                            scaleFactor = Math.max(0.5f, Math.min(scaleFactor * d.getScaleFactor(), 4f));
                            invalidate();
                            return true;
                        }
                    });

            // Setup gesture detection for double-tap reset
            gestureDetector = new GestureDetector(ctx,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDoubleTap(MotionEvent e) {
                            scaleFactor = 1f; translateX = 0f; translateY = 0f;
                            invalidate();
                            return true;
                        }
                    });

            peers = MeshManager.getInstance().getPeerProfiles();
        }

        /** Starts the radar pulse animation loop. */
        public void startPulse() {
            if (pulseAnimator != null && pulseAnimator.isRunning()) return;

            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(1800);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
            pulseAnimator.setInterpolator(new LinearInterpolator());
            pulseAnimator.addUpdateListener(anim -> {
                pulseProgress = (float) anim.getAnimatedValue();
                invalidate(); // Triggers redraw at display VSync rate
            });
            pulseAnimator.start();
        }

        /** Cancels the pulse animation loop. */
        public void stopPulse() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
        }

        /** Updates the peer list from MeshManager and triggers a redraw. */
        public void refreshPeers() {
            peers = MeshManager.getInstance().getPeerProfiles();
            invalidate();
        }

        // ── Rendering logic ───────────────────────────────────────────────────

        @Override
        protected void onDraw(Canvas canvas) {
            // View center point adjusted for current pan offset
            float cx = getWidth()  / 2f + translateX;
            float cy = getHeight() / 2f + translateY;

            canvas.drawPaint(paintBackground);
            canvas.save();
            // Apply zoom centered on the screen center
            canvas.scale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f);

            drawGrid(canvas, cx, cy);
            drawRings(canvas, cx, cy);
            drawPeers(canvas, cx, cy);
            drawSelf(canvas, cx, cy);

            canvas.restore();
            drawHud(canvas);
        }

        /** Draws a static background grid for spatial reference. */
        private void drawGrid(Canvas canvas, float cx, float cy) {
            float step = 80f;
            for (float x = cx % step; x < getWidth(); x += step)
                canvas.drawLine(x, 0, x, getHeight(), paintGrid);
            for (float y = cy % step; y < getHeight(); y += step)
                canvas.drawLine(0, y, getWidth(), y, paintGrid);
        }

        /** Draws the circular distance reference rings. */
        private void drawRings(Canvas canvas, float cx, float cy) {
            for (int i = 0; i < RING_RADII.length; i++) {
                canvas.drawCircle(cx, cy, RING_RADII[i], paintRing);
                canvas.drawText(RING_LABELS[i],
                        cx + RING_RADII[i] * 0.68f,
                        cy - RING_RADII[i] * 0.68f,
                        paintRingLabel);
            }
        }

        /** Draws the central marker representing the current user and its pulse effect. */
        private void drawSelf(Canvas canvas, float cx, float cy) {
            // Animated pulse ring driven by ValueAnimator
            float pulseR     = 18f + pulseProgress * 50f;
            int   pulseAlpha = (int)((1f - pulseProgress) * 60f);
            paintSelfGlow.setColor(0x002196F3 | (pulseAlpha << 24));
            canvas.drawCircle(cx, cy, pulseR, paintSelfGlow);

            // Central blue dot
            canvas.drawCircle(cx, cy, 18f, paintSelf);
            paintLabel.setColor(0xFFFFFFFF);
            canvas.drawText("YOU", cx + 22f, cy + 8f, paintLabel);
        }

        /** Draws all nearby peers discovered via the mesh network. */
        private void drawPeers(Canvas canvas, float cx, float cy) {
            if (peers == null || peers.isEmpty()) return;

            String selfId = MeshManager.getInstance().getSelfId();
            List<PeerProfile> triagePeers = new java.util.ArrayList<>();

            // Filter peers to display only relevant triage-eligible survivors
            for (PeerProfile peer : peers) {
                if (!peer.endpointId.equals(selfId) && peer.isSurvivor()) {
                    triagePeers.add(peer);
                }
            }

            int count = triagePeers.size();
            for (int i = 0; i < count; i++) {
                PeerProfile peer = triagePeers.get(i);

                // Calculate marker position based on index (procedural radar layout)
                double angle    = (2 * Math.PI * i) / count - Math.PI / 2;
                float  distance = 90f + (i * 40f % 100f);
                float  px       = cx + (float)(Math.cos(angle) * distance);
                float  py       = cy + (float)(Math.sin(angle) * distance);

                // Status glow based on role (Green = Volunteer, Orange = Survivor)
                paintPeerGlow.setColor(peer.isVolunteer() ? 0x2200C853 : 0x22FF4F00);
                canvas.drawCircle(px, py, 28f, paintPeerGlow);

                // Marker dot
                canvas.drawCircle(px, py, 16f,
                        peer.isVolunteer() ? paintVolunteer : paintSurvivor);

                // Peer name label
                paintLabel.setColor(0xFFCCCCCC);
                String name = (peer.name != null && !peer.name.isEmpty())
                        ? peer.name
                        : (peer.isVolunteer() ? "Volunteer" : "Survivor");
                canvas.drawText(name, px + 20f, py + 8f, paintLabel);

                // Detailed status text (Skills or Situation)
                if (peer.isVolunteer()
                        && peer.skills != null && !peer.skills.isEmpty()) {
                    paintSubLabel.setColor(0xFF00C853);
                    String s = peer.skills.length() > 22
                            ? peer.skills.substring(0, 22) + "…" : peer.skills;
                    canvas.drawText(s, px + 20f, py + 30f, paintSubLabel);
                } else if (peer.isSurvivor()
                        && peer.situation != null && !peer.situation.isEmpty()) {
                    paintSubLabel.setColor(0xFFFF8800);
                    String s = peer.situation.length() > 22
                            ? peer.situation.substring(0, 22) + "…" : peer.situation;
                    canvas.drawText(s, px + 20f, py + 30f, paintSubLabel);
                }
            }
        }

        /** Draws the heads-up display overlay with peer count and instructions. */
        private void drawHud(Canvas canvas) {
            int count = peers != null ? peers.size() : 0;
            canvas.drawText(
                    count + " peer" + (count == 1 ? "" : "s") + " visible",
                    32f, 68f, paintHud);
            canvas.drawText(
                    "Pinch to zoom  ·  Double-tap to reset",
                    32f, getHeight() - 28f, paintHint);
        }

        /** Handles touch events for radar interaction (scaling and panning). */
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    isPanning  = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isPanning && event.getPointerCount() == 1) {
                        translateX += event.getX() - lastTouchX;
                        translateY += event.getY() - lastTouchY;
                        lastTouchX  = event.getX();
                        lastTouchY  = event.getY();
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isPanning = false;
                    break;
            }
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            // Ensure resources are released when view is no longer needed
            stopPulse();
        }
    }
}
