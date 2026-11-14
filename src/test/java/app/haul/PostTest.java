package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class PostTest {
    /** Shaped like yt-dlp -J on an Instagram carousel: a playlist of photo entries (no formats) and video entries. */
    private static final String CAROUSEL = "{\"_type\":\"playlist\",\"title\":\"Post by studio\",\"entries\":["
            + "{\"id\":\"a\",\"formats\":[],\"thumbnails\":["
            + "{\"url\":\"https://cdn/a-150.jpg\",\"width\":150},{\"url\":\"https://cdn/a-480.jpg\",\"width\":480},"
            + "{\"url\":\"https://cdn/a-1080.jpg\",\"width\":1080}]},"
            + "{\"id\":\"b\",\"duration\":14.6,\"formats\":[{\"url\":\"v.mp4\"}],\"thumbnails\":[{\"url\":\"https://cdn/b.jpg\",\"width\":240}]},"
            + "{\"id\":\"c\",\"formats\":[],\"thumbnail\":\"https://cdn/c.jpg\"}]}";

    @Test
    public void carouselEntriesInOrder() throws Exception {
        Post p = Post.parse(new JSONObject(CAROUSEL));
        assertEquals("Post by studio", p.title);
        assertEquals(3, p.entries.size());
        assertEquals(1, p.entries.get(0).index);
        assertFalse(p.entries.get(0).video);
        assertTrue(p.entries.get(1).video);
        assertEquals(15, p.entries.get(1).duration);
        assertEquals(3, p.entries.get(2).index);
    }

    @Test
    public void gridPictureIsSmallestAtLeast320ElseLargest() throws Exception {
        Post p = Post.parse(new JSONObject(CAROUSEL));
        assertEquals("https://cdn/a-480.jpg", p.entries.get(0).thumbnail);
        assertEquals("https://cdn/b.jpg", p.entries.get(1).thumbnail);   // only a small one
        assertEquals("https://cdn/c.jpg", p.entries.get(2).thumbnail);   // just "thumbnail"
    }

    @Test
    public void aSingleVideoIsOneEntry() throws Exception {
        Post p = Post.parse(new JSONObject("{\"id\":\"x\",\"formats\":[{\"url\":\"v\"}],\"duration\":30}"));
        assertEquals(1, p.entries.size());
        assertTrue(p.entries.get(0).video);
        assertEquals(0, Post.parse(null).entries.size());
    }

    @Test
    public void aTikTokPhotoPostIsItsPicturesAndWhetherItHasSound() {
        String start = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{\"desc\":\"Three rooms\",";
        String images = "\"imagePost\":{\"images\":[{\"imageURL\":{\"urlList\":[\"https://p16/1.jpeg\"]}},"
                + "{\"imageURL\":{\"urlList\":[\"https://p16/2.jpeg\"]}}]}";
        String end = "}}}}}</script>";
        Post p = Post.tiktok(start + "\"music\":{\"playUrl\":\"https://v16/sound.mp3\"}," + images + end);
        assertEquals(2, p.entries.size());
        assertEquals(2, p.entries.get(1).index);
        assertEquals("https://p16/2.jpeg", p.entries.get(1).thumbnail);
        assertFalse(p.entries.get(0).video);
        assertEquals("Three rooms", p.title);
        assertTrue(p.sound);
        assertFalse(Post.tiktok(start + "\"music\":{\"playUrl\":\"\"}," + images + end).sound);
        assertFalse(Post.tiktok(start + images + end).sound);
        assertEquals(0, Post.tiktok("<html>no data</html>").entries.size());
    }

    @Test
    public void chosenPicturesComeBackInOrder() {
        assertEquals(Arrays.asList(1, 3, 4), Post.chosen("1,3,4", 5));
        assertEquals(Arrays.asList(1, 2, 3), Post.chosen("", 3));          // nothing chosen: all
        assertEquals(Arrays.asList(2), Post.chosen("2,2,9,x", 3));         // only those the post has, each once
        assertEquals(Arrays.asList(1, 2), Post.chosen("7,8", 2));          // the post changed: all of it
        assertTrue(Post.chosen(null, 0).isEmpty());
    }

    @Test
    public void chosenItemsForYtDlp() {
        assertEquals("1,3,4", Post.items(Arrays.asList(1, 3, 4)));
        assertEquals("2", Post.items(Arrays.asList(2)));
    }
}
