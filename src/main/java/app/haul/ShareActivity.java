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
 * sharing" is on) and "Haul audio" (the ShareAudio alias: just the sound).
 */
public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        CharSequence text = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (Haul.firstLink(text) == null) text = in.getStringExtra(Intent.EXTRA_SUBJECT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        }
        String url = Haul.firstLink(text);
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

    /** HAUL THIS AS: three buttons over the app the link came from; the default quality is the filled one. */
    private void ask(String url) {
        setContentView(R.layout.sheet_quality);
        Engine.readAhead(this, url);   // read the link while the user picks: the download then starts from what was read
        ((TextView) findViewById(R.id.qSite)).setText(Haul.site(url));
        ((TextView) findViewById(R.id.qLink)).setText(Look.shortLink(url));
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
        // A TikTok photo post asks how to save it, and an Instagram carousel shows its items to pick, over the app the
        // link came from; anything else is added at once.
        Choose.add(this, url, quality, -1, -1, i -> {
            if (i != null) confirm(url, quality);
            finish();
        });
    }

    private void confirm(String url, String quality) {
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
