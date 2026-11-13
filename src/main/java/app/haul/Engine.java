package app.haul;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * yt-dlp on the phone, through youtubedl-android: a bundled Python runs yt-dlp, and FFmpeg joins the separate
 * video and audio streams that YouTube, X and Instagram serve. Sites change often and break yt-dlp, so it updates
 * itself from GitHub once a day (when the queue is empty, so it never delays a download), and once more when a
 * download fails.
 */
final class Engine {
    private static final long DAY = 24 * 3600_000L, HOUR = 3600_000L;
    private static final List<String> MEDIA = Arrays.asList(
            "mp4", "m4v", "mov", "webm", "mkv", "m4a", "mp3", "aac", "opus", "ogg", "jpg", "jpeg", "png", "webp", "gif");
    /** Audio that plays everywhere inside an MP4: AAC, and MP3. */
    private static final List<String> PLAYABLE_AUDIO = Arrays.asList(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_MPEG);
    /** Downloads hold it to read, an update to write: replacing yt-dlp in the middle of a download would break it. */
    private static final ReentrantReadWriteLock RUN = new ReentrantReadWriteLock();
    private static final String BROWSER =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
    private static boolean ready;

    /** Unpacks Python and FFmpeg the first time (a few seconds), then returns at once. */
    static synchronized void init(Context c) throws Exception {
        if (ready) return;
        YoutubeDL.getInstance().init(c.getApplicationContext());
        FFmpeg.getInstance().init(c.getApplicationContext());
        ready = true;
    }

