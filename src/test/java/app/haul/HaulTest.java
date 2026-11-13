package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
