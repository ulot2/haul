package app.haul;

import java.util.List;
import java.util.Locale;

/**
 * A TikTok photo post as a video, the way TikTok plays it: each picture for a few seconds, looping through them for
 * as long as the sound lasts, in a 1080×1920 frame. Plans FFmpeg's input; plain Java, unit-tested.
 */
final class Slideshow {
    static final int SECONDS_EACH = 3;
    private static final int MAX_SECONDS = 600;

    /** How long the video runs: the sound's length, but at least one pass through the pictures, and at most 10 minutes. */
    static int seconds(int pictures, long soundMillis) {
        int once = Math.max(1, pictures) * SECONDS_EACH;
        int sound = (int) Math.ceil(Math.max(0, soundMillis) / 1000.0);
        return Math.min(MAX_SECONDS, Math.max(once, sound));
    }

    /** FFmpeg's concat list: the pictures in turn, SECONDS_EACH apiece, repeated to fill total seconds. */
    static String concat(List<String> paths, int total) {
        StringBuilder b = new StringBuilder("ffconcat version 1.0\n");
        if (paths.isEmpty()) return b.toString();
        int shown = 0;
        String last = paths.get(0);
        for (int k = 0; shown < total; k++) {
            last = paths.get(k % paths.size());
            int each = Math.min(SECONDS_EACH, total - shown);
            b.append("file '").append(last.replace("'", "'\\''")).append("'\n");
            b.append(String.format(Locale.ROOT, "duration %d\n", each));
            shown += each;
        }
        // The concat demuxer ignores the last duration unless the last file is listed once more.
        b.append("file '").append(last.replace("'", "'\\''")).append("'\n");
        return b.toString();
    }

    /** Fits any picture into the portrait frame, black around it, at 30 frames a second, in a pixel format every player reads. */
    static final String FILTER =
            "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,fps=30,format=yuv420p";

    /**
     * Video to GIF: 15 frames a second, at most 480 wide, with a palette made for this clip (a GIF has 256 colors),
     * dithered so gradients don't band.
     */
    static final String GIF_FILTER = "fps=15,scale='min(480,iw)':-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];"
            + "[b][p]paletteuse=dither=bayer:bayer_scale=4";

    private Slideshow() {}
}
