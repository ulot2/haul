package app.haul;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The floating badge: while Haul reads a post shared from another app, a small disc floats over that app (Android's
 * "Display over other apps"), so the user carries on instead of waiting. It shows Haul's loading sign while reading;
 * turns clay with a count, pulsing with a ring that ripples out, when a post waits to be picked from (a tap opens the
 * picker); shows a check and fades once all is in the queue. Drag it anywhere: let go, it settles on the nearer side,
 * and stays there next time. A long press puts it away (the notification still says what waits).
 * Without the permission there's no badge: the queue's notification and the "pick" notification do its job.
 */
final class Badge {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int SIZE = 56, ROOM = 96;   // dp: the disc, and its window (room for the ring to ripple)

    private static FrameLayout window;
    private static View disc, ring;
    private static ImageView loader, check;
    private static TextView count;
    private static WindowManager.LayoutParams params;
    private static ValueAnimator pulse;
    private static boolean listening, put_away, hiddenForHaul, wasBusy;
    private static Context app;
    private static final Runnable REFRESH = Badge::refresh;

    static boolean allowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** A post was just shared to be read: show the badge (again, if it was put away). Main thread. */
    static void show(Context c) {
        app = c.getApplicationContext();
        put_away = false;
        if (!listening) {
            Haul.listen(REFRESH);
            listening = true;
        }
        refresh();
    }

    /** Haul's own screen is on top: no badge over it (its Downloads tab shows the same). */
    static void haulOnScreen(boolean on) {
        hiddenForHaul = on;
        if (app != null) refresh();
    }

    private static void refresh() {
        if (app == null) return;
        int[] n = Haul.reading(app);
        boolean busy = n[0] > 0 || n[1] > 0;
        if (!allowed(app) || put_away || hiddenForHaul) {
            remove();
            if (!busy) stopListening();
            return;
        }
        if (!busy) {
            if (window != null && wasBusy) done();
            else if (window == null) stopListening();
            wasBusy = false;
            return;
        }
        wasBusy = true;
        if (window == null && !add()) return;
        check.setVisibility(View.GONE);
        window.animate().cancel();
        window.setAlpha(1f);
        if (n[1] > 0) {
            // Something to pick: clay, the count, and the ring rippling out until it's tapped.
            disc.setBackgroundResource(R.drawable.bg_float_ready);
            loader.setVisibility(View.GONE);
            count.setVisibility(View.VISIBLE);
            count.setText(String.valueOf(n[1]));
            window.setContentDescription(n[1] == 1 ? "Haul: a post to pick from. Tap to pick." : "Haul: " + n[1] + " posts to pick from. Tap to pick.");
            startPulse();
        } else {
            disc.setBackgroundResource(R.drawable.bg_float);
            count.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
            if (loader.getDrawable() instanceof Animatable) ((Animatable) loader.getDrawable()).start();
            window.setContentDescription("Haul is reading the post");
            stopPulse();
        }
    }

    /** All read and in the queue: a check, then the badge fades away. */
    private static void done() {
        stopPulse();
        disc.setBackgroundResource(R.drawable.bg_float);
        loader.setVisibility(View.GONE);
        count.setVisibility(View.GONE);
        check.setVisibility(View.VISIBLE);
        window.animate().alpha(0f).setStartDelay(900).setDuration(250).withEndAction(() -> {
            remove();
            if (Haul.reading(app)[0] == 0 && Haul.reading(app)[1] == 0) stopListening();
        }).start();
    }

