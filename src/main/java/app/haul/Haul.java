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
    /** Read, and waiting for the user to pick from the post (a TikTok photo post's pictures, an Instagram carousel). */
    static final String CHOOSE = "choose";
    static final String BEST = "best", SMALL = "small", AUDIO = "audio", GIF = "gif";
    /** A frame of a saved video, saved as a photo from the player: made on the phone, never downloaded again. */
    static final String STILL = "still";
    static final String VIDEO = "video", PHOTOS = "photos";   // an item's kind, with AUDIO
    /** How to save a TikTok photo post: its pictures (and its sound, as a file), or a video of each with the sound. */
    static final String AS_PHOTOS = "photos", AS_SLIDESHOW = "slideshow";
    /** How many hauls the hold keeps; the oldest finished ones go first. Search makes a long hold easy to look through. */
    private static final int KEEP = 200;
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"'{}|\\\\^`]+", Pattern.CASE_INSENSITIVE);

    /** One link the user asked for, and what came of it. */
    static final class Item {
        String id, url, title = "", site = "", status = QUEUED, error = "", uri = "", mime = "", quality = BEST;
        String kind = "";              // VIDEO, AUDIO or PHOTOS once saved
        String pick = "";              // the chosen items of a post, as yt-dlp's -I takes them ("1,3"); "" for all
        int seconds;                   // how long each video of a TikTok picture runs; 0 for as long as the sound
        String took = "";              // where the download's time went, from Stages ("12.4 s: start 2.1 · read 3.4 …")
        boolean sound;                 // saved photos whose last file is the post's sound (a TikTok photo post)
        String as = "";                // AS_PHOTOS or AS_SLIDESHOW for a TikTok photo post; "" otherwise
        String caption = "", by = "";  // what the post said, and who posted it; "" when unknown (or saved before Haul kept them)
        long posted;                   // when it was posted, in milliseconds; 0 when unknown
        String page = "";              // the post's own address, as the site gave it (a short link's full one)
        String source = "";            // a sound clipped from a saved file: that file (content://); "" otherwise
        long cutFrom = -1, cutTo = -1; // the clipped section of source, in milliseconds
        int split;                     // a saved video split into parts this long (seconds) for a WhatsApp Status; 0 otherwise
        boolean decide;                // shared from another app: read the post first (Reading), then download or ask
        String folder = "";            // the folder under …/Haul its files went to (Folders); "" for none
        int retries;                   // times tried again after the site limited downloads (Retry)
        long retryAt;                  // waiting until then to try again (the site is limiting downloads); 0 otherwise
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
                    .put("pick", pick).put("as", as).put("sound", sound).put("took", took).put("seconds", seconds)
                    .put("caption", caption).put("by", by).put("posted", posted).put("page", page)
                    .put("source", source).put("cutFrom", cutFrom).put("cutTo", cutTo).put("split", split).put("decide", decide)
                    .put("retries", retries).put("retryAt", retryAt).put("folder", folder);
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
            i.seconds = o.optInt("seconds");
            i.caption = o.optString("caption");
            i.by = o.optString("by");
            i.posted = o.optLong("posted");
            i.page = o.optString("page");
            i.source = o.optString("source");
            i.cutFrom = o.optLong("cutFrom", -1);
            i.cutTo = o.optLong("cutTo", -1);
            i.split = o.optInt("split");
            i.decide = o.optBoolean("decide");
            i.retries = o.optInt("retries");
            i.retryAt = o.optLong("retryAt");
            i.folder = o.optString("folder");
            return i;
        }

        /** How many of the saved files are pictures: all of them, but for the sound that came with them. */
        int photos() {
            return sound ? count - 1 : count;
        }

        boolean active() {
            return QUEUED.equals(status) || WORKING.equals(status);
        }

        /** Still to be dealt with: going, waiting, or waiting for the user to pick. Never trimmed from the hold. */
        boolean held() {
            return active() || CHOOSE.equals(status);
        }

        boolean clipped() {
            return clipStart >= 0 && clipEnd > clipStart;
        }

        /** A saved video split into parts for a WhatsApp Status: made on the phone, no site involved. */
        boolean splitting() {
            return !source.isEmpty() && split > 0;
        }

        /** Made on the phone from a file already saved (a clipped sound, a split video): never downloaded again. */
        boolean madeHere() {
            return cut() || splitting() || STILL.equals(quality) || Statuses.isStatus(url);
        }

        /** A sound clipped from a file already saved, not a download: made on the phone, no site involved. */
        boolean cut() {
            return !source.isEmpty() && cutFrom >= 0 && cutTo > cutFrom;
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

    /** Where new downloads go: Folders.ONE (all together), SITE or CREATOR. */
    static String folders(Context c) {
        return prefs(c).getString("folders", Folders.ONE);
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

    // ---- The hold: the last 200 hauls, newest first ----

    static synchronized List<Item> items(Context c) {
        return new ArrayList<>(load(c));
    }

    static synchronized Item get(Context c, String id) {
        for (Item i : load(c)) if (i.id.equals(id)) return i;
        return null;
    }

    /** A new haul for this link, with its own quality and, if wanted, a section (seconds; -1 for the whole). */
    static synchronized Item add(Context c, String url, String quality, int clipStart, int clipEnd, String pick, String as, int seconds) {
        return add(c, url, quality, clipStart, clipEnd, pick, as, seconds, null);
    }

    /** The same, with more set up (setup, when given) before the queue can see it. */
    static synchronized Item add(Context c, String url, String quality, int clipStart, int clipEnd, String pick, String as, int seconds,
                                 Consumer<Item> setup) {
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
        i.seconds = Math.max(0, seconds);
        int no = prefs(c).getInt("next_no", 1);
        i.no = no;
        if (setup != null) setup.accept(i);
        prefs(c).edit().putInt("next_no", no + 1).apply();
        list.add(0, i);
        // Trim the oldest finished ones; never drop one that is still going.
        for (int k = list.size() - 1; k >= 0 && list.size() > KEEP; k--) if (!list.get(k).held()) list.remove(k);
        save(c);
        return i;
    }

    // ---- Backup and restore ----

    static final String FROM_BACKUP = "From your backup. Tap to download it again.";

    /** The whole hold as a backup file's text: {"haul":1,"at":…,"items":[…]}. */
    static synchronized String backup(Context c) throws Exception {
        JSONArray a = new JSONArray();
        for (Item i : load(c)) a.put(i.json());
        return new JSONObject().put("haul", 1).put("at", System.currentTimeMillis()).put("items", a).toString();
    }

    /**
     * A haul from a backup, as it comes back: saved, when its file is on this phone (fileThere); else waiting to be
     * downloaded again with a tap (FROM_BACKUP). Null for one that can't come back without its file: a WhatsApp status,
     * a still, a clipped sound, which were made on the old phone, not downloaded.
     */
    static Item restored(Item i, boolean fileThere) {
        if (i == null || i.id == null || i.id.isEmpty() || i.url == null || i.url.isEmpty()) return null;
        if (DONE.equals(i.status) && fileThere) return i;
        if (Statuses.isStatus(i.url) || STILL.equals(i.quality) || !i.source.isEmpty()) return null;
        i.status = FAILED;
        i.error = FROM_BACKUP;
        i.decide = false;
        i.retries = 0;
        i.retryAt = 0;
        i.uri = "";
        i.uris = new ArrayList<>();
        return i;
    }

    /**
     * Brings a backup's hauls into the hold: those it hasn't already (by id), newest first, keeping the last KEEP.
     * fileThere tells whether a saved haul's first file is on this phone. Returns how many came back.
     */
    static synchronized int restore(Context c, String text, java.util.function.Predicate<Item> fileThere) throws Exception {
        JSONArray a = new JSONObject(text).getJSONArray("items");
        List<Item> list = load(c);
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Item i : list) have.add(i.id);
        int n = 0;
        for (int k = 0; k < a.length(); k++) {
            Item raw = Item.of(a.getJSONObject(k));
            if (have.contains(raw.id)) continue;
            Item back = restored(raw, DONE.equals(raw.status) && fileThere.test(raw));
            if (back == null) continue;
            list.add(back);
            have.add(back.id);
            n++;
        }
        list.sort((x, y) -> Long.compare(y.time, x.time));
        for (int k = list.size() - 1; k >= 0 && list.size() > KEEP; k--) if (!list.get(k).held()) list.remove(k);
        if (n > 0) save(c);
        return n;
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

    /** The oldest waiting haul, now marked as working, or null when none is waiting. Links still to be read wait for takeReading. */
    static synchronized Item takeNext(Context c) {
        List<Item> list = load(c);
        for (int k = list.size() - 1; k >= 0; k--) {
            Item i = list.get(k);
            if (QUEUED.equals(i.status) && !i.decide && i.retryAt <= System.currentTimeMillis()) {
                i.status = WORKING;
                save(c);
                return i;
            }
        }
        return null;
    }

    /** The oldest link waiting to be read (shared from another app), now marked as working, or null. */
    static synchronized Item takeReading(Context c) {
        List<Item> list = load(c);
        for (int k = list.size() - 1; k >= 0; k--) {
            Item i = list.get(k);
            if (QUEUED.equals(i.status) && i.decide) {
                i.status = WORKING;
                save(c);
                return i;
            }
        }
        return null;
    }

    /** How many shared links are being read or wait to be, and how many wait for the user to pick: for the badge. */
    static synchronized int[] reading(Context c) {
        int read = 0, pick = 0;
        for (Item i : load(c)) {
            if (i.decide && i.active()) read++;
            if (CHOOSE.equals(i.status)) pick++;
        }
        return new int[]{read, pick};
    }

    /** The oldest haul waiting for the user to pick from its post, or null. */
    static synchronized Item firstToChoose(Context c) {
        List<Item> list = load(c);
        for (int k = list.size() - 1; k >= 0; k--) if (CHOOSE.equals(list.get(k).status)) return list.get(k);
        return null;
    }

    /** The soonest time a download waits for to try again (the site was limiting downloads), or 0 for none. */
    static synchronized long nextRetry(Context c) {
        long soonest = 0;
        for (Item i : load(c)) {
            if (!QUEUED.equals(i.status) || i.retryAt <= 0) continue;
            if (soonest == 0 || i.retryAt < soonest) soonest = i.retryAt;
        }
        return soonest;
    }

    static synchronized int waiting(Context c) {
        int n = 0;
        for (Item i : load(c)) if (QUEUED.equals(i.status)) n++;
        return n;
    }

    /**
     * The haul for this link that is waiting or downloading, if any: the same items of it, saved the same way. (The
     * pictures of one TikTok post, each as its own video, are hauls of one link, and wait side by side.)
     */
    static synchronized Item activeFor(Context c, String url, String pick, String as) {
        for (Item i : load(c)) if (i.active() && i.url.equals(url) && i.pick.equals(pick) && i.as.equals(as)) return i;
        return null;
    }

    /**
     * The newest saved haul of the same post, saved the same way (as sound, as a GIF, or as video and photos), or null.
     * A clip is a section picked on purpose, so it doesn't count either way.
     */
    static synchronized Item saved(Context c, String url, String quality) {
        for (Item i : load(c)) {
            if (!DONE.equals(i.status) || i.clipped() || i.madeHere() || !sameWay(i.quality, quality)) continue;
            if (Seen.same(url, i.url) || Seen.same(url, i.page)) return i;
        }
        return null;
    }

    /** Best and 720p are both "the video"; the sound alone and a GIF are other things to keep. */
    static boolean sameWay(String a, String b) {
        boolean audioA = AUDIO.equals(a), audioB = AUDIO.equals(b), gifA = GIF.equals(a), gifB = GIF.equals(b);
        boolean stillA = STILL.equals(a), stillB = STILL.equals(b);
        return audioA == audioB && gifA == gifB && stillA == stillB;
    }

    /** True when the search finds this haul: in its title, caption, creator, site, link or ticket number ("148"). */
    static boolean matches(Item i, String query) {
        return Find.matches(query, i.title, i.caption, i.by, i.site, i.url, i.page, "No. " + Format.ticket(i.no));
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
        while (m.find()) {
            String url = trim(m.group());
            if (!url.isEmpty()) return url;
        }
        return null;
    }

    /**
     * Every link in some text, in order, each post once (the same post under two links counts once): a list pasted
     * from notes or a chat. Empty when there is none.
     */
    static List<String> links(CharSequence text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        java.util.Set<String> posts = new java.util.HashSet<>();
        Matcher m = LINK.matcher(text);
        while (m.find()) {
            String url = trim(m.group());
            if (!url.isEmpty() && posts.add(Seen.key(url))) out.add(url);
        }
        return out;
    }

    /** How long links collected in the dock wait for more: copied later than this after the last one, a link starts afresh. */
    static final long COLLECT_FOR = 30 * 60 * 1000L;
    /** At most this many links wait in the dock; the oldest go first. */
    static final int COLLECT_MAX = 30;

    /**
     * The dock's links once more were copied: those waiting, then the new ones, each post once. Copied in TikTok one by
     * one, links can only be read when Haul is back on screen (Android allows no other way), so the dock gathers them.
     * A link already sent from the dock (handled) isn't taken again; a post already waiting keeps its place.
     */
    static List<String> collect(List<String> waiting, List<String> copied, List<String> handled) {
        List<String> out = new ArrayList<>(waiting);
        java.util.Set<String> posts = new java.util.HashSet<>();
        for (String w : waiting) posts.add(Seen.key(w));
        java.util.Set<String> done = new java.util.HashSet<>();
        for (String h : handled) done.add(Seen.key(h));
        for (String c : copied) {
            String key = Seen.key(c);
            if (key.isEmpty() || done.contains(key) || !posts.add(key)) continue;
            out.add(c);
        }
        while (out.size() > COLLECT_MAX) out.remove(0);
        return out;
    }

    /** A link without the punctuation that ends the sentence around it: "(see https://x.com/a/status/1)." */
    private static String trim(String url) {
        while (!url.isEmpty() && ".,;:!?)]".indexOf(url.charAt(url.length() - 1)) >= 0) url = url.substring(0, url.length() - 1);
        return url;
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
