package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SlideshowTest {
    @Test
    public void runsForTheSoundButShowsEveryPictureOnce() {
        assertEquals(15, Slideshow.seconds(5, 0));          // no sound: one pass, 3 s each
        assertEquals(15, Slideshow.seconds(5, 9_000));      // short sound: still one pass
        assertEquals(31, Slideshow.seconds(5, 30_400));     // long sound: as long as the sound
        assertEquals(600, Slideshow.seconds(3, 3_600_000)); // at most 10 minutes
    }

    @Test
    public void aPicturesVideoRunsAsLongAsAskedOrAsTheSound() {
        assertEquals(10, Slideshow.length(10, 47_300));     // asked: that long, whatever the sound
        assertEquals(10, Slideshow.length(10, 0));
        assertEquals(48, Slideshow.length(0, 47_300));      // not asked: as long as the sound
        assertEquals(3, Slideshow.length(0, 0));            // no sound: a few seconds
        assertEquals(600, Slideshow.length(9_999, 0));      // at most 10 minutes
    }

    @Test
    public void concatLoopsThePicturesAndRepeatsTheLast() {
        String list = Slideshow.concat(Arrays.asList("/d/1.jpg", "/d/2.jpg"), 8);
        assertEquals("ffconcat version 1.0\n"
                + "file '/d/1.jpg'\nduration 3\n"
                + "file '/d/2.jpg'\nduration 3\n"
                + "file '/d/1.jpg'\nduration 2\n"
                + "file '/d/1.jpg'\n", list);
    }

    @Test
    public void oneRoundEndToEndFillsTheLength() {
        assertEquals("ffconcat version 1.0\nfile '/d/round.mp4'\nfile '/d/round.mp4'\nfile '/d/round.mp4'\nfile '/d/round.mp4'\n",
                Slideshow.repeat("/d/round.mp4", 3, 10));   // 4 rounds of 3 s cover 10 s; FFmpeg cuts at 10
        assertEquals("ffconcat version 1.0\nfile '/d/round.mp4'\n", Slideshow.repeat("/d/round.mp4", 3, 3));
    }

    @Test
    public void quotesInPathsAreEscaped() {
        assertTrue(Slideshow.concat(Arrays.asList("/d/it's.jpg"), 3).contains("file '/d/it'\\''s.jpg'"));
    }
}
