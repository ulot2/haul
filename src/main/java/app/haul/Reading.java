package app.haul;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A link shared from another app that has to be read before it can download: a TikTok short link (where does it
 * lead?), a TikTok /video/ link (photos or a video? TikTok's Share button hands over /video/ links for photo posts
 * too), a TikTok photo post (which pictures, and how to save them?), an Instagram post (several photos and videos?).
 *
 * Reading used to cover the app the user was in, with a sheet, until it was done. Now the link goes into the hold at
 * once (while the share screen is still up, so Android lets the download queue start), marked to be read first, and
 * the user carries on: the queue reads it alongside any download (Downloads.Runner), while the Badge floats over the
 * app (or the queue's notification shows it). Then it either downloads like any link, or, when the post has
 * something to pick from, waits as CHOOSE: the badge pulses, a notification says so, and a tap opens the picker
 * (Choose.choose) over whatever app is on screen.
 */
final class Reading {
    static final String CH_CHOOSE = "choose";

    /** What was read of a post waiting for the user to pick from it. Kept while Haul runs; gone, it is read again. */
    static final class Pending {
        final String url;
        final Post post;
        final boolean tiktok;

        Pending(String url, Post post, boolean tiktok) {
            this.url = url;
            this.post = post;
            this.tiktok = tiktok;
        }
    }

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    /** True when this link has to be read before it can download; a section to clip never does. */
    static boolean needed(String url, String quality, int clipStart) {
        if (clipStart >= 0) return false;
        String site = Haul.site(url);
        if ("TikTok".equals(site)) {
            boolean shortLink = Photos.tiktokPhotoId(url) == null && !url.contains("/video/");
            return shortLink || !Haul.AUDIO.equals(quality);   // the sound alone needs no pictures picked
        }
        return "Instagram".equals(site) && url.contains("/p/") && !Haul.AUDIO.equals(quality);
    }

    static Pending pending(String id) {
        return PENDING.get(id);
    }

    /** The user picked (or the haul went): nothing waits any more. */
    static void forget(Context c, String id) {
        PENDING.remove(id);
        c.getSystemService(NotificationManager.class).cancel(notificationId(id));
    }

    /**
     * Reads the haul's post, on the queue's reader thread, and leaves it either waiting to download (QUEUED, at the
     * link that downloads it) or waiting for the user (CHOOSE). The same decisions as Choose makes on screen.
     */
    static void decide(Context c, Haul.Item item) {
        String link = item.url, quality = item.quality;
        try {
            Engine.init(c);
        } catch (Exception ignored) {
            // the reading below fails in turn, and the link downloads as it is
        }
        if ("TikTok".equals(item.site)) {
            String url = link;
            if (Photos.tiktokPhotoId(link) == null && !link.contains("/video/")) {
                String full = Engine.resolve(link);
                if (full.startsWith("http")) url = full;
            }
            String id = Photos.tiktokPhotoId(url) == null ? Photos.tiktokId(url) : null;
            if (id != null && !Haul.AUDIO.equals(quality)) {
                Post post = Engine.tiktokPhotosOf(c, link, id);
                if (post == null || post.entries.isEmpty()) queue(c, item, link);   // a video
                else choose(c, item, Photos.tiktokPhotoLink(url, id), post, true);
            } else if (Photos.tiktokPhotoId(url) == null) {
                queue(c, item, link);   // a video: the link as given, so what was read ahead is found again
            } else if (Haul.AUDIO.equals(quality)) {
                queue(c, item, url);    // the sound of a photo post: no pictures to pick
            } else {
                Post post;
                try {
                    post = Engine.readTikTok(c, url);
                } catch (Exception e) {
                    post = new Post();  // the pictures can't be shown: the picker still asks how to save them all
                }
                choose(c, item, url, post, true);
            }
            return;
        }
        if ("Instagram".equals(item.site)) {
            Post post;
            try {
                post = Post.parse(Engine.read(c, link));
            } catch (Exception e) {
                post = null;
            }
            if (post != null) post.withMusic(Engine.music(c, link));
            // One photo or video, without a song: as it is. With a song, or several: the user picks.
            if (post == null || post.entries.size() <= 1 && !post.sound) queue(c, item, link);
            else choose(c, item, link, post, false);
            return;
        }
        queue(c, item, link);
    }

    private static void queue(Context c, Haul.Item item, String url) {
        Haul.update(c, item.id, i -> {
            if (!Haul.WORKING.equals(i.status)) return;   // canceled meanwhile
            i.url = url;
            i.decide = false;
            i.status = Haul.QUEUED;
        });
    }

    private static void choose(Context c, Haul.Item item, String url, Post post, boolean tiktok) {
        boolean[] still = {false};
        Haul.update(c, item.id, i -> {
            if (!Haul.WORKING.equals(i.status)) return;   // canceled meanwhile
            still[0] = true;
            i.url = url;
            i.decide = false;
            i.status = Haul.CHOOSE;
            if (!post.title.isEmpty()) i.title = post.title;
        });
        if (!still[0]) return;
        PENDING.put(item.id, new Pending(url, post, tiktok));
        Haul.Item now = Haul.get(c, item.id);
        if (now != null) notifyChoose(c, now, post, tiktok);
    }

    /** "Pick from the TikTok post": over the app the user is in (high importance), until it's picked. */
    private static void notifyChoose(Context c, Haul.Item i, Post post, boolean tiktok) {
        int n = post.entries.size();
        String what = tiktok
                ? (n > 0 ? n + " pictures. Tap to pick them, and how to save them." : "Tap to choose how to save its pictures.")
                : n + " photos and videos. Tap to pick the ones to keep.";
        PendingIntent open = PendingIntent.getActivity(c, notificationId(i.id), open(c, i.id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(c, CH_CHOOSE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Pick from the " + i.site + " post")
                .setContentText(what)
                .setStyle(new Notification.BigTextStyle().bigText((i.title.isEmpty() ? "" : i.title + "\n") + what))
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(notificationId(i.id), note);
    }

    /** The picker for a haul waiting as CHOOSE, over whatever app is on screen (the share screen, see-through). */
    static Intent open(Context c, String id) {
        return new Intent(c, ShareActivity.class)
                .setAction(ShareActivity.ACTION_CHOOSE)
                .putExtra(ShareActivity.EXTRA_CHOOSE, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    static int notificationId(String id) {
        return 50_000 + Math.abs(id.hashCode() % 40_000);
    }

    private Reading() {}
}
