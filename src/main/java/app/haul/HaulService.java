package app.haul;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads one link at a time while showing a progress notification, then moves the files into the Gallery
 * (Movies/Haul, or Music/Haul for audio). Runs as a foreground service so Android does not stop it when the user
 * goes back to TikTok right after sharing.
 */
public class HaulService extends Service {
    static final String ACTION_ADD = "app.haul.ADD", ACTION_CANCEL = "app.haul.CANCEL", EXTRA_ID = "id";
    static final String CH_WORK = "work", CH_DONE = "done";
    private static final int ONGOING = 1;
    static final String TIMED_OUT = "Android allows downloads in the background for 6 hours a day. Tap to try again later.";
    private static final AtomicInteger notifyIds = new AtomicInteger(100);

    static volatile boolean running;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger pending = new AtomicInteger();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String current;
    private volatile boolean timedOut, destroyed;
    private volatile Notification shown;
    private long lastNotify;

    /** Adds the link to the hold and starts downloading it. */
    static void start(Context c, String url) {
        Haul.Item i = Haul.add(c, url);
        enqueue(c, i.id);
    }

    /** Tries an existing (failed or canceled) item again. */
    static void retry(Context c, String id) {
        Haul.update(c, id, i -> {
            i.status = Haul.QUEUED;
            i.error = "";
            i.quality = Haul.quality(c);
        });
        enqueue(c, id);
    }

    static void cancel(Context c, String id) {
        c.startService(new Intent(c, HaulService.class).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id));
    }

    private static void enqueue(Context c, String id) {
        c.startForegroundService(new Intent(c, HaulService.class).setAction(ACTION_ADD).putExtra(EXTRA_ID, id));
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel work = new NotificationChannel(CH_WORK, "Downloading", NotificationManager.IMPORTANCE_LOW);
        work.setShowBadge(false);
        nm.createNotificationChannel(work);
        nm.createNotificationChannel(new NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        channels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every start must promise the foreground notification at once, even a cancel that arrives late.
        Notification n = shown;
        startForeground(ONGOING, n != null ? n : ongoing("Getting ready…", null, -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        String action = intent == null ? null : intent.getAction();
        String id = intent == null ? null : intent.getStringExtra(EXTRA_ID);
        if (ACTION_CANCEL.equals(action) && id != null) {
            Haul.Item i = Haul.get(this, id);
            if (i != null && i.active()) Haul.update(this, id, it -> it.status = Haul.CANCELED);
            if (id.equals(current)) Engine.cancel(id);
            if (pending.get() == 0) stopNow();
        } else if (ACTION_ADD.equals(action) && id != null) {
            pending.incrementAndGet();
            worker.execute(() -> {
                try {
                    run(id);
                    // The daily yt-dlp update waits until the queue is empty, so it never delays a download.
                    if (pending.get() == 1 && !timedOut && Engine.stale(this)) {
                        showStatus("Updating the download engine…");
                        Engine.updateIfStale(this);
                    }
                } finally {
                    // Stop on the main thread, where a new link may have just arrived (onStartCommand runs there too).
                    if (pending.decrementAndGet() == 0) main.post(() -> {
                        if (pending.get() == 0 && !destroyed) stopNow();
                    });
                }
            });
        } else if (pending.get() == 0) {
            stopNow();
        }
        return START_NOT_STICKY;
    }

    private void stopNow() {
        shown = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /**
     * Android 15+ lets a data-sync service run 6 hours a day, then calls this. The service must stop within seconds or
     * Android crashes the app, so everything still going or waiting is marked as stopped, to try again later.
     */
    @Override
    public void onTimeout(int startId, int fgsType) {
        timedOut = true;
        for (Haul.Item i : Haul.items(this)) {
            if (i.active()) Haul.update(this, i.id, it -> {
                it.status = Haul.FAILED;
                it.error = TIMED_OUT;
            });
        }
        String id = current;
        if (id != null) Engine.cancel(id);
        stopNow();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- One download ----

    private void run(String id) {
        Haul.Item item = Haul.get(this, id);
        if (item == null || !Haul.QUEUED.equals(item.status)) return;   // canceled while waiting
        current = id;
        Haul.update(this, id, i -> i.status = Haul.WORKING);
        Haul.setProgress(id, -1);
        showProgress(item, -1);

        File dir = new File(getCacheDir(), "haul/" + id);
        try {
            Engine.init(this);
            List<File> files;
            try {
                files = fetch(item, dir);
            } catch (Exception first) {
                if (timedOut || Engine.canceled(first) || isCanceled(id)) throw first;
                // Sites change and break yt-dlp; a fresh yt-dlp usually fixes it.
                if (!Engine.updateAfterFailure(this)) throw first;
                files = fetch(item, dir);
            }
            if (isCanceled(id)) throw new InterruptedException();

            Uri firstUri = null;
            String firstMime = "", title = "";
            for (File f : files) {
                String mime = mime(f.getName());
                Uri u = save(f, mime);
                if (firstUri == null) {
                    firstUri = u;
                    firstMime = mime;
                    title = titleOf(f.getName());
                }
            }
            final Uri uri = firstUri;
            final String m = firstMime, t = title;
            final int count = files.size();
            Haul.update(this, id, i -> {
                i.status = Haul.DONE;
                i.uri = String.valueOf(uri);
                i.mime = m;
                i.title = t;
                i.count = count;
            });
            notifyDone(Haul.get(this, id));
        } catch (Exception e) {
            if (timedOut) {
                // onTimeout already marked it, with the reason.
            } else if (Engine.canceled(e) || isCanceled(id) || e instanceof InterruptedException) {
                Haul.update(this, id, i -> i.status = Haul.CANCELED);
            } else {
                String why = Engine.friendly(e);
                Haul.update(this, id, i -> {
                    i.status = Haul.FAILED;
                    i.error = why;
                });
                notifyFailed(Haul.get(this, id));
            }
        } finally {
            current = null;
            deleteAll(dir);
        }
    }

    private List<File> fetch(Haul.Item item, File dir) throws Exception {
        deleteAll(dir);
        dir.mkdirs();
        return Engine.download(this, item.id, item.url, item.quality, dir, p -> {
            Haul.setProgress(item.id, p);
            showProgress(item, p);
        });
    }

    private boolean isCanceled(String id) {
        Haul.Item i = Haul.get(this, id);
        return i == null || Haul.CANCELED.equals(i.status);
    }

    // ---- Into the Gallery ----

    /** Copies the file into shared storage through MediaStore, so no storage permission is needed. */
    private Uri save(File f, String mime) throws Exception {
        String folder;
        Uri collection;
        if (mime.startsWith("audio/")) {
            folder = Environment.DIRECTORY_MUSIC;
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else if (mime.startsWith("image/")) {
            folder = Environment.DIRECTORY_PICTURES;
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            folder = Environment.DIRECTORY_MOVIES;
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/Haul");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = cr.insert(collection, v);
        if (uri == null) throw new Exception("Couldn't save to the Gallery.");
        try (InputStream in = new FileInputStream(f); OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new Exception("Couldn't save to the Gallery.");
            byte[] buf = new byte[256 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } catch (Exception e) {
            cr.delete(uri, null, null);
            String msg = String.valueOf(e.getMessage());
            throw new Exception(msg.contains("ENOSPC") ? "no space" : msg, e);
        }
        v.clear();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return uri;
    }

    static String mime(String name) {
        String ext = Engine.ext(name);
        if (ext.equals("m4a")) return "audio/mp4";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "video/mp4" : m;
    }

    /** "Funny cat [7301234].mp4" → "Funny cat". */
    static String titleOf(String name) {
        String t = name.replaceFirst("\\.[^.]+$", "").replaceFirst("\\s*\\[[^\\]]+\\]$", "").trim();
        return t.isEmpty() ? name : t;
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ---- Notifications ----

    private void showProgress(Haul.Item item, float percent) {
        long now = System.currentTimeMillis();
        if (percent >= 0 && percent < 100 && now - lastNotify < 500) return;   // Android drops floods of updates anyway
        lastNotify = now;
        int waiting = Math.max(0, pending.get() - 1);
        String title = percent < 0 ? "Looking at the link…" : "Downloading " + Math.round(percent) + "%";
        String text = item.site + (waiting > 0 ? " · " + waiting + " more waiting" : "");
        shown = ongoing(title, text, percent, item.id);
        if (pending.get() > 0) getSystemService(NotificationManager.class).notify(ONGOING, shown);
    }

    /** A notification without a percent or a Cancel button, for work between downloads. */
    private void showStatus(String title) {
        shown = ongoing(title, null, -1);
        getSystemService(NotificationManager.class).notify(ONGOING, shown);
    }

    private Notification ongoing(String title, String text, float percent) {
        return ongoing(title, text, percent, null);
    }

    private Notification ongoing(String title, String text, float percent, String id) {
        Notification.Builder b = new Notification.Builder(this, CH_WORK)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(0))
                .setProgress(100, Math.max(0, Math.round(percent)), percent < 0);
        if (text != null) b.setContentText(text);
        if (id != null) {
            PendingIntent stop = PendingIntent.getService(this, 1,
                    new Intent(this, HaulService.class).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(null, "Cancel", stop).build());
        }
        return b.build();
    }

    private void notifyDone(Haul.Item i) {
        if (i == null) return;
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.uri), i.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, notifyIds.incrementAndGet(), view, PendingIntent.FLAG_IMMUTABLE);
        String where = i.mime.startsWith("audio/") ? "Music/Haul" : i.mime.startsWith("image/") ? "Pictures/Haul" : "Movies/Haul";
        String what = i.count > 1 ? i.count + " files saved" : "Saved";
        post(new Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(i.title.isEmpty() ? i.site : i.title)
                .setContentText(what + " to " + where + " · " + i.site)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }

    private void notifyFailed(Haul.Item i) {
        if (i == null) return;
        post(new Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Couldn't download from " + i.site)
                .setContentText(i.error)
                .setStyle(new Notification.BigTextStyle().bigText(i.error))
                .setContentIntent(openApp(2))
                .setAutoCancel(true)
                .build());
    }

    private void post(Notification n) {
        getSystemService(NotificationManager.class).notify(notifyIds.incrementAndGet(), n);
    }

    private PendingIntent openApp(int code) {
        return PendingIntent.getActivity(this, code, new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
    }
}