    private static boolean add() {
        Context c = app;
        int room = dp(c, ROOM), size = dp(c, SIZE);
        window = new FrameLayout(c);
        window.setClipChildren(false);

        ring = new View(c);
        ring.setBackgroundResource(R.drawable.bg_float_ring);
        ring.setAlpha(0f);
        window.addView(ring, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        FrameLayout d = new FrameLayout(c);
        d.setBackgroundResource(R.drawable.bg_float);
        d.setElevation(dp(c, 8));
        disc = d;
        loader = new ImageView(c);
        loader.setImageResource(R.drawable.avd_loader);
        d.addView(loader, new FrameLayout.LayoutParams(dp(c, 36), dp(c, 36), Gravity.CENTER));
        count = new TextView(c);
        count.setTextColor(c.getColor(R.color.on_clay));
        count.setTextSize(20);
        count.setTypeface(c.getResources().getFont(R.font.archivo_extrabold));
        count.setGravity(Gravity.CENTER);
        d.addView(count, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        check = new ImageView(c);
        check.setImageResource(R.drawable.ic_check);
        check.setImageTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.clay)));
        check.setScaleType(ImageView.ScaleType.CENTER);
        d.addView(check, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        window.addView(d, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        params = new WindowManager.LayoutParams(room, room, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        // Where it was left last time; at first, bottom left, above the navigation bar and the app's own tabs.
        SharedPreferences p = Haul.prefs(c);
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        params.x = p.getInt("badge_x", 0);
        params.y = Math.min(p.getInt("badge_y", screenH - dp(c, 240)), screenH - room);
        window.setOnTouchListener(new Drag());
        try {
            c.getSystemService(WindowManager.class).addView(window, params);
        } catch (RuntimeException e) {
            window = null;   // the permission was taken back meanwhile
            return false;
        }
        window.setScaleX(0.4f);
        window.setScaleY(0.4f);
        window.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator()).start();
        return true;
    }

    private static void remove() {
        stopPulse();
        if (window == null) return;
        try {
            app.getSystemService(WindowManager.class).removeView(window);
        } catch (RuntimeException ignored) {
        }
        window = null;
    }

    private static void stopListening() {
        if (listening) Haul.unlisten(REFRESH);
        listening = false;
    }

    /** The ring ripples out of the disc and fades, over and over; the disc swells a little with each. */
    private static void startPulse() {
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1300);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (ring == null || disc == null) return;
            ring.setScaleX(1f + 0.65f * t);
            ring.setScaleY(1f + 0.65f * t);
            ring.setAlpha(0.8f * (1f - t));
            float swell = 1f + 0.08f * (float) Math.sin(Math.PI * Math.min(1f, t * 2.5f));
            disc.setScaleX(swell);
            disc.setScaleY(swell);
        });
        pulse.start();
    }

    private static void stopPulse() {
        if (pulse != null) pulse.cancel();
        pulse = null;
        if (ring != null) ring.setAlpha(0f);
        if (disc != null) {
            disc.setScaleX(1f);
            disc.setScaleY(1f);
        }
    }

    /** Drag anywhere, settle on the nearer side; a tap opens what waits; a long press puts the badge away. */
    private static final class Drag implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;
        private final Runnable longPress = () -> {
            longPressed = true;
            put_away = true;
            Toast.makeText(app, "Badge hidden. The notification still has what waits.", Toast.LENGTH_SHORT).show();
            refresh();
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            int slop = ViewConfiguration.get(app).getScaledTouchSlop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = params.x;
                    startY = params.y;
                    dragging = longPressed = false;
                    disc.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start();
                    MAIN.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() + 200);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        MAIN.removeCallbacks(longPress);
                    }
                    if (dragging && window != null) {
                        params.x = startX + Math.round(dx);
                        params.y = Math.max(0, startY + Math.round(dy));
                        app.getSystemService(WindowManager.class).updateViewLayout(window, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    MAIN.removeCallbacks(longPress);
                    if (disc != null) disc.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new OvershootInterpolator()).start();
                    if (longPressed || window == null) return true;
                    if (dragging) settle();
                    else if (e.getActionMasked() == MotionEvent.ACTION_UP) tap();
                    return true;
                default:
                    return false;
            }
        }

        /** To the nearer side of the screen, and remembered there. */
        private void settle() {
            int screenW = app.getResources().getDisplayMetrics().widthPixels;
            int room = dp(app, ROOM);
            int to = params.x + room / 2 < screenW / 2 ? 0 : screenW - room;
            ValueAnimator slide = ValueAnimator.ofInt(params.x, to);
            slide.setDuration(220);
            slide.setInterpolator(new DecelerateInterpolator());
            slide.addUpdateListener(a -> {
                if (window == null) return;
                params.x = (int) a.getAnimatedValue();
                app.getSystemService(WindowManager.class).updateViewLayout(window, params);
            });
            slide.start();
            Haul.prefs(app).edit().putInt("badge_x", to).putInt("badge_y", params.y).apply();
        }

        private void tap() {
            Haul.Item waiting = Haul.firstToChoose(app);
            if (waiting != null) {
                app.startActivity(Reading.open(app, waiting.id));
            } else {
                Toast.makeText(app, "Haul is reading the post…", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    private Badge() {}
}
