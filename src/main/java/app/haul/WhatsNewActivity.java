package app.haul;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

/**
 * WHAT'S NEW: shown once, the first time Haul opens after an update that brought something new (NEWS), so people who
 * used Haul before know what changed. A fresh install gets the welcome instead, and never this. Settings › About
 * opens it again. Got it (or Back) closes it.
 */
public class WhatsNewActivity extends Activity {
    /** The release this page tells about. Raise it (and change activity_news) with the next release that has news. */
    static final String NEWS = "1.5";
    private static final String SEEN = "news_seen";

    /** True the first time Haul opens after updating to a release with news it hasn't shown. */
    static boolean due(Context c) {
        return !NEWS.equals(Haul.prefs(c).getString(SEEN, ""));
    }

    /** Shown, or not needed (a fresh install, which gets the welcome): not again until the next release with news. */
    static void seen(Context c) {
        Haul.prefs(c).edit().putString(SEEN, NEWS).apply();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_news);
        MainActivity.edgeToEdge(this);
        View root = findViewById(R.id.newsRoot);
        int top = root.getPaddingTop(), bottom = root.getPaddingBottom(), start = root.getPaddingStart(), end = root.getPaddingEnd();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] b = MainActivity.bars(insets);
            v.setPadding(start + b[0], top + b[1], end + b[2], bottom + b[3]);
            return insets;
        });
        ((TextView) findViewById(R.id.newsVersion)).setText("Haul " + NEWS);
        SpannableString title = new SpannableString("WHAT'S NEW");
        title.setSpan(new ForegroundColorSpan(getColor(R.color.clay)), 7, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.newsTitle)).setText(title);
        findViewById(R.id.newsDone).setOnClickListener(v -> finish());
        seen(this);
    }
}
