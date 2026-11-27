package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class InstaMusicTest {
    private static final String PAGE = "{\"data\":{\"xdt_api__v1__media__shortcode__web_info\":{\"items\":[{\"code\":\"DfpcTxGSftw\","
            + "\"carousel_media_count\":3,\"music_metadata\":{\"audio_type\":\"licensed_music\",\"music_info\":{"
            + "\"music_asset_info\":{\"title\":\"Song Name\",\"display_artist\":\"Some Artist\","
            + "\"progressive_download_url\":\"https:\\/\\/scontent.cdninstagram.com\\/v\\/song.m4a?a=1\\u0026b=2\","
            + "\"duration_in_ms\":180000},\"music_consumption_info\":{\"audio_asset_start_time_in_ms\":42000,"
            + "\"overlap_duration_in_ms\":15000}}}}]}}}";

    @Test
    public void theSongAndThePartThePostPlays() {
        InstaMusic.Music m = InstaMusic.parse(Arrays.asList("<html>a login page</html>", PAGE));
        assertEquals("https://scontent.cdninstagram.com/v/song.m4a?a=1&b=2", m.url);
        assertEquals("Song Name", m.title);
        assertEquals("Some Artist", m.artist);
        assertEquals("Song Name · Some Artist", m.name());
        assertEquals(42000, m.from);
        assertEquals(15000, m.length);
    }

    @Test
    public void keptAndReadBack() throws Exception {
        InstaMusic.Music back = InstaMusic.of(InstaMusic.json(InstaMusic.parse(Collections.singletonList(PAGE))));
        assertEquals(42000, back.from);
        assertEquals("Some Artist", back.artist);
    }

    @Test
    public void noSongNoMusic() {
        assertNull(InstaMusic.parse(Collections.singletonList("{\"items\":[{\"code\":\"x\",\"image_versions2\":{}}]}")));
        assertNull(InstaMusic.parse(Collections.singletonList("{\"music_metadata\":null}")));
        assertNull(InstaMusic.parse(null));
        assertNull(InstaMusic.of(null));
    }
}
