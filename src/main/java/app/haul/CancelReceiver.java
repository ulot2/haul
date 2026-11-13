package app.haul;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The Cancel button on the download notification. */
public class CancelReceiver extends BroadcastReceiver {
    static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra(EXTRA_ID);
        if (id != null) Downloads.cancel(c, id);
    }
}
