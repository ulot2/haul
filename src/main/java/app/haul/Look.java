package app.haul;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** How a haul reads on screen, shared by the hold's rows and the haul's sheet. */
final class Look {
    private static final int[] TONES = {R.color.tone0, R.color.tone1, R.color.tone2, R.color.tone3, R.color.tone4, R.color.tone5};

    /** The picture box: a steady tone per haul, the site's tag, and the saved file's own frame once there is one. */
    static void picture(Context c, View box, ImageView image, ImageView icon, TextView tag, Haul.Item i, int w, int h) {
        box.setBackgroundTintList(ColorStateList.valueOf(c.getColor(TONES[Math.abs(i.id.hashCode()) % TONES.length])));
        box.setClipToOutline(true);
        tag.setText(tag(i.site));
        boolean audio = Haul.AUDIO.equals(i.kind) || (!Haul.DONE.equals(i.status) && Haul.AUDIO.equals(i.quality));
        boolean picture = Haul.DONE.equals(i.status) && !audio && !i.uri.isEmpty();
        if (picture) {
            Thumbs.show(c, image, i.uri, w, h);
        } else {
            image.setTag(null);
            image.setImageDrawable(null);
        }
        icon.setVisibility(audio ? View.VISIBLE : View.GONE);
        if (audio) icon.setImageResource(R.drawable.ic_music);
    }

    /** "TT", "IG", "X"… for the corner of the picture. */
    static String tag(String site) {
        switch (site) {
            case "TikTok": return "TT";
            case "Instagram": return "IG";
            case "X": return "X";
            case "YouTube": return "YT";
            case "Facebook": return "FB";
            case "Reddit": return "RD";
            case "Snapchat": return "SC";
            case "Threads": return "TH";
            case "Pinterest": return "PN";
            case "Vimeo": return "VM";
            default: return site.length() > 2 ? site.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : site.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** What a haul is doing before there is a percent, in the notification and the hold. */
    static String stage(Haul.Item i, float progress) {
        if (progress == Engine.Progress.READING) return Haul.PHOTOS.equals(i.kind) || !i.as.isEmpty() ? "Reading the post" : "Reading the link";
        if (progress == Engine.Progress.WORKING) {
            if (Haul.AS_SLIDESHOW.equals(i.as)) return "Making the video";
            if (Haul.GIF.equals(i.quality)) return "Making the GIF";
            if (i.clipped()) return "Cutting the section";
            return "X".equals(i.site) ? "Making the GIF" : "Downloading";   // X GIFs arrive as video, made into GIFs
        }
        return "Starting";
    }

    static String qualityName(String q) {
        return Haul.AUDIO.equals(q) ? "Audio" : Haul.SMALL.equals(q) ? "720p" : Haul.GIF.equals(q) ? "GIF" : "Best";
    }

    /** What was saved, or asked for: "1080p", "Audio · M4A", "5 photos", plus "· clip 1:20–2:05". */
    static String what(Haul.Item i) {
        String s;
        if (Haul.DONE.equals(i.status)) {
            boolean gif = i.mime.equals("image/gif");
            if (Haul.PHOTOS.equals(i.kind)) {
                s = i.photos() == 1 ? (gif ? "GIF" : "Photo") : i.photos() + (gif ? " GIFs" : " photos");
                if (i.sound) s += " + sound";
            }
            else if (Haul.AUDIO.equals(i.kind)) s = "Audio · M4A";
            else s = (i.height > 0 ? i.height + "p" : qualityName(i.quality)) + (i.count > 1 ? " · " + i.count + " files" : "");
        } else {
            s = Haul.AUDIO.equals(i.quality) ? "Audio" : qualityName(i.quality);
        }
        if (i.clipped()) s += " · clip " + Format.clock(i.clipStart) + "–" + Format.clock(i.clipEnd);
        return s;
    }

    /** "Today 09:12", "Yesterday", "Nov 11". */
    static String when(Context c, long time) {
        if (time <= 0) return "–";
        if (DateUtils.isToday(time)) return "Today " + DateUtils.formatDateTime(c, time, DateUtils.FORMAT_SHOW_TIME);
        if (DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS)) return "Yesterday";
        return DateUtils.formatDateTime(c, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
    }

    /** "tiktok.com/@user/video/73…" without the https:// and the tracking query. */
    static String shortLink(String url) {
        String s = url.replaceFirst("^https?://(www\\.)?", "");
        int q = s.indexOf('?');
        return q > 0 ? s.substring(0, q) : s;
    }

    static String title(Haul.Item i) {
        return i.title.isEmpty() ? shortLink(i.url) : i.title;
    }

    private Look() {}
}
