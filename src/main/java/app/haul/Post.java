package app.haul;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What a post holds, from yt-dlp's details of the link (yt-dlp -J): one item, or several (an Instagram carousel).
 * The picker shows these; the download then takes the chosen ones with yt-dlp's -I. Plain Java, unit-tested.
 */
final class Post {
    /** One photo or video of the post. */
    static final class Entry {
        int index;           // 1-based, as yt-dlp's -I counts
        boolean video;
        int duration = -1;   // seconds, for videos
        String thumbnail = "";
    }

    final List<Entry> entries = new ArrayList<>();
    String title = "";

    static Post parse(JSONObject info) {
        Post p = new Post();
        if (info == null) return p;
        p.title = info.optString("title");
        JSONArray list = "playlist".equals(info.optString("_type")) ? info.optJSONArray("entries") : null;
        if (list == null) {
            p.entries.add(entry(info, 1));
            return p;
        }
        for (int k = 0; k < list.length(); k++) {
            JSONObject e = list.optJSONObject(k);
            if (e != null) p.entries.add(entry(e, k + 1));
        }
        return p;
    }

    private static Entry entry(JSONObject e, int index) {
        Entry x = new Entry();
        x.index = index;
        JSONArray formats = e.optJSONArray("formats");
        x.video = formats != null && formats.length() > 0;
        x.duration = (int) Math.round(e.optDouble("duration", -1));
        x.thumbnail = gridPicture(e);
        return x;
    }

    /** A thumbnail for a small grid cell: the smallest one at least 320 wide, else the largest there is. */
    private static String gridPicture(JSONObject e) {
        JSONArray thumbs = e.optJSONArray("thumbnails");
        String fit = null, largest = e.optString("thumbnail", "");
        int fitWidth = Integer.MAX_VALUE, largestWidth = -1;
        for (int k = 0; thumbs != null && k < thumbs.length(); k++) {
            JSONObject t = thumbs.optJSONObject(k);
            if (t == null || t.optString("url").isEmpty()) continue;
            int w = t.optInt("width", 0);
            if (w >= 320 && w < fitWidth) {
                fitWidth = w;
                fit = t.optString("url");
            }
            if (w >= largestWidth) {
                largestWidth = w;
                largest = t.optString("url");
            }
        }
        return fit != null ? fit : largest;
    }

    /** "1,3,4" for yt-dlp's -I, from the chosen 1-based indexes. */
    static String items(List<Integer> chosen) {
        StringBuilder b = new StringBuilder();
        for (int i : chosen) {
            if (b.length() > 0) b.append(',');
            b.append(i);
        }
        return b.toString();
    }
}
