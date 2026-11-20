package app.haul;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Share a post to Haul from TikTok, Instagram, X or a browser: the download starts and the user stays where they
 * were. Two share targets open this screen: "Haul" (the default quality, or a quick choice when "Ask quality when
 * sharing" is on) and "Haul audio" (the ShareAudio alias: just the sound). Text holding several links (a list shared
 * from notes) downloads them all, with no questions.
 */
public class ShareActivity extends Activity {
    /** Opened from the badge or the "pick" notification: the picker for a post read in the background (Reading). */
    static final String ACTION_CHOOSE = "app.haul.CHOOSE", EXTRA_CHOOSE = "choose";
    /** Every link in what was shared; more than one makes it a list. */
    private java.util.List<String> links = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        if (ACTION_CHOOSE.equals(in.getAction())) {
            Haul.Item waiting = Haul.get(this, in.getStringExtra(EXTRA_CHOOSE));
            if (waiting == null || !Haul.CHOOSE.equals(waiting.status)) {
                Toast.makeText(this, "Haul: that post was picked from already", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Choose.choose(this, waiting, i -> finish());
            return;
        }
        CharSequence text = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (Haul.firstLink(text) == null) text = in.getStringExtra(Intent.EXTRA_SUBJECT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        }
        String url = Haul.firstLink(text);
        links = Haul.links(text);
        if (url == null) {
            Toast.makeText(this, "Haul: there's no link in what you shared", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        boolean audioTarget = getComponentName().getClassName().endsWith(".ShareAudio");
        if (audioTarget) {
            start(url, Haul.AUDIO);
        } else if (Haul.askQuality(this)) {
            ask(url);
        } else {
            start(url, Haul.quality(this));
        }
    }

    // The badge steps aside while Haul's own sheets are up (the picker, the quality); it's back when they close.
    @Override
    protected void onResume() {
        super.onResume();
        Badge.haulOnScreen(true);
    }

    @Override
    protected void onPause() {
        Badge.haulOnScreen(false);
        super.onPause();
    }

    /** HAUL THIS AS: three buttons over the app the link came from; the default quality is the filled one. */
    private void ask(String url) {
        setContentView(R.layout.sheet_quality);
        if (links.size() > 1) {
            ((TextView) findViewById(R.id.qSite)).setText(links.size() + " links");
            ((TextView) findViewById(R.id.qLink)).setText(Look.shortLink(url) + " and " + (links.size() - 1) + " more");
        } else {
            Engine.readAhead(this, url);   // read the link while the user picks: the download then starts from what was read
            ((TextView) findViewById(R.id.qSite)).setText(Haul.site(url));
            ((TextView) findViewById(R.id.qLink)).setText(Look.shortLink(url));
        }
        findViewById(R.id.qScrim).setOnClickListener(v -> finish());
        // Android 15+ draws every screen under the navigation bar: lift the buttons above it.
        android.view.View sheet = findViewById(R.id.qSheet);
        int pad = sheet.getPaddingBottom();
        findViewById(R.id.qScrim).setOnApplyWindowInsetsListener((v, insets) -> {
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), pad + MainActivity.bars(insets)[3]);
            return insets;
        });
        int[] ids = {R.id.qBest, R.id.qSmall, R.id.qAudio, R.id.qGif};
        String[] qs = {Haul.BEST, Haul.SMALL, Haul.AUDIO, Haul.GIF};
        for (int k = 0; k < ids.length; k++) {
            Button b = findViewById(ids[k]);
            String q = qs[k];
            b.setActivated(q.equals(Haul.quality(this)));
            b.setOnClickListener(v -> start(url, q));
        }
    }

    private void start(String url, String quality) {
        android.view.View sheet = findViewById(R.id.qScrim);
        if (sheet != null) sheet.setVisibility(android.view.View.GONE);   // the quality sheet makes way for what follows
        if (links.size() > 1) {
            Toast.makeText(this, "Haul: " + Downloads.addAll(this, links, quality), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // A post that has to be read first (a TikTok photo post, an Instagram carousel, a TikTok short link) is read
        // aside, with a badge floating over the app; asked once whether that badge may float there.
        if (Reading.needed(url, quality, -1) && !Badge.allowed(this) && !Haul.prefs(this).getBoolean("asked_overlay", false)) {
            askBadge(url, quality);
            return;
        }
        go(url, quality, null);
    }

    private void go(String url, String quality, Runnable after) {
        Choose.add(this, url, quality, -1, -1, true, i -> {
            if (i != null) confirm(url, quality, i);
            if (after != null) after.run();
            finish();
        });
    }

    /** KEEP SCROLLING: may the badge float over other apps? Allow opens Android's switch for it; the link goes on either way. */
    private void askBadge(String url, String quality) {
        setContentView(R.layout.sheet_overlay);
        Haul.prefs(this).edit().putBoolean("asked_overlay", true).apply();
        android.view.View sheet = findViewById(R.id.oSheet);
        int pad = sheet.getPaddingBottom();
        findViewById(R.id.oScrim).setOnApplyWindowInsetsListener((v, insets) -> {
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), pad + MainActivity.bars(insets)[3]);
            return insets;
        });
        Runnable later = () -> {
            findViewById(R.id.oScrim).setVisibility(android.view.View.GONE);
            go(url, quality, null);
        };
        findViewById(R.id.oScrim).setOnClickListener(v -> later.run());
        findViewById(R.id.oLater).setOnClickListener(v -> later.run());
        findViewById(R.id.oAllow).setOnClickListener(v -> {
            findViewById(R.id.oScrim).setVisibility(android.view.View.GONE);
            // This post goes on with a notification; once allowed, the next one gets the badge.
            go(url, quality, () -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                    // no such screen on this phone: Settings › Floating badge says where
                }
            });
        });
    }

    private void confirm(String url, String quality, Haul.Item i) {
        if (i.decide && Badge.allowed(this)) return;   // the badge says it
        if (i.decide) {
            Toast.makeText(this, "Haul: reading the " + Haul.site(url) + " post. Carry on; a notification says when it's ready.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            // Without notifications there is no progress and no "saved" message, so say where to look.
            Toast.makeText(this, "Haul: downloading from " + Haul.site(url) + ". Open Haul to see when it's done, "
                    + "or turn on its notifications.", Toast.LENGTH_LONG).show();
        } else {
            String what = Haul.AUDIO.equals(quality) ? "the sound from " : "from ";
            Toast.makeText(this, "Haul: downloading " + what + Haul.site(url), Toast.LENGTH_SHORT).show();
        }
    }
}
