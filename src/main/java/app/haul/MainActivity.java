package app.haul;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private View header, wordmark, updateCard, clear, empty;
    private EditText link;
    private LinearLayout hold;
    private TextView engine, updateTitle;
    private Button engineUpdate;
    private int headerHeight, wordmarkTop;
    private final Runnable render = this::render;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else edgeToEdgeLegacy();

        header = findViewById(R.id.header);
        wordmark = findViewById(R.id.wordmark);
        link = findViewById(R.id.link);
        hold = findViewById(R.id.hold);
        empty = findViewById(R.id.empty);
        clear = findViewById(R.id.clear);
        engine = findViewById(R.id.engine);
        engineUpdate = findViewById(R.id.engineUpdate);
        updateCard = findViewById(R.id.updateCard);
        updateTitle = findViewById(R.id.updateTitle);
        headerHeight = header.getLayoutParams().height;
        wordmarkTop = ((ViewGroup.MarginLayoutParams) wordmark.getLayoutParams()).topMargin;
        findViewById(R.id.scroll).setOnApplyWindowInsetsListener(this::applyInsets);

        RadioGroup quality = findViewById(R.id.quality);
        String q = Haul.quality(this);
        quality.check(Haul.AUDIO.equals(q) ? R.id.qAudio : Haul.SMALL.equals(q) ? R.id.qSmall : R.id.qBest);
        quality.setOnCheckedChangeListener((g, id) ->
                Haul.setQuality(this, id == R.id.qAudio ? Haul.AUDIO : id == R.id.qSmall ? Haul.SMALL : Haul.BEST));

        findViewById(R.id.download).setOnClickListener(v -> download());
        link.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            download();
            return true;
        });
        findViewById(R.id.paste).setOnClickListener(v -> {
            String url = clipboardLink();
            if (url == null) Toast.makeText(this, "There's no link on the clipboard", Toast.LENGTH_SHORT).show();
            else link.setText(url);
        });
        clear.setOnClickListener(v -> Haul.clearFinished(this));
        engineUpdate.setOnClickListener(v -> updateEngine());
        findViewById(R.id.update).setOnClickListener(v -> {
            String url = Haul.prefs(this).getString("update_url", "");
            if (!url.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Haul.prefs(this).getBoolean("asked_notifications", false)) {
            Haul.prefs(this).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @SuppressWarnings("deprecation")
    private void edgeToEdgeLegacy() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | getWindow().getDecorView().getSystemUiVisibility());
    }

    // The system-window inset getters cover API 29, where WindowInsets.Type does not exist yet. On 30+ they include the keyboard.
    @SuppressWarnings("deprecation")
    private WindowInsets applyInsets(View v, WindowInsets insets) {
        int top = insets.getSystemWindowInsetTop();
        ViewGroup.LayoutParams lp = header.getLayoutParams();
        lp.height = headerHeight + top;
        header.setLayoutParams(lp);
        ((ViewGroup.MarginLayoutParams) wordmark.getLayoutParams()).topMargin = wordmarkTop + top;
        v.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
        return insets.consumeSystemWindowInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Haul.listen(render);
        render();
        Updates.checkSoon(this, () -> runOnUiThread(this::render));
    }

    @Override
    protected void onPause() {
        Haul.unlisten(render);
        super.onPause();
    }

    /** Android only lets an app read the clipboard once its window has focus, so the link is filled in here. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || link.length() > 0) return;
        String url = clipboardLink();
        // Fill each copied link once: not again after it was downloaded or cleared.
        if (url != null && !url.equals(Haul.prefs(this).getString("last_filled", ""))) {
            Haul.prefs(this).edit().putString("last_filled", url).apply();
            link.setText(url);
        }
    }

    private String clipboardLink() {
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            return Haul.firstLink(clip.getItemAt(0).coerceToText(this));
        } catch (Exception e) {
            return null;
        }
    }

    private void download() {
        String url = Haul.firstLink(link.getText());
        if (url == null) {
            link.setError(link.length() == 0 ? "Paste a link first" : "That isn't a link. It should start with https://");
            return;
        }
        HaulService.start(this, url);
        link.setText("");
        link.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(link.getWindowToken(), 0);
    }

    private void updateEngine() {
        if (HaulService.running) {
            Toast.makeText(this, "Wait until the downloads finish", Toast.LENGTH_SHORT).show();
            return;
        }
        engineUpdate.setEnabled(false);
        engineUpdate.setText("Updating…");
        new Thread(() -> {
            String msg;
            try {
                msg = Engine.update(this) ? "Engine updated to " + Engine.version(this) : "The engine is up to date";
            } catch (Exception e) {
                msg = "Couldn't reach GitHub. Try again later.";
            }
            String m = msg;
            runOnUiThread(() -> {
                engineUpdate.setEnabled(true);
                engineUpdate.setText("Update engine");
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
                render();
            });
        }).start();
    }

    // ---- Drawing ----

    private void render() {
        List<Haul.Item> items = Haul.items(this);
        hold.removeAllViews();
        boolean anyFinished = false;
        LayoutInflater inf = getLayoutInflater();
        for (Haul.Item i : items) {
            View row = inf.inflate(R.layout.item_haul, hold, false);
            bind(row, i);
            hold.addView(row);
            if (!i.active()) anyFinished = true;
        }
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        clear.setVisibility(anyFinished ? View.VISIBLE : View.GONE);

        String v = Engine.version(this);
        engine.setText(v.isEmpty() ? "Engine: yt-dlp" : "Engine: yt-dlp " + v);

        String newVersion = Haul.prefs(this).getString("update_version", "");
        updateCard.setVisibility(newVersion.isEmpty() ? View.GONE : View.VISIBLE);
        updateTitle.setText("Haul " + newVersion + " is out");
    }

    private void bind(View row, Haul.Item i) {
        TextView title = row.findViewById(R.id.itemTitle);
        TextView meta = row.findViewById(R.id.itemMeta);
        ProgressBar bar = row.findViewById(R.id.itemProgress);
        title.setText(i.title.isEmpty() ? shortLink(i.url) : i.title);

        String status;
        boolean bad = false;
        bar.setVisibility(View.GONE);
        switch (i.status) {
            case Haul.QUEUED:
                status = "Waiting";
                break;
            case Haul.WORKING:
                float p = Haul.progress(i.id);
                status = p < 0 ? "Looking at the link…" : "Downloading " + Math.round(p) + "%";
                bar.setVisibility(View.VISIBLE);
                bar.setIndeterminate(p < 0);
                if (p >= 0) bar.setProgress(Math.round(p));
                break;
            case Haul.DONE:
                status = i.count > 1 ? "Saved " + i.count + " files" : i.mime.startsWith("audio/") ? "Saved to Music" : "Saved";
                break;
            case Haul.CANCELED:
                status = "Canceled. Tap to try again";
                break;
            default:
                status = i.error.isEmpty() ? "Failed. Tap to try again" : i.error;
                bad = true;
        }
        meta.setText(i.site + " · " + when(i.time) + " · " + status);
        meta.setTextColor(getColor(bad ? R.color.adrift : R.color.ink_muted));

        row.setOnClickListener(v -> {
            if (Haul.DONE.equals(i.status)) play(i);
            else if (Haul.FAILED.equals(i.status) || Haul.CANCELED.equals(i.status)) HaulService.retry(this, i.id);
        });
        row.setOnLongClickListener(v -> {
            menu(i);
            return true;
        });
    }

    private void menu(Haul.Item i) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (i.active()) {
            labels.add("Cancel download");
            actions.add(() -> HaulService.cancel(this, i.id));
        } else {
            if (Haul.DONE.equals(i.status)) {
                labels.add("Share");
                actions.add(() -> share(i));
            }
            labels.add(Haul.DONE.equals(i.status) ? "Download again" : "Try again");
            actions.add(() -> HaulService.retry(this, i.id));
            labels.add("Remove from list");
            actions.add(() -> Haul.remove(this, i.id));
        }
        labels.add("Copy link");
        actions.add(() -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Link", i.url));
            Haul.prefs(this).edit().putString("last_filled", i.url).apply();   // don't paste it back into the field
        });
        labels.add("Open in browser");
        actions.add(() -> open(new Intent(Intent.ACTION_VIEW, Uri.parse(i.url))));
        new AlertDialog.Builder(this)
                .setTitle(i.title.isEmpty() ? i.site : i.title)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void play(Haul.Item i) {
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.uri), i.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    private void share(Haul.Item i) {
        Intent send = new Intent(Intent.ACTION_SEND).setType(i.mime)
                .putExtra(Intent.EXTRA_STREAM, Uri.parse(i.uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        open(Intent.createChooser(send, null));
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Nothing on this phone can open that, or the file was deleted", Toast.LENGTH_LONG).show();
        }
    }

    private String when(long time) {
        int flags = DateUtils.isToday(time) ? DateUtils.FORMAT_SHOW_TIME : DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
        return DateUtils.formatDateTime(this, time, flags);
    }

    /** "tiktok.com/@user/video/73…" without the https:// and the tracking query. */
    private static String shortLink(String url) {
        String s = url.replaceFirst("^https?://(www\\.)?", "");
        int q = s.indexOf('?');
        return q > 0 ? s.substring(0, q) : s;
    }
}
