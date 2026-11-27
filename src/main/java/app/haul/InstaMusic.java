package app.haul;

import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The song on an Instagram photo post, from what Instagram sends when the post is read (yt-dlp prints those pages with
 * --dump-pages). Instagram keeps it under "music_metadata" (or "music_info"): the song's file
 * ("progressive_download_url"), where the part it plays starts ("audio_asset_start_time_in_ms") and how long it plays
 * ("overlap_duration_in_ms"), and its title and artist. Read by the names, wherever they sit, since Instagram moves
 * things around; whether it sends them to someone not signed in is up to Instagram. Plain Java, unit-tested.
 */
final class InstaMusic {
    /** A post's song, and the part of it the post plays (milliseconds; length 0 when Instagram doesn't say). */
    static final class Music {
        String url = "", title = "", artist = "";
        long from, length;

        /** "Song · Artist", or what there is of it. */
        String name() {
            return title.isEmpty() ? artist : artist.isEmpty() ? title : title + " · " + artist;
        }
    }

    private static final Pattern URL = text("progressive_download_url");
    private static final Pattern TITLE = text("title");
    private static final Pattern ARTIST = text("display_artist");
    private static final Pattern FROM = number("audio_asset_start_time_in_ms");
    private static final Pattern LENGTH = number("overlap_duration_in_ms");
    /** How far past "music_metadata" the song's details are looked for. */
    private static final int REACH = 30_000;

    /** The song of the first page that has one, or null when Instagram sent none. */
    static Music parse(List<String> pages) {
        if (pages == null) return null;
        for (String raw : pages) {
            if (raw == null) continue;
            // A web page may carry the post's data inside a script's string, its quotes escaped (\"music_metadata\").
            String page = raw.contains("\"music_metadata\"") || raw.contains("\"music_info\"") ? raw : unescape(raw);
            int at = page.indexOf("\"music_metadata\"");
            if (at < 0) at = page.indexOf("\"music_info\"");
            if (at < 0) continue;
            String near = page.substring(at, Math.min(page.length(), at + REACH));
            String url = first(URL, near);
            if (!url.startsWith("http")) continue;
            Music m = new Music();
            m.url = url;
            int asset = near.indexOf("\"music_asset_info\"");
            String info = asset >= 0 ? near.substring(asset) : near;
            m.title = first(TITLE, info);
            m.artist = first(ARTIST, info);
            m.from = Math.max(0, firstNumber(FROM, near));
            m.length = Math.max(0, firstNumber(LENGTH, near));
            return m;
        }
        return null;
    }

    /** A page's escaped JSON made plain: \" to ", \/ to /, and \\ to \. */
    static String unescape(String s) {
        return s.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\\/", "/").replace("\u0000", "\\");
    }

    /** As kept beside the post's details: {"url":…,"title":…,"artist":…,"from":…,"length":…}. */
    static JSONObject json(Music m) throws Exception {
        return new JSONObject().put("url", m.url).put("title", m.title).put("artist", m.artist).put("from", m.from).put("length", m.length);
    }

    static Music of(JSONObject o) {
        if (o == null || !o.optString("url").startsWith("http")) return null;
        Music m = new Music();
        m.url = o.optString("url");
        m.title = o.optString("title");
        m.artist = o.optString("artist");
        m.from = o.optLong("from");
        m.length = o.optLong("length");
        return m;
    }

    private static Pattern text(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    }

    private static Pattern number(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(\\d+)");
    }

    /** The first value of a JSON string field, unescaped ("\/" and "&" as Instagram writes them); "" if none. */
    private static String first(Pattern p, String in) {
        Matcher m = p.matcher(in);
        if (!m.find()) return "";
        try {
            return new JSONObject("{\"v\":\"" + m.group(1) + "\"}").getString("v").trim();
        } catch (Exception e) {
            return m.group(1).replace("\\/", "/").trim();
        }
    }

    private static long firstNumber(Pattern p, String in) {
        Matcher m = p.matcher(in);
        try {
            return m.find() ? Long.parseLong(m.group(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private InstaMusic() {}
}
