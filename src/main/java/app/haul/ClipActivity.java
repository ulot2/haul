package app.haul;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Toast;

/** Opened by the Quick Settings tile: reads the copied link once it has focus (Android's rule), starts it, closes. */
public class ClipActivity extends Activity {
    private boolean done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        String url = null;
        java.util.List<String> links = new java.util.ArrayList<>();
        try {
            ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                links = Haul.links(clip.getItemAt(0).coerceToText(this));
                url = links.isEmpty() ? null : links.get(0);
            }
        } catch (RuntimeException ignored) {
            // no clipboard access: same as no link
        }
        if (url == null) {
            Toast.makeText(this, "Haul: copy a link first", Toast.LENGTH_SHORT).show();
            close();
            return;
        }
        if (links.size() > 1) {
            // A copied list: all of it, with no questions.
            Toast.makeText(this, "Haul: " + Downloads.addAll(this, links, Haul.quality(this)), Toast.LENGTH_LONG).show();
            Haul.prefs(this).edit().putString("last_filled", String.join("\n", links)).apply();   // the hold won't offer it again
            close();
            return;
        }
        String link = url;
        // Same questions as the share sheet: how to save a TikTok photo post, which items of an Instagram carousel.
        Choose.add(this, link, Haul.quality(this), -1, -1, true, i -> {
            if (i != null) {
                Haul.prefs(this).edit().putString("last_filled", link).apply();   // the hold won't offer it again
                if (!i.decide || !Badge.allowed(this)) {   // the badge says it otherwise
                    Toast.makeText(this, (i.decide ? "Haul: reading the post from " : "Haul: downloading from ") + Haul.site(link),
                            Toast.LENGTH_SHORT).show();
                }
            }
            close();
        });
    }

    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }
}
