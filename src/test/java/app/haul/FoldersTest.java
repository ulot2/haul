package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FoldersTest {
    @Test
    public void oneFolderBySiteOrByCreator() {
        assertEquals("", Folders.sub(Folders.ONE, "TikTok", "some.one"));
        assertEquals("TikTok", Folders.sub(Folders.SITE, "TikTok", "some.one"));
        assertEquals("some.one", Folders.sub(Folders.CREATOR, "TikTok", "some.one"));
        assertEquals("Instagram", Folders.sub(Folders.CREATOR, "Instagram", ""));   // who isn't known: the site's
        assertEquals("", Folders.sub(null, "TikTok", "x"));
    }

    @Test
    public void namesTheGalleryTakes() {
        assertEquals("AC DC fan club", Folders.clean("AC/DC: fan club"));
        assertEquals("hidden", Folders.clean("..hidden"));
        assertEquals(40, Folders.clean("a very long creator name that goes on and on and on").length());
        assertEquals("", Folders.clean(null));
    }
}
