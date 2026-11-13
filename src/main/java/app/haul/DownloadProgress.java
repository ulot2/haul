package app.haul;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One progress bar for a whole link. yt-dlp counts each file from 0 to 100 on its own: the video, then the audio it
 * joins to it, and for a post with several videos, each video in turn. Without this the bar jumps back to 0 partway.
 * Reads yt-dlp's output lines to know which file it is on. Plain Java, so it is unit-tested off the phone.
 */
final class DownloadProgress {
    private static final Pattern ITEM = Pattern.compile("^\\[download\\] Downloading (?:item|video) (\\d+) of (\\d+)");
    private static final Pattern FORMATS = Pattern.compile("^\\[info\\] .*Downloading \\d+ format\\(s\\): (\\S+)");
    /** The audio is small next to the video: it gets this share of the bar when the two are downloaded apart. */
    private static final float AUDIO_SHARE = 0.1f;

    private int item, items = 1;   // which post of several (from 0), and how many
    private int part = -1, parts = 1;   // which file of this post (-1 before the first), and how many
    private float best = -1;

    /**
     * Takes one line of yt-dlp's output and the percent the engine read from it (-1 when it has none), and returns
     * the whole link's progress from 0 to 100. It never goes back. -1 until the first file starts.
     */
    float on(String line, float percent) {
        if (line != null) {
            Matcher m = ITEM.matcher(line);
            if (m.find()) {
                items = Math.max(1, Integer.parseInt(m.group(2)));
                item = Math.min(Math.max(0, Integer.parseInt(m.group(1)) - 1), items - 1);
                part = -1;
                parts = 1;
            }
            m = FORMATS.matcher(line);
            if (m.find()) {
                // "137+140" is a video and an audio stream, downloaded one after the other and then joined.
                parts = m.group(1).split("\\+").length;
                part = -1;
            }
            if (line.startsWith("[download] Destination:")) part++;
        }
        if (percent < 0) return best;

        int p = Math.min(Math.max(part, 0), parts - 1);
        float before = 0, share = 1;
        if (parts > 1) {
            float video = (1 - AUDIO_SHARE) / (parts - 1);
            before = p * video;
            share = p == parts - 1 ? AUDIO_SHARE : video;
        }
        float post = before + share * Math.min(percent, 100f) / 100f;
        float total = Math.min(100f, (item + post) / items * 100f);
        if (total > best) best = total;
        return best;
    }
}
