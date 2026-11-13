package app.haul;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * The Quick Settings tile: "Download copied link". A tile can't read the clipboard (Android only lets the app on
 * screen do that), so it opens ClipActivity, an invisible screen that reads the link, starts the download and closes.
 */
public class HaulTile extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("Download copied link");
        t.updateTile();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        Intent open = new Intent(this, ClipActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(open);
        }
    }
}
