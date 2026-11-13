package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadsTest {
    @Test
    public void titleDropsTheIdAndExtension() {
        assertEquals("Funny cat", Downloads.titleOf("Funny cat [7301234].mp4"));
        assertEquals("Song [live]", Downloads.titleOf("Song [live] [abc].m4a"));
        assertEquals("[only-id].mp4", Downloads.titleOf("[only-id].mp4"));
    }
}