    /** yt-dlp's version, like "2026.11.02", or "" before the first start. */
    static String version(Context c) {
        try {
            String v = YoutubeDL.getInstance().versionName(c.getApplicationContext());
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * True when a newer yt-dlp was installed. Waits for downloads in progress to finish first.
     * "engine_tried" is every attempt; "engine_checked" only a successful one, so being offline doesn't count as checked.
     */
    static boolean update(Context c) throws Exception {
        init(c);
        Haul.prefs(c).edit().putLong("engine_tried", System.currentTimeMillis()).apply();
        YoutubeDL.UpdateStatus s;
        RUN.writeLock().lock();
        try {
            s = YoutubeDL.getInstance().updateYoutubeDL(c.getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
        } finally {
            RUN.writeLock().unlock();
        }
        Haul.prefs(c).edit().putLong("engine_checked", System.currentTimeMillis()).apply();
        return s == YoutubeDL.UpdateStatus.DONE;
    }

    /** True when the last successful check was over a day ago, and the last try (maybe offline) over an hour ago. */
    static boolean stale(Context c) {
        long now = System.currentTimeMillis();
        return now - Haul.prefs(c).getLong("engine_checked", 0) >= DAY && now - Haul.prefs(c).getLong("engine_tried", 0) >= HOUR;
    }

    static void updateIfStale(Context c) {
        if (!stale(c)) return;
        try {
            update(c);
        } catch (Exception ignored) {
            // offline or GitHub unreachable: the bundled or last yt-dlp still works for most links
        }
    }

    /** After a failure: worth updating and trying again, unless we only just tried. */
    static boolean updateAfterFailure(Context c) {
        if (System.currentTimeMillis() - Haul.prefs(c).getLong("engine_tried", 0) < HOUR) return false;
        try {
            return update(c);
        } catch (Exception e) {
            return false;
        }
    }

    /** Each haul's yt-dlp process right now (haul id → process id), so Cancel stops the right one. */
    private static final Map<String, String> PROCESS = new ConcurrentHashMap<>();
    private static final AtomicInteger RUNS = new AtomicInteger();

    /**
     * Runs yt-dlp; any number at once, but never while it is being replaced by an update. Every run gets its own process
     * id: the library forgets an id when its process ends, so a stopped attempt that ends late would otherwise forget the
     * next attempt's id, and that attempt would then end as "canceled" though nobody canceled it.
     */
    private static YoutubeDLResponse run(YoutubeDLRequest r, String id, Function3<Float, Long, String, Unit> cb) throws Exception {
        String pid = id + "#" + RUNS.incrementAndGet();
        PROCESS.put(id, pid);
        RUN.readLock().lock();
        try {
            return cb == null ? YoutubeDL.getInstance().execute(r, pid) : YoutubeDL.getInstance().execute(r, pid, cb);
        } finally {
            RUN.readLock().unlock();
            PROCESS.remove(id, pid);
        }
    }

    // ---- Downloads ----

    /** 0 to 100; or WORKING when there is no percent to show (a clip is being cut). */
    interface Progress {
        float WORKING = -2;

        void on(float percent);
    }

    /** What a download brought in: the files, and what yt-dlp knew about the first one. */
    static final class Result {
        final List<File> files = new ArrayList<>();
        String title = "";
        int duration = -1, height = -1;
    }

    /** Empties dir, downloads everything at the link into it (a section only, with clipStart/clipEnd in seconds). */
    static Result download(Context c, String id, String url, String quality, int clipStart, int clipEnd, File dir,
                           Progress progress) throws Exception {
        Result r = fetch(c, id, url, quality, clipStart, clipEnd, dir, progress, false);
        if (Haul.AUDIO.equals(quality)) return r;
        for (File f : r.files) {
            if (needsAac(f)) {
                // Rare: the site has no AAC sound at all, so the video came with Opus or Vorbis, which WhatsApp and
                // some players can't play. Fetch it again, converting the sound to AAC on the way.
                return fetch(c, id, url, quality, clipStart, clipEnd, dir, progress, true);
            }
        }
        return r;
    }

    private static Result fetch(Context c, String id, String url, String quality, int clipStart, int clipEnd, File dir,
                                Progress progress, boolean toAac) throws Exception {
        init(c);
        deleteAll(dir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        boolean audio = Haul.AUDIO.equals(quality), clip = clipStart >= 0 && clipEnd > clipStart;
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        r.addOption("-o", dir.getAbsolutePath() + "/%(title).70B [%(id)s].%(ext)s");
        r.addOption("--windows-filenames");      // no characters that the Gallery or Windows trip over
        r.addOption("--no-mtime");               // date the file today, so it shows at the top of the Gallery
        r.addOption("--no-playlist");            // a video in a playlist: just the video
        r.addOption("-I", "1:20");               // posts with several videos or photos: at most 20
        // A photo in a post is an entry without video formats: skip it instead of failing the whole post, and write
        // each entry's details, from which the photos are then fetched.
        r.addOption("--ignore-no-formats-error");
        r.addOption("--write-info-json");
        r.addOption("--no-write-playlist-metafiles");
        if (clip) {
            // Cuts at the nearest keyframe (no re-encoding, so it is quick): may start a second or two early.
            r.addOption("--download-sections", "*" + clipStart + "-" + clipEnd);
        }
        if (audio) {
            r.addOption("-f", "ba[ext=m4a]/ba/b");   // M4A when the site has it, so it needs no converting
            r.addOption("-x");
            r.addOption("--audio-format", "m4a");
        } else {
            // H.264 + AAC in MP4 plays everywhere (Gallery, WhatsApp status, old laptops); VP9/AV1 often does not.
            r.addOption("-f", "bv*+ba/b");
            r.addOption("-S", Haul.SMALL.equals(quality) ? "vcodec:h264,res:720,acodec:aac" : "vcodec:h264,res,acodec:aac");
            r.addOption("--merge-output-format", "mp4");
            if (toAac) {
                // yt-dlp joins and remuxes with "-c copy"; a later "-c:a aac" makes FFmpeg convert only the sound.
                r.addOption("--ppa", "Merger+ffmpeg_o:-c:a aac -b:a 192k");
                r.addOption("--remux-video", "mp4");
                r.addOption("--ppa", "VideoRemuxer+ffmpeg_o:-c:a aac -b:a 192k");
            }
        }
        // yt-dlp reports each file on its own (video, then audio); add them up so the bar only moves forward.
        // A clip is cut by FFmpeg, which reports no percent.
        DownloadProgress overall = new DownloadProgress();
        float[] shown = {-1};
        Function3<Float, Long, String, Unit> cb = (percent, eta, line) -> {
            float p = overall.on(line, percent == null ? -1 : percent);
            if (clip && p >= 0) p = Progress.WORKING;
            if (p > shown[0] || (p == Progress.WORKING && shown[0] != p)) {
                shown[0] = p;
                progress.on(p);
            }
            return Unit.INSTANCE;
        };
        Exception failed = null;
        try {
            run(r, id, cb);
        } catch (Exception e) {
            if (canceled(e)) throw e;
            failed = e;   // a post may still have photos, or a slideshow behind an "unsupported" link
        }

        Result out = collect(dir);
        if (!audio) {
            savePhotos(dir, out, progress);
            if (out.files.isEmpty()) tiktokSlideshow(id, url, dir, out, progress);
        }
        if (out.files.isEmpty()) throw failed != null ? failed : new Exception("NO_FILES");
        return out;
    }

    /** The media files in dir, in name order, and the first video's details from its info JSON. */
    private static Result collect(File dir) {
        Result out = new Result();
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            if (f.isFile() && f.length() > 0 && MEDIA.contains(ext(f.getName()))) out.files.add(f);
        }
        for (JSONObject info : infos(dir)) {
            JSONArray formats = info.optJSONArray("formats");
            if (formats == null || formats.length() == 0) continue;
            out.title = info.optString("title");
            out.duration = (int) Math.round(info.optDouble("duration", -1));
            out.height = info.optInt("height", -1);
            break;
        }
        return out;
    }

    private static List<JSONObject> infos(File dir) {
        List<JSONObject> out = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".info.json"));
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            try (InputStream in = new FileInputStream(f)) {
                out.add(new JSONObject(Haul.readAll(in)));
            } catch (Exception ignored) {
                // a half-written file: skip it
            }
        }
        return out;
    }

