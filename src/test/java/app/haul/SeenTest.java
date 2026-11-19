package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeenTest {
    @Test
    public void instagramPostsByTheirCode() {
        assertTrue(Seen.same("https://www.instagram.com/p/DfpcTxGSftw/?igsh=abc", "https://instagram.com/p/DfpcTxGSftw"));
        assertTrue(Seen.same("https://www.instagram.com/reel/DfAbvKXS1s5/?gwpx=x", "https://www.instagram.com/p/DfAbvKXS1s5/"));
        assertTrue(Seen.same("https://www.instagram.com/someone/reel/DfAbvKXS1s5/", "https://www.instagram.com/reels/DfAbvKXS1s5"));
        assertFalse(Seen.same("https://www.instagram.com/p/DfpcTxGSftw/", "https://www.instagram.com/p/DfAbvKXS1s5/"));
    }

    @Test
    public void xPostsWhicheverAddress() {
        assertTrue(Seen.same("https://x.com/someone/status/1858012345678901234?s=20", "https://twitter.com/someone/status/1858012345678901234"));
        assertTrue(Seen.same("https://mobile.twitter.com/a/status/1858012345678901234/video/1", "https://fxtwitter.com/a/status/1858012345678901234"));
        assertFalse(Seen.same("https://x.com/a/status/1", "https://x.com/a/status/2"));
    }

    @Test
    public void tiktokPostsByTheirId() {
        assertTrue(Seen.same("https://www.tiktok.com/@some.one/video/7301234567890?is_from_webapp=1", "https://www.tiktok.com/@_/photo/7301234567890"));
        // A short link is its own address until it is looked up; the same short link is the same post.
        assertTrue(Seen.same("https://vm.tiktok.com/ZMabc/", "https://vm.tiktok.com/ZMabc"));
        assertFalse(Seen.same("https://vm.tiktok.com/ZMabc/", "https://vm.tiktok.com/ZMxyz/"));
    }

    @Test
    public void youtubeByTheVideo() {
        assertTrue(Seen.same("https://youtu.be/dQw4w9WgXcQ?si=xyz", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"));
        assertTrue(Seen.same("https://m.youtube.com/shorts/dQw4w9WgXcQ", "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"));
    }

    @Test
    public void otherSitesWithoutTheTracking() {
        assertEquals("example.com/v/1?id=3", Seen.tidy("https://www.Example.com/v/1/?utm_source=x&id=3#top"));
        assertTrue(Seen.same("https://www.reddit.com/r/a/comments/xyz/title/?share_id=1", "https://reddit.com/r/a/comments/xyz/title"));
        assertFalse(Seen.same("https://site.com/watch?id=1", "https://site.com/watch?id=2"));
    }

    @Test
    public void nothingIsNotAPost() {
        assertFalse(Seen.same("", ""));
        assertFalse(Seen.same(null, null));
        assertFalse(Seen.same("https://x.com/a/status/1", ""));
    }
}
