package app.haul;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ClipData;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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
    private static final String NO_WIFI = "Wi-Fi only is on, and the phone is on mobile data. Tap to try again on Wi-Fi.";
    private static final AtomicInteger notifyIds = new AtomicInteger(100);
    /** Set once Android has stopped a download job for its own reasons: from then on Haul uses the service. */
    static final String USE_SERVICE = "use_service";

    /** Guards {@link #active}: a runner is scheduled or running, so a new link only needs to join the queue. */
    private static final Object LOCK = new Object();
    private static boolean active;
    /** A runner is downloading in this process right now (not just scheduled). */
    private static volatile boolean draining;

    // ---- What the screens and the share sheet call ----

    /** Adds the link with the default quality, the whole video. */
    static Haul.Item add(Context c, String url) {
        return add(c, url, Haul.quality(c), -1, -1, "", "");
    }

    static Haul.Item add(Context c, String url, String quality, int clipStart, int clipEnd) {
        return add(c, url, quality, clipStart, clipEnd, "", "");
    }

    /**
     * Adds the link to the hold and starts the queue: in this quality, and only clipStart to clipEnd (seconds) when
     * those are set. Null when this link is already waiting or downloading.
     */
    static Haul.Item add(Context c, String url, String quality, int clipStart, int clipEnd, String pick, String as) {
        Haul.Item i;
        synchronized (Downloads.class) {   // two quick shares of one link: only the first gets in
            if (Haul.activeFor(c, url) != null) return null;
            i = Haul.add(c, url, quality, clipStart, clipEnd, pick, as);
        }
        start(c);
        return i;
    }

    /** Tries an existing (failed, canceled or finished) haul again, with the quality and section it had. */
    static void retry(Context c, String id) {
        Haul.update(c, id, i -> {
            i.status = Haul.QUEUED;
            i.error = "";
        });
        start(c);
    }

    /**
     * Deletes a haul's files from the Gallery and the haul from the hold. Returns the files Android wouldn't let Haul
     * delete (made before a reinstall, so no longer Haul's): MainActivity asks the user for those.
     */
    static List<Uri> delete(Context c, Haul.Item i) {
        List<Uri> refused = new ArrayList<>();
        for (String u : i.uris) {
            if (u.isEmpty() || "null".equals(u)) continue;
            Uri uri = Uri.parse(u);
            try {
                c.getContentResolver().delete(uri, null, null);
            } catch (SecurityException e) {
                refused.add(uri);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        Haul.remove(c, i.id);
        return refused;
    }

    /** Wi-Fi only was switched: a job still waiting is scheduled again with the new rule. */
    static void settingsChanged(Context c) {
        if (Build.VERSION.SDK_INT < 34 || draining) return;
        if (c.getSystemService(JobScheduler.class).getPendingJob(HaulJob.ID) != null) HaulJob.schedule(c);
    }

    /** Stops a waiting or downloading haul. */
    static void cancel(Context c, String id) {
        Haul.update(c, id, i -> {
            if (!i.active()) return;
            i.status = Haul.CANCELED;
            i.error = "Canceled.";
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
        if (Build.VERSION.SDK_INT >= 34 && !Haul.prefs(c).getBoolean(USE_SERVICE, false) && HaulJob.schedule(c)) return;
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

        /** True when Android starts the host only on a suitable network (the job), so Wi-Fi only needs no check. */
        boolean waitsForNetwork();
    }

    /** How a runner was stopped from outside: put the current link back in the queue, cancel all, or fail all. */
    static final int REQUEUE = 1, CANCEL_ALL = 2, FAIL_ALL = 3;

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
            draining = true;
            try {
                return drainQueue();
            } finally {
                draining = false;
            }
        }

        private boolean drainQueue() {
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

        /** Called on the main thread when Android stops the host, with the reason to show. Must return at once. */
        void stop(int how, String why) {
            stopped = how;
            for (Haul.Item i : Haul.items(c)) {
                if (!i.active()) continue;
                Haul.update(c, i.id, it -> {
                    if (how == REQUEUE) {
                        it.status = Haul.QUEUED;
                    } else {
                        it.status = how == CANCEL_ALL ? Haul.CANCELED : Haul.FAILED;
                        it.error = why;
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
                if (!host.waitsForNetwork() && Haul.wifiOnly(c)
                        && c.getSystemService(ConnectivityManager.class).isActiveNetworkMetered()) {
                    throw new IllegalStateException(NO_WIFI);
                }
                Engine.init(c);
                Engine.Result got;
                try {
                    got = fetch(item, dir);
                } catch (Exception first) {
                    if (stopped != 0 || Engine.canceled(first) || isCanceled(id)) throw first;
                    // Sites change and break yt-dlp; a fresh yt-dlp usually fixes it.
                    if (!Engine.updateAfterFailure(c)) throw first;
                    got = fetch(item, dir);
                }
                if (stopped != 0 || isCanceled(id)) throw new InterruptedException();

                List<String> uris = new ArrayList<>();
                String firstMime = "";
                long size = 0;
                boolean anyVideo = false, anyAudio = false, anyImage = false;
                for (File f : got.files) {
                    String mime = mime(f.getName());
                    size += f.length();
                    uris.add(String.valueOf(save(f, mime)));
                    if (firstMime.isEmpty()) firstMime = mime;
                    anyVideo |= mime.startsWith("video/");
                    anyAudio |= mime.startsWith("audio/");
                    anyImage |= mime.startsWith("image/");
                }
                // Pictures that came with their sound (a TikTok photo post) are still photos.
                final String m = firstMime, kind = anyVideo ? Haul.VIDEO : anyImage || !anyAudio ? Haul.PHOTOS : Haul.AUDIO;
                final boolean sound = Haul.PHOTOS.equals(kind) && anyAudio;
                final String t = !got.title.isEmpty() ? got.title : titleOf(got.files.get(0).getName());
                final long bytes = size;
                final int count = got.files.size(), height = got.height;
                final int duration = item.clipped() ? item.clipEnd - item.clipStart : got.duration;
                Haul.update(c, id, i -> {
                    i.status = Haul.DONE;
                    i.error = "";
                    i.uris = uris;
                    i.uri = uris.get(0);
                    i.mime = m;
                    i.kind = kind;
                    i.sound = sound;
                    i.title = t;
                    i.count = count;
                    i.size = bytes;
                    i.duration = duration;
                    i.height = Haul.VIDEO.equals(kind) ? height : -1;
                    i.doneAt = System.currentTimeMillis();
                });
                notifyDone(Haul.get(c, id));
            } catch (Exception e) {
                if (stopped != 0) {
                    // stop() already marked it, with the reason.
                } else if (Engine.canceled(e) || isCanceled(id) || e instanceof InterruptedException) {
                    Haul.update(c, id, i -> {
                        i.status = Haul.CANCELED;
                        if (i.error.isEmpty()) i.error = "Canceled.";
                    });
                } else {
                    String why = NO_WIFI.equals(e.getMessage()) ? NO_WIFI : Engine.friendly(e);
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

        private Engine.Result fetch(Haul.Item item, File dir) throws Exception {
            return Engine.download(c, item, dir, p -> {
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
            String title = percent >= 0 ? "Downloading " + Math.round(percent) + "%" : Look.stage(item, percent) + "…";
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

        /** "Saved", with Share and Delete: straight from TikTok to WhatsApp without opening the Gallery. */
        private void notifyDone(Haul.Item i) {
            if (i == null) return;
            int nid = notifyIds.incrementAndGet();
            // A tap opens it in Haul's own player.
            Intent view = PlayerActivity.open(c, i.id, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent open = PendingIntent.getActivity(c, nid, view, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent share = PendingIntent.getActivity(c, nid, shareIntent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE);
            PendingIntent delete = PendingIntent.getBroadcast(c, nid, new Intent(c, DeleteReceiver.class)
                    .putExtra(DeleteReceiver.EXTRA_ID, i.id).putExtra(DeleteReceiver.EXTRA_NOTIFICATION, nid),
                    PendingIntent.FLAG_IMMUTABLE);
            String what = i.count <= 1 ? "Saved" : !Haul.PHOTOS.equals(i.kind) ? i.count + " files saved"
                    : (i.photos() == 1 ? "Photo" : i.photos() + " photos") + (i.sound ? " and the sound saved" : " saved");
            post(nid, new Notification.Builder(c, CH_DONE)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(i.title.isEmpty() ? i.site : i.title)
                    .setContentText(what + " to " + album(i) + " · " + i.site)
                    .setContentIntent(open)
                    .addAction(new Notification.Action.Builder(null, "Share", share).build())
                    .addAction(new Notification.Action.Builder(null, "Delete", delete).build())
                    .setAutoCancel(true)
                    .build());
        }

        /** "Couldn't download", with Try again (it opens Haul, which Android needs to start a download). */
        private void notifyFailed(Haul.Item i) {
            if (i == null) return;
            int nid = notifyIds.incrementAndGet();
            PendingIntent retry = PendingIntent.getActivity(c, nid, new Intent(c, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_RETRY, i.id).putExtra(MainActivity.EXTRA_NOTIFICATION, nid)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            post(nid, new Notification.Builder(c, CH_DONE)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("Couldn't download from " + i.site)
                    .setContentText(i.error)
                    .setStyle(new Notification.BigTextStyle().bigText(i.error))
                    .setContentIntent(openApp(c, 2))
                    .addAction(new Notification.Action.Builder(null, "Try again", retry).build())
                    .setAutoCancel(true)
                    .build());
        }

        private void post(int nid, Notification n) {
            c.getSystemService(NotificationManager.class).notify(nid, n);
        }
    }

    static PendingIntent openApp(Context c, int code) {
        return PendingIntent.getActivity(c, code, new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
    }

    /** The share sheet for a haul's files: one file, or all of a post's. */
    static Intent shareIntent(Haul.Item i) {
        List<Uri> uris = new ArrayList<>();
        for (String u : i.uris) if (!u.isEmpty() && !"null".equals(u)) uris.add(Uri.parse(u));
        if (uris.isEmpty() && !i.uri.isEmpty()) uris.add(Uri.parse(i.uri));
        String type = uris.size() > 1 ? (i.mime.isEmpty() || i.sound ? "*/*" : i.mime.split("/")[0] + "/*") : i.mime;
        return shareIntent(uris, type, i.title);
    }

    /** The share sheet for any saved files (several hauls at once, from the hold). */
    static Intent shareIntent(List<Uri> uris, String type, String title) {
        Intent send;
        if (uris.size() > 1) {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE).setType(type)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
        } else {
            send = new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris.isEmpty() ? null : uris.get(0));
        }
        if (!uris.isEmpty()) {
            ClipData clip = ClipData.newRawUri(title, uris.get(0));
            for (int k = 1; k < uris.size(); k++) clip.addItem(new ClipData.Item(uris.get(k)));
            send.setClipData(clip);
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    /** Where a haul's files are in the Gallery. */
    static String album(Haul.Item i) {
        if (i.sound) return "Pictures/Haul and Music/Haul";
        return Haul.AUDIO.equals(i.kind) ? "Music/Haul" : Haul.PHOTOS.equals(i.kind) ? "Pictures/Haul" : "Movies/Haul";
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
