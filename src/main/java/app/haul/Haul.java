package app.haul;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared bits: settings, the list of hauls (the hold), and links. */
final class Haul {
    static final String QUEUED = "queued", WORKING = "working", DONE = "done", FAILED = "failed", CANCELED = "canceled";
    static final String BEST = "best", SMALL = "small", AUDIO = "audio", GIF = "gif";
    static final String VIDEO = "video", PHOTOS = "photos";   // an item's kind, with AUDIO
    /** How to save a TikTok photo post: its pictures (and its sound, as a file), or a video of them with the sound. */
    static final String AS_PHOTOS = "photos", AS_SLIDESHOW = "slideshow";
    private static final int KEEP = 50;
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"'{}|\\\\^`]+", Pattern.CASE_INSENSITIVE);

    /** One link the user asked for, and what came of it. */
    static final class Item {
        String id, url, title = "", site = "", status = QUEUED, error = "", uri = "", mime = "", quality = BEST;
        String kind = "";              // VIDEO, AUDIO or PHOTOS once saved
        String pick = "";              // the chosen items of a post, as yt-dlp's -I takes them ("1,3"); "" for all
        String took = "";              // where the download's time went, from Stages ("12.4 s: start 2.1 · read 3.4 …")
        boolean sound;                 // saved photos whose last file is the post's sound (a TikTok photo post)
        String as = "";                // AS_PHOTOS or AS_SLIDESHOW for a TikTok photo post; "" otherwise
        List<String> uris = new ArrayList<>();   // every saved file; uri is the first
        int no;                        // the ticket number shown as "NO. 0148"
        int count = 0;                 // files saved; a post with several videos or photos saves several
        int clipStart = -1, clipEnd = -1;   // seconds, when only a section was asked for
        int duration = -1, height = -1;     // of the first file, when known
        long size, time, doneAt;

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("url", url).put("title", title).put("site", site)
                    .put("status", status).put("error", error).put("uri", uri).put("mime", mime)
                    .put("quality", quality).put("count", count).put("time", time).put("kind", kind)
                    .put("uris", new JSONArray(uris)).put("no", no).put("clipStart", clipStart).put("clipEnd", clipEnd)
                    .put("duration", duration).put("height", height).put("size", size).put("doneAt", doneAt)
                    .put("pick", pick).put("as", as).put("sound", sound).put("took", took);
        }

        static Item of(JSONObject o) {
            Item i = new Item();
            i.id = o.optString("id");
            i.url = o.optString("url");
            i.title = o.optString("title");
            i.site = o.optString("site");
            i.status = o.optString("status", FAILED);
            i.error = o.optString("error");
            i.uri = o.optString("uri");
            i.mime = o.optString("mime");
            i.quality = o.optString("quality", BEST);
            i.count = o.optInt("count");
            i.time = o.optLong("time");
            i.kind = o.optString("kind", i.mime.startsWith("audio/") ? AUDIO : i.mime.startsWith("image/") ? PHOTOS : VIDEO);
            JSONArray u = o.optJSONArray("uris");
            for (int k = 0; u != null && k < u.length(); k++) i.uris.add(u.optString(k));
            if (i.uris.isEmpty() && !i.uri.isEmpty()) i.uris.add(i.uri);
            i.no = o.optInt("no");
            i.clipStart = o.optInt("clipStart", -1);
            i.clipEnd = o.optInt("clipEnd", -1);
            i.duration = o.optInt("duration", -1);
            i.height = o.optInt("height", -1);
            i.size = o.optLong("size");
            i.doneAt = o.optLong("doneAt", i.time);
            i.pick = o.optString("pick");
            i.as = o.optString("as");
            i.sound = o.optBoolean("sound");
            i.took = o.optString("took");
            return i;
        }

        /** How many of the saved files are pictures: all of them, but for the sound that came with them. */
        int photos() {
            return sound ? count - 1 : count;
        }

        boolean active() {
            return QUEUED.equals(status) || WORKING.equals(status);
        }

        boolean clipped() {
            return clipStart >= 0 && clipEnd > clipStart;
        }
    }

    /** Made on first use, so the link helpers below also load in unit tests, where there is no Android main thread. */
    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Map<String, Float> progress = new ConcurrentHashMap<>();
    private static final Runnable NOTIFY = () -> {
        for (Runnable r : listeners) r.run();
    };
    private static List<Item> items;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("haul", Context.MODE_PRIVATE);
    }

    static String quality(Context c) {
        return prefs(c).getString("quality", BEST);
    }

    static void setQuality(Context c, String q) {
        prefs(c).edit().putString("quality", q).apply();
    }

    /** Settings, all off by default except the "Haul audio" share target. */
    static boolean askQuality(Context c) {
        return prefs(c).getBoolean("ask_quality", false);
    }

    static boolean wifiOnly(Context c) {
        return prefs(c).getBoolean("wifi_only", false);
    }

    static boolean audioTarget(Context c) {
        return prefs(c).getBoolean("audio_target", true);
    }

    static void set(Context c, String key, boolean on) {
        prefs(c).edit().putBoolean(key, on).apply();
        changed();
    }

    // ---- The hold: the last 50 hauls, newest first ----

    static synchronized List<Item> items(Context c) {
        return new ArrayList<>(load(c));
    }

    static synchronized Item get(Context c, String id) {
        for (Item i : load(c)) if (i.id.equals(id)) return i;
        return null;
    }

    /** A new haul for this link, with its own quality and, if wanted, a section (seconds; -1 for the whole). */
    static synchronized Item add(Context c, String url, String quality, int clipStart, int clipEnd, String pick, String as) {
        List<Item> list = load(c);
        Item i = new Item();
        i.time = System.currentTimeMillis();
        i.id = Long.toString(i.time, 36) + Integer.toString((int) (Math.random() * 1296), 36);
        i.url = url;
        i.site = site(url);
        i.quality = quality;
        i.clipStart = clipStart;
        i.clipEnd = clipEnd;
        i.pick = pick == null ? "" : pick;
        i.as = as == null ? "" : as;
        int no = prefs(c).getInt("next_no", 1);
        i.no = no;
        prefs(c).edit().putInt("next_no", no + 1).apply();
        list.add(0, i);
        // Trim the oldest finished ones; never drop one that is still going.
        for (int k = list.size() - 1; k >= 0 && list.size() > KEEP; k--) if (!list.get(k).active()) list.remove(k);
        save(c);
        return i;
    }

    static synchronized void update(Context c, String id, Consumer<Item> edit) {
        for (Item i : load(c)) {
            if (i.id.equals(id)) {
                edit.accept(i);
                save(c);
                return;
            }
        }
    }

    static synchronized void remove(Context c, String id) {
        load(c).removeIf(i -> i.id.equals(id));
        progress.remove(id);
        save(c);
    }

    static synchronized void clearFinished(Context c) {
        load(c).removeIf(i -> !i.active());
        save(c);
    }

    /**
     * In a new process nothing is downloading. If Android still holds a download job for us (waiting for a connection,
     * or about to start), the queue is kept for it; otherwise whatever was going was cut off: mark it to try again.
     */
    static synchronized void settle(Context c, boolean jobWaiting) {
        boolean changed = false;
        for (Item i : load(c)) {
            if (!i.active()) continue;
            if (jobWaiting) {
                if (WORKING.equals(i.status)) i.status = QUEUED;
            } else {
                i.status = FAILED;
                i.error = "Stopped before it finished. Tap to try again.";
            }
            changed = true;
        }
        if (changed) save(c);
    }

    /** The oldest waiting haul, now marked as working, or null when none is waiting. */
    static synchronized Item takeNext(Context c) {
        List<Item> list = load(c);
        for (int k = list.size() - 1; k >= 0; k--) {
            Item i = list.get(k);
            if (QUEUED.equals(i.status)) {
                i.status = WORKING;
                save(c);
                return i;
            }
        }
        return null;
    }

    static synchronized int waiting(Context c) {
        int n = 0;
        for (Item i : load(c)) if (QUEUED.equals(i.status)) n++;
        return n;
    }

    /** The haul for this link that is waiting or downloading, if any. */
    static synchronized Item activeFor(Context c, String url) {
        for (Item i : load(c)) if (i.active() && i.url.equals(url)) return i;
        return null;
    }

    static void setProgress(String id, float percent) {
        progress.put(id, percent);
        changed();
    }

    /** 0 to 100, or -1 while it is still looking at the link. */
    static float progress(String id) {
        Float p = progress.get(id);
        return p == null ? -1 : p;
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    static void unlisten(Runnable r) {
        listeners.remove(r);
    }

    /** Coalesces bursts (progress arrives many times a second) into one redraw. */
    private static void changed() {
        Main.HANDLER.removeCallbacks(NOTIFY);
        Main.HANDLER.postDelayed(NOTIFY, 120);
    }

    private static List<Item> load(Context c) {
        if (items != null) return items;
        items = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("hold", "[]"));
            for (int k = 0; k < a.length(); k++) items.add(Item.of(a.getJSONObject(k)));
        } catch (Exception ignored) {
        }
        return items;
    }

    private static void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) a.put(i.json());
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("hold", a.toString()).apply();
        changed();
    }

    // ---- Links ----

    /** The first web link in some text. Apps share "Watch this! https://vm.tiktok.com/abc/ #fyp", not a bare link. */
    static String firstLink(CharSequence text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        if (!m.find()) return null;
        String url = m.group();
        while (!url.isEmpty() && ".,;:!?)]".indexOf(url.charAt(url.length() - 1)) >= 0) url = url.substring(0, url.length() - 1);
        return url.isEmpty() ? null : url;
    }

    /** "TikTok", "Instagram", "X"... from the link, for the list. Unknown sites show their address. */
    static String site(String url) {
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            host = null;
        }
        if (host == null) return "Link";
        host = host.toLowerCase(Locale.ROOT).replaceFirst("^(www|m|mobile|vm|vt)\\.", "");
        if (host.contains("tiktok")) return "TikTok";
        if (host.contains("instagram")) return "Instagram";
        if (host.equals("x.com") || host.contains("twitter")) return "X";
        if (host.contains("youtube") || host.equals("youtu.be")) return "YouTube";
        if (host.contains("facebook") || host.equals("fb.watch")) return "Facebook";
        if (host.contains("reddit") || host.equals("redd.it")) return "Reddit";
        if (host.contains("snapchat")) return "Snapchat";
        if (host.contains("threads")) return "Threads";
        if (host.contains("pinterest") || host.equals("pin.it")) return "Pinterest";
        if (host.contains("vimeo")) return "Vimeo";
        return host;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private Haul() {}
}
