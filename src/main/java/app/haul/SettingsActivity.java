package app.haul;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings: downloads, shortcuts, notifications, the engine, about. */
public class SettingsActivity extends Activity {
    private static final String AUDIO_TARGET = "app.haul.ShareAudio";
    private Button engineUpdate;
    private TextView engineVersion, engineHelp, notifState;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        MainActivity.edgeToEdge(this);
        View content = findViewById(R.id.settingsContent);
        int top = content.getPaddingTop(), bottom = content.getPaddingBottom();
        findViewById(R.id.settingsScroll).setOnApplyWindowInsetsListener((v, insets) -> {
            int[] b = MainActivity.bars(insets);
            content.setPadding(content.getPaddingLeft(), top + b[1], content.getPaddingRight(), bottom + b[3]);
            v.setPadding(b[0], 0, b[2], 0);
            return insets;
        });
        findViewById(R.id.back).setOnClickListener(v -> finish());

        Button[] q = {findViewById(R.id.sBest), findViewById(R.id.sSmall), findViewById(R.id.sAudio)};
        String[] ids = {Haul.BEST, Haul.SMALL, Haul.AUDIO};
        Runnable mark = () -> {
            for (int k = 0; k < q.length; k++) q[k].setActivated(ids[k].equals(Haul.quality(this)));
        };
        for (int k = 0; k < q.length; k++) {
            String id = ids[k];
            q[k].setOnClickListener(v -> {
                Haul.setQuality(this, id);
                mark.run();
            });
        }
        mark.run();

        Switch ask = findViewById(R.id.swAsk), audio = findViewById(R.id.swAudio), wifi = findViewById(R.id.swWifi);
        ask.setChecked(Haul.askQuality(this));
        ask.setOnCheckedChangeListener((b, on) -> Haul.set(this, "ask_quality", on));
        audio.setChecked(Haul.audioTarget(this));
        audio.setOnCheckedChangeListener((b, on) -> {
            Haul.set(this, "audio_target", on);
            // The second share target is an alias of the share screen; turning it off hides it from the share sheet.
            getPackageManager().setComponentEnabledSetting(new ComponentName(this, AUDIO_TARGET),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        });
        wifi.setChecked(Haul.wifiOnly(this));
        wifi.setOnCheckedChangeListener((b, on) -> {
            Haul.set(this, "wifi_only", on);
            Downloads.settingsChanged(this);
        });
        ((TextView) findViewById(R.id.wifiHelp)).setText(Build.VERSION.SDK_INT >= 34
                ? "Links wait in the hold until the phone is on Wi-Fi."
                : "On mobile data, links stop with a note; tap them on Wi-Fi. (Android 14 and later wait for Wi-Fi instead.)");

        findViewById(R.id.addTile).setOnClickListener(v -> addTile());

        notifState = findViewById(R.id.notifState);
        findViewById(R.id.notifSettings).setOnClickListener(v -> Sheets.open(this, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));

        engineVersion = findViewById(R.id.engineVersion);
        engineHelp = findViewById(R.id.engineHelp);
        engineUpdate = findViewById(R.id.engineUpdate);
        engineUpdate.setOnClickListener(v -> updateEngine());

        ((TextView) findViewById(R.id.aboutVersion)).setText("Haul " + Updates.installed(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = getSystemService(NotificationManager.class).areNotificationsEnabled();
        notifState.setText(on ? "Notifications are on" : "Notifications are off");
        String v = Engine.version(this);
        engineVersion.setText(v.isEmpty() ? "yt-dlp" : "yt-dlp " + v);
    }

    /** Android 13+ asks the user to add the tile right here; older Android has to be told where to drag it. */
    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this, HaulTile.class),
                    "Haul", Icon.createWithResource(this, R.drawable.ic_notify), getMainExecutor(), result -> {
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            Toast.makeText(this, "The Haul tile is already in Quick Settings", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "Pull down the shade twice, tap the pencil, and drag Haul into your tiles.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateEngine() {
        if (Downloads.busy()) {
            Toast.makeText(this, "Wait until the downloads finish", Toast.LENGTH_SHORT).show();
            return;
        }
        engineUpdate.setEnabled(false);
        engineUpdate.setText("Updating…");
        new Thread(() -> {
            String msg;
            try {
                msg = Engine.update(this) ? "Updated to " + Engine.version(this) + "." : "Up to date. Checked just now.";
            } catch (Exception e) {
                msg = "Couldn't reach GitHub. Try again later.";
            }
            String m = msg;
            runOnUiThread(() -> {
                engineUpdate.setEnabled(true);
                engineUpdate.setText("Update");
                engineHelp.setText(m);
                String v = Engine.version(this);
                engineVersion.setText(v.isEmpty() ? "yt-dlp" : "yt-dlp " + v);
            });
        }).start();
    }
}
