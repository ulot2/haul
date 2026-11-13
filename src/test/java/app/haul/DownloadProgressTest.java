package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Feeds lines shaped like yt-dlp's real output, with the percent the engine library reads from each (-1 if none). */
public class DownloadProgressTest {
    private final DownloadProgress p = new DownloadProgress();

    private float line(String line) {
        return p.on(line, -1);
    }

    private float at(float percent) {
        return p.on(String.format(java.util.Locale.ROOT, "[download]  %.1f%% of 10.00MiB at 1.00MiB/s ETA 00:05", percent), percent);
    }

    @Test
    public void nothingUntilTheFirstFileStarts() {
        assertEquals(-1, line("[TikTok] Extracting URL: https://www.tiktok.com/@a/video/1"), 0);
        assertEquals(-1, line("[info] 1: Downloading 1 format(s): bytevc1_720p"), 0);
    }

    @Test
    public void singleFileIsPassedThrough() {
        line("[info] 1: Downloading 1 format(s): h264_540p");
        line("[download] Destination: /cache/haul/x/Cat [1].mp4");
        assertEquals(0, at(0), 0.01);
        assertEquals(42, at(42), 0.01);
        assertEquals(100, at(100), 0.01);
    }

    @Test
    public void videoThenAudioMakeOneBarThatNeverGoesBack() {
        line("[info] abc: Downloading 1 format(s): 137+140");
        line("[download] Destination: /cache/haul/x/Cat [abc].f137.mp4");
        assertEquals(45, at(50), 0.01);
        assertEquals(90, at(100), 0.01);
        line("[download] Destination: /cache/haul/x/Cat [abc].f140.m4a");
        assertEquals(90, at(0), 0.01);   // the audio starts at 0, the bar stays put
        assertEquals(95, at(50), 0.01);
        assertEquals(100, at(100), 0.01);
        line("[Merger] Merging formats into \"/cache/haul/x/Cat [abc].mp4\"");
        assertEquals(100, line("Deleting original file /cache/haul/x/Cat [abc].f137.mp4"), 0.01);
    }

    @Test
    public void severalVideosInOnePostShareTheBar() {
        line("[download] Downloading item 1 of 2");
        line("[info] a: Downloading 1 format(s): 1+2");
        line("[download] Destination: a.f1.mp4");
        assertEquals(45, at(100), 0.01);   // half the bar per video, and this video's own video stream is 90% of it
        line("[download] Destination: a.f2.m4a");
        assertEquals(50, at(100), 0.01);
        line("[download] Downloading item 2 of 2");
        line("[info] b: Downloading 1 format(s): hd");
        line("[download] Destination: b.mp4");
        assertEquals(75, at(50), 0.01);
        assertEquals(100, at(100), 0.01);
    }

    @Test
    public void aRetriedFragmentDoesNotMoveTheBarBack() {
        line("[download] Destination: a.mp4");
        assertEquals(60, at(60), 0.01);
        assertEquals(60, at(30), 0.01);
    }
}
