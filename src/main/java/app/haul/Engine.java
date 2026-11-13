package app.haul;

import android.content.Context;

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
 * itself from GitHub once a day, and once more when a download fails.
 */
final class Engine {
    private static final long DAY = 24 * 3600_000L, HOUR = 3600_000L;
    private static final List<String> MEDIA = Arrays.asList(
            "mp4", "m4v", "mov", "webm", "mkv", "m4a", "mp3", "aac", "opus", "ogg", "jpg", "jpeg", "png", "webp", "gif");
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

    /** True when a newer yt-dlp was installed. */
    static boolean update(Context c) throws Exception {
        init(c);
        Haul.prefs(c).edit().putLong("engine_checked", System.currentTimeMillis()).apply();
        YoutubeDL.UpdateStatus s = YoutubeDL.getInstance().updateYoutubeDL(c.getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
        return s == YoutubeDL.UpdateStatus.DONE;
    }

    static void updateIfStale(Context c) {
        if (System.currentTimeMillis() - Haul.prefs(c).getLong("engine_checked", 0) < DAY) return;
        try {
            update(c);
        } catch (Exception ignored) {
            // offline or GitHub unreachable: the bundled or last yt-dlp still works for most links
        }
    }

    /** After a failure: worth updating and trying again, unless we only just checked. */
    static boolean updateAfterFailure(Context c) {
        if (System.currentTimeMillis() - Haul.prefs(c).getLong("engine_checked", 0) < HOUR) return false;
        try {
            return update(c);
        } catch (Exception e) {
            return false;
        }
    }

    interface Progress {
        void on(float percent);
    }

    /** Downloads everything at the link into dir (empty first) and returns the finished files. */
    static List<File> download(Context c, String id, String url, String quality, File dir, Progress progress) throws Exception {
        init(c);
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        r.addOption("-o", dir.getAbsolutePath() + "/%(title).70B [%(id)s].%(ext)s");
        r.addOption("--windows-filenames");      // no characters that the Gallery or Windows trip over
        r.addOption("--no-mtime");               // date the file today, so it shows at the top of the Gallery
        r.addOption("--no-playlist");            // a video in a playlist: just the video
        r.addOption("-I", "1:20");               // posts with several videos: at most 20
        if (Haul.AUDIO.equals(quality)) {
            r.addOption("-f", "ba/b");
            r.addOption("-x");
            r.addOption("--audio-format", "m4a");
        } else {
            // H.264 + AAC in MP4 plays everywhere (Gallery, WhatsApp status, old laptops); VP9/AV1 often does not.
            r.addOption("-f", "bv*+ba/b");
            r.addOption("-S", Haul.SMALL.equals(quality) ? "vcodec:h264,res:720,acodec:aac" : "vcodec:h264,res,acodec:aac");
            r.addOption("--merge-output-format", "mp4");
        }
        Function3<Float, Long, String, Unit> cb = (percent, eta, line) -> {
            if (percent != null && percent >= 0) progress.on(Math.min(percent, 100f));
            return Unit.INSTANCE;
        };
        YoutubeDL.getInstance().execute(r, id, cb);

        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : files) if (f.isFile() && f.length() > 0 && MEDIA.contains(ext(f.getName()))) out.add(f);
        }
        if (out.isEmpty()) throw new Exception("NO_FILES");
        return out;
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

    /** Turns yt-dlp's error into one sentence a person can act on. */
    static String friendly(Throwable e) {
        String m = String.valueOf(e.getMessage());
        String low = m.toLowerCase(Locale.ROOT);
        if (m.equals("NO_FILES") || low.contains("no video formats") || low.contains("no video could be found")
                || low.contains("there's no video") || low.contains("no media found"))
            return "No video in this post. Photo posts are not supported yet.";
        if (low.contains("unsupported url")) return "Haul can't read links from this site.";
        if (low.contains("login") || low.contains("log in") || low.contains("sign in") || low.contains("cookies")
                || low.contains("private") || low.contains("age-restricted") || low.contains("rate-limit"))
            return "This post needs a signed-in account (private, age-restricted, or the site is limiting downloads). Try again later.";
        if (low.contains("unable to download") || low.contains("timed out") || low.contains("network")
                || low.contains("connection") || low.contains("resolve") || low.contains("http error 5"))
            return "Couldn't reach the site. Check your connection and try again.";
        if (low.contains("http error 404") || low.contains("not found") || low.contains("removed") || low.contains("unavailable"))
            return "This post was deleted or isn't available.";
        if (low.contains("no space")) return "The phone is out of storage.";
        // Otherwise yt-dlp's own last "ERROR:" line, without the prefix.
        String last = "";
        for (String line : m.split("\n")) if (line.startsWith("ERROR:")) last = line.substring(6).trim();
        if (last.isEmpty()) last = m.trim();
        last = last.replaceFirst("^\\[[^\\]]+\\]\\s*([^:]+:\\s*)?", "");
        return last.length() > 160 ? last.substring(0, 157) + "…" : last;
    }

    private Engine() {}
}
