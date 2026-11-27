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

    @Test
    public void theDockGathersLinksCopiedOneByOne() {
        java.util.List<String> none = java.util.Collections.emptyList();
        String a = "https://www.tiktok.com/@a/video/7301234567890", b = "https://vm.tiktok.com/ZMb/",
                c = "https://www.tiktok.com/@c/video/7309999999999?is_from_webapp=1";
        java.util.List<String> one = Haul.collect(none, java.util.Arrays.asList(a), none);
        assertEquals(java.util.Arrays.asList(a), one);
        java.util.List<String> two = Haul.collect(one, java.util.Arrays.asList(b), none);
        assertEquals(java.util.Arrays.asList(a, b), two);
        // The same post copied again (another address for it) keeps its place; a new one goes last.
        java.util.List<String> three = Haul.collect(two, java.util.Arrays.asList("https://www.tiktok.com/@_/video/7301234567890", c), none);
        assertEquals(java.util.Arrays.asList(a, b, c), three);
        // Already sent from the dock: not taken again.
        assertTrue(Haul.collect(none, java.util.Arrays.asList(a), java.util.Arrays.asList(a, b)).isEmpty());
    }

    @Test
    public void theDockKeepsTheNewestThirty() {
        java.util.List<String> many = new java.util.ArrayList<>();
        for (int k = 0; k < 35; k++) many.add("https://x.com/a/status/" + (1000 + k));
        java.util.List<String> kept = Haul.collect(java.util.Collections.emptyList(), many, java.util.Collections.emptyList());
        assertEquals(Haul.COLLECT_MAX, kept.size());
        assertEquals("https://x.com/a/status/1005", kept.get(0));
        assertEquals("https://x.com/a/status/1034", kept.get(kept.size() - 1));
    }

    @Test
    public void whatComesBackFromABackup() {
        Haul.Item saved = new Haul.Item();
        saved.id = "a";
        saved.url = "https://www.tiktok.com/@a/video/7301234567890";
        saved.status = Haul.DONE;
        saved.uri = "content://media/1";
        saved.uris.add(saved.uri);
        assertEquals(Haul.DONE, Haul.restored(saved, true).status);   // its file is here

        Haul.Item gone = Haul.restored(saved, false);                   // on the old phone
        assertEquals(Haul.FAILED, gone.status);
        assertEquals(Haul.FROM_BACKUP, gone.error);
        assertTrue(gone.uris.isEmpty());

        Haul.Item status = new Haul.Item();
        status.id = "b";
        status.url = Statuses.link(Statuses.WHATSAPP, "x.jpg");
        status.status = Haul.DONE;
        assertNull(Haul.restored(status, false));                        // made there, can't be downloaded again

        Haul.Item still = new Haul.Item();
        still.id = "c";
        still.url = saved.url;
        still.quality = Haul.STILL;
        assertNull(Haul.restored(still, false));
        assertNull(Haul.restored(null, true));
    }
}
