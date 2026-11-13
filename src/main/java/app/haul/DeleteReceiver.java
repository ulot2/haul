package app.haul;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** The Delete button on a "Saved" notification: removes the files from the Gallery and the haul from the hold. */
public class DeleteReceiver extends BroadcastReceiver {
    static final String EXTRA_ID = "id", EXTRA_NOTIFICATION = "notification";

    @Override
    public void onReceive(Context c, Intent intent) {
        c.getSystemService(NotificationManager.class).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
        Haul.Item i = Haul.get(c, intent.getStringExtra(EXTRA_ID));
        if (i == null) return;
        boolean refused = !Downloads.delete(c, i).isEmpty();
        // Files saved before Haul was reinstalled aren't Haul's any more; only the Gallery can delete those.
        Toast.makeText(c, refused ? "Removed from Haul. Delete the file in your Gallery." : "Deleted", Toast.LENGTH_SHORT).show();
    }
}
