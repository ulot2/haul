package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class PhotosTest {
    @Test
    public void instagramPhotoIsItsLargestThumbnail() throws Exception {
        // yt-dlp's Instagram extractor: no formats, thumbnails listed smallest to largest.
        JSONObject photo = new JSONObject("{\"id\":\"C9x\",\"formats\":[],\"thumbnails\":["
                + "{\"url\":\"https://cdn/s.jpg\",\"width\":320,\"height\":400},"
                + "{\"url\":\"https://cdn/m.jpg\",\"width\":640,\"height\":800},"
                + "{\"url\":\"https://cdn/l.jpg\",\"width\":1080,\"height\":1350}]}");
        assertEquals("https://cdn/l.jpg", Photos.bestImage(photo));
    }

    @Test
    public void withoutSizesTheLastThumbnailWins() throws Exception {
        JSONObject photo = new JSONObject("{\"thumbnails\":[{\"url\":\"a\"},{\"url\":\"b\"}]}");
        assertEquals("b", Photos.bestImage(photo));
    }

    @Test
    public void videosAndPlaylistsAreNotPhotos() throws Exception {
        assertNull(Photos.bestImage(new JSONObject("{\"formats\":[{\"url\":\"v.mp4\"}],\"thumbnails\":[{\"url\":\"t.jpg\"}]}")));
        assertNull(Photos.bestImage(new JSONObject("{\"_type\":\"playlist\",\"thumbnails\":[{\"url\":\"t.jpg\"}]}")));
        assertNull(Photos.bestImage(new JSONObject("{\"formats\":[]}")));
        assertNull(Photos.bestImage(null));
    }

    @Test
    public void tiktokPhotoIds() {
        assertEquals("7301234567", Photos.tiktokPhotoId("https://www.tiktok.com/@some.one/photo/7301234567?lang=en"));
        assertEquals("7301234567", Photos.tiktokPhotoId("https://www.tiktok.com/photo/7301234567"));
        assertNull(Photos.tiktokPhotoId("https://www.tiktok.com/@some.one/video/7301234567"));
        assertNull(Photos.tiktokPhotoId(null));
    }

    @Test
    public void slideshowPhotosFromThePrintedPage() {
        String html = "<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"statusCode\":0,\"itemInfo\":{\"itemStruct\":{"
                + "\"desc\":\"Three rooms\",\"imagePost\":{\"images\":["
                + "{\"imageURL\":{\"urlList\":[\"https://p16/1.jpeg\",\"https://p19/1.jpeg\"]}},"
                + "{\"imageURL\":{\"urlList\":[\"https://p16/2.jpeg\"]}}]}}}}}}"
                + "</script></html>";
        String printed = "[TikTok] Extracting URL: https://www.tiktok.com/@_/video/1\n"
                + "[TikTok] 1: Dumping request to https://www.tiktok.com/@_/video/1\n"
                + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)) + "\n"
                + "WARNING: [TikTok] 1: Requested format is not available\n";
        assertEquals(1, Photos.dumpedPages(printed).size());
        assertEquals(Arrays.asList("https://p16/1.jpeg", "https://p16/2.jpeg"), Photos.tiktokImages(Photos.dumpedPages(printed).get(0)));
    }

    @Test
    public void aPageWithoutPhotosGivesNone() {
        assertTrue(Photos.tiktokImages("<html>no data</html>").isEmpty());
        assertTrue(Photos.tiktokImages("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"__DEFAULT_SCOPE__\":{}}</script>").isEmpty());
        assertTrue(Photos.dumpedPages("Dumping request to x\nnot base64 at all!\n").isEmpty());
    }
}
