package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EngineTest {
    private static String friendly(String ytDlpError) {
        return Engine.friendly(new Exception(ytDlpError));
    }

    private static final String DELETED = "This post was deleted or isn't available.";
    private static final String OFFLINE = "Couldn't reach the site. Check your connection and try again.";
    private static final String SIGN_IN =
            "This post needs a signed-in account (private, age-restricted, or the site is limiting downloads). Try again later.";

    @Test
    public void deletedPostsSayDeletedNotOffline() {
        // yt-dlp wraps HTTP errors in "Unable to download ...", which once matched the network message first.
        assertEquals(DELETED, friendly("ERROR: [TikTok] 7301234: Unable to download webpage: HTTP Error 404: Not Found"));
        assertEquals(DELETED, friendly("ERROR: [generic] Unable to download webpage: HTTP Error 410: Gone"));
        assertEquals(DELETED, friendly("ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader"));
    }

    @Test
    public void forbiddenIsARefusalNotOffline() {
        assertEquals("The site refused the download. Try again later.",
                friendly("ERROR: [twitter] 123: Unable to download JSON metadata: HTTP Error 403: Forbidden"));
    }

    @Test
    public void geoBlockedSaysCountryEvenWhenUnavailable() {
        assertEquals("This post isn't available in your country.",
                friendly("ERROR: [youtube] abc: Video unavailable. The uploader has not made this video available in your country"));
    }

    @Test
    public void realNetworkErrorsStillSayOffline() {
        assertEquals(OFFLINE, friendly(
                "ERROR: [TikTok] 1: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"));
        assertEquals(OFFLINE, friendly("ERROR: [TikTok] 1: Unable to download webpage: The read operation timed out"));
        assertEquals(OFFLINE, friendly("ERROR: [Instagram] 1: Unable to download webpage: HTTP Error 502: Bad Gateway"));
    }

    @Test
    public void signInAndRateLimits() {
        assertEquals(SIGN_IN, friendly("ERROR: [Instagram] C1: Requested content is not available, rate-limit reached or login required"));
        assertEquals(SIGN_IN, friendly("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users."));
        assertEquals(SIGN_IN, friendly("ERROR: [TikTok] 1: Unable to download webpage: HTTP Error 429: Too Many Requests"));
    }

    @Test
    public void fullStorageEvenInsideADownloadError() {
        assertEquals("The phone is out of storage.",
                friendly("ERROR: unable to download video data: [Errno 28] No space left on device"));
        assertEquals("The phone is out of storage.", friendly("no space"));   // what HaulService.save throws on ENOSPC
    }

    @Test
    public void noVideoAndUnsupportedSites() {
        assertEquals("Haul found no video or photos in this post.", friendly("NO_FILES"));
        assertEquals("Haul found no video or photos in this post.",
                friendly("ERROR: [twitter] 1: No video could be found in this tweet"));
        assertEquals("Haul can't read links from this site.", friendly("ERROR: Unsupported URL: https://example.com/x"));
    }

    @Test
    public void haulsOwnFfmpegErrorsAreShownAsTheyAre() {
        // An FFmpeg error saying "not found" once read as "This post was deleted".
        String m = Engine.MADE + "CANNOT LINK EXECUTABLE: library \"libavdevice.so.61\" not found";
        assertEquals(m, friendly(m));
    }

    @Test
    public void otherwiseYtDlpsOwnLastErrorWithoutPrefix() {
        assertEquals("Something odd happened", friendly("WARNING: meh\nERROR: [vimeo] 123: Something odd happened"));
    }

    @Test
    public void ext() {
        assertEquals("mp4", Engine.ext("Cat [1].MP4"));
        assertEquals("", Engine.ext("no-extension"));
    }

    @Test
    public void aLinksDetailsGiveTheClipSheetItsTitleLengthAndPicture() throws Exception {
        Engine.Info one = Engine.info(new org.json.JSONObject("{\"title\":\"Cat\",\"duration\":61.6,\"thumbnail\":\"https://i/best.jpg\"}"));
        assertEquals("Cat", one.title);
        assertEquals(62, one.duration);
        assertEquals("https://i/best.jpg", one.thumbnail);
        // A post with several items: the first. No "thumbnail": the last (best) of the list. No length: -1.
        Engine.Info first = Engine.info(new org.json.JSONObject("{\"_type\":\"playlist\",\"title\":\"Post\",\"entries\":["
                + "{\"title\":\"First\",\"thumbnails\":[{\"url\":\"https://i/s.jpg\"},{\"url\":\"https://i/l.jpg\"}]},{\"title\":\"Second\"}]}"));
        assertEquals("First", first.title);
        assertEquals(-1, first.duration);
        assertEquals("https://i/l.jpg", first.thumbnail);
    }
}
