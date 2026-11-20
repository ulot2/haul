package app.haul;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

/**
 * Haul's screen, in four tabs:
 * - DOWNLOAD, where it opens: the wordmark and a link field. Paste (or a link found copied) opens HAUL THIS AS
 *   (Sheets.add): quality, clip, Download. Links copied one after another gather in the field.
 * - DOWNLOADS: the hold, every haul as a ticket with its picture, with search and filters; long-press to select.
 * - STATUSES: WhatsApp's statuses (StatusesPage).
 * - SETTINGS (SettingsPage).
 * Every tap shows: buttons give under the finger (animator/press) as well as their ripple.
 */
public class MainActivity extends Activity {
    static final String EXTRA_RETRY = "retry", EXTRA_NOTIFICATION = "notification", EXTRA_TAB = "tab";
    static final String TAB_DOWNLOAD = "download", TAB_HOLD = "hold", TAB_STATUSES = "statuses", TAB_SETTINGS = "settings";
    private static final String ALL = "all", VIDEOS = "videos", PHOTOS = "photos", AUDIO = "audio", FAILED = "failed";

    private View content, dock, updateCard, notifyCard, wifiBadge, tabs, pages, linkBox, downloadContent, statusesContent, settingsContent;
    private View pageDownload, pageHold, pageStatuses, pageSettings;
    private LinearLayout hold;
    private TextView count, empty, updateTitle, selectCount, holdCount;
    private EditText linkField, search;
    private ImageButton linkGo;
    private StatusesPage statuses;
    private SettingsPage settings;
    private String tab = TAB_DOWNLOAD;
    /** The open HAUL THIS AS, so a link copied meanwhile doesn't open a second. */
    private Dialog haulSheet;
    /** Rows chosen with a long-press, to share or delete together; empty when not selecting. */
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<String> shown = new ArrayList<>();
    private Object backCallback;   // an OnBackInvokedCallback on Android 13+
    private final Map<String, Button> filters = new HashMap<>();
    private String filter = ALL;
    /** What is typed in the search box; "" shows everything (that the filter lets through). */
    private String query = "";
    private int contentTop, downloadTop, statusesTop, settingsTop, tabsBottom, holdBottom;
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

        pages = findViewById(R.id.pages);
        tabs = findViewById(R.id.tabs);
        pageDownload = findViewById(R.id.pageDownload);
        pageHold = findViewById(R.id.pageHold);
        pageStatuses = findViewById(R.id.pageStatuses);
        pageSettings = findViewById(R.id.pageSettings);
        settingsContent = findViewById(R.id.settingsContent);
        downloadContent = findViewById(R.id.downloadContent);
        statusesContent = findViewById(R.id.stContent);
        content = findViewById(R.id.content);
        hold = findViewById(R.id.hold);
        count = findViewById(R.id.count);
        empty = findViewById(R.id.empty);
        wifiBadge = findViewById(R.id.wifiBadge);
        updateCard = findViewById(R.id.updateCard);
        updateTitle = findViewById(R.id.updateTitle);
        notifyCard = findViewById(R.id.notifyCard);
        dock = findViewById(R.id.dock);
        selectCount = findViewById(R.id.selectCount);
        holdCount = findViewById(R.id.tabHoldCount);
        linkBox = findViewById(R.id.linkBox);
        linkField = findViewById(R.id.linkField);
        linkGo = findViewById(R.id.linkGo);
        statuses = new StatusesPage(this, pageStatuses);
        settings = new SettingsPage(this, pageSettings);

