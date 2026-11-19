package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HaulTest {
    @Test
    public void findsTheLinkInSharedText() {
        assertEquals("https://vm.tiktok.com/ZMabc/", Haul.firstLink("Watch this! https://vm.tiktok.com/ZMabc/ #fyp"));
        assertEquals("https://x.com/a/status/1", Haul.firstLink("(see https://x.com/a/status/1)."));
        assertEquals("http://example.com/v?id=1&t=2", Haul.firstLink("http://example.com/v?id=1&t=2"));
    }

    @Test
    public void noLink() {
        assertNull(Haul.firstLink(null));
        assertNull(Haul.firstLink("just words, www.example.com without a scheme"));
    }

    @Test
    public void savedTheSameWay() {
        assertTrue(Haul.sameWay(Haul.BEST, Haul.SMALL));
        assertTrue(Haul.sameWay(Haul.AUDIO, Haul.AUDIO));
        assertFalse(Haul.sameWay(Haul.BEST, Haul.AUDIO));
        assertFalse(Haul.sameWay(Haul.GIF, Haul.BEST));
    }

    @Test
    public void everyLinkInAList() {
        String notes = "for later:\nhttps://www.tiktok.com/@a/video/7301234567890\n"
                + "https://x.com/b/status/1858012345678901234?s=20, and https://www.instagram.com/p/DfpcTxGSftw/.\n"
                + "again: https://twitter.com/b/status/1858012345678901234";
        assertEquals(java.util.Arrays.asList("https://www.tiktok.com/@a/video/7301234567890",
                "https://x.com/b/status/1858012345678901234?s=20", "https://www.instagram.com/p/DfpcTxGSftw/"), Haul.links(notes));
        assertEquals(1, Haul.links("Watch this! https://vm.tiktok.com/ZMabc/ #fyp").size());
        assertTrue(Haul.links("no links here").isEmpty());
        assertTrue(Haul.links(null).isEmpty());
    }
}
