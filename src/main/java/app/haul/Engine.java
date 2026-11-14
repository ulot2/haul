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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    static final String BROWSER =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
    private static boolean ready;

    /** Unpacks Python and FFmpeg the first time (a few seconds), then returns at once. */
    static synchronized void init(Context c) throws Exception {
        if (ready) return;
        YoutubeDL.getInstance().init(c.getApplicationContext());
        FFmpeg.getInstance().init(c.getApplicationContext());
        findFfmpeg(c.getApplicationContext());
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

    /**
     * 0 to 100 once there is a percent. Before that, what is going on: STARTING (Python and yt-dlp are starting up on the
     * phone), READING (yt-dlp is reading the link: the site's page and the video's details), WORKING (busy, with no
     * percent to show: a clip being cut, a slideshow being made).
     */
    interface Progress {
        float STARTING = -1, WORKING = -2, READING = -3;

        void on(float percent);
    }

    /** What a download brought in: the files, and what yt-dlp knew about the first one. */
    static final class Result {
        final List<File> files = new ArrayList<>();
        String title = "";
        int duration = -1, height = -1;
    }

    /** Empties dir, downloads the haul into it: the link (or its chosen items, or a section), or a TikTok photo post. */
    static Result download(Context c, Haul.Item item, File dir, Progress progress) throws Exception {
        if (!item.as.isEmpty()) return tiktokPhotos(c, item, dir, progress);
        Result r = fetch(c, item, dir, progress, false);
        if (Haul.AUDIO.equals(item.quality)) return r;
        if (!Haul.GIF.equals(item.quality)) {
            boolean any = false;
            for (File f : r.files) any |= needsAac(f);
            if (any) {
                // Rare: the site has no AAC sound at all, so the video came with Opus or Vorbis, which WhatsApp and
                // some players can't play. Convert the sound here, keeping the picture as it came; only if that
                // fails, fetch the video again, converting on the way.
                try {
                    toAac(item, dir, r, progress);
                } catch (Exception e) {
                    if (canceled(e) || e instanceof InterruptedException) throw e;
                    r = fetch(c, item, dir, progress, true);
                }
            }
        }
        return gifs(item, dir, r, progress);
    }

    /** Gives each video whose sound won't play everywhere AAC sound, in an MP4 of the same name; the picture is copied. */
    private static void toAac(Haul.Item item, File dir, Result r, Progress progress) throws Exception {
        for (int k = 0; k < r.files.size(); k++) {
            File f = r.files.get(k);
            if (!needsAac(f)) continue;
            progress.on(Progress.WORKING);
            String base = f.getName().replaceFirst("\\.[^.]+$", "");
            File made = new File(dir, base + ".aac.mp4"), named = new File(dir, base + ".mp4");
            ffmpeg(item.id, new ArrayList<>(Arrays.asList(ffmpegBinary.getAbsolutePath(), "-y", "-hide_banner", "-loglevel", "error",
                    "-i", f.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                    "-movflags", "+faststart", made.getAbsolutePath())), made);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!made.renameTo(named)) throw new Exception(MADE + "couldn't rename the converted video");
            r.files.set(k, named);
        }
    }

    private static Result fetch(Context c, Haul.Item item, File dir, Progress progress, boolean toAac) throws Exception {
        init(c);
        deleteAll(dir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String quality = item.quality;
        boolean audio = Haul.AUDIO.equals(quality), clip = item.clipped();
        String url = item.url;
        if (audio && Photos.tiktokPhotoId(url) != null) {
            // The sound of a TikTok photo post: yt-dlp reads those only by their /video/ address.
            url = "https://www.tiktok.com/@_/video/" + Photos.tiktokPhotoId(url);
        }
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        // Read ahead: when Haul already read this link (while the copied link sat in the dock, or for the carousel
        // picker), yt-dlp starts from those details instead of asking the site again. Stale details make yt-dlp
        // fall back to the link by itself.
        awaitAhead(c, item, progress);
        File ahead = Ahead.get(c, item.url);
        if (ahead != null) r.addOption("--load-info-json", ahead.getAbsolutePath());
        r.addOption("-o", dir.getAbsolutePath() + "/%(title).70B [%(id)s].%(ext)s");
        r.addOption("--windows-filenames");      // no characters that the Gallery or Windows trip over
        r.addOption("--no-mtime");               // date the file today, so it shows at the top of the Gallery
        r.addOption("--no-playlist");            // a video in a playlist: just the video
        // Posts with several videos or photos: the chosen ones, or at most 20.
        r.addOption("-I", item.pick.isEmpty() ? "1:20" : item.pick);
        r.addOption("--concurrent-fragments", "4");   // X and YouTube serve video in pieces: fetch 4 at a time
        // A photo in a post is an entry without video formats: skip it instead of failing the whole post, and write
        // each entry's details, from which the photos are then fetched.
        r.addOption("--ignore-no-formats-error");
        r.addOption("--write-info-json");
        r.addOption("--no-write-playlist-metafiles");
        if (clip) {
            // Cuts at the nearest keyframe (no re-encoding, so it is quick): may start a second or two early.
            r.addOption("--download-sections", "*" + item.clipStart + "-" + item.clipEnd);
        }
        if (audio) {
            r.addOption("-f", "ba[ext=m4a]/ba/b");   // M4A when the site has it, so it needs no converting
            r.addOption("-x");
            r.addOption("--audio-format", "m4a");
        } else {
            // H.264 + AAC in MP4 plays everywhere (Gallery, WhatsApp status, old laptops); VP9/AV1 often does not.
            // For a GIF, a small video without sound is all that's needed.
            boolean gif = Haul.GIF.equals(quality);
            r.addOption("-f", gif ? "bv*/b" : "bv*+ba/b");
            r.addOption("-S", gif ? "res:720" : Haul.SMALL.equals(quality) ? "vcodec:h264,res:720,acodec:aac" : "vcodec:h264,res,acodec:aac");
            r.addOption("--merge-output-format", "mp4");
            if (toAac) {
                // yt-dlp joins and remuxes with "-c copy"; a later "-c:a aac" makes FFmpeg convert only the sound.
                r.addOption("--ppa", "Merger+ffmpeg_o:-c:a aac -b:a 192k");
                r.addOption("--remux-video", "mp4");
                r.addOption("--ppa", "VideoRemuxer+ffmpeg_o:-c:a aac -b:a 192k");
            }
        }
        Exception failed = null;
        try {
            run(r, item.id, callback(progress, clip));
        } catch (Exception e) {
            if (canceled(e)) throw e;
            failed = e;   // a post may still have photos, or a slideshow behind an "unsupported" link
        }

        Result out = collect(dir);
        if (!audio) {
            savePhotos(dir, out, progress);
            if (out.files.isEmpty() && "TikTok".equals(item.site)) {
                // A TikTok photo post added without asking (from the tile, or before Haul knew): its pictures and sound.
                Haul.Item photos = copy(item);
                photos.as = Haul.AS_PHOTOS;
                try {
                    return tiktokPhotos(c, photos, dir, progress);
                } catch (Exception e) {
                    if (canceled(e)) throw e;
                }
            }
        }
        if (out.files.isEmpty()) throw failed != null ? failed : new Exception("NO_FILES");
        return out;
    }

    /**
     * Reports what yt-dlp is doing: READING from its first line of output (until then Python and yt-dlp are still
     * starting), then one percent for the whole link. yt-dlp counts each file from 0 to 100 on its own (video, then
     * audio); DownloadProgress adds them up so the bar only moves forward. A clip is cut by FFmpeg, which reports no
     * percent, so it shows WORKING.
     */
    private static Function3<Float, Long, String, Unit> callback(Progress progress, boolean clip) {
        DownloadProgress overall = new DownloadProgress();
        float[] shown = {Progress.STARTING};
        return (percent, eta, line) -> {
            float p = overall.on(line, percent == null ? -1 : percent);
            if (clip && p >= 0) p = Progress.WORKING;
            if (p < 0 && p != Progress.WORKING) p = Progress.READING;
            if (p != shown[0] && (p >= shown[0] || shown[0] < 0)) {
                shown[0] = p;
                progress.on(p);
            }
            return Unit.INSTANCE;
        };
    }

    private static Haul.Item copy(Haul.Item i) {
        Haul.Item x = new Haul.Item();
        x.id = i.id;
        x.url = i.url;
        x.site = i.site;
        x.quality = i.quality;
        return x;
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
        List<Want> wants = new ArrayList<>();
        for (int k = 0; k < photos.size(); k++) {
            JSONObject info = photos.get(k);
            String base = safeName(info.optString("title", "Photo")) + " [" + safeName(info.optString("id", "" + k)) + "]";
            wants.add(new Want(Photos.bestImage(info), info.optJSONObject("http_headers"), base));
        }
        List<File> got = fetchImages(wants, dir, onlyPhotos ? progress : null, 100f);
        for (int k = 0; k < got.size(); k++) {
            if (got.get(k) == null) continue;
            out.files.add(got.get(k));
            if (out.title.isEmpty()) out.title = photos.get(k).optString("title");
        }
    }

    /** One photo to fetch: where from, with which headers, and its file name without the extension. */
    private static final class Want {
        final String url, base;
        final JSONObject headers;

        Want(String url, JSONObject headers, String base) {
            this.url = url;
            this.headers = headers;
            this.base = base;
        }
    }

    /**
     * Downloads photos into dir, four at a time. Returns a file for each, in the order asked; null where one failed.
     * Reports progress (when given) from 0 to full as they arrive.
     */
    private static List<File> fetchImages(List<Want> wants, File dir, Progress progress, float full) {
        List<File> out = new ArrayList<>();
        if (wants.isEmpty()) return out;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, wants.size()));
        try {
            int[] arrived = {0};
            List<Future<File>> jobs = new ArrayList<>();
            for (Want w : wants) {
                jobs.add(pool.submit(() -> {
                    File f = fetchImage(w.url, w.headers, dir, w.base);
                    synchronized (arrived) {   // in turn, so the bar only moves forward
                        arrived[0]++;
                        if (progress != null) progress.on(full * arrived[0] / wants.size());
                    }
                    return f;
                }));
            }
            for (Future<File> job : jobs) {
                File f = null;
                try {
                    f = job.get();
                } catch (Exception ignored) {
                    // that photo failed; the others still count
                }
                out.add(f);
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    // ---- TikTok photo posts ----

    /**
     * A TikTok photo post, as its pictures and its sound (AS_PHOTOS) or as videos, one for each picture, with the
     * sound (AS_SLIDESHOW); only the chosen pictures, when some were chosen. yt-dlp doesn't read these posts, but it gets
     * past TikTok's checks: one yt-dlp run fetches the post's page and prints it (the pictures are listed in it) and
     * downloads the sound. For the video, FFmpeg then puts the two together.
     */
    private static Result tiktokPhotos(Context c, Haul.Item item, File dir, Progress progress) throws Exception {
        init(c);
        deleteAll(dir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        boolean video = Haul.AS_SLIDESHOW.equals(item.as);
        String photoId = Photos.tiktokPhotoId(item.url);
        if (photoId == null) photoId = Photos.tiktokPhotoId(resolve(item.url));
        if (photoId == null) throw new Exception("NO_FILES");
        JSONObject headers = new JSONObject();
        try {
            headers.put("Referer", "https://www.tiktok.com/").put("User-Agent", BROWSER);
        } catch (Exception ignored) {
        }
        File sounds = new File(dir, "sound");
        //noinspection ResultOfMethodCallIgnored
        sounds.mkdirs();
        List<String> pages = new ArrayList<>();
        File sound = null;
        // Read for the picker a moment ago: the page is here already, and it names the sound's address, so the sound
        // is fetched straight from there and yt-dlp doesn't have to start at all.
        String read = TikTokPage.get(c, photoId);
        if (read != null) {
            progress.on(Progress.WORKING);
            sound = fetchSound(Photos.tiktokSoundUrl(read), headers, sounds);
        }
        boolean asked = read == null || (sound == null && Photos.tiktokHasSound(read));
        if (asked) {
            YoutubeDLRequest r = tiktokPage(photoId);
            r.addOption("-o", sounds.getAbsolutePath() + "/sound.%(ext)s");
            if (video) {
                r.addOption("-f", "ba/b");
            } else {
                // Saved as a file of its own, so the same as the sound alone: M4A.
                r.addOption("-f", "ba[ext=m4a]/ba/b");
                r.addOption("-x");
                r.addOption("--audio-format", "m4a");
            }
            // The sound counts from 0 to 100 on its own, before the pictures do: show it as busy, so the bar only moves forward.
            pages.addAll(Photos.dumpedPages(run(r, item.id, callback(progress, true)).getOut()));
            File[] found = sounds.listFiles();
            if (found != null) {
                Arrays.sort(found, (x, y) -> x.getName().compareTo(y.getName()));
                for (File f : found) {
                    if (sound == null && f.isFile() && f.length() > 0 && MEDIA.contains(ext(f.getName()))) sound = f;
                }
            }
        }
        if (read != null) pages.add(read);

        Result out = new Result();
        File pictures = video ? new File(dir, "pictures") : dir;
        //noinspection ResultOfMethodCallIgnored
        pictures.mkdirs();
        for (String page : pages) {
            List<String> images = Photos.tiktokImages(page);
            if (images.isEmpty()) continue;
            String desc = Photos.tiktokDesc(page);
            out.title = desc.isEmpty() ? "TikTok photos" : desc;
            List<Want> wants = new ArrayList<>();
            for (int n : Post.chosen(item.pick, images.size())) {
                // named by its place in the post, whichever were chosen
                wants.add(new Want(images.get(n - 1), headers, "TikTok " + photoId + " " + String.format(Locale.ROOT, "%02d", n)));
            }
            for (File f : fetchImages(wants, pictures, progress, video ? 50f : 100f)) if (f != null) out.files.add(f);
            break;
        }
        if (out.files.isEmpty() && !asked) {
            // The page kept from the picker no longer leads to the pictures: read the post afresh.
            TikTokPage.forget(c, photoId);
            return tiktokPhotos(c, item, dir, progress);
        }
        if (out.files.isEmpty()) throw new Exception("NO_FILES");

        if (!video) {
            // The sound comes along as a file of its own, after the pictures. A post without one is just its pictures.
            if (sound != null) {
                File named = new File(dir, safeName(out.title) + " [" + photoId + "]." + ext(sound.getName()));
                if (sound.renameTo(named)) out.files.add(named);
            }
            return out;
        }

        progress.on(Progress.WORKING);
        // One video for each chosen picture: the picture, still, over the sound, for as long as was asked.
        int seconds = Slideshow.length(item.seconds, sound == null ? 0 : durationMillis(sound));
        Result made = new Result();
        for (File picture : out.files) {
            // "TikTok 7301234 03.jpg" → "Caption [7301234] 03.mp4": numbered by the picture's place in the post
            java.util.regex.Matcher place = java.util.regex.Pattern.compile(" (\\d+)\\.[^.]+$").matcher(picture.getName());
            String no = place.find() ? " " + place.group(1) : " " + (made.files.size() + 1);
            File mp4 = new File(dir, safeName(out.title) + " [" + photoId + "]" + no + ".mp4");
            slideshow(item.id, java.util.Collections.singletonList(picture), sound, seconds, mp4);
            made.files.add(mp4);
        }
        made.title = out.title;
        made.duration = seconds;
        made.height = 1920;
        return made;
    }

    /** A yt-dlp run that fetches a TikTok photo post's page and prints it. */
    private static YoutubeDLRequest tiktokPage(String photoId) {
        YoutubeDLRequest r = new YoutubeDLRequest("https://www.tiktok.com/@_/video/" + photoId);
        r.addOption("--dump-pages");
        // yt-dlp may well complain that a photo post has no video; this keeps the library from turning that into an
        // exception, so the printed page still comes back.
        r.addOption("--ignore-errors");
        r.addOption("--ignore-no-formats-error");
        r.addOption("--no-playlist");
        return r;
    }

    /** A TikTok photo post's pictures (and whether it has a sound), read for the picker without downloading anything. */
    static Post readTikTok(Context c, String url) throws Exception {
        init(c);
        String photoId = Photos.tiktokPhotoId(url);
        if (photoId == null) throw new Exception("NO_FILES");
        YoutubeDLRequest r = tiktokPage(photoId);
        r.addOption("--skip-download");
        String printed = run(r, "ahead-tiktok-" + photoId, null).getOut();
        for (String page : Photos.dumpedPages(printed)) {
            Post post = Post.tiktok(page);
            if (post.entries.isEmpty()) continue;
            TikTokPage.keep(c, photoId, page);   // the download that follows starts from it
            return post;
        }
        throw new Exception("NO_FILES");
    }

    /** A TikTok photo post's page as the picker read it, kept in the cache for 20 minutes for the download. */
    private static final class TikTokPage {
        private static File file(Context c, String photoId) {
            return new File(c.getCacheDir(), "ahead/tiktok-" + photoId + ".html");
        }

        static void keep(Context c, String photoId, String page) {
            File f = file(c, photoId);
            //noinspection ResultOfMethodCallIgnored
            f.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(f)) {
                os.write(page.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();   // the download reads the post itself, as before
            }
        }

        /** The page, if it was read lately and still lists pictures; null otherwise. */
        static String get(Context c, String photoId) {
            File f = file(c, photoId);
            if (!f.isFile() || System.currentTimeMillis() - f.lastModified() >= 20 * 60_000L) return null;
            try (InputStream in = new FileInputStream(f)) {
                String page = Haul.readAll(in);
                return Photos.tiktokImages(page).isEmpty() ? null : page;
            } catch (Exception e) {
                return null;
            }
        }

        static void forget(Context c, String photoId) {
            //noinspection ResultOfMethodCallIgnored
            file(c, photoId).delete();
        }

        private TikTokPage() {}
    }

    /** Downloads a sound from its address into dir as "sound" + its type's extension; null when there is none or it fails. */
    private static File fetchSound(String url, JSONObject headers, File dir) {
        if (url == null || !url.startsWith("http")) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15_000);
            con.setReadTimeout(30_000);
            for (java.util.Iterator<String> k = headers.keys(); k.hasNext(); ) {
                String name = k.next();
                con.setRequestProperty(name, headers.optString(name));
            }
            if (con.getResponseCode() / 100 != 2) return null;
            String ext = Photos.soundExt(con.getContentType(), url);
            if (ext == null) return null;   // a page saying no, not a sound
            File f = new File(dir, "sound." + ext);
            try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
            }
            if (f.length() > 0 && durationMillis(f) > 0) return f;
            //noinspection ResultOfMethodCallIgnored
            f.delete();   // not something that plays: let yt-dlp fetch the sound instead
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static long durationMillis(File media) {
        MediaExtractor x = new MediaExtractor();
        try {
            x.setDataSource(media.getAbsolutePath());
            long most = 0;
            for (int t = 0; t < x.getTrackCount(); t++) {
                MediaFormat f = x.getTrackFormat(t);
                if (f.containsKey(MediaFormat.KEY_DURATION)) most = Math.max(most, f.getLong(MediaFormat.KEY_DURATION) / 1000);
            }
            return most;
        } catch (Exception e) {
            return 0;
        } finally {
            x.release();
        }
    }

    /** FFmpeg processes running for a haul, so Cancel stops them too. */
    private static final Map<String, Process> FFMPEG = new ConcurrentHashMap<>();
    /** Hauls whose FFmpeg was stopped by Cancel: FFmpeg then ends like any failure, and this tells the two apart. */
    private static final java.util.Set<String> STOPPED = ConcurrentHashMap.newKeySet();

    /**
     * The pictures (or just one) as an H.264 + AAC video (plays everywhere), seconds long, with the sound under them,
     * from its start, repeated if it is shorter.
     * Runs the FFmpeg that came with the engine, the way the engine itself runs it.
     */
    private static void slideshow(String id, List<File> pictures, File sound, int seconds, File out) throws Exception {
        File list = new File(out.getParentFile(), "pictures.txt");
        List<String> paths = new ArrayList<>();
        for (File f : pictures) paths.add(f.getAbsolutePath());
        int once = Slideshow.seconds(pictures.size(), 0);
        if (sound != null && seconds > once) {
            // The sound outlasts the pictures, so they come round again. Encoding is the slow part: encode one round
            // (without B-frames, so the rounds join cleanly), then repeat it under the sound as it is.
            try {
                File round = new File(out.getParentFile(), "round.mp4");
                try (OutputStream os = new FileOutputStream(list)) {
                    os.write(Slideshow.concat(paths, once).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                ffmpeg(id, new ArrayList<>(Arrays.asList(ffmpegBinary.getAbsolutePath(), "-y", "-hide_banner", "-loglevel", "error",
                        "-f", "concat", "-safe", "0", "-i", list.getAbsolutePath(), "-t", String.valueOf(once),
                        "-vf", Slideshow.FILTER, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-bf", "0", "-an",
                        round.getAbsolutePath())), round);
                ffmpeg(id, new ArrayList<>(Arrays.asList(ffmpegBinary.getAbsolutePath(), "-y", "-hide_banner", "-loglevel", "error",
                        "-stream_loop", "-1", "-i", round.getAbsolutePath(), "-stream_loop", "-1", "-i", sound.getAbsolutePath(),
                        "-t", String.valueOf(seconds), "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                        "-movflags", "+faststart", out.getAbsolutePath())), out);
                return;
            } catch (InterruptedException e) {
                throw e;   // canceled
            } catch (Exception e) {
                // this FFmpeg wouldn't repeat it: make the whole video in one go, as below
            }
        }
        try (OutputStream os = new FileOutputStream(list)) {
            os.write(Slideshow.concat(paths, seconds).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        List<String> cmd = new ArrayList<>(Arrays.asList(ffmpegBinary.getAbsolutePath(), "-y", "-hide_banner", "-loglevel", "error",
                "-f", "concat", "-safe", "0", "-i", list.getAbsolutePath()));
        if (sound != null) cmd.addAll(Arrays.asList("-stream_loop", "-1", "-i", sound.getAbsolutePath()));
        cmd.addAll(Arrays.asList("-t", String.valueOf(seconds), "-vf", Slideshow.FILTER,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23"));
        if (sound != null) cmd.addAll(Arrays.asList("-map", "0:v:0", "-map", "1:a:0", "-c:a", "aac", "-b:a", "192k"));
        cmd.addAll(Arrays.asList("-movflags", "+faststart", out.getAbsolutePath()));
        ffmpeg(id, cmd, out);
    }

    /** Runs the engine's FFmpeg with these arguments (the program first) for a haul, until out is written. */
    private static void ffmpeg(String id, List<String> cmd, File out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", ffmpegLibs);
        STOPPED.remove(id);
        Process p = pb.start();
        FFMPEG.put(id, p);
        String said;
        try (InputStream in = p.getInputStream()) {
            said = Haul.readAll(in);
        } finally {
            FFMPEG.remove(id, p);
        }
        int exit = p.waitFor();
        if (exit != 0 || !out.isFile() || out.length() == 0) {
            if (STOPPED.remove(id) || exit == 137 || exit == 143) throw new InterruptedException();   // canceled
            throw new Exception(MADE + lastLine(said));
        }
    }

    // ---- GIFs ----

    /** GIFs last at most this long: a GIF holds every frame as a picture, so longer ones grow huge. */
    static final int GIF_SECONDS = 30;

    /**
     * Turns videos into GIFs where wanted: all of them for the GIF quality, and on its own for a post that was a GIF to
     * begin with (X keeps "GIFs" as silent MP4s). Each video's info JSON sits next to it under the same name.
     */
    private static Result gifs(Haul.Item item, File dir, Result r, Progress progress) throws Exception {
        boolean all = Haul.GIF.equals(item.quality);
        List<File> out = new ArrayList<>();
        boolean any = false;
        for (File f : r.files) {
            boolean video = Arrays.asList("mp4", "m4v", "mov", "mkv", "webm").contains(ext(f.getName()));
            if (!video || !(all || Photos.isGif(info(dir, f)))) {
                out.add(f);
                continue;
            }
            if (!any) progress.on(Progress.WORKING);
            any = true;
            File gif = new File(dir, f.getName().replaceFirst("\\.[^.]+$", "") + ".gif");
            ffmpeg(item.id, new ArrayList<>(Arrays.asList(ffmpegBinary.getAbsolutePath(), "-y", "-hide_banner", "-loglevel", "error",
                    "-i", f.getAbsolutePath(), "-t", String.valueOf(GIF_SECONDS), "-an", "-vf", Slideshow.GIF_FILTER, "-loop", "0",
                    gif.getAbsolutePath())), gif);
            out.add(gif);
        }
        if (!any) return r;
        r.files.clear();
        r.files.addAll(out);
        r.height = -1;
        return r;
    }

    /** The info JSON yt-dlp wrote next to a downloaded file ("Title [id].mp4" → "Title [id].info.json"), or null. */
    private static JSONObject info(File dir, File media) {
        File f = new File(dir, media.getName().replaceFirst("\\.[^.]+$", "") + ".info.json");
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject(Haul.readAll(in));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The engine's FFmpeg: the program among the app's native libraries, and the library path the engine itself runs
     * it with (Python's, FFmpeg's and aria2c's unpacked libraries). The engine unpacks them into the no-backup files
     * folder; pointing anywhere else leaves FFmpeg unable to start ("library … not found").
     */
    private static File ffmpegBinary;
    private static String ffmpegLibs;

    private static void findFfmpeg(Context c) {
        ffmpegBinary = new File(c.getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
        File packages = new File(c.getNoBackupFilesDir(), "youtubedl-android/packages");
        ffmpegLibs = new File(packages, "python/usr/lib") + ":" + new File(packages, "ffmpeg/usr/lib") + ":"
                + new File(packages, "aria2c/usr/lib");
    }

    /** Where a short link (vm.tiktok.com/…) leads; the link itself when it can't be followed. */
    static String resolve(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "facebookexternalhit/1.1");   // what yt-dlp uses for these links
            con.setConnectTimeout(5_000);
            con.setReadTimeout(5_000);
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

    /**
     * Reads the link the way a download does and keeps what was read, so a download that follows (of the section
     * picked) starts from it instead of reading the link again.
     */
    static Info info(Context c, String url) throws Exception {
        return info(read(c, url));
    }

    /** The first item's title, length and picture, from yt-dlp's details of a link. */
    static Info info(JSONObject details) {
        JSONObject o = details;
        JSONArray entries = "playlist".equals(o.optString("_type")) ? o.optJSONArray("entries") : null;
        if (entries != null && entries.optJSONObject(0) != null) o = entries.optJSONObject(0);
        Info i = new Info();
        i.title = o.optString("title");
        double d = o.optDouble("duration", -1);   // absent for a live stream, or a site that doesn't say
        i.duration = Double.isNaN(d) || d <= 0 ? -1 : (int) Math.round(d);
        String t = o.optString("thumbnail");
        JSONArray thumbs = o.optJSONArray("thumbnails");
        // yt-dlp lists thumbnails worst to best.
        for (int k = 0; t.isEmpty() && thumbs != null && k < thumbs.length(); k++) {
            JSONObject x = thumbs.optJSONObject(thumbs.length() - 1 - k);
            if (x != null) t = x.optString("url");
        }
        if (t.startsWith("http")) i.thumbnail = t;
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

    /** Stops the yt-dlp (or FFmpeg) process of this haul, if one is running. */
    static void cancel(String id) {
        Process ff = FFMPEG.get(id);
        if (ff != null) {
            STOPPED.add(id);
            ff.destroy();
        }
        String pid = PROCESS.get(id);
        if (pid == null) return;
        try {
            YoutubeDL.getInstance().destroyProcessById(pid);
        } catch (Exception ignored) {
        }
    }

    // ---- Reading ahead ----

    /**
     * A link's details from yt-dlp -J, kept in the cache for 20 minutes. Reading a link (the site's page, the video's
     * details) is most of "reading the link" before a download; done while the user is still looking at the link (in
     * the dock, in the carousel picker), the download that follows starts straight from these details.
     */
    static final class Ahead {
        private static final long FRESH = 20 * 60_000L;
        private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

        static File file(Context c, String url) {
            return new File(c.getCacheDir(), "ahead/" + Integer.toHexString(url.hashCode()) + "-" + url.length() + ".json");
        }

        /** The details read for this link, if they are fresh; null otherwise. */
        static File get(Context c, String url) {
            File f = file(c, url);
            return f.isFile() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < FRESH ? f : null;
        }

        static Object lock(String url) {
            return LOCKS.computeIfAbsent(url, k -> new Object());
        }

        private Ahead() {}
    }

    /** Reads the link's details (or takes the fresh ones already read), keeps them for the download, returns them. */
    static JSONObject read(Context c, String url) throws Exception {
        synchronized (Ahead.lock(url)) {   // the dock's read-ahead and the picker wait for each other, not read twice
            File f = Ahead.get(c, url);
            if (f == null) {
                init(c);
                YoutubeDLRequest r = new YoutubeDLRequest(url);
                r.addOption("-J");
                r.addOption("--no-playlist");
                r.addOption("--no-warnings");
                r.addOption("--ignore-no-formats-error");
                r.addOption("-I", "1:20");
                String out = run(r, "ahead-" + Integer.toHexString(url.hashCode()), null).getOut();
                String json = "";
                for (String line : out.split("\n")) if (line.startsWith("{")) json = line;
                if (json.isEmpty()) throw new Exception("NO_FILES");
                f = Ahead.file(c, url);
                //noinspection ResultOfMethodCallIgnored
                f.getParentFile().mkdirs();
                File tmp = new File(f.getPath() + ".tmp");
                try (OutputStream os = new FileOutputStream(tmp)) {
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(f)) throw new Exception("Couldn't keep the link's details");
            }
            try (InputStream in = new FileInputStream(f)) {
                return new JSONObject(Haul.readAll(in));
            }
        }
    }

    /** Reads ahead in the background, quietly; for a link the user is looking at and will likely download. */
    static void readAhead(Context c, String url) {
        Context app = c.getApplicationContext();
        if (Ahead.get(app, url) != null || Photos.tiktokPhotoId(url) != null || !AHEAD.add(url)) return;
        new Thread(() -> {
            try {
                read(app, url);
            } catch (Exception ignored) {
                // the download reads the link itself, as before
            } finally {
                AHEAD.remove(url);
            }
        }, "haul-ahead").start();
    }

    /**
     * A download asked for while its link is still being read ahead waits for that read (up to half a minute) and
     * starts from it, rather than reading the link a second time alongside.
     */
    private static void awaitAhead(Context c, Haul.Item item, Progress progress) throws InterruptedException {
        long until = System.currentTimeMillis() + 30_000;
        boolean said = false;
        while (AHEAD.contains(item.url) && System.currentTimeMillis() < until) {
            Haul.Item now = Haul.get(c, item.id);
            if (now == null || Haul.CANCELED.equals(now.status)) throw new InterruptedException();
            if (!said) progress.on(Progress.READING);
            said = true;
            Thread.sleep(150);
        }
    }

    /** Links being read ahead right now, so a screen redrawing many times a second starts one read, not many. */
    private static final java.util.Set<String> AHEAD = ConcurrentHashMap.newKeySet();

    static boolean canceled(Throwable e) {
        return e instanceof YoutubeDL.CanceledException;
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Errors from Haul's own FFmpeg work start with this, and are shown as they are, not guessed at like a site's. */
    static final String MADE = "Couldn't make the file. FFmpeg said: ";

    static String lastLine(String text) {
        String last = "";
        for (String line : String.valueOf(text).split("\n")) if (!line.trim().isEmpty()) last = line.trim();
        return last.length() > 200 ? last.substring(0, 197) + "…" : last;
    }

    /** Turns yt-dlp's error into one sentence a person can act on. Most specific first: yt-dlp wraps nearly every
     *  HTTP failure in "Unable to download webpage: HTTP Error 404", so the plain network check has to come last. */
    static String friendly(Throwable e) {
        String m = String.valueOf(e.getMessage());
        if (m.startsWith(MADE)) return m;
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
