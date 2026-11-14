package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void xGifsAreToldFromXVideos() throws Exception {
        // yt-dlp's X extractor doesn't flag GIFs; their MP4s come from /tweet_video/, other videos from elsewhere.
        assertTrue(Photos.isGif(new JSONObject("{\"formats\":[{\"url\":\"https://video.twimg.com/tweet_video/AbC.mp4\"}]}")));
        assertTrue(Photos.isGif(new JSONObject("{\"thumbnail\":\"https://pbs.twimg.com/tweet_video_thumb/AbC.jpg\"}")));
        assertFalse(Photos.isGif(new JSONObject("{\"formats\":[{\"url\":\"https://video.twimg.com/ext_tw_video/1/pu/vid/720x1280/x.mp4\"}],"
                + "\"thumbnail\":\"https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/x.jpg\"}")));
        assertFalse(Photos.isGif(new JSONObject("{\"formats\":[{\"url\":\"https://video.twimg.com/amplify_video/1/vid/x.mp4\"}]}")));
        assertFalse(Photos.isGif(null));
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
        assertEquals("Three rooms", Photos.tiktokDesc(Photos.dumpedPages(printed).get(0)));
        assertEquals(Arrays.asList("https://p16/1.jpeg", "https://p16/2.jpeg"), Photos.tiktokImages(Photos.dumpedPages(printed).get(0)));
    }

    @Test
    public void theSoundOfATikTokPostAndItsFileType() {
        String page = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{"
                + "\"music\":{\"playUrl\":\"https://sf16/obj/sound.mp3\"}}}}}}</script>";
        assertEquals("https://sf16/obj/sound.mp3", Photos.tiktokSoundUrl(page));
        assertEquals("", Photos.tiktokSoundUrl("<html>no data</html>"));
        assertEquals("mp3", Photos.soundExt("audio/mpeg", "https://sf16/obj/abc"));
        assertEquals("m4a", Photos.soundExt("audio/mp4", "https://sf16/obj/abc"));
        assertEquals("m4a", Photos.soundExt("application/octet-stream", "https://sf16/obj/abc.m4a?x=1"));
        assertEquals("mp3", Photos.soundExt(null, "https://sf16/obj/abc"));
        assertNull(Photos.soundExt("text/html; charset=utf-8", "https://sf16/obj/abc.mp3"));   // a page saying no
    }

    @Test
    public void aPageWithoutPhotosGivesNone() {
        assertTrue(Photos.tiktokImages("<html>no data</html>").isEmpty());
        assertTrue(Photos.tiktokImages("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"__DEFAULT_SCOPE__\":{}}</script>").isEmpty());
        assertTrue(Photos.dumpedPages("Dumping request to x\nnot base64 at all!\n").isEmpty());
    }
}