    /** Photos in the post (entries without video), saved next to the videos as "<title> [id].jpg". */
    private static void savePhotos(File dir, Result out, Progress progress) {
        List<JSONObject> photos = new ArrayList<>();
        for (JSONObject info : infos(dir)) if (Photos.bestImage(info) != null) photos.add(info);
        boolean onlyPhotos = out.files.isEmpty();   // with videos too, the bar already reached 100
        for (int k = 0; k < photos.size(); k++) {
            JSONObject info = photos.get(k);
            String base = safeName(info.optString("title", "Photo")) + " [" + safeName(info.optString("id", "" + k)) + "]";
            File f = fetchImage(Photos.bestImage(info), info.optJSONObject("http_headers"), dir, base);
            if (f != null) {
                out.files.add(f);
                if (out.title.isEmpty()) out.title = info.optString("title");
            }
            if (onlyPhotos) progress.on(100f * (k + 1) / photos.size());
        }
    }

    /** A TikTok slideshow: yt-dlp fetches and prints the post's page, and the photos are read from it. */
    private static void tiktokSlideshow(String id, String url, File dir, Result out, Progress progress) {
        if (!"TikTok".equals(Haul.site(url))) return;
        String photoId = Photos.tiktokPhotoId(url);
        if (photoId == null) photoId = Photos.tiktokPhotoId(resolve(url));
        if (photoId == null) return;
        YoutubeDLRequest r = new YoutubeDLRequest("https://www.tiktok.com/@_/video/" + photoId);
        r.addOption("--dump-pages");
        // yt-dlp may well complain that a slideshow has no video; this keeps the library from turning that into an
        // exception, so the printed page still comes back.
        r.addOption("--ignore-errors");
        r.addOption("--skip-download");
        r.addOption("--ignore-no-formats-error");
        r.addOption("--no-playlist");
        String printed;
        try {
            printed = run(r, id, null).getOut();
        } catch (Exception e) {
            return;
        }
        JSONObject headers = new JSONObject();
        try {
            headers.put("Referer", "https://www.tiktok.com/").put("User-Agent", BROWSER);
        } catch (Exception ignored) {
        }
        for (String page : Photos.dumpedPages(printed)) {
            List<String> images = Photos.tiktokImages(page);
            for (int k = 0; k < images.size(); k++) {
                File f = fetchImage(images.get(k), headers, dir, "TikTok " + photoId + " " + (k + 1));
                if (f != null) out.files.add(f);
                progress.on(100f * (k + 1) / images.size());
            }
            if (!images.isEmpty()) {
                out.title = "TikTok photos";
                return;
            }
        }
    }

