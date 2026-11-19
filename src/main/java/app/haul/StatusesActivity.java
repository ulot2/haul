package app.haul;

import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp's page in Haul: the statuses the user has opened in WhatsApp (or WhatsApp Business), from the folder
 * where WhatsApp keeps them for about a day. Reading it takes the user's say-so once, in Android's folder picker,
 * which Haul opens inside that folder; Android remembers it. A tap saves a status into the Gallery (and the hold,
 * like a download, with Play, Share and Delete); a saved one opens in Haul's player.
 */
public class StatusesActivity extends Activity {
    private static final int REQ_FOLDER = 7;
    private static final int[] TONES = {R.color.tone0, R.color.tone1, R.color.tone2, R.color.tone3, R.color.tone4, R.color.tone5};

    /** One file in the statuses folder. */
    private static final class Status {
        String name, uri, mime, kind;
        long modified;
    }

    private String app = Statuses.WHATSAPP;
    private final List<Status> statuses = new ArrayList<>();
    /** Saved statuses: their link in the hold → the haul. */
    private final Map<String, Haul.Item> saved = new HashMap<>();
    private Button chipWhatsApp, chipBusiness, saveAll;
    private View connect, bar;
    private TextView connectTitle, connectBody, count, empty;
    private LinearLayout grid;
    private boolean saving;
    /** Bumped on every load, so a slow read of the other tab doesn't draw over this one. */
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_statuses);
        MainActivity.edgeToEdge(this);
        View content = findViewById(R.id.stContent);
        int top = content.getPaddingTop(), bottom = content.getPaddingBottom();
        findViewById(R.id.stScroll).setOnApplyWindowInsetsListener((v, insets) -> {
            int[] b = MainActivity.bars(insets);
            content.setPadding(content.getPaddingLeft(), top + b[1], content.getPaddingRight(), bottom + b[3]);
            v.setPadding(b[0], 0, b[2], 0);
            return insets;
        });
        findViewById(R.id.stBack).setOnClickListener(v -> finish());
        chipWhatsApp = findViewById(R.id.stWhatsApp);
        chipBusiness = findViewById(R.id.stBusiness);
        connect = findViewById(R.id.stConnect);
        connectTitle = findViewById(R.id.stConnectTitle);
        connectBody = findViewById(R.id.stConnectBody);
        bar = findViewById(R.id.stBar);
        count = findViewById(R.id.stCount);
        saveAll = findViewById(R.id.stSaveAll);
        empty = findViewById(R.id.stEmpty);
        grid = findViewById(R.id.stGrid);

        // The last tab used, so someone who only has WhatsApp Business lands on it.
        app = Haul.prefs(this).getString("statuses_app", Statuses.WHATSAPP);
        chipWhatsApp.setOnClickListener(v -> show(Statuses.WHATSAPP));
        chipBusiness.setOnClickListener(v -> show(Statuses.BUSINESS));
        findViewById(R.id.stConnectGo).setOnClickListener(v -> pickFolder());
        saveAll.setOnClickListener(v -> saveAll());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();   // back from WhatsApp with new statuses, or from the player
    }

    private void show(String which) {
        app = which;
        Haul.prefs(this).edit().putString("statuses_app", which).apply();
        load();
    }

    // ---- Access to the folder ----

    /** The folder Android lets Haul read for this WhatsApp, or null when the user hasn't allowed one (or took it back). */
    private Uri folder() {
        String kept = Haul.prefs(this).getString("statuses_tree_" + app, "");
        if (kept.isEmpty()) return null;
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (p.isReadPermission() && p.getUri().toString().equals(kept)) return p.getUri();
        }
        return null;
    }

    private void pickFolder() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(Statuses.pickerStart(app)))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, REQ_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "This phone has no folder picker Haul can open", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_FOLDER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        String id;
        try {
            id = DocumentsContract.getTreeDocumentId(tree);
        } catch (Exception e) {
            id = null;
        }
        if (!Statuses.isStatusFolder(id)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("That isn't the statuses folder")
                    .setMessage("Tap Allow access again, then Use this folder without moving: the picker opens in the "
                            + "right place. If it doesn't, open " + Statuses.name(app) + ", look at a status, and try again.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Android didn't keep the access. Try again.", Toast.LENGTH_LONG).show();
            return;
        }
        String which = Statuses.appOf(id);
        Haul.prefs(this).edit().putString("statuses_tree_" + which, tree.toString()).apply();
        show(which);
    }

    // ---- Reading and drawing ----

    private void load() {
        chipWhatsApp.setActivated(Statuses.WHATSAPP.equals(app));
        chipBusiness.setActivated(Statuses.BUSINESS.equals(app));
        int gen = ++generation;
        Uri tree = folder();
        if (tree == null) {
            statuses.clear();
            grid.removeAllViews();
            bar.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            connect.setVisibility(View.VISIBLE);
            boolean had = !Haul.prefs(this).getString("statuses_tree_" + app, "").isEmpty();
            connectTitle.setText(had ? "Haul can't see " + Statuses.name(app) + "'s statuses any more"
                    : "Let Haul see your " + Statuses.name(app) + " statuses");
            connectBody.setText("Android asks you once. Tap Allow access: a folder picker opens in "
                    + Statuses.name(app) + "'s statuses folder. Tap Use this folder, then Allow. Haul only reads that folder.");
            return;
        }
        connect.setVisibility(View.GONE);
        String which = app;
        new Thread(() -> {
            List<Status> found = list(tree);
            Map<String, Haul.Item> have = new HashMap<>();
            for (Haul.Item i : Haul.items(this)) {
                if (Statuses.isStatus(i.url) && Haul.DONE.equals(i.status) && Downloads.stillThere(this, i)) have.put(i.url, i);
            }
            runOnUiThread(() -> {
                if (gen != generation || isFinishing() || !which.equals(app)) return;
                statuses.clear();
                if (found != null) statuses.addAll(found);
                saved.clear();
                saved.putAll(have);
                draw(found == null);
            });
        }, "haul-statuses").start();
    }

    /** The statuses in the folder, newest first; null when the folder can't be read (gone, or access taken back). */
    private List<Status> list(Uri tree) {
        List<Status> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        try (Cursor cur = getContentResolver().query(children, cols, null, null, null)) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                Status s = new Status();
                s.name = cur.getString(1);
                s.kind = Statuses.kindOf(s.name, cur.getString(2));
                if (s.kind == null) continue;
                s.mime = Statuses.mimeOf(s.name, cur.getString(2));
                s.uri = DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)).toString();
                s.modified = cur.isNull(3) ? 0 : cur.getLong(3);
                out.add(s);
            }
        } catch (Exception e) {
            return null;
        }
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    private void draw(boolean unreadable) {
        grid.removeAllViews();
        if (unreadable) {
            bar.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            empty.setText("Haul couldn't read " + Statuses.name(app) + "'s statuses folder. If " + Statuses.name(app)
                    + " was reinstalled, or the folder was moved, allow access again.");
            connect.setVisibility(View.VISIBLE);
            connectTitle.setText("Allow access again");
            connectBody.setText("The folder picker opens in " + Statuses.name(app) + "'s statuses folder. Tap Use this folder, then Allow.");
            return;
        }
        if (statuses.isEmpty()) {
            bar.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            empty.setText("No statuses right now. Open " + Statuses.name(app) + ", look at the statuses you want, "
                    + "then come back. WhatsApp clears them after about a day.");
            return;
        }
        empty.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE);
        int fresh = 0;
        for (Status s : statuses) if (!saved.containsKey(Statuses.link(app, s.name))) fresh++;
        count.setText(statuses.size() + (statuses.size() == 1 ? " status" : " statuses")
                + (statuses.size() - fresh > 0 ? " · " + (statuses.size() - fresh) + " saved" : ""));
        saveAll.setEnabled(fresh > 0 && !saving);
        saveAll.setAlpha(fresh > 0 && !saving ? 1f : 0.4f);
        saveAll.setText(fresh > 0 ? "Save all · " + fresh : "All saved");

        int gap = Sheets.dp(this, 8);
        int width = (getResources().getDisplayMetrics().widthPixels - Sheets.dp(this, 40) - 2 * gap) / 3;
        int height = width * 16 / 9;   // statuses are made for a phone held upright
        LinearLayout row = null;
        for (int k = 0; k < statuses.size(); k++) {
            if (k % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (k > 0) lp.topMargin = gap;
                grid.addView(row, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            if (k % 3 > 0) lp.leftMargin = gap;
            row.addView(tile(statuses.get(k), width, height), lp);
        }
    }

    private View tile(Status s, int width, int height) {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundResource(R.drawable.bg_thumb);
        box.setBackgroundTintList(ColorStateList.valueOf(getColor(TONES[Math.abs(s.name.hashCode()) % TONES.length])));
        box.setClipToOutline(true);
        String link = Statuses.link(app, s.name);
        Haul.Item had = saved.get(link);

        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(picture, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (!Haul.AUDIO.equals(s.kind)) Thumbs.showStatus(this, picture, s.uri, Haul.VIDEO.equals(s.kind), width, height);

        // What it is, at the bottom: a video, or a voice status (which has no picture, so its sign sits in the middle).
        if (!Haul.PHOTOS.equals(s.kind)) {
            ImageView sign = new ImageView(this);
            sign.setImageResource(Haul.AUDIO.equals(s.kind) ? R.drawable.ic_music : R.drawable.ic_play);
            sign.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            sign.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = Sheets.dp(this, Haul.AUDIO.equals(s.kind) ? 32 : 20);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size,
                    Haul.AUDIO.equals(s.kind) ? Gravity.CENTER : Gravity.BOTTOM | Gravity.START);
            lp.setMargins(Sheets.dp(this, 8), 0, 0, Sheets.dp(this, 8));
            if (Haul.AUDIO.equals(s.kind)) lp.setMargins(0, 0, 0, 0);
            box.addView(sign, lp);
        }
        if (had != null) {
            ImageView check = new ImageView(this);
            check.setBackgroundResource(R.drawable.bg_check_on);
            check.setImageResource(R.drawable.ic_check);
            check.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            check.setPadding(Sheets.dp(this, 4), Sheets.dp(this, 4), Sheets.dp(this, 4), Sheets.dp(this, 4));
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = Sheets.dp(this, 24);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
            lp.setMargins(0, Sheets.dp(this, 8), Sheets.dp(this, 8), 0);
            box.addView(check, lp);
        }
        String what = Haul.VIDEO.equals(s.kind) ? "Video" : Haul.AUDIO.equals(s.kind) ? "Voice status" : "Photo";
        box.setContentDescription(what + (s.modified > 0 ? ", " + DateUtils.getRelativeTimeSpanString(s.modified) : "")
                + (had != null ? ", saved. Tap to open." : ". Tap to save."));
        box.setOnClickListener(v -> {
            if (had != null) {
                startActivity(PlayerActivity.open(this, had.id, 0));
            } else if (!saving) {
                save(java.util.Collections.singletonList(s));
            }
        });
        return box;
    }

    // ---- Saving ----

    private void saveAll() {
        List<Status> fresh = new ArrayList<>();
        for (Status s : statuses) if (!saved.containsKey(Statuses.link(app, s.name))) fresh.add(s);
        if (!fresh.isEmpty()) save(fresh);
    }

    /** Copies the statuses into the Gallery, each a haul of its own, then redraws with their ✓. */
    private void save(List<Status> list) {
        saving = true;
        saveAll.setEnabled(false);
        saveAll.setAlpha(0.4f);
        String which = app;
        new Thread(() -> {
            int ok = 0;
            String failed = null;
            for (Status s : list) {
                try {
                    saveOne(which, s);
                    ok++;
                } catch (Exception e) {
                    failed = String.valueOf(e.getMessage());
                }
            }
            int done = ok;
            String why = failed;
            runOnUiThread(() -> {
                saving = false;
                if (why != null && done == 0) {
                    Toast.makeText(this, why.contains("no space") ? "The phone is out of storage."
                            : "Couldn't save: it may be gone already (WhatsApp clears statuses after a day).", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, (done == 1 ? "Saved" : "Saved " + done + " statuses") + " to your Gallery"
                            + (why != null ? ". Some couldn't be saved." : ""), Toast.LENGTH_SHORT).show();
                }
                load();
            });
        }, "haul-status-save").start();
    }

    private void saveOne(String which, Status s) throws Exception {
        Uri from = Uri.parse(s.uri);
        long size;
        Uri to;
        try (InputStream in = getContentResolver().openInputStream(from)) {
            if (in == null) throw new Exception("gone");
            // "Status 2026-11-19 14.02.10.mp4": when WhatsApp got it, and never the same name twice.
            String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT)
                    .format(new java.util.Date(s.modified > 0 ? s.modified : System.currentTimeMillis()));
            String ext = s.name.contains(".") ? s.name.substring(s.name.lastIndexOf('.')) : "";
            to = Downloads.toGallery(this, in, "Status " + when + " " + s.name.replaceFirst("\\.[^.]+$", "") + ext, s.mime);
        }
        size = 0;
        try (Cursor cur = getContentResolver().query(to, new String[]{android.provider.MediaStore.MediaColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) size = cur.getLong(0);
        } catch (Exception ignored) {
        }
        int seconds = -1, height = -1;
        if (!Haul.PHOTOS.equals(s.kind)) {
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            try {
                r.setDataSource(this, to);
                String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) seconds = (int) Math.round(Long.parseLong(d) / 1000.0);
                String h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                // The smaller side, as "720p" means for a video held either way.
                if (h != null && w != null) height = Math.min(Integer.parseInt(h), Integer.parseInt(w));
            } catch (Exception ignored) {
            } finally {
                try {
                    r.release();
                } catch (Exception ignored) {
                }
            }
        }
        String title = Statuses.name(which) + " status · " + DateUtils.formatDateTime(this, s.modified > 0 ? s.modified : System.currentTimeMillis(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME);
        String uri = to.toString();
        long bytes = size;
        int length = seconds, tall = height;
        Haul.add(this, Statuses.link(which, s.name), Haul.AUDIO.equals(s.kind) ? Haul.AUDIO : Haul.BEST, -1, -1, "", "", 0, i -> {
            i.status = Haul.DONE;
            i.site = "WhatsApp";
            i.title = title;
            i.kind = s.kind;
            i.mime = s.mime;
            i.uri = uri;
            i.uris = new ArrayList<>(java.util.Collections.singletonList(uri));
            i.count = 1;
            i.size = bytes;
            i.duration = length;
            i.height = Haul.VIDEO.equals(s.kind) ? tall : -1;
            i.doneAt = System.currentTimeMillis();
        });
    }
}
