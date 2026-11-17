package app.haul;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * The first time Haul opens: how to use it, in three steps (share to Haul, or copy the link; pick what you keep). Swipe
 * or tap Next; Skip leaves at any point. At the end Haul asks for notifications, now that it has said what they're for.
 * Settings › About opens it again.
 */
public class WelcomeActivity extends Activity {
    private static final String WELCOMED = "welcomed";
    private ViewGroup pages;
    private LinearLayout steps;
    private Button back, next, skip;
    private int page;
    private GestureDetector swipe;

    /**
     * True the first time Haul opens on a phone: never welcomed, and nothing in the prefs that a used Haul has (a
     * download, a quality, the notification question). So people who already use Haul don't get it after an update.
     */
    static boolean due(Context c) {
        android.content.SharedPreferences p = Haul.prefs(c);
        if (p.getBoolean(WELCOMED, false)) return false;
        boolean used = p.contains("next_no") || p.contains("quality") || p.contains("asked_notifications") || p.contains("hold");
        if (used) p.edit().putBoolean(WELCOMED, true).apply();
        return !used;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_welcome);
        MainActivity.edgeToEdge(this);
        View root = findViewById(R.id.welcomeRoot);
        int top = root.getPaddingTop(), bottom = root.getPaddingBottom(), start = root.getPaddingStart(), end = root.getPaddingEnd();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] b = MainActivity.bars(insets);
            v.setPadding(start + b[0], top + b[1], end + b[2], bottom + b[3]);
            return insets;
        });

        pages = findViewById(R.id.welcomePages);
        steps = findViewById(R.id.welcomeSteps);
        back = findViewById(R.id.welcomeBack);
        next = findViewById(R.id.welcomeNext);
        skip = findViewById(R.id.welcomeSkip);
        findViewById(R.id.welcomeVideo).setActivated(true);   // the picture of the picker shows Video chosen
        findViewById(R.id.welcomeNotify).setVisibility(needsNotifications() ? View.VISIBLE : View.GONE);
        for (int k = 0; k < pages.getChildCount(); k++) {
            View mark = new View(this);
            mark.setBackgroundResource(R.drawable.bg_step);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Sheets.dp(this, 8), Sheets.dp(this, 4));
            lp.setMargins(Sheets.dp(this, 3), 0, Sheets.dp(this, 3), 0);
            steps.addView(mark, lp);
        }

        next.setOnClickListener(v -> {
            if (page < pages.getChildCount() - 1) go(page + 1);
            else done();
        });
        back.setOnClickListener(v -> go(page - 1));
        skip.setOnClickListener(v -> done());
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent from, MotionEvent to, float vx, float vy) {
                if (Math.abs(vx) < 600 || Math.abs(vx) < Math.abs(vy) * 1.5f) return false;
                if (vx < 0 && page < pages.getChildCount() - 1) go(page + 1);
                else if (vx > 0 && page > 0) go(page - 1);
                return true;
            }
        });
        if (state != null) page = state.getInt("page", 0);
        show(page, 0);
        // Back goes to the previous step, and from the first one leaves like Skip. Android 13+ asks for this up front
        // (and from Android 16 no longer calls onBackPressed at all).
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::backOne);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        swipe.onTouchEvent(e);   // a sideways swipe anywhere turns the page; taps and scrolling still reach the views
        return super.dispatchTouchEvent(e);
    }

    private void go(int to) {
        if (to < 0 || to >= pages.getChildCount() || to == page) return;
        int from = page;
        page = to;
        show(to, to > from ? 1 : -1);
    }

    /** Shows one step; direction 1 slides it in from the right, -1 from the left, 0 not at all. */
    private void show(int k, int direction) {
        for (int i = 0; i < pages.getChildCount(); i++) pages.getChildAt(i).setVisibility(i == k ? View.VISIBLE : View.GONE);
        View in = pages.getChildAt(k);
        if (direction != 0) {
            in.setAlpha(0f);
            in.setTranslationX(direction * Sheets.dp(this, 48));
            in.animate().alpha(1f).translationX(0f).setDuration(220).start();
        }
        ((View) pages.getParent()).scrollTo(0, 0);
        for (int i = 0; i < steps.getChildCount(); i++) {
            View mark = steps.getChildAt(i);
            ViewGroup.LayoutParams lp = mark.getLayoutParams();
            lp.width = Sheets.dp(this, i == k ? 24 : 8);
            mark.setLayoutParams(lp);
            mark.setBackgroundTintList(i == k ? ColorStateList.valueOf(getColor(R.color.clay)) : null);
        }
        boolean last = k == pages.getChildCount() - 1;
        back.setVisibility(k > 0 ? View.VISIBLE : View.GONE);
        skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        next.setText(last ? "Start hauling" : "Next");
    }

    private boolean needsNotifications() {
        return Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Haul.prefs(this).getBoolean("asked_notifications", false);
    }

    /** Leaves for the hold; first, once, Android's question about notifications. */
    private void done() {
        Haul.prefs(this).edit().putBoolean(WELCOMED, true).apply();
        if (needsNotifications()) {
            Haul.prefs(this).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;   // finishes when the answer comes
        }
        leave();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        leave();
    }

    private void leave() {
        if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class));   // opened on its own: go on to the hold
        finish();
    }

    private void backOne() {
        if (page > 0) go(page - 1);
        else done();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        backOne();   // Android 10-12
    }
}
