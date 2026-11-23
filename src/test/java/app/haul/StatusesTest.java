package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusesTest {
    @Test
    public void thePickerOpensInsideTheFolder() {
        assertEquals("content://com.android.externalstorage.documents/document/"
                        + "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses",
                Statuses.pickerStart(Statuses.WHATSAPP));
        assertEquals("content://com.android.externalstorage.documents/document/"
                        + "primary%3AAndroid%2Fmedia%2Fcom.whatsapp.w4b%2FWhatsApp%20Business%2FMedia%2F.Statuses",
                Statuses.pickerStart(Statuses.BUSINESS));
    }

    @Test
    public void onlyAStatusesFolderWillDo() {
        assertTrue(Statuses.isStatusFolder("primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"));
        assertTrue(Statuses.isStatusFolder("primary:WhatsApp/Media/.Statuses"));   // before Android 11
        assertFalse(Statuses.isStatusFolder("primary:Android/media/com.whatsapp/WhatsApp/Media"));
        assertFalse(Statuses.isStatusFolder("primary:Download"));
        assertFalse(Statuses.isStatusFolder(null));
        assertEquals(Statuses.BUSINESS, Statuses.appOf("primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"));
        assertEquals(Statuses.WHATSAPP, Statuses.appOf("primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"));
    }

    @Test
    public void photosVideosAndVoice() {
        assertEquals(Haul.VIDEO, Statuses.kindOf("a1b2c3.mp4", "video/mp4"));
        assertEquals(Haul.PHOTOS, Statuses.kindOf("a1b2c3.jpg", null));
        assertEquals(Haul.PHOTOS, Statuses.kindOf("a1b2c3.webp", "application/octet-stream"));
        assertEquals(Haul.AUDIO, Statuses.kindOf("a1b2c3.opus", ""));
        assertNull(Statuses.kindOf(".nomedia", null));
        assertNull(Statuses.kindOf("notes.txt", "text/plain"));
        assertEquals("audio/ogg", Statuses.mimeOf("v.opus", null));
    }

    @Test
    public void savedStatusesAreNotLinks() {
        String link = Statuses.link(Statuses.WHATSAPP, "a1b2c3.mp4");
        assertEquals("whatsapp-status:com.whatsapp/a1b2c3.mp4", link);
        assertTrue(Statuses.isStatus(link));
        assertFalse(Statuses.isStatus("https://x.com/a/status/1"));
        assertFalse(Statuses.isStatus(null));
    }
}
