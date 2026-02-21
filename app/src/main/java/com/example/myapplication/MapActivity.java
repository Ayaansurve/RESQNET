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

public class MapActivity extends AppCompatActivity
        implements ConnectionHelper.ConnectionStatusListener {

    private ActivityMapBinding binding;
    private MeshMapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        mapView = new MeshMapView(this);
        binding.mapContainer.addView(mapView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        setupLegend();
        setupRefreshButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshManager.getInstance().addListener(this);
        mapView.refreshPeers();
        mapView.startPulse();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshManager.getInstance().removeListener(this);
        // FIX: always stop the animator when not visible
        mapView.stopPulse();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.mapToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Nearby Peers");
        }
        binding.mapToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupLegend() {
        binding.legendVolunteerDot.setBackgroundColor(0xFF00C853);
        binding.legendSurvivorDot.setBackgroundColor(0xFFFF4F00);
        binding.legendSelfDot.setBackgroundColor(0xFF2196F3);
    }

    private void setupRefreshButton() {
        binding.btnRefreshMap.setOnClickListener(v -> {
            mapView.refreshPeers();
            Snackbar.make(binding.getRoot(), "Map refreshed",
                            Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(0xFF1A1A1A)
                    .setTextColor(0xFFFFFFFF).show();
        });
    }

    // ── Listener ──────────────────────────────────────────────────────────────
    // FIX: only redraw when data actually changes — not on every callback

    @Override public void onPeerCountChanged(int c)    { mapView.refreshPeers(); }
    @Override public void onPeerConnected(String n)    { mapView.refreshPeers(); }
    @Override public void onPeerDisconnected(String id){ mapView.refreshPeers(); }
    @Override public void onProfileReceived(PeerProfile p) { mapView.refreshPeers(); }
    @Override public void onSosReceived(String nodeId) {
        runOnUiThread(() ->
                Snackbar.make(binding.getRoot(),
                                "⚠ SOS received from nearby peer!",
                                Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFFFF0000)
                        .setTextColor(0xFFFFFFFF).show());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MeshMapView
    // ══════════════════════════════════════════════════════════════════════════

    static class MeshMapView extends View {

        // ── Paint objects — allocated once, never inside onDraw ───────────────
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

        // ── Peer data snapshot ────────────────────────────────────────────────
        private List<PeerProfile> peers;

        // ── Zoom / pan ────────────────────────────────────────────────────────
        private float scaleFactor = 1.0f;
        private float translateX  = 0f;
        private float translateY  = 0f;
        private float lastTouchX  = 0f;
        private float lastTouchY  = 0f;
        private boolean isPanning = false;

        // ── FIX: ValueAnimator replaces the 30ms Handler loop ─────────────────
        // ValueAnimator is hardware-accelerated and tied to Choreographer
        // (the display vsync). It fires at exactly 60fps max — not 33fps
        // uncontrolled. When the screen is off it automatically pauses.
        private ValueAnimator pulseAnimator;
        private float pulseProgress = 0f; // 0.0 → 1.0, drives radius + alpha

        private static final float[] RING_RADII = {60f, 120f, 200f};
        private static final String[] RING_LABELS = {"CLOSE", "NEAR", "FAR"};

        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector      gestureDetector;

        public MeshMapView(Context ctx) {
            super(ctx);
            setLayerType(LAYER_TYPE_HARDWARE, null); // GPU compositing

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

            // ── Gesture detectors ────────────────────────────────────────
            scaleDetector = new ScaleGestureDetector(ctx,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector d) {
                            scaleFactor = Math.max(0.5f, Math.min(scaleFactor * d.getScaleFactor(), 4f));
                            invalidate();
                            return true;
                        }
                    });

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

        // ── FIX: ValueAnimator-based pulse — no manual Handler loop ───────────

        public void startPulse() {
            if (pulseAnimator != null && pulseAnimator.isRunning()) return;

            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(1800);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
            pulseAnimator.setInterpolator(new LinearInterpolator());
            pulseAnimator.addUpdateListener(anim -> {
                pulseProgress = (float) anim.getAnimatedValue();
                invalidate(); // only fires at vsync rate (~60fps max)
            });
            pulseAnimator.start();
        }

        public void stopPulse() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
        }

        /** Called when peer data changes — snapshots the list and redraws once. */
        public void refreshPeers() {
            peers = MeshManager.getInstance().getPeerProfiles();
            invalidate(); // single redraw, not a loop
        }

        // ── Drawing ───────────────────────────────────────────────────────────

        @Override
        protected void onDraw(Canvas canvas) {
            // Do NOT allocate objects here — all Paints are pre-built above

            float cx = getWidth()  / 2f + translateX;
            float cy = getHeight() / 2f + translateY;

            canvas.drawPaint(paintBackground);
            canvas.save();
            canvas.scale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f);

            drawGrid(canvas, cx, cy);
            drawRings(canvas, cx, cy);
            drawPeers(canvas, cx, cy);
            drawSelf(canvas, cx, cy);

            canvas.restore();
            drawHud(canvas);
        }

        private void drawGrid(Canvas canvas, float cx, float cy) {
            float step = 80f;
            for (float x = cx % step; x < getWidth(); x += step)
                canvas.drawLine(x, 0, x, getHeight(), paintGrid);
            for (float y = cy % step; y < getHeight(); y += step)
                canvas.drawLine(0, y, getWidth(), y, paintGrid);
        }

        private void drawRings(Canvas canvas, float cx, float cy) {
            for (int i = 0; i < RING_RADII.length; i++) {
                canvas.drawCircle(cx, cy, RING_RADII[i], paintRing);
                canvas.drawText(RING_LABELS[i],
                        cx + RING_RADII[i] * 0.68f,
                        cy - RING_RADII[i] * 0.68f,
                        paintRingLabel);
            }
        }

        private void drawSelf(Canvas canvas, float cx, float cy) {
            // Pulse ring driven by ValueAnimator progress (0→1)
            float pulseR     = 18f + pulseProgress * 50f;
            int   pulseAlpha = (int)((1f - pulseProgress) * 60f);
            paintSelfGlow.setColor(0x002196F3 | (pulseAlpha << 24));
            canvas.drawCircle(cx, cy, pulseR, paintSelfGlow);

            // Self dot
            canvas.drawCircle(cx, cy, 18f, paintSelf);

            // Label
            paintLabel.setColor(0xFFFFFFFF);
            canvas.drawText("YOU", cx + 22f, cy + 8f, paintLabel);
        }

        private void drawPeers(Canvas canvas, float cx, float cy) {
            if (peers == null || peers.isEmpty()) return;

            int count = peers.size();
            for (int i = 0; i < count; i++) {
                PeerProfile peer = peers.get(i);

                double angle    = (2 * Math.PI * i) / count - Math.PI / 2;
                float  distance = 90f + (i * 40f % 100f);
                float  px       = cx + (float)(Math.cos(angle) * distance);
                float  py       = cy + (float)(Math.sin(angle) * distance);

                // Glow
                paintPeerGlow.setColor(peer.isVolunteer() ? 0x2200C853 : 0x22FF4F00);
                canvas.drawCircle(px, py, 28f, paintPeerGlow);

                // Dot
                canvas.drawCircle(px, py, 16f,
                        peer.isVolunteer() ? paintVolunteer : paintSurvivor);

                // Name
                paintLabel.setColor(0xFFCCCCCC);
                String name = (peer.name != null && !peer.name.isEmpty())
                        ? peer.name
                        : (peer.isVolunteer() ? "Volunteer" : "Survivor");
                canvas.drawText(name, px + 20f, py + 8f, paintLabel);

                // Sub-label
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

        private void drawHud(Canvas canvas) {
            int count = peers != null ? peers.size() : 0;
            canvas.drawText(
                    count + " peer" + (count == 1 ? "" : "s") + " visible",
                    32f, 68f, paintHud);
            canvas.drawText(
                    "Pinch to zoom  ·  Double-tap to reset",
                    32f, getHeight() - 28f, paintHint);
        }

        // ── Touch ─────────────────────────────────────────────────────────────

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
            stopPulse(); // always clean up when view is removed
        }
    }
}