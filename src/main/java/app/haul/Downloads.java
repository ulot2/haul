package app.haul;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The download queue. Links wait in the hold as "queued"; one runner takes them one at a time, oldest first, and
 * saves the files into the Gallery (Movies/Haul, or Music/Haul for audio).
 *
 * The runner lives in whatever Android allows to keep working after the user goes back to TikTok:
 * on Android 14+ a user-initiated data transfer job ({@link HaulJob}), which waits for a connection and has no daily
 * limit; on Android 10-13, or if Android refuses the job, a foreground service ({@link HaulService}).
 */
final class Downloads {
    static final String CH_WORK = "work", CH_DONE = "done";
    static final int ONGOING = 1;
    static final String TIMED_OUT = "Android allows downloads in the background for 6 hours a day. Tap to try again later.";
    private static final String NOT_STARTED = "Android didn't let Haul start the download. Open Haul and tap to try again.";
    private static final AtomicInteger notifyIds = new AtomicInteger(100);

    /** Guards {@link #active}: a runner is scheduled or running, so a new link only needs to join the queue. */
    private static final Object LOCK = new Object();
    private static boolean active;

    // ---- What the screens and the share sheet call ----

    /** Adds the link to the hold and starts the queue. Null when this link is already waiting or downloading. */
    static Haul.Item add(Context c, String url) {
        Haul.Item i;
        synchronized (Downloads.class) {   // two quick shares of one link: only the first gets in
            if (Haul.activeFor(c, url) != null) return null;
            i = Haul.add(c, url);
        }
        start(c);
        return i;
    }

    /** Tries an existing (failed, canceled or finished) haul again. */
    static void retry(Context c, String id) {
        Haul.update(c, id, i -> {
            i.status = Haul.QUEUED;
            i.error = "";
            i.quality = Haul.quality(c);
        });
        start(c);
    }

    /** Stops a waiting or downloading haul. */
    static void cancel(Context c, String id) {
        Haul.update(c, id, i -> {
            if (i.active()) i.status = Haul.CANCELED;
        });
        Engine.cancel(id);   // stops yt-dlp if this one is downloading; nothing otherwise
    }

    /** True while a runner is scheduled or running. */
    static boolean busy() {
        synchronized (LOCK) {
            return active;
        }
    }

    /** At process start: keep the queue if Android still holds our job, else mark what was going as cut off. */
    static void settle(Context c) {
        boolean jobWaiting = Build.VERSION.SDK_INT >= 34
                && c.getSystemService(JobScheduler.class).getPendingJob(HaulJob.ID) != null;
        Haul.settle(c, jobWaiting);
        synchronized (LOCK) {
            active = jobWaiting;
        }
    }

