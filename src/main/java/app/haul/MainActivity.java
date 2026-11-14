package app.haul;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The hold: every haul as a ticket with its picture, filters, and the dock with the copied link. */
public class MainActivity extends Activity {
    static final String EXTRA_RETRY = "retry", EXTRA_NOTIFICATION = "notification";
    private static final String ALL = "all", VIDEOS = "videos", PHOTOS = "photos", AUDIO = "audio", FAILED = "failed";

    private View content, dock, dockClip, dockAdd, dockSelect, updateCard, notifyCard, wifiBadge;
    private LinearLayout hold;
    private TextView count, empty, dockLabel, dockLink, updateTitle, selectCount;
    /** Rows chosen with a long-press, to share or delete together; empty when not selecting. */
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<String> shown = new ArrayList<>();
    private Object backWhileSelecting;   // an OnBackInvokedCallback on Android 13+
    private Button dockDownload;
    private final Map<String, Button> filters = new HashMap<>();
    private String filter = ALL;
    private int contentTop, dockBottom, navBottom;
    private final Runnable render = this::render;
    /** The hold's rows by haul id, and what each one shows, so a redraw only touches rows that changed. */
    private final Map<String, View> rows = new HashMap<>();
    private final Map<String, String> drawn = new HashMap<>();
    private boolean offline, metered;
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network n) {
            runOnUiThread(MainActivity.this::render);
        }

        @Override
        public void onLost(Network n) {
            runOnUiThread(MainActivity.this::render);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        setTheme(R.style.Theme_Haul);   // the launch screen's theme (the Pull mark) has done its part
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31) fadeInFromLaunch();
        setContentView(R.layout.activity_main);
        edgeToEdge(this);

        content = findViewById(R.id.content);
        hold = findViewById(R.id.hold);
        count = findViewById(R.id.count);
        empty = findViewById(R.id.empty);
        wifiBadge = findViewById(R.id.wifiBadge);
        updateCard = findViewById(R.id.updateCard);
        updateTitle = findViewById(R.id.updateTitle);
        notifyCard = findViewById(R.id.notifyCard);
        dock = findViewById(R.id.dock);
        dockClip = findViewById(R.id.dockClip);
        dockAdd = findViewById(R.id.dockAdd);
        dockLabel = findViewById(R.id.dockLabel);
        dockLink = findViewById(R.id.dockLink);
        dockDownload = findViewById(R.id.dockDownload);
        dockSelect = findViewById(R.id.dockSelect);
        selectCount = findViewById(R.id.selectCount);
        findViewById(R.id.selectClose).setOnClickListener(v -> stopSelecting());
        findViewById(R.id.selectAll).setOnClickListener(v -> {
            selected.addAll(shown);
            render();
        });
        findViewById(R.id.selectShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.selectDelete).setOnClickListener(v -> deleteSelected());
        contentTop = content.getPaddingTop();
        dockBottom = ((ViewGroup.MarginLayoutParams) dock.getLayoutParams()).bottomMargin;
        findViewById(R.id.root).setOnApplyWindowInsetsListener(this::applyInsets);
        // Keep the last row clear of the dock, whatever height it has.
        dock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitBottom());

        String[][] chips = {{ALL, "" + R.id.fAll}, {VIDEOS, "" + R.id.fVideo}, {PHOTOS, "" + R.id.fPhotos},
                {AUDIO, "" + R.id.fAudio}, {FAILED, "" + R.id.fFailed}};
        for (String[] chip : chips) {
            Button b = findViewById(Integer.parseInt(chip[1]));
            filters.put(chip[0], b);
            b.setOnClickListener(v -> {
                filter = chip[0];
                render();
            });
        }

        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.dockOptions).setOnClickListener(v -> Sheets.add(this, new Sheets.Draft(dockUrl(), Haul.quality(this)), this::added));
        dockDownload.setOnClickListener(v -> {
            String url = dockUrl();
            Choose.add(this, url, Haul.quality(this), -1, -1, i -> {
                if (i != null) added(url);
            });
        });
        dockAdd.setOnClickListener(v -> Sheets.add(this, new Sheets.Draft("", Haul.quality(this)), this::added));
        findViewById(R.id.notifyOn).setOnClickListener(v -> Sheets.open(this, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        findViewById(R.id.update).setOnClickListener(v -> {
            String url = Haul.prefs(this).getString("update_url", "");
            if (!url.isEmpty()) Sheets.open(this, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Haul.prefs(this).getBoolean("asked_notifications", false)) {
            Haul.prefs(this).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /**
     * Android 12+ plays the Pull mark on the launch screen. Haul is often ready before the mark has played, which would
     * cut it short: keep the launch screen until the mark is done, then fade it out over the app.
     */
    @android.annotation.TargetApi(31)
    private void fadeInFromLaunch() {
        getSplashScreen().setOnExitAnimationListener(view -> {
            long left = 0;
            java.time.Instant start = view.getIconAnimationStart();
            java.time.Duration length = view.getIconAnimationDuration();
            if (start != null && length != null) {
                left = Math.max(0, Math.min(1000, start.toEpochMilli() + length.toMillis() - System.currentTimeMillis()));
            }
            view.animate().alpha(0f).setStartDelay(left).setDuration(220).withEndAction(view::remove).start();
        });
    }

    /** "Try again" on a failed-download notification lands here: Android lets an app on screen start a download. */
    private void handle(Intent intent) {
        String retry = intent == null ? null : intent.getStringExtra(EXTRA_RETRY);
        if (retry == null) return;
        getSystemService(NotificationManager.class).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
        intent.removeExtra(EXTRA_RETRY);
        if (Haul.get(this, retry) != null) Downloads.retry(this, retry);
    }

    /** Draws under the status and navigation bars (the screens pad themselves by the insets). */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.getWindow().setDecorFitsSystemWindows(false);
        } else {
            View decor = a.getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** The bars' sizes as {left, top, right, bottom}; bottom includes the keyboard. Android 10 has only the old getters. */
    @SuppressWarnings("deprecation")
    static int[] bars(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
            int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            return new int[]{b.left, b.top, b.right, Math.max(b.bottom, ime)};
        }
        return new int[]{insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
    }

    private WindowInsets applyInsets(View v, WindowInsets insets) {
        int[] b = bars(insets);
        navBottom = b[3];
        content.setPadding(content.getPaddingLeft(), contentTop + b[1], content.getPaddingRight(), content.getPaddingBottom());
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) dock.getLayoutParams();
        lp.bottomMargin = dockBottom + navBottom;
        dock.setLayoutParams(lp);
        v.setPadding(b[0], 0, b[2], 0);
        fitBottom();
        return insets;
    }

    private void fitBottom() {
        int bottom = dock.getHeight() + dockBottom + navBottom + Sheets.dp(this, 24);
        if (content.getPaddingBottom() != bottom) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), bottom);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Haul.listen(render);
        try {
            getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(network);
        } catch (RuntimeException ignored) {
            // without it, the waiting labels just update less often
        }
        render();
        Updates.checkSoon(this, () -> runOnUiThread(this::render));
    }

    @Override
    protected void onPause() {
        Haul.unlisten(render);
        try {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);
        } catch (RuntimeException ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == Sheets.REQ_DELETE) render();
    }

    // ---- The dock ----

    /**
     * Android only lets an app read the clipboard once its window has focus, so the copied link is picked up here.
     * Reading it makes Android 12+ show "Haul pasted from your clipboard", so it is read only when something new was
     * copied: the clip's description (which shows no message) carries the time it was copied.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        ClipDescription d;
        try {
            d = getSystemService(ClipboardManager.class).getPrimaryClipDescription();
        } catch (RuntimeException e) {
            return;
        }
        if (d == null || !d.hasMimeType("text/*")) return;
        if (d.getTimestamp() == Haul.prefs(this).getLong("clip_seen", -1)) return;
        Haul.prefs(this).edit().putLong("clip_seen", d.getTimestamp()).apply();
        String url = null;
        try {
            android.content.ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) url = Haul.firstLink(clip.getItemAt(0).coerceToText(this));
        } catch (RuntimeException ignored) {
            // no access: nothing to offer
        }
        // Offer each copied link once: not again after it was downloaded, or copied from Haul itself.
        if (url != null && !url.equals(Haul.prefs(this).getString("last_filled", ""))) {
            Haul.prefs(this).edit().putString("dock_link", url).apply();
            render();
        }
    }

    private String dockUrl() {
        return Haul.prefs(this).getString("dock_link", "");
    }

    /** A link went into the queue: the dock lets go of it. */
    private void added(String url) {
        Haul.prefs(this).edit().putString("last_filled", url).putString("dock_link", "").apply();
        filter = ALL;
        render();
    }

    // ---- Drawing ----

    /** Runs many times a second while downloading, so it only rebinds the rows whose text changed. */
    private void render() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        // Only Android 14+ download jobs wait for a (Wi-Fi) connection; on older Android an offline download fails.
        offline = Build.VERSION.SDK_INT >= 34 && cm.getActiveNetwork() == null;
        metered = Build.VERSION.SDK_INT >= 34 && Haul.wifiOnly(this) && cm.isActiveNetworkMetered();

        List<Haul.Item> all = Haul.items(this);
        int failedCount = 0;
        for (Haul.Item i : all) if (Haul.FAILED.equals(i.status) || Haul.CANCELED.equals(i.status)) failedCount++;
        filters.get(FAILED).setText(failedCount > 0 ? "Failed · " + failedCount : "Failed");
        for (Map.Entry<String, Button> f : filters.entrySet()) f.getValue().setActivated(f.getKey().equals(filter));
        count.setText("The hold · " + all.size() + (all.size() == 1 ? " item" : " items"));
        wifiBadge.setVisibility(Haul.wifiOnly(this) ? View.VISIBLE : View.GONE);

        LayoutInflater inf = getLayoutInflater();
        List<View> order = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Haul.Item i : all) {
            if (!shows(i)) continue;
            View row = rows.get(i.id);
            if (row == null) {
                row = inf.inflate(R.layout.item_haul, hold, false);
                rows.put(i.id, row);
            }
            String look = look(i);
            if (!look.equals(drawn.get(i.id))) {
                bind(row, i);
                drawn.put(i.id, look);
            }
            order.add(row);
            ids.add(i.id);
        }
        rows.keySet().retainAll(ids);
        drawn.keySet().retainAll(ids);
        shown.clear();
        shown.addAll(ids);
        Set<String> exist = new HashSet<>();
        for (Haul.Item i : all) exist.add(i.id);
        if (selected.retainAll(exist) && selected.isEmpty()) watchBack(false);
        boolean same = hold.getChildCount() == order.size();
        for (int k = 0; same && k < order.size(); k++) same = hold.getChildAt(k) == order.get(k);
        if (!same) {
            hold.removeAllViews();
            for (View row : order) hold.addView(row);
        }
        empty.setVisibility(order.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(all.isEmpty()
                ? "Nothing yet. In TikTok, Instagram or X tap Share, then Haul. Or copy a link and come back here.\n\n"
                + "Videos land in your Gallery in Movies/Haul, photos in Pictures/Haul, audio in Music/Haul."
                : "Nothing here.");

        notifyCard.setVisibility(getSystemService(NotificationManager.class).areNotificationsEnabled() ? View.GONE : View.VISIBLE);
        String newVersion = Haul.prefs(this).getString("update_version", "");
        updateCard.setVisibility(newVersion.isEmpty() ? View.GONE : View.VISIBLE);
        updateTitle.setText("Haul " + newVersion + " is out");

        String link = dockUrl();
        boolean hasLink = !link.isEmpty(), selecting = !selected.isEmpty();
        dockSelect.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectCount.setText(selected.size() + " SELECTED");
        dockClip.setVisibility(hasLink && !selecting ? View.VISIBLE : View.GONE);
        dockAdd.setVisibility(hasLink || selecting ? View.GONE : View.VISIBLE);
        if (hasLink) {
            // Read the link while the user looks at it: the download then starts from what was read.
            Engine.readAhead(this, link);
            dockLabel.setText("Copied link · " + Haul.site(link));
            dockLink.setText(Look.shortLink(link));
            dockDownload.setText("Download · " + Look.qualityName(Haul.quality(this)));
        }
    }

    private boolean shows(Haul.Item i) {
        boolean done = Haul.DONE.equals(i.status);
        switch (filter) {
            case VIDEOS: return done ? Haul.VIDEO.equals(i.kind) : i.active() && !Haul.AUDIO.equals(i.quality);
            case PHOTOS: return done && Haul.PHOTOS.equals(i.kind);
            case AUDIO: return done ? Haul.AUDIO.equals(i.kind) : i.active() && Haul.AUDIO.equals(i.quality);
            case FAILED: return Haul.FAILED.equals(i.status) || Haul.CANCELED.equals(i.status);
            default: return true;
        }
    }

    /** Everything a row shows, as one string: when it changes, the row is redrawn. */
    private String look(Haul.Item i) {
        float p = Haul.WORKING.equals(i.status) ? Math.round(Haul.progress(i.id)) : 0;
        return i.status + '|' + p + '|' + i.title + '|' + i.error + '|' + i.size + '|' + i.kind + '|' + i.uri + '|'
                + (Haul.QUEUED.equals(i.status) ? waiting() : "") + '|' + selected.isEmpty() + selected.contains(i.id);
    }

    private String waiting() {
        return offline ? "Offline" : metered ? "Waiting for Wi-Fi" : "Waiting";
    }

    private void bind(View row, Haul.Item i) {
        ImageView thumb = row.findViewById(R.id.thumb);
        Look.picture(this, row.findViewById(R.id.thumbBox), thumb, row.findViewById(R.id.thumbIcon),
                row.findViewById(R.id.thumbTag), i, 162, 288);
        TextView len = row.findViewById(R.id.thumbLen);
        String lenText = !Haul.DONE.equals(i.status) ? ""
                : Haul.PHOTOS.equals(i.kind) ? (i.photos() > 1 ? "×" + i.photos() : "")
                : i.duration > 0 ? Format.clock(i.duration) : "";
        len.setText(lenText);
        len.setVisibility(lenText.isEmpty() ? View.GONE : View.VISIBLE);

        ((TextView) row.findViewById(R.id.itemNo)).setText("No. " + Format.ticket(i.no));
        ((TextView) row.findViewById(R.id.itemTitle)).setText(Look.title(i));
        TextView meta = row.findViewById(R.id.itemMeta);
        TextView pct = row.findViewById(R.id.itemPct), state = row.findViewById(R.id.itemState), failed = row.findViewById(R.id.itemFailed);
        ProgressBar bar = row.findViewById(R.id.itemProgress);
        pct.setVisibility(View.GONE);
        state.setVisibility(View.GONE);
        failed.setVisibility(View.GONE);
        bar.setVisibility(View.GONE);

        switch (i.status) {
            case Haul.WORKING:
                float p = Haul.progress(i.id);
                meta.setText(i.site + " · " + (p >= 0 ? "Downloading" : Look.stage(i, p)));
                bar.setVisibility(View.VISIBLE);
                bar.setIndeterminate(p < 0);
                if (p >= 0) {
                    bar.setProgress(Math.round(p * 10));
                    pct.setVisibility(View.VISIBLE);
                    pct.setText(Math.round(p) + "%");
                }
                break;
            case Haul.QUEUED:
                meta.setText(i.site + " · " + Look.what(i) + " · in line");
                state.setVisibility(View.VISIBLE);
                state.setAllCaps(true);
                state.setText(waiting());
                break;
            case Haul.DONE:
                meta.setText(i.site + " · " + Look.what(i));
                state.setVisibility(View.VISIBLE);
                state.setAllCaps(false);
                state.setText(Format.size(i.size));
                break;
            case Haul.CANCELED:
                meta.setText(i.error.isEmpty() || "Canceled.".equals(i.error) ? i.site + " · Canceled. Tap to try again" : i.error);
                failed.setVisibility(View.VISIBLE);
                failed.setText("Canceled");
                break;
            default:
                meta.setText(i.error.isEmpty() ? i.site + " · Tap to try again" : i.error);
                failed.setVisibility(View.VISIBLE);
                failed.setText("Failed");
        }
        // Selecting: a long-press starts it; then taps add or take away rows.
        boolean selecting = !selected.isEmpty(), chosen = selected.contains(i.id);
        row.setActivated(chosen);
        ImageView check = row.findViewById(R.id.thumbCheck);
        check.setVisibility(selecting ? View.VISIBLE : View.GONE);
        check.setBackgroundResource(chosen ? R.drawable.bg_check_on : R.drawable.bg_check_off);
        check.setImageResource(chosen ? R.drawable.ic_check : 0);
        row.setOnClickListener(v -> {
            if (selected.isEmpty()) Sheets.item(this, i);
            else toggle(i.id);
        });
        row.setOnLongClickListener(v -> {
            toggle(i.id);
            return true;
        });
    }

    // ---- Selecting several ----

    private void toggle(String id) {
        if (!selected.remove(id)) selected.add(id);
        watchBack(!selected.isEmpty());
        render();
    }

    private void stopSelecting() {
        selected.clear();
        watchBack(false);
        render();
    }

    /** While selecting, Back stops selecting instead of leaving Haul (Android 13+ asks for this up front). */
    private void watchBack(boolean on) {
        if (Build.VERSION.SDK_INT < 33) return;
        android.window.OnBackInvokedDispatcher back = getOnBackInvokedDispatcher();
        if (on && backWhileSelecting == null) {
            android.window.OnBackInvokedCallback cb = this::stopSelecting;
            backWhileSelecting = cb;
            back.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!on && backWhileSelecting != null) {
            back.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backWhileSelecting);
            backWhileSelecting = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!selected.isEmpty()) stopSelecting();
        else super.onBackPressed();
    }

    private List<Haul.Item> chosen() {
        List<Haul.Item> out = new ArrayList<>();
        for (Haul.Item i : Haul.items(this)) if (selected.contains(i.id)) out.add(i);
        return out;
    }

    private void shareSelected() {
        List<Uri> uris = new ArrayList<>();
        Set<String> types = new HashSet<>();
        for (Haul.Item i : chosen()) {
            if (!Haul.DONE.equals(i.status)) continue;
            for (String u : i.uris) if (!u.isEmpty() && !"null".equals(u)) uris.add(Uri.parse(u));
            types.add(i.mime.contains("/") ? i.mime.substring(0, i.mime.indexOf('/')) : "*");
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "None of these is saved yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = types.size() == 1 ? types.iterator().next() + "/*" : "*/*";
        Sheets.open(this, Downloads.shareIntent(uris, type, selected.size() + " from Haul"));
    }

    /** Saved ones: delete their files from the Gallery too, or only take them off the list. */
    private void deleteSelected() {
        List<Haul.Item> chosen = chosen();
        int saved = 0;
        for (Haul.Item i : chosen) if (Haul.DONE.equals(i.status)) saved++;
        String n = chosen.size() + (chosen.size() == 1 ? " download" : " downloads");
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this).setTitle(n);
        if (saved > 0) {
            b.setMessage("Delete " + (saved == 1 ? "its file" : "their files") + " from your Gallery too, or only take "
                    + (chosen.size() == 1 ? "it" : "them") + " off this list?")
                    .setPositiveButton("Delete files", (x, y) -> remove(chosen, true))
                    .setNegativeButton("Only from the list", (x, y) -> remove(chosen, false))
                    .setNeutralButton("Keep", null);
        } else {
            b.setMessage("Take " + (chosen.size() == 1 ? "it" : "them") + " off the list? Any still downloading stop.")
                    .setPositiveButton("Remove", (x, y) -> remove(chosen, false))
                    .setNegativeButton("Keep", null);
        }
        b.show();
    }

    private void remove(List<Haul.Item> chosen, boolean files) {
        List<Uri> refused = new ArrayList<>();
        for (Haul.Item i : chosen) {
            if (i.active()) Downloads.cancel(this, i.id);
            if (files && Haul.DONE.equals(i.status)) {
                for (String u : i.uris) Thumbs.forget(u);
                refused.addAll(Downloads.delete(this, i));
            } else {
                Haul.remove(this, i.id);
            }
        }
        stopSelecting();
        if (refused.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 30) {
            // Saved before a reinstall, so no longer Haul's: Android asks the user itself, once for all of them.
            try {
                startIntentSenderForResult(android.provider.MediaStore.createDeleteRequest(getContentResolver(), refused)
                        .getIntentSender(), Sheets.REQ_DELETE, null, 0, 0, 0);
                return;
            } catch (Exception ignored) {
                // fall through to the note
            }
        }
        Toast.makeText(this, "Removed from Haul. Delete the older files in your Gallery.", Toast.LENGTH_LONG).show();
    }
}
