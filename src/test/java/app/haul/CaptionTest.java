package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CaptionTest {
    @Test
    public void fromYtDlpsDetails() throws Exception {
        // An Instagram carousel: every entry carries the post's caption; the first that says wins.
        JSONObject first = new JSONObject("{\"description\":\"  Pasta, 3 ways 🍝\\n#recipe  \",\"uploader\":\"Some Cook\","
                + "\"channel\":\"somecook\",\"timestamp\":1763500000}");
        JSONObject second = new JSONObject("{\"description\":\"Pasta, 3 ways 🍝\\n#recipe\",\"uploader\":\"Some Cook\"}");
        Caption c = Caption.of(Arrays.asList(first, second));
        assertEquals("Pasta, 3 ways 🍝\n#recipe", c.text);
        assertEquals("Some Cook", c.by);
        assertEquals(1763500000000L, c.posted);
    }

    @Test
    public void whoAndWhenFallBack() throws Exception {
        Caption c = Caption.of(Arrays.asList(
                new JSONObject("{\"title\":\"no caption\"}"),
                new JSONObject("{\"description\":\"hi\",\"uploader_id\":\"someone\",\"upload_date\":\"20261119\"}")));
        assertEquals("hi", c.text);
        assertEquals("someone", c.by);
        assertEquals(Caption.day("20261119"), c.posted);
    }

    @Test
    public void aDateAloneIsNoonUtc() {
        assertEquals(1763553600000L, Caption.day("20251119"));   // 2025-11-19 12:00 UTC
        assertEquals(0, Caption.day("2025-11-19"));
        assertEquals(0, Caption.day(null));
    }

    @Test
    public void nothingKnown() {
        Caption c = Caption.of(Collections.emptyList());
        assertTrue(c.text.isEmpty());
        assertTrue(c.by.isEmpty());
        assertEquals(0, c.posted);
        assertEquals("", Caption.byOf(new JSONObject()));
    }

    @Test
    public void tiktokPhotoPostsFromTheirPage() {
        String page = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{"
                + "\"desc\":\"Three rooms, one weekend #diy\",\"createTime\":\"1763500000\","
                + "\"author\":{\"uniqueId\":\"some.one\",\"nickname\":\"Some One\"}}}}}}</script>";
        Caption c = Photos.tiktokCaption(page);
        assertEquals("Three rooms, one weekend #diy", c.text);
        assertEquals("some.one", c.by);
        assertEquals(1763500000000L, c.posted);
        assertFalse(Photos.tiktokCaption("<html>Please wait…</html>").by.length() > 0);
    }
}
