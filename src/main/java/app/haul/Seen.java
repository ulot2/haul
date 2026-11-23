package app.haul;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells whether two links are the same post, to say "already in Haul" instead of saving it twice. Apps share one
 * post under many links: x.com and twitter.com, an Instagram /reel/ and /p/, with tracking added ("?igsh=…",
 * "?s=20", "?si=…"). So where the site's post id is in the link, the id is what counts; elsewhere, the address
 * without its query's tracking. Plain Java, unit-tested.
 */
final class Seen {
    private static final Pattern INSTAGRAM = Pattern.compile("instagram\\.com/(?:[\\w.]+/)?(?:p|reels?|tv)/([\\w-]+)");
    private static final Pattern X = Pattern.compile("(?:^|\\.)(?:x|twitter|fxtwitter|vxtwitter|fixupx)\\.com/[^/]+/status(?:es)?/(\\d+)");
    private static final Pattern YOUTUBE = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/)|youtu\\.be/)([\\w-]{11})");
    /** Query parameters that only say who shared a link, or from where. */
    private static final Pattern TRACKING = Pattern.compile(
            "(?i)^(utm_\\w+|igsh|igshid|si|s|t|fbclid|ref|ref_src|ref_url|feature|share_id|is_from_webapp|sender_device|_r|_t|mibextid|rdid|context)$");

    /** The post a link leads to, as "site:id" where the id is in the link, else as a tidied address; "" for nothing. */
    static String key(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String u = url.trim();
        String tiktok = Photos.tiktokId(u);
        if (tiktok != null) return "tiktok:" + tiktok;
        Matcher m = INSTAGRAM.matcher(u);
        if (m.find()) return "instagram:" + m.group(1);
        m = X.matcher(u.replaceFirst("^https?://", ""));
        if (m.find()) return "x:" + m.group(1);
        m = YOUTUBE.matcher(u);
        if (m.find()) return "youtube:" + m.group(1);
        return tidy(u);
    }

    /** Posts on the sites Haul knows, beyond the four whose ids key() reads (TikTok, Instagram, X, YouTube). */
    private static final Pattern POST = Pattern.compile("(?i)^https?://(?:[\\w-]+\\.)*(?:"
            + "(?:vm|vt)\\.tiktok\\.com/\\w+"                                     // TikTok short links
            + "|tiktok\\.com/t/\\w+"
            + "|instagram\\.com/(?:stories|share)/"
            + "|facebook\\.com/(?:.+/videos/|watch|reel/|share/|story\\.php)|fb\\.watch/"
            + "|reddit\\.com/r/[^/]+/(?:comments|s)/|redd\\.it/"
            + "|threads\\.(?:net|com)/@[^/]+/post/"
            + "|snapchat\\.com/(?:spotlight|t)/"
            + "|pinterest\\.[a-z.]+/pin/|pin\\.it/"
            + "|vimeo\\.com/\\d+"
            + ")");

    /**
     * True when a link looks like a post Haul can download (a video or photos on a site it knows), so it's worth
     * offering when copied. A link to anything else (a document, a shop, a news page) isn't offered; it can still be
     * pasted by hand, for the other sites yt-dlp reads.
     */
    static boolean isPost(String url) {
        if (url == null) return false;
        String k = key(url);
        if (k.startsWith("tiktok:") || k.startsWith("instagram:") || k.startsWith("x:") || k.startsWith("youtube:")) return true;
        return POST.matcher(url.trim()).find();
    }

    /** True when both links lead to the same post. */
    static boolean same(String a, String b) {
        String ka = key(a);
        return !ka.isEmpty() && ka.equals(key(b));
    }

    /** "https://www.Site.com/a/b/?utm_source=x&id=3#top" → "site.com/a/b?id=3". */
    static String tidy(String url) {
        String s = url.replaceFirst("(?i)^https?://", "").replaceFirst("#.*$", "");
        String query = "";
        int q = s.indexOf('?');
        if (q >= 0) {
            query = s.substring(q + 1);
            s = s.substring(0, q);
        }
        int slash = s.indexOf('/');
        String host = (slash < 0 ? s : s.substring(0, slash)).toLowerCase(Locale.ROOT).replaceFirst("^(www|m|mobile)\\.", "");
        String path = slash < 0 ? "" : s.substring(slash).replaceFirst("/+$", "");
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty() || TRACKING.matcher(part.replaceFirst("=.*$", "")).matches()) continue;
            kept.append(kept.length() == 0 ? "?" : "&").append(part);
        }
        return host + path + kept;
    }

    private Seen() {}
}
