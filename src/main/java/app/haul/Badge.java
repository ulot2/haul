package app.haul;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The floating badge: while Haul reads a post shared from another app, it floats over that app (Android's "Display
 * over other apps"), so the user carries on instead of waiting.
 * - Reading: a capsule, Haul's turning mark and "Reading the post…", for 3 seconds; then it draws in to an 80dp disc,
 *   the mark with a clay arc running round it. A tap opens it out again.
 * - Something to pick: a clay capsule, the count, "Pick the photos", for 5 seconds; then an 80dp clay disc with the
 *   count. Either way rings ripple out of it until it's tapped; a tap opens the picker.
 * - Done: a clay check and "Downloading", then it fades away.
 * Drag it anywhere: let go, it settles on the nearer side (and grows and shrinks from that side), and stays there
 * next time. A long press puts it away (the notification still says what waits).
 * Without the permission there's no badge: the queue's notification and the "pick" notification do its job.
 */
final class Badge {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int READING = 1, PICK = 2, DONE = 3;
    private static final int OPEN_READING = 3000, OPEN_PICK = 5000;
    /** dp around the badge, inside its window, for the rings to ripple into. */
    private static final int ROOM = 24;

    private static FrameLayout window, holder, disc, iconBox;
    private static LinearLayout capsule, discCount;
    private static View ring;
    private static ImageView loader, check, chevron, discMark;
    private static ProgressBar arc;
    private static TextView title, label, iconCount, discNumber;
    private static WindowManager.LayoutParams params;
    private static ValueAnimator pulse;
    private static int state;
    private static boolean listening, putAway, hiddenForHaul, expanded, rightSide;
    private static Context app;
    private static final Runnable REFRESH = Badge::refresh;
    private static final Runnable COLLAPSE = () -> {
        expanded = false;
        draw(true);
    };

    static boolean allowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** A post was just shared to be read: show the badge, opened out (again, if it was put away). Main thread. */
    static void show(Context c) {
        app = c.getApplicationContext();
        putAway = false;
        if (!listening) {
            Haul.listen(REFRESH);
            listening = true;
        }
        openFor(OPEN_READING);
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
        if (!allowed(app) || putAway || hiddenForHaul) {
            remove();
            if (!busy) stopListening();
            return;
        }
        if (!busy) {
            if (window != null && state != DONE) done();
            else if (window == null) stopListening();
            return;
        }
        int was = state;
        state = n[1] > 0 ? PICK : READING;
        if (state == PICK && was != PICK) openFor(OPEN_PICK);   // something to pick: open out to say so
        if (window == null && !add()) return;
        window.animate().cancel();
        window.setAlpha(1f);
        draw(false);
    }

    private static void openFor(int ms) {
        expanded = true;
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.postDelayed(COLLAPSE, ms);
    }

    /** All read and in the queue: opened out, a clay check and "Downloading", then the badge fades away. */
    private static void done() {
        state = DONE;
        expanded = true;
        MAIN.removeCallbacks(COLLAPSE);
        draw(false);
        window.animate().alpha(0f).setStartDelay(1100).setDuration(250).withEndAction(() -> {
            remove();
            int[] n = Haul.reading(app);
            if (n[0] == 0 && n[1] == 0) stopListening();
        }).start();
    }

    /** Shows the state: the capsule (opened out) or the disc, in the colors and words of reading, picking or done. */
    private static void draw(boolean fade) {
        if (window == null) return;
        Context c = app;
        int surface = c.getColor(R.color.surface), clay = c.getColor(R.color.clay), onClay = c.getColor(R.color.on_clay);
        boolean pick = state == PICK, done = state == DONE;

        // The capsule.
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(c, 32));
        shape.setColor(pick ? clay : surface);
        if (!pick) shape.setStroke(dp(c, 1), c.getColor(R.color.line));
        capsule.setBackground(shape);
        GradientDrawable icon = new GradientDrawable();
        icon.setShape(GradientDrawable.OVAL);
        icon.setColor(pick ? onClay : done ? clay : c.getColor(R.color.raised));
        iconBox.setBackground(icon);
        loader.setVisibility(state == READING ? View.VISIBLE : View.GONE);
        if (state == READING && loader.getDrawable() instanceof Animatable) ((Animatable) loader.getDrawable()).start();
        iconCount.setVisibility(pick ? View.VISIBLE : View.GONE);
        check.setVisibility(done ? View.VISIBLE : View.GONE);
        chevron.setVisibility(pick ? View.VISIBLE : View.GONE);
        title.setTextColor(pick ? onClay : c.getColor(R.color.ink));
        label.setTextColor(pick ? (onClay & 0x00FFFFFF) | 0xBF000000 : c.getColor(R.color.ink_muted));

        // The disc.
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setColor(pick ? clay : surface);
        if (!pick) round.setStroke(dp(c, 1), c.getColor(R.color.line));
        disc.setBackground(round);
        arc.setVisibility(state == READING ? View.VISIBLE : View.GONE);
        discMark.setVisibility(state == READING ? View.VISIBLE : View.GONE);
        discCount.setVisibility(pick ? View.VISIBLE : View.GONE);

        // The words.
        if (pick) {
            Haul.Item first = Haul.firstToChoose(c);
            int posts = Haul.reading(c)[1];
            Reading.Pending p = first == null ? null : Reading.pending(first.id);
            int items = p == null ? 0 : p.post.entries.size();
            boolean many = posts > 1 || items == 0;
            String number = String.valueOf(many ? posts : items);
            iconCount.setText(number);
            discNumber.setText(number);
            title.setText(many ? (posts == 1 ? "Pick from the post" : "Pick from " + posts + " posts")
                    : p.tiktok ? "Pick the photos" : "Pick photos and videos");
            label.setText(((first == null || many && posts > 1) ? "Haul" : first.site) + " · tap to choose");
            window.setContentDescription(title.getText() + ". Tap to choose.");
            startPulse();
        } else if (done) {
            title.setText("Downloading");
            label.setText("See it in Haul");
            window.setContentDescription("Haul is downloading it");
            stopPulse();
        } else {
            String site = "";
            for (Haul.Item i : Haul.items(c)) if (i.decide && i.active()) site = i.site;
            title.setText("Reading the post…");
            label.setText(site.isEmpty() ? "Haul" : "Haul · " + site);
            window.setContentDescription("Haul is reading the post");
            stopPulse();
        }

        // Opened out, or drawn in.
        View show = expanded ? capsule : disc, hide = expanded ? disc : capsule;
        if (show.getVisibility() != View.VISIBLE) {
            show.setVisibility(View.VISIBLE);
            if (fade) {
                show.setAlpha(0f);
                show.setScaleX(0.85f);
                show.setScaleY(0.85f);
                show.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator()).start();
            }
        }
        hide.setVisibility(View.GONE);
    }

    private static boolean add() {
        Context c = app;
        int room = dp(c, ROOM);
        window = new FrameLayout(c);
        window.setClipChildren(false);
        window.setPadding(room, room, room, room);
        holder = new FrameLayout(c);
        holder.setClipChildren(false);
        window.addView(holder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The ring, the badge's own shape, behind it: ripples out while something waits to be picked.
        ring = new View(c);
        GradientDrawable ringShape = new GradientDrawable();
        ringShape.setCornerRadius(dp(c, 999));
        ringShape.setStroke(dp(c, 3), c.getColor(R.color.clay));
        ring.setBackground(ringShape);
        ring.setAlpha(0f);
        holder.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The capsule: 64dp tall, an icon disc, two lines of words, an arrow when it's a button.
        capsule = new LinearLayout(c);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(dp(c, 8), 0, dp(c, 18), 0);
        capsule.setElevation(dp(c, 8));
        iconBox = new FrameLayout(c);
        loader = new ImageView(c);
        loader.setImageResource(R.drawable.avd_loader);
        iconBox.addView(loader, new FrameLayout.LayoutParams(dp(c, 30), dp(c, 34), Gravity.CENTER));
        iconCount = text(c, 22, R.font.archivo_extrabold, Gravity.CENTER);
        iconCount.setTextColor(c.getColor(R.color.clay));
        iconBox.addView(iconCount, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        check = new ImageView(c);
        check.setImageResource(R.drawable.ic_check);
        check.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.on_clay)));
        check.setScaleType(ImageView.ScaleType.CENTER);
        iconBox.addView(check, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        capsule.addView(iconBox, new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        title = text(c, 16, R.font.archivo_extrabold, Gravity.START);
        label = text(c, 11, R.font.jetbrains_mono_regular, Gravity.START);
        label.setAllCaps(true);
        label.setLetterSpacing(0.09f);
        words.addView(title);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        under.topMargin = dp(c, 2);
        words.addView(label, under);
        LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        beside.leftMargin = dp(c, 12);
        capsule.addView(words, beside);
        chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.on_clay)));
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18));
        arrow.leftMargin = dp(c, 10);
        capsule.addView(chevron, arrow);
        holder.addView(capsule, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 64)));

        // The disc: 80dp, the mark with a clay arc running round it, or the count and PICK.
        disc = new FrameLayout(c);
        disc.setElevation(dp(c, 8));
        arc = new ProgressBar(c, null, android.R.attr.progressBarStyleLarge);
        arc.setIndeterminate(true);
        arc.setIndeterminateTintList(ColorStateList.valueOf(c.getColor(R.color.clay)));
        disc.addView(arc, new FrameLayout.LayoutParams(dp(c, 76), dp(c, 76), Gravity.CENTER));
        discMark = new ImageView(c);
        discMark.setImageResource(R.drawable.ic_mark);
        disc.addView(discMark, new FrameLayout.LayoutParams(dp(c, 34), dp(c, 40), Gravity.CENTER));
        discCount = new LinearLayout(c);
        discCount.setOrientation(LinearLayout.VERTICAL);
        discCount.setGravity(Gravity.CENTER);
        discNumber = text(c, 30, R.font.archivo_condensed_black, Gravity.CENTER);
        discNumber.setTextColor(c.getColor(R.color.on_clay));
        discNumber.setIncludeFontPadding(false);
        TextView pickWord = text(c, 10, R.font.jetbrains_mono_semibold, Gravity.CENTER);
        pickWord.setText("PICK");
        pickWord.setLetterSpacing(0.09f);
        pickWord.setTextColor(c.getColor(R.color.on_clay));
        discCount.addView(discNumber);
        discCount.addView(pickWord);
        disc.addView(discCount, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.addView(disc, new FrameLayout.LayoutParams(dp(c, 80), dp(c, 80)));
        disc.setVisibility(View.GONE);

        params = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        // Where it was left last time; at first, bottom left, above the navigation bar and the app's own tabs.
        SharedPreferences p = Haul.prefs(c);
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        rightSide = p.getBoolean("badge_right", false);
        params.x = 0;
        params.y = Math.min(p.getInt("badge_y", screenH - dp(c, 260)), screenH - dp(c, 140));
        window.setOnTouchListener(new Drag());
        // On the right side, it grows and shrinks from the right edge, so it never runs off the screen.
        window.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!rightSide || window == null) return;
            int want = c.getResources().getDisplayMetrics().widthPixels - (r - l);
            if (params.x != want) {
                params.x = want;
                c.getSystemService(WindowManager.class).updateViewLayout(window, params);
            }
        });
        try {
            c.getSystemService(WindowManager.class).addView(window, params);
        } catch (RuntimeException e) {
            window = null;   // the permission was taken back meanwhile
            return false;
        }
        holder.setScaleX(0.4f);
        holder.setScaleY(0.4f);
        holder.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator()).start();
        return true;
    }

    private static TextView text(Context c, int sp, int font, int gravity) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(c.getResources().getFont(font));
        t.setGravity(gravity);
        t.setMaxLines(1);
        return t;
    }

    private static void remove() {
        stopPulse();
        MAIN.removeCallbacks(COLLAPSE);
        state = 0;
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

    /** Rings ripple out of the badge (its own shape, growing by the room around it) and fade; it swells a little with each. */
    private static void startPulse() {
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1300);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.addUpdateListener(a -> {
            if (ring == null || holder == null || holder.getWidth() == 0) return;
            float t = (float) a.getAnimatedValue(), grow = 2f * dp(app, ROOM) * t;
            ring.setScaleX(1f + grow / holder.getWidth());
            ring.setScaleY(1f + grow / holder.getHeight());
            ring.setAlpha(0.8f * (1f - t));
            View face = expanded ? capsule : disc;
            float swell = 1f + 0.05f * (float) Math.sin(Math.PI * Math.min(1f, t * 2.5f));
            face.setScaleX(swell);
            face.setScaleY(swell);
        });
        pulse.start();
    }

    private static void stopPulse() {
        if (pulse != null) pulse.cancel();
        pulse = null;
        if (ring != null) ring.setAlpha(0f);
        for (View v : new View[]{capsule, disc}) {
            if (v == null) continue;
            v.setScaleX(1f);
            v.setScaleY(1f);
        }
    }

    /** Drag anywhere, settle on the nearer side; a tap opens what waits (or opens the badge out); a long press puts it away. */
    private static final class Drag implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;
        private final Runnable longPress = () -> {
            longPressed = true;
            putAway = true;
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
                    holder.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
                    MAIN.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() + 200);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        rightSide = false;   // follows the finger; settles again on letting go
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
                    if (holder != null) holder.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new OvershootInterpolator()).start();
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
            int screenW = app.getResources().getDisplayMetrics().widthPixels, w = window.getWidth();
            boolean right = params.x + w / 2 > screenW / 2;
            int to = right ? screenW - w : 0;
            ValueAnimator slide = ValueAnimator.ofInt(params.x, to);
            slide.setDuration(220);
            slide.setInterpolator(new DecelerateInterpolator());
            slide.addUpdateListener(a -> {
                if (window == null) return;
                params.x = (int) a.getAnimatedValue();
                app.getSystemService(WindowManager.class).updateViewLayout(window, params);
            });
            slide.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    rightSide = right;
                }
            });
            slide.start();
            Haul.prefs(app).edit().putBoolean("badge_right", right).putInt("badge_y", params.y).apply();
        }

        private void tap() {
            Haul.Item waiting = Haul.firstToChoose(app);
            if (waiting != null) {
                app.startActivity(Reading.open(app, waiting.id));
            } else if (!expanded && state == READING) {
                openFor(OPEN_READING);   // what is it doing? it says, for a moment
                draw(true);
            }
        }
    }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    private Badge() {}
}