    private static void start(Context c) {
        synchronized (LOCK) {
            if (active) return;   // the runner takes the new link after the current one
            active = true;
        }
        if (Build.VERSION.SDK_INT >= 34 && HaulJob.schedule(c)) return;
        try {
            c.startForegroundService(new Intent(c, HaulService.class));
        } catch (Exception e) {
            // Only from the background, which Haul never does on purpose; leave the links ready to retry.
            synchronized (LOCK) {
                active = false;
            }
            for (Haul.Item i : Haul.items(c)) {
                if (Haul.QUEUED.equals(i.status)) Haul.update(c, i.id, it -> {
                    it.status = Haul.FAILED;
                    it.error = NOT_STARTED;
                });
            }
        }
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel work = new NotificationChannel(CH_WORK, "Downloading", NotificationManager.IMPORTANCE_LOW);
        work.setShowBadge(false);
        nm.createNotificationChannel(work);
        nm.createNotificationChannel(new NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT));
    }

    // ---- The runner ----

    /** Where the runner lives: shows its ongoing notification the way that host has to. */
    interface Host {
        void show(Notification n);
    }

    /** How a runner was stopped from outside: put the current link back in the queue, cancel all, or fail all. */
    static final int REQUEUE = 1, CANCEL_ALL = 2, TIME_OUT = 3;

    static final class Runner {
        private final Context c;
        private final Host host;
        private volatile String current;
        private volatile int stopped;
        private long lastNotify;

        Runner(Context c, Host host) {
            this.c = c.getApplicationContext();
            this.host = host;
        }

        Notification ready() {
            return ongoing("Getting ready…", null, -1, null);
        }

        /**
         * Downloads until the queue is empty, then updates yt-dlp if a day has passed (so the update never delays a
         * download). Returns true when the queue ran out, false when it was stopped from outside.
         */
        boolean drain() {
            boolean updated = false;
            while (true) {
                Haul.Item next;
                synchronized (LOCK) {
                    if (stopped != 0) {
                        active = stopped == REQUEUE;   // Android starts the job again later
                        return false;
                    }
                    next = Haul.takeNext(c);
                    if (next == null && (updated || !Engine.stale(c))) {
                        active = false;
                        return true;
                    }
                }
                if (next != null) {
                    run(next);
                } else {
                    updated = true;
                    host.show(ongoing("Updating the download engine…", null, -1, null));
                    Engine.updateIfStale(c);
                }
            }
        }

        /** Called on the main thread when Android stops the host. Must return at once. */
        void stop(int how) {
            stopped = how;
            for (Haul.Item i : Haul.items(c)) {
                if (!i.active()) continue;
                Haul.update(c, i.id, it -> {
                    if (how == REQUEUE) {
                        it.status = Haul.QUEUED;
                    } else if (how == CANCEL_ALL) {
                        it.status = Haul.CANCELED;
                    } else {
                        it.status = Haul.FAILED;
                        it.error = TIMED_OUT;
                    }
                });
            }
            String id = current;
            if (id != null) Engine.cancel(id);
        }

        // ---- One download ----

        private void run(Haul.Item item) {
            String id = item.id;
            current = id;
            Haul.setProgress(id, -1);
            showProgress(item, -1);
            File dir = new File(c.getCacheDir(), "haul/" + id);
            try {
                Engine.init(c);
                List<File> files;
                try {
                    files = fetch(item, dir);
                } catch (Exception first) {
                    if (stopped != 0 || Engine.canceled(first) || isCanceled(id)) throw first;
                    // Sites change and break yt-dlp; a fresh yt-dlp usually fixes it.
                    if (!Engine.updateAfterFailure(c)) throw first;
                    files = fetch(item, dir);
                }
                if (stopped != 0 || isCanceled(id)) throw new InterruptedException();

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
                Haul.update(c, id, i -> {
                    i.status = Haul.DONE;
                    i.error = "";
                    i.uri = String.valueOf(uri);
                    i.mime = m;
                    i.title = t;
                    i.count = count;
                });
                notifyDone(Haul.get(c, id));
            } catch (Exception e) {
                if (stopped != 0) {
                    // stop() already marked it, with the reason.
                } else if (Engine.canceled(e) || isCanceled(id) || e instanceof InterruptedException) {
                    Haul.update(c, id, i -> i.status = Haul.CANCELED);
                } else {
                    String why = Engine.friendly(e);
                    Haul.update(c, id, i -> {
                        i.status = Haul.FAILED;
                        i.error = why;
                    });
                    notifyFailed(Haul.get(c, id));
                }
            } finally {
                current = null;
                Engine.deleteAll(dir);
            }
        }

        private List<File> fetch(Haul.Item item, File dir) throws Exception {
            return Engine.download(c, item.id, item.url, item.quality, dir, p -> {
                Haul.setProgress(item.id, p);
                showProgress(item, p);
            });
        }

        private boolean isCanceled(String id) {
            Haul.Item i = Haul.get(c, id);
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
            ContentResolver cr = c.getContentResolver();
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

        // ---- Notifications ----

        private void showProgress(Haul.Item item, float percent) {
            long now = System.currentTimeMillis();
            if (percent >= 0 && percent < 100 && now - lastNotify < 500) return;   // Android drops floods of updates anyway
            lastNotify = now;
            int waiting = Haul.waiting(c);
            String title = percent < 0 ? "Looking at the link…" : "Downloading " + Math.round(percent) + "%";
            String text = item.site + (waiting > 0 ? " · " + waiting + " more waiting" : "");
            if (stopped == 0) host.show(ongoing(title, text, percent, item.id));
        }

        private Notification ongoing(String title, String text, float percent, String id) {
            Notification.Builder b = new Notification.Builder(c, CH_WORK)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(title)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(openApp(c, 0))
                    .setProgress(100, Math.max(0, Math.round(percent)), percent < 0);
            if (text != null) b.setContentText(text);
            if (id != null) {
                PendingIntent stop = PendingIntent.getBroadcast(c, 1,
                        new Intent(c, CancelReceiver.class).putExtra(CancelReceiver.EXTRA_ID, id),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(new Notification.Action.Builder(null, "Cancel", stop).build());
            }
            return b.build();
        }

        private void notifyDone(Haul.Item i) {
            if (i == null) return;
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.uri), i.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent open = PendingIntent.getActivity(c, notifyIds.incrementAndGet(), view, PendingIntent.FLAG_IMMUTABLE);
            String where = i.mime.startsWith("audio/") ? "Music/Haul" : i.mime.startsWith("image/") ? "Pictures/Haul" : "Movies/Haul";
            String what = i.count > 1 ? i.count + " files saved" : "Saved";
            post(new Notification.Builder(c, CH_DONE)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(i.title.isEmpty() ? i.site : i.title)
                    .setContentText(what + " to " + where + " · " + i.site)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build());
        }

        private void notifyFailed(Haul.Item i) {
            if (i == null) return;
            post(new Notification.Builder(c, CH_DONE)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("Couldn't download from " + i.site)
                    .setContentText(i.error)
                    .setStyle(new Notification.BigTextStyle().bigText(i.error))
                    .setContentIntent(openApp(c, 2))
                    .setAutoCancel(true)
                    .build());
        }

        private void post(Notification n) {
            c.getSystemService(NotificationManager.class).notify(notifyIds.incrementAndGet(), n);
        }
    }

    static PendingIntent openApp(Context c, int code) {
        return PendingIntent.getActivity(c, code, new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
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

    private Downloads() {}
}