    /** Where a short link (vm.tiktok.com/…) leads; the link itself when it can't be followed. */
    private static String resolve(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "facebookexternalhit/1.1");   // what yt-dlp uses for these links
            con.setConnectTimeout(10_000);
            con.setReadTimeout(10_000);
            con.getResponseCode();
            String to = con.getURL().toString();
            con.disconnect();
            return to;
        } catch (Exception e) {
            return url;
        }
    }

    /** Downloads one photo into dir as base + its type's extension; null when it fails. */
    private static File fetchImage(String url, JSONObject headers, File dir, String base) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15_000);
            con.setReadTimeout(30_000);
            con.setRequestProperty("User-Agent", BROWSER);
            if (headers != null) {
                for (java.util.Iterator<String> k = headers.keys(); k.hasNext(); ) {
                    String name = k.next();
                    con.setRequestProperty(name, headers.optString(name));
                }
            }
            if (con.getResponseCode() / 100 != 2) return null;
            String type = String.valueOf(con.getContentType()).toLowerCase(Locale.ROOT);
            String ext = type.contains("png") ? "png" : type.contains("webp") ? "webp" : type.contains("gif") ? "gif" : "jpg";
            File f = new File(dir, base + "." + ext);
            try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
            }
            return f.length() > 0 ? f : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String safeName(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").trim();
        return t.length() > 70 ? t.substring(0, 70).trim() : t.isEmpty() ? "Photo" : t;
    }

    // ---- What the clip picker needs ----

    /** A video's title, length and picture, read without downloading it. */
    static final class Info {
        String title = "", thumbnail = "";
        int duration = -1;
    }

    static Info info(Context c, String url) throws Exception {
        init(c);
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        r.addOption("--skip-download");
        r.addOption("--no-playlist");
        r.addOption("--no-warnings");
        r.addOption("-I", "1");
        r.addOption("--print", "%(duration)s");
        r.addOption("--print", "%(thumbnail)s");
        r.addOption("--print", "%(title)s");
        String[] lines = run(r, "info-" + url.hashCode(), null).getOut().trim().split("\n");
        Info i = new Info();
        if (lines.length > 0) {
            try {
                i.duration = (int) Math.round(Double.parseDouble(lines[0].trim()));
            } catch (NumberFormatException ignored) {
                // "NA": a live stream or a site that doesn't say
            }
        }
        if (lines.length > 1 && lines[1].startsWith("http")) i.thumbnail = lines[1].trim();
        if (lines.length > 2 && !"NA".equals(lines[2].trim())) i.title = lines[2].trim();
        return i;
    }

    /** True for a video whose sound won't play everywhere (Opus, Vorbis...). Unreadable files are left alone. */
    private static boolean needsAac(File f) {
        if (!Arrays.asList("mp4", "m4v", "mov", "mkv", "webm").contains(ext(f.getName()))) return false;
        MediaExtractor x = new MediaExtractor();
        try {
            x.setDataSource(f.getAbsolutePath());
            for (int t = 0; t < x.getTrackCount(); t++) {
                String mime = x.getTrackFormat(t).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && !PLAYABLE_AUDIO.contains(mime)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            x.release();
        }
    }

    static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Stops the yt-dlp process of this haul, if one is running. */
    static void cancel(String id) {
        String pid = PROCESS.get(id);
        if (pid == null) return;
        try {
            YoutubeDL.getInstance().destroyProcessById(pid);
        } catch (Exception ignored) {
        }
    }

    static boolean canceled(Throwable e) {
        return e instanceof YoutubeDL.CanceledException;
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Turns yt-dlp's error into one sentence a person can act on. Most specific first: yt-dlp wraps nearly every
     *  HTTP failure in "Unable to download webpage: HTTP Error 404", so the plain network check has to come last. */
    static String friendly(Throwable e) {
        String m = String.valueOf(e.getMessage());
        String low = m.toLowerCase(Locale.ROOT);
        if (m.equals("NO_FILES") || has(low, "no video formats", "no video could be found", "there's no video", "no media found"))
            return "Haul found no video or photos in this post.";
        if (low.contains("unsupported url")) return "Haul can't read links from this site.";
        if (has(low, "login", "log in", "sign in", "cookies", "private", "age-restricted", "rate-limit",
                "http error 429", "too many requests"))
            return "This post needs a signed-in account (private, age-restricted, or the site is limiting downloads). Try again later.";
        if (has(low, "no space", "errno 28")) return "The phone is out of storage.";
        if (has(low, "your country", "your location", "geo restrict", "geo-restrict"))
            return "This post isn't available in your country.";
        if (has(low, "http error 404", "http error 410", "not found", "removed", "unavailable", "no longer available"))
            return "This post was deleted or isn't available.";
        if (has(low, "http error 403", "forbidden")) return "The site refused the download. Try again later.";
        if (has(low, "unable to download", "timed out", "network", "connection", "resolve", "name resolution", "http error 5"))
            return "Couldn't reach the site. Check your connection and try again.";
        // Otherwise yt-dlp's own last "ERROR:" line, without the prefix.
        String last = "";
        for (String line : m.split("\n")) if (line.startsWith("ERROR:")) last = line.substring(6).trim();
        if (last.isEmpty()) last = m.trim();
        last = last.replaceFirst("^\\[[^\\]]+\\]\\s*([^:]+:\\s*)?", "");
        return last.length() > 160 ? last.substring(0, 157) + "…" : last;
    }

    private static boolean has(String text, String... parts) {
        for (String p : parts) if (text.contains(p)) return true;
        return false;
    }

    private Engine() {}
}