        findViewById(R.id.selectClose).setOnClickListener(v -> stopSelecting());
        findViewById(R.id.selectAll).setOnClickListener(v -> {
            selected.addAll(shown);
            render();
        });
        findViewById(R.id.selectShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.selectDelete).setOnClickListener(v -> deleteSelected());
        contentTop = content.getPaddingTop();
        holdBottom = content.getPaddingBottom();
        downloadTop = downloadContent.getPaddingTop();
        statusesTop = statusesContent.getPaddingTop();
        settingsTop = settingsContent.getPaddingTop();
        tabsBottom = tabs.getPaddingBottom();
        findViewById(R.id.root).setOnApplyWindowInsetsListener(this::applyInsets);
        // Keep the last row clear of the selection bar, whatever height it has.
        dock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitBottom());

        String[][] tabIds = {{TAB_DOWNLOAD, "" + R.id.tabDownload}, {TAB_HOLD, "" + R.id.tabHold},
                {TAB_STATUSES, "" + R.id.tabStatuses}, {TAB_SETTINGS, "" + R.id.tabSettings}};
        for (String[] t : tabIds) {
            findViewById(Integer.parseInt(t[1])).setOnClickListener(v -> {
                show(t[0]);
            });
        }

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

        search = findViewById(R.id.search);
        View clear = findViewById(R.id.searchClear);
        search.addTextChangedListener(new Typed(s -> {
            query = s.trim();
            clear.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
            render();
        }));
        search.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard(search);
            return true;
        });
        clear.setOnClickListener(v -> {
            search.setText("");
            hideKeyboard(search);
        });

        // The Download page's field: the links waiting (copied, or typed), shown side by side.
        linkField.setText(String.join(" ", split(Haul.prefs(this).getString("dock_link", ""))));
        linkField.addTextChangedListener(new Typed(s -> markField()));
        linkField.setOnFocusChangeListener((v, focused) -> markField());
        markField();
        linkField.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_GO && action != EditorInfo.IME_ACTION_DONE) return false;
            openSheet();
            return true;
        });
        linkGo.setOnClickListener(v -> {
            if (Haul.links(linkField.getText()).isEmpty()) paste();
            else openSheet();
        });

        findViewById(R.id.notifyOn).setOnClickListener(v -> Sheets.open(this, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        findViewById(R.id.update).setOnClickListener(v -> {
            String url = Haul.prefs(this).getString("update_url", "");
            if (!url.isEmpty()) Sheets.open(this, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        // Haul opens on the Download page; turned (or brought back by Android), it stays where it was.
        show(state == null ? TAB_DOWNLOAD : state.getString(EXTRA_TAB, TAB_DOWNLOAD));

        if (state == null && WelcomeActivity.due(this)) {
            // First time on this phone: how to use Haul, which also asks for notifications once it has said why.
            startActivity(new Intent(this, WelcomeActivity.class));
        } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Haul.prefs(this).getBoolean("asked_notifications", false)) {
            Haul.prefs(this).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        handle(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_TAB, tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /** A TextWatcher that only cares about the text once it changed. */
    private static final class Typed implements android.text.TextWatcher {
        private final java.util.function.Consumer<String> then;

        Typed(java.util.function.Consumer<String> then) {
            this.then = then;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            then.accept(s.toString());
        }
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

    /**
     * From a notification: "Try again" on a failed download (Android lets an app on screen start one), or a tap on
     * one in progress; both show the Downloads tab.
     */
    private void handle(Intent intent) {
        if (intent == null) return;
        String to = intent.getStringExtra(EXTRA_TAB);
        if (to != null) {
            intent.removeExtra(EXTRA_TAB);
            show(to);
        }
        String retry = intent.getStringExtra(EXTRA_RETRY);
        if (retry == null) return;
        getSystemService(NotificationManager.class).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
        intent.removeExtra(EXTRA_RETRY);
        show(TAB_HOLD);
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

    /**
     * Each page's top clears the status bar; the tab bar's bottom clears the navigation bar. With the keyboard up the
     * tab bar steps aside and the pages end above the keyboard, so the field being typed in stays in view.
     */
    private WindowInsets applyInsets(View v, WindowInsets insets) {
        int[] b = bars(insets);
        int keyboard = 0, nav = b[3];
        if (Build.VERSION.SDK_INT >= 30) {
            keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            nav = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
        }
        content.setPadding(content.getPaddingLeft(), contentTop + b[1], content.getPaddingRight(), content.getPaddingBottom());
        downloadContent.setPadding(downloadContent.getPaddingLeft(), downloadTop + b[1], downloadContent.getPaddingRight(), 0);
        statusesContent.setPadding(statusesContent.getPaddingLeft(), statusesTop + b[1], statusesContent.getPaddingRight(),
                statusesContent.getPaddingBottom());
        settingsContent.setPadding(settingsContent.getPaddingLeft(), settingsTop + b[1], settingsContent.getPaddingRight(),
                settingsContent.getPaddingBottom());
        boolean typing = keyboard > nav;
        tabs.setVisibility(typing ? View.GONE : View.VISIBLE);
        tabs.setPadding(tabs.getPaddingLeft(), tabs.getPaddingTop(), tabs.getPaddingRight(), tabsBottom + nav);
        pages.setPadding(0, 0, 0, typing ? keyboard : 0);
        v.setPadding(b[0], 0, b[2], 0);
        fitBottom();
        return insets;
    }

    /** The hold's last row clear of the selection bar while selecting. */
    private void fitBottom() {
        int bottom = holdBottom + (dock.getVisibility() == View.VISIBLE ? dock.getHeight() + Sheets.dp(this, 12) : 0);
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
        Badge.haulOnScreen(true);   // the Downloads tab shows what the badge would
        render();
        if (TAB_STATUSES.equals(tab)) statuses.resume();
        if (TAB_SETTINGS.equals(tab)) settings.resume();
        Updates.checkSoon(this, () -> runOnUiThread(this::render));
    }

    @Override
    protected void onPause() {
        Haul.unlisten(render);
        Badge.haulOnScreen(false);
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
        if (request == StatusesPage.REQ_FOLDER) statuses.onActivityResult(request, result, data);
    }

    // ---- The tabs ----

    private void show(String which) {
        if (!TAB_HOLD.equals(which) && !TAB_STATUSES.equals(which) && !TAB_SETTINGS.equals(which)) which = TAB_DOWNLOAD;
        boolean changed = !which.equals(tab);
        tab = which;
        View[] all = {pageDownload, pageHold, pageStatuses, pageSettings};
        String[] names = {TAB_DOWNLOAD, TAB_HOLD, TAB_STATUSES, TAB_SETTINGS};
        for (int k = 0; k < all.length; k++) {
            boolean on = names[k].equals(tab);
            if (on && changed && all[k].getVisibility() != View.VISIBLE) {
                // A quick fade in, so a tab switch reads as moving, not as a flicker.
                all[k].animate().cancel();
                all[k].setAlpha(0f);
                all[k].animate().alpha(1f).setDuration(160).start();
            }
            all[k].setVisibility(on ? View.VISIBLE : View.GONE);
        }
        mark(R.id.tabDownload, R.id.tabDownloadPill, R.id.tabDownloadIcon, R.id.tabDownloadLabel, TAB_DOWNLOAD);
        mark(R.id.tabHold, R.id.tabHoldPill, R.id.tabHoldIcon, R.id.tabHoldLabel, TAB_HOLD);
        mark(R.id.tabStatuses, R.id.tabStatusesPill, R.id.tabStatusesIcon, R.id.tabStatusesLabel, TAB_STATUSES);
        mark(R.id.tabSettings, R.id.tabSettingsPill, R.id.tabSettingsIcon, R.id.tabSettingsLabel, TAB_SETTINGS);
        if (changed) {
            hideKeyboard(linkField);
            if (!TAB_HOLD.equals(tab) && !selected.isEmpty()) stopSelecting();
            if (TAB_STATUSES.equals(tab)) statuses.resume();
            if (TAB_SETTINGS.equals(tab)) settings.resume();
        }
        watchBack();
    }

    /** The current tab: its icon in clay on a soft pill, its name in bold ink; the others muted. */
    private void mark(int tabId, int pillId, int iconId, int labelId, String which) {
        boolean on = which.equals(tab);
        findViewById(pillId).setActivated(on);
        ((ImageView) findViewById(iconId)).setImageTintList(ColorStateList.valueOf(getColor(on ? R.color.clay : R.color.ink_muted)));
        TextView label = findViewById(labelId);
        label.setTextColor(getColor(on ? R.color.ink : R.color.ink_muted));
        label.setTypeface(getResources().getFont(on ? R.font.archivo_extrabold : R.font.archivo_regular));
        View t = findViewById(tabId);
        t.setSelected(on);
        t.setContentDescription(label.getText() + (on ? ", selected" : ""));
    }

    // ---- The Download page ----

    /** The field holds a link: outlined in clay, and its button downloads instead of pasting. */
    private void markField() {
        boolean has = !Haul.links(linkField.getText()).isEmpty();
        linkBox.setActivated(has || linkField.hasFocus());
        linkGo.setImageResource(has ? R.drawable.ic_download : R.drawable.ic_paste);
        linkGo.setContentDescription(has ? "Download" : "Paste");
    }

    /** The round button with nothing in the field: what was copied goes in, and HAUL THIS AS opens. */
    private void paste() {
        List<String> links = new ArrayList<>();
        try {
            android.content.ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) links = Haul.links(clip.getItemAt(0).coerceToText(this));
        } catch (RuntimeException ignored) {
            // no access: same as nothing copied
        }
        if (links.isEmpty()) {
            Toast.makeText(this, "Copy a link first: in TikTok, Instagram or X, tap Share, then Copy link", Toast.LENGTH_LONG).show();
            return;
        }
        setField(links);
        openSheet();
    }

    private void setField(List<String> links) {
        linkField.setText(String.join(" ", links));
        linkField.setSelection(linkField.length());
        Haul.prefs(this).edit().putString("dock_link", String.join("\n", links))
                .putLong("dock_time", System.currentTimeMillis()).apply();
    }

    /** HAUL THIS AS for the link(s) in the field; a note in the field when there's none. */
    private void openSheet() {
        if (haulSheet != null && haulSheet.isShowing()) return;
        List<String> links = Haul.links(linkField.getText());
        if (links.isEmpty()) {
            linkField.setError(linkField.length() == 0 ? "Paste a link first" : "That isn't a link. It should start with https://");
            return;
        }
        hideKeyboard(linkField);
        haulSheet = Sheets.add(this, new Sheets.Draft(String.join(" ", links), Haul.quality(this)), this::added);
    }

    /**
     * Android only lets an app read the clipboard once its window has focus, so a copied link is picked up here.
     * Reading it makes Android 12+ show "Haul pasted from your clipboard", so it is read only when something new was
     * copied: the clip's description (which shows no message) carries the time it was copied. The link goes into the
     * Download page's field and HAUL THIS AS opens. Links copied one after another (in TikTok, Copy link, back to
     * Haul, again) gather in the field, since the clipboard holds only the last; a list left a while starts afresh.
     * Each link is offered once: not after it was downloaded from here, or copied from Haul itself.
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
        List<String> links = new ArrayList<>();
        try {
            android.content.ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) links = Haul.links(clip.getItemAt(0).coerceToText(this));
        } catch (RuntimeException ignored) {
            // no access: nothing to offer
        }
        if (links.isEmpty()) return;
        List<String> handled = split(Haul.prefs(this).getString("last_filled", ""));
        boolean fresh = System.currentTimeMillis() - Haul.prefs(this).getLong("dock_time", 0) > Haul.COLLECT_FOR;
        List<String> waiting = fresh ? new ArrayList<>() : Haul.links(linkField.getText());
        List<String> now = Haul.collect(waiting, links, handled);
        if (now.isEmpty() || now.equals(waiting)) return;   // nothing new
        setField(now);
        if (!selected.isEmpty()) return;   // busy choosing rows: the link waits in the field
        show(TAB_DOWNLOAD);
        if (haulSheet != null && haulSheet.isShowing()) haulSheet.dismiss();   // reopened with the links gathered so far
        openSheet();
    }

    private static List<String> split(String lines) {
        List<String> out = new ArrayList<>();
        for (String s : lines.split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** A link (or several, one to a line) went into the queue: the field lets go of it, and the Downloads tab shows it. */
    private void added(String url) {
        Haul.prefs(this).edit().putString("last_filled", url).putString("dock_link", "").apply();
        linkField.setText("");
        filter = ALL;
        show(TAB_HOLD);
        render();
    }

    private void hideKeyboard(View v) {
        v.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ---- Drawing ----

    /** Runs many times a second while downloading, so it only rebinds the rows whose text changed. */
    private void render() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        // Only Android 14+ download jobs wait for a (Wi-Fi) connection; on older Android an offline download fails.
        offline = Build.VERSION.SDK_INT >= 34 && cm.getActiveNetwork() == null;
        metered = Build.VERSION.SDK_INT >= 34 && Haul.wifiOnly(this) && cm.isActiveNetworkMetered();

        List<Haul.Item> all = Haul.items(this);
        int failedCount = 0, going = 0;
        for (Haul.Item i : all) {
            if (Haul.FAILED.equals(i.status) || Haul.CANCELED.equals(i.status)) failedCount++;
            if (i.active()) going++;
        }
        holdCount.setVisibility(going > 0 ? View.VISIBLE : View.GONE);
        holdCount.setText(String.valueOf(going));
        findViewById(R.id.tabHold).setContentDescription("Downloads" + (going > 0 ? ", " + going + " going" : "")
                + (TAB_HOLD.equals(tab) ? ", selected" : ""));
        filters.get(FAILED).setText(failedCount > 0 ? "Failed · " + failedCount : "Failed");
        for (Map.Entry<String, Button> f : filters.entrySet()) f.getValue().setActivated(f.getKey().equals(filter));
        wifiBadge.setVisibility(Haul.wifiOnly(this) ? View.VISIBLE : View.GONE);

        LayoutInflater inf = getLayoutInflater();
        List<View> order = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Haul.Item i : all) {
            if (!shows(i) || !Haul.matches(i, query)) continue;
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
        if (selected.retainAll(exist) && selected.isEmpty()) watchBack();
        boolean same = hold.getChildCount() == order.size();
        for (int k = 0; same && k < order.size(); k++) same = hold.getChildAt(k) == order.get(k);
        if (!same) {
            hold.removeAllViews();
            for (View row : order) hold.addView(row);
        }
        count.setText(query.isEmpty() ? all.size() + (all.size() == 1 ? " item" : " items")
                : "Found " + order.size() + " of " + all.size());
        empty.setVisibility(order.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(!all.isEmpty() && !query.isEmpty() ? "Nothing here matches \u201c" + query + "\u201d"
                + (filter.equals(ALL) ? "." : ". Try All.")
                : all.isEmpty()
                ? "Nothing yet. Paste a link on the Download tab, or in TikTok, Instagram or X tap Share, then Haul.\n\n"
                + "Videos land in your Gallery in Movies/Haul, photos in Pictures/Haul, audio in Music/Haul."
                : "Nothing here.");

        notifyCard.setVisibility(getSystemService(NotificationManager.class).areNotificationsEnabled() ? View.GONE : View.VISIBLE);
        String newVersion = Updates.offered(this);   // not the one just installed
        updateCard.setVisibility(newVersion.isEmpty() ? View.GONE : View.VISIBLE);
        updateTitle.setText("Haul " + newVersion + " is out");

        boolean selecting = !selected.isEmpty();
        dock.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectCount.setText(selected.size() + " SELECTED");
        fitBottom();
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
            case Haul.CHOOSE:
                // Read in the background, and waiting for the user to pick from the post: a tap opens the picker.
                meta.setText(i.site + " · Tap to pick from the post");
                state.setVisibility(View.VISIBLE);
                state.setAllCaps(true);
                state.setText("Pick");
                state.setTextColor(getColor(R.color.clay_text));
                break;
            case Haul.QUEUED:
                meta.setText(i.site + " · " + (i.decide ? "Reading the post · in line" : Look.what(i) + " · in line"));
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
        if (!Haul.CHOOSE.equals(i.status)) state.setTextColor(getColor(R.color.ink_muted));
        row.setOnClickListener(v -> {
            if (selected.isEmpty() && Haul.CHOOSE.equals(i.status)) Choose.choose(this, i, added -> render());
            else if (selected.isEmpty()) Sheets.item(this, i);
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
        watchBack();
        render();
    }

    private void stopSelecting() {
        selected.clear();
        watchBack();
        render();
    }

    /**
     * Back stops selecting, then leaves another tab for the Download page, then leaves Haul. Android 13+ asks for
     * that up front: a callback is registered only while Back has something to do in Haul.
     */
    private void watchBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean on = !selected.isEmpty() || !TAB_DOWNLOAD.equals(tab);
        android.window.OnBackInvokedDispatcher back = getOnBackInvokedDispatcher();
        if (on && backCallback == null) {
            android.window.OnBackInvokedCallback cb = this::back;
            backCallback = cb;
            back.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!on && backCallback != null) {
            back.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    private void back() {
        if (!selected.isEmpty()) stopSelecting();
        else show(TAB_DOWNLOAD);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!selected.isEmpty() || !TAB_DOWNLOAD.equals(tab)) back();
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
            if (Haul.CHOOSE.equals(i.status)) Reading.forget(this, i.id);   // and its "pick" notification
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
