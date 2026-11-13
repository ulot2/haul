package app.haul;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    /** Held while yt-dlp runs or updates: replacing it in the middle of a download would break that download. */
    private static final Object RUN = new Object();
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
     * True when a newer yt-dlp was installed. Waits for a download in progress to finish first.
     * "engine_tried" is every attempt; "engine_checked" only a successful one, so being offline doesn't count as checked.
     */
    static boolean update(Context c) throws Exception {
        init(c);
        Haul.prefs(c).edit().putLong("engine_tried", System.currentTimeMillis()).apply();
        YoutubeDL.UpdateStatus s;
        synchronized (RUN) {
            s = YoutubeDL.getInstance().updateYoutubeDL(c.getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
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

    interface Progress {
        void on(float percent);
    }

    /** Empties dir, downloads everything at the link into it, and returns the finished files. */
    static List<File> download(Context c, String id, String url, String quality, File dir, Progress progress) throws Exception {
        List<File> files = fetch(c, id, url, quality, dir, progress, false);
        if (Haul.AUDIO.equals(quality)) return files;
        for (File f : files) {
            if (needsAac(f)) {
                // Rare: the site has no AAC sound at all, so the video came with Opus or Vorbis, which WhatsApp and
                // some players can't play. Fetch it again, converting the sound to AAC on the way.
                return fetch(c, id, url, quality, dir, progress, true);
            }
        }
        return files;
    }

    private static List<File> fetch(Context c, String id, String url, String quality, File dir, Progress progress,
                                    boolean toAac) throws Exception {
        init(c);
        deleteAll(dir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        r.addOption("-o", dir.getAbsolutePath() + "/%(title).70B [%(id)s].%(ext)s");
        r.addOption("--windows-filenames");      // no characters that the Gallery or Windows trip over
        r.addOption("--no-mtime");               // date the file today, so it shows at the top of the Gallery
        r.addOption("--no-playlist");            // a video in a playlist: just the video
        r.addOption("-I", "1:20");               // posts with several videos: at most 20
        if (Haul.AUDIO.equals(quality)) {
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
        DownloadProgress overall = new DownloadProgress();
        float[] shown = {-1};
        Function3<Float, Long, String, Unit> cb = (percent, eta, line) -> {
            float p = overall.on(line, percent == null ? -1 : percent);
            if (p > shown[0]) {
                shown[0] = p;
                progress.on(p);
            }
            return Unit.INSTANCE;
        };
        synchronized (RUN) {
            YoutubeDL.getInstance().execute(r, id, cb);
        }

        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : files) if (f.isFile() && f.length() > 0 && MEDIA.contains(ext(f.getName()))) out.add(f);
        }
        if (out.isEmpty()) throw new Exception("NO_FILES");
        return out;
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

    static void cancel(String id) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id);
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
            return "No video in this post. Photo posts are not supported yet.";
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
