package app.haul;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Share a post to Haul from TikTok, Instagram, X or a browser: the download starts and the user stays where they were. */
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
        } else if (Downloads.add(this, url) == null) {
            Toast.makeText(this, "Haul is already downloading this", Toast.LENGTH_SHORT).show();
        } else if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            // Without notifications there is no progress and no "saved" message, so say where to look.
            Toast.makeText(this, "Haul: downloading from " + Haul.site(url) + ". Open Haul to see when it's done, "
                    + "or turn on its notifications.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Haul: downloading from " + Haul.site(url), Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
