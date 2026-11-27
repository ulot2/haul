package app.haul;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** The Settings tab: downloads, shortcuts, notifications, the engine, about. MainActivity shows it and passes on its resume. */
final class SettingsPage {
    static final int REQ_BACKUP = 21, REQ_RESTORE = 22;
    private static final String AUDIO_TARGET = "app.haul.ShareAudio";
    private Button engineUpdate, aboutCheck;
    private TextView engineVersion, engineHelp, notifState, aboutCheckResult;

    private final Activity a;
    private final View root;

    SettingsPage(Activity a, View root) {
        this.a = a;
        this.root = root;
        findViewById(R.id.aboutWelcome).setOnClickListener(v -> a.startActivity(new Intent(a, WelcomeActivity.class)));
        findViewById(R.id.aboutNews).setOnClickListener(v -> a.startActivity(new Intent(a, WhatsNewActivity.class)));
        findViewById(R.id.aboutReport).setOnClickListener(v -> saveReport());

        Button[] q = {findViewById(R.id.sBest), findViewById(R.id.sSmall), findViewById(R.id.sAudio)};
        String[] ids = {Haul.BEST, Haul.SMALL, Haul.AUDIO};
        Runnable mark = () -> {
            for (int k = 0; k < q.length; k++) q[k].setActivated(ids[k].equals(Haul.quality(a)));
        };
        for (int k = 0; k < q.length; k++) {
            String id = ids[k];
            q[k].setOnClickListener(v -> {
                Haul.setQuality(a, id);
                mark.run();
            });
        }
        mark.run();

        Button[] f = {findViewById(R.id.fOne), findViewById(R.id.fSite), findViewById(R.id.fCreator)};
        String[] modes = {Folders.ONE, Folders.SITE, Folders.CREATOR};
        String[] where = {"Everything in Movies/Haul, Pictures/Haul and Music/Haul.",
                "A folder per site: Movies/Haul/TikTok, Movies/Haul/Instagram… For new downloads.",
                "A folder per creator: Movies/Haul/some.one… For new downloads."};
        TextView foldersHelp = findViewById(R.id.foldersHelp);
        Runnable markFolders = () -> {
            for (int k = 0; k < f.length; k++) {
                f[k].setActivated(modes[k].equals(Haul.folders(a)));
                if (modes[k].equals(Haul.folders(a))) foldersHelp.setText(where[k]);
            }
        };
        for (int k = 0; k < f.length; k++) {
            String mode = modes[k];
            f[k].setOnClickListener(v -> {
                Haul.prefs(a).edit().putString("folders", mode).apply();
                markFolders.run();
            });
        }
        markFolders.run();

        Switch ask = findViewById(R.id.swAsk), audio = findViewById(R.id.swAudio), wifi = findViewById(R.id.swWifi);
        ask.setChecked(Haul.askQuality(a));
        ask.setOnCheckedChangeListener((b, on) -> Haul.set(a, "ask_quality", on));
        audio.setChecked(Haul.audioTarget(a));
        audio.setOnCheckedChangeListener((b, on) -> {
            Haul.set(a, "audio_target", on);
            // The second share target is an alias of the share screen; turning it off hides it from the share sheet.
            a.getPackageManager().setComponentEnabledSetting(new ComponentName(a, AUDIO_TARGET),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        });
        wifi.setChecked(Haul.wifiOnly(a));
        wifi.setOnCheckedChangeListener((b, on) -> {
            Haul.set(a, "wifi_only", on);
            Downloads.settingsChanged(a);
        });
        ((TextView) findViewById(R.id.wifiHelp)).setText(Build.VERSION.SDK_INT >= 34
                ? "Links wait in the hold until the phone is on Wi-Fi."
                : "On mobile data, links stop with a note; tap them on Wi-Fi. (Android 14 and later wait for Wi-Fi instead.)");

        findViewById(R.id.addTile).setOnClickListener(v -> addTile());
        findViewById(R.id.backupSave).setOnClickListener(v -> {
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
            open(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "Haul backup " + day + ".json"), REQ_BACKUP);
        });
        findViewById(R.id.backupRestore).setOnClickListener(v -> open(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "application/octet-stream", "text/plain"}), REQ_RESTORE));
        findViewById(R.id.badgeSettings).setOnClickListener(v -> Sheets.open(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + a.getPackageName()))));

        notifState = findViewById(R.id.notifState);
        findViewById(R.id.notifSettings).setOnClickListener(v -> Sheets.open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())));

        engineVersion = findViewById(R.id.engineVersion);
        engineHelp = findViewById(R.id.engineHelp);
        engineUpdate = findViewById(R.id.engineUpdate);
        engineUpdate.setOnClickListener(v -> updateEngine());

        ((TextView) findViewById(R.id.aboutVersion)).setText("Haul " + Updates.installed(a));
        aboutCheck = findViewById(R.id.aboutCheck);
        aboutCheckResult = findViewById(R.id.aboutCheckResult);
        aboutCheck.setOnClickListener(v -> checkForUpdate());
        showUpdate();   // a newer Haul found earlier (the card on the hold) is offered here too
    }

    /** With a newer Haul known, the button downloads it; otherwise it checks. */
    private void showUpdate() {
        String newer = Updates.offered(a);
        aboutCheck.setEnabled(true);
        if (newer.isEmpty()) {
            aboutCheck.setText("Check for updates");
            aboutCheck.setOnClickListener(v -> checkForUpdate());
        } else {
            aboutCheck.setText("Download Haul " + newer);
            aboutCheck.setOnClickListener(v -> {
                String url = Haul.prefs(a).getString("update_url", "");
                if (!url.isEmpty()) Sheets.open(a, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            });
        }
    }

    private void checkForUpdate() {
        aboutCheck.setEnabled(false);
        aboutCheck.setText("Checking…");
        new Thread(() -> {
            Updates.Found found = Updates.checkNow(a);
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                aboutCheckResult.setVisibility(View.VISIBLE);
                aboutCheckResult.setText(found == Updates.Found.NEWER
                        ? "Haul " + Updates.offered(a) + " is out. Download it, open it, and tap Update. Your hold stays."
                        : found == Updates.Found.LATEST ? "You have the latest Haul."
                        : "Couldn't reach GitHub. Check your connection and try again.");
                showUpdate();
            });
        }, "haul-update-check").start();
    }

    private <T extends View> T findViewById(int id) {
        return root.findViewById(id);
    }

    /** The tab is shown, or Haul is back on screen with it: notifications may have been turned on or off meanwhile. */
    void resume() {
        boolean on = a.getSystemService(NotificationManager.class).areNotificationsEnabled();
        notifState.setText(on ? "Notifications are on" : "Notifications are off");
        ((TextView) findViewById(R.id.badgeState)).setText(Badge.allowed(a) ? "Floating badge: on" : "Floating badge: off");
        String v = Engine.version(a);
        engineVersion.setText(v.isEmpty() ? "yt-dlp" : "yt-dlp " + v);
    }

    /**
     * The last Instagram read (what Instagram sent around any song it mentioned), saved to the phone's Download
     * folder: for a photo post whose song didn't come through, to send and look into.
     */
    private void saveReport() {
        java.io.File report = Engine.instagramReport(a);
        if (!report.isFile()) {
            Toast.makeText(a, "Download an Instagram post first, then save the report", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String said;
            try {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Haul Instagram report.txt");
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri to = a.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                if (to == null) throw new Exception("no file");
                try (java.io.InputStream in = new java.io.FileInputStream(report);
                     java.io.OutputStream out = a.getContentResolver().openOutputStream(to)) {
                    if (out == null) throw new Exception("no file");
                    byte[] buf = new byte[64 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                }
                said = "Saved \"Haul Instagram report.txt\" to Download";
            } catch (Exception e) {
                said = "Couldn't save the report";
            }
            String message = said;
            a.runOnUiThread(() -> Toast.makeText(a, message, Toast.LENGTH_LONG).show());
        }, "haul-report").start();
    }

    private void open(Intent pick, int request) {
        try {
            a.startActivityForResult(pick, request);
        } catch (Exception e) {
            Toast.makeText(a, "This phone has no file picker Haul can open", Toast.LENGTH_LONG).show();
        }
    }

    /** The file picker's answer, passed on by MainActivity: write the backup there, or read one from there. */
    void onActivityResult(int request, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri file = data.getData();
        new Thread(() -> {
            String said;
            try {
                if (request == REQ_BACKUP) {
                    try (java.io.OutputStream out = a.getContentResolver().openOutputStream(file, "wt")) {
                        if (out == null) throw new Exception("no file");
                        out.write(Haul.backup(a).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    int n = Haul.items(a).size();
                    said = "Backed up " + n + (n == 1 ? " download" : " downloads");
                } else {
                    String text;
                    try (java.io.InputStream in = a.getContentResolver().openInputStream(file)) {
                        if (in == null) throw new Exception("no file");
                        text = Haul.readAll(in);
                    }
                    int n = Haul.restore(a, text, i -> Downloads.stillThere(a, i));
                    said = n == 0 ? "Nothing new in that backup" : "Brought back " + n + (n == 1 ? " download" : " downloads");
                }
            } catch (Exception e) {
                said = request == REQ_BACKUP ? "Couldn't write the backup there" : "That isn't a Haul backup";
            }
            String message = said;
            a.runOnUiThread(() -> Toast.makeText(a, message, Toast.LENGTH_LONG).show());
        }, "haul-backup").start();
    }

    /** Android 13+ asks the user to add the tile right here; older Android has to be told where to drag it. */
    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            a.getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(a, HaulTile.class),
                    "Haul", Icon.createWithResource(a, R.drawable.ic_notify), a.getMainExecutor(), result -> {
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            Toast.makeText(a, "The Haul tile is already in Quick Settings", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(a, "Pull down the shade twice, tap the pencil, and drag Haul into your tiles.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateEngine() {
        if (Downloads.busy()) {
            Toast.makeText(a, "Wait until the downloads finish", Toast.LENGTH_SHORT).show();
            return;
        }
        engineUpdate.setEnabled(false);
        engineUpdate.setText("Updating…");
        new Thread(() -> {
            String msg;
            try {
                msg = Engine.update(a) ? "Updated to " + Engine.version(a) + "." : "Up to date. Checked just now.";
            } catch (Exception e) {
                msg = "Couldn't reach GitHub. Try again later.";
            }
            String m = msg;
            a.runOnUiThread(() -> {
                engineUpdate.setEnabled(true);
                engineUpdate.setText("Update");
                engineHelp.setText(m);
                String v = Engine.version(a);
                engineVersion.setText(v.isEmpty() ? "yt-dlp" : "yt-dlp " + v);
            });
        }).start();
    }
}
