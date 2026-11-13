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
        try {
            ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) url = Haul.firstLink(clip.getItemAt(0).coerceToText(this));
        } catch (RuntimeException ignored) {
            // no clipboard access: same as no link
        }
        if (url == null) {
            Toast.makeText(this, "Haul: copy a link first", Toast.LENGTH_SHORT).show();
        } else if (Downloads.add(this, url) == null) {
            Toast.makeText(this, "Haul is already downloading this", Toast.LENGTH_SHORT).show();
        } else {
            Haul.prefs(this).edit().putString("last_filled", url).apply();   // the hold won't offer it again
            Toast.makeText(this, "Haul: downloading from " + Haul.site(url), Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
