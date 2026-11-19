package app.haul;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * What a post said, and who posted it when: kept with a download, so the text (a recipe, a song's name, a place)
 * isn't lost once the post is. From yt-dlp's info JSON: "description" (the caption on TikTok, Instagram and X),
 * "uploader" and "timestamp" or "upload_date". Plain Java with org.json, unit-tested.
 */
final class Caption {
    static final Caption NONE = new Caption("", "", 0);

    final String text, by;
    /** When it was posted, in milliseconds; 0 when the site doesn't say. */
    final long posted;

    Caption(String text, String by, long posted) {
        this.text = text == null ? "" : text.trim();
        this.by = by == null ? "" : by.trim();
        this.posted = Math.max(0, posted);
    }

    /**
     * From the info JSON of each file of a post: the first caption there is (a carousel's entries all carry the
     * post's), and who posted it and when from the first entry that says.
     */
    static Caption of(List<JSONObject> infos) {
        String text = "", by = "";
        long posted = 0;
        for (JSONObject info : infos) {
            if (info == null) continue;
            if (text.isEmpty()) text = info.optString("description").trim();
            if (by.isEmpty()) by = byOf(info);
            if (posted == 0) posted = postedOf(info);
        }
        return new Caption(text, by, posted);
    }

    /** The name to show: the account's name where the site gives one (TikTok's is the @handle), else its handle. */
    static String byOf(JSONObject info) {
        for (String key : new String[]{"uploader", "channel", "creator", "uploader_id"}) {
            String v = info.optString(key).trim();
            if (!v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    /** "timestamp" (seconds), else "upload_date" ("20261119"), as milliseconds; 0 when neither is there. */
    static long postedOf(JSONObject info) {
        long ts = info.optLong("timestamp", 0);
        if (ts > 0) return ts * 1000;
        return day(info.optString("upload_date"));
    }

    /**
     * "20261119" as noon UTC that day: the date alone, so noon keeps it on the same day wherever the phone is
     * (from 12 hours behind UTC to 12 ahead). 0 when it isn't a date.
     */
    static long day(String yyyymmdd) {
        if (yyyymmdd == null || !yyyymmdd.matches("\\d{8}")) return 0;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(Integer.parseInt(yyyymmdd.substring(0, 4)), Integer.parseInt(yyyymmdd.substring(4, 6)) - 1,
                Integer.parseInt(yyyymmdd.substring(6, 8)), 12, 0, 0);
        return cal.getTimeInMillis();
    }
}
