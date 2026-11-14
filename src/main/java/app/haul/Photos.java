package app.haul;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Photo posts. yt-dlp is built for video, so photos are found two ways:
 * - Instagram (and other sites yt-dlp knows): a photo comes through as an entry with no video formats, and its
 *   "thumbnails" are the photo itself in several sizes. Haul asks yt-dlp to write each entry's info JSON and takes
 *   the largest.
 * - TikTok slideshows: yt-dlp doesn't read them, but it can fetch the post's page (it gets past TikTok's checks) and
 *   print it. The photo list sits in the page's rehydration data, under imagePost.images.
 * Plain Java with org.json, unit-tested.
 */
final class Photos {
    private static final Pattern TIKTOK_PHOTO = Pattern.compile("tiktok\\.com/(?:@([\\w.-]*)/)?photo/(\\d+)");
    private static final Pattern REHYDRATION = Pattern.compile(
            "<script[^>]+id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL);

    /** The best photo URL of one info-JSON entry, or null when the entry is a video (it has formats) or has none. */
    static String bestImage(JSONObject entry) {
        if (entry == null || "playlist".equals(entry.optString("_type"))) return null;
        JSONArray formats = entry.optJSONArray("formats");
        if (formats != null && formats.length() > 0) return null;
        JSONArray thumbs = entry.optJSONArray("thumbnails");
        if (thumbs == null || thumbs.length() == 0) return null;
        String best = null;
        long bestArea = -1;
        for (int k = 0; k < thumbs.length(); k++) {
            JSONObject t = thumbs.optJSONObject(k);
            if (t == null || t.optString("url").isEmpty()) continue;
            long area = (long) t.optInt("width", 0) * t.optInt("height", 0);
            // yt-dlp lists thumbnails worst to best, so on a tie (or no sizes) the later one wins.
            if (area >= bestArea) {
                bestArea = area;
                best = t.optString("url");
            }
        }
        return best;
    }

    /**
     * True for a post that is a GIF: X keeps GIFs as silent MP4s, served from /tweet_video/ (its other videos come from
     * /ext_tw_video/ or /amplify_video/), with a tweet_video_thumb picture.
     */
    static boolean isGif(JSONObject info) {
        if (info == null) return false;
        if (info.optString("thumbnail").contains("/tweet_video_thumb/")) return true;
        JSONArray formats = info.optJSONArray("formats");
        for (int k = 0; formats != null && k < formats.length(); k++) {
            JSONObject f = formats.optJSONObject(k);
            if (f != null && f.optString("url").contains("video.twimg.com/tweet_video/")) return true;
        }
        return false;
    }

    /** The id of a TikTok photo post ("/@user/photo/7301234"), or null. */
    static String tiktokPhotoId(String url) {
        Matcher m = url == null ? null : TIKTOK_PHOTO.matcher(url);
        return m != null && m.find() ? m.group(2) : null;
    }

    /** The pages yt-dlp printed with --dump-pages: each follows a "Dumping request to …" line, base64-encoded. */
    static List<String> dumpedPages(String out) {
        List<String> pages = new ArrayList<>();
        if (out == null) return pages;
        String[] lines = out.split("\n");
        for (int k = 0; k + 1 < lines.length; k++) {
            if (!lines[k].contains("Dumping request to ")) continue;
            try {
                pages.add(new String(Base64.getDecoder().decode(lines[k + 1].trim()), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // not base64 after all
            }
        }
        return pages;
    }

    /** The post in a TikTok page's rehydration data; null when the page has none. */
    private static JSONObject tiktokItem(String html) {
        Matcher m = REHYDRATION.matcher(html == null ? "" : html);
        if (!m.find()) return null;
        try {
            return new JSONObject(m.group(1)).optJSONObject("__DEFAULT_SCOPE__")
                    .optJSONObject("webapp.video-detail").optJSONObject("itemInfo").optJSONObject("itemStruct");
        } catch (Exception e) {
            return null;   // the page changed shape
        }
    }

    /** True when a TikTok post page names a sound to play under the pictures. */
    static boolean tiktokHasSound(String html) {
        JSONObject item = tiktokItem(html);
        JSONObject music = item == null ? null : item.optJSONObject("music");
        return music != null && !music.optString("playUrl").isEmpty();
    }

    /** The photo URLs of a TikTok slideshow page, in order; empty when the page has none. */
    static List<String> tiktokImages(String html) {
        List<String> urls = new ArrayList<>();
        JSONObject item = tiktokItem(html);
        if (item == null) return urls;
        try {
            JSONArray images = item.optJSONObject("imagePost").optJSONArray("images");
            for (int k = 0; images != null && k < images.length(); k++) {
                JSONArray list = images.optJSONObject(k).optJSONObject("imageURL").optJSONArray("urlList");
                if (list != null && list.length() > 0) urls.add(list.optString(0));
            }
        } catch (Exception ignored) {
            // the page changed shape: no photos
        }
        return urls;
    }

    /** The caption of a TikTok post page, for the title; "" when there is none. */
    static String tiktokDesc(String html) {
        JSONObject item = tiktokItem(html);
        String d = item == null ? "" : item.optString("desc").trim();
        return d.length() > 80 ? d.substring(0, 80).trim() + "…" : d;
    }

    private Photos() {}
}
