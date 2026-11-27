package app.haul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RetryTest {
    @Test
    public void instagramLimitsAreWaitedOut() {
        assertTrue(Retry.limited("Instagram", "ERROR: [Instagram] DfpcTxGSftw: Requested content is not available, "
                + "rate-limit reached or login required. Use --cookies, --cookies-from-browser"));
        assertTrue(Retry.limited("Instagram", "ERROR: Please wait a few minutes before you try again."));
        assertTrue(Retry.limited("TikTok", "ERROR: Unable to download webpage: HTTP Error 429: Too Many Requests"));
    }

    @Test
    public void otherFailuresAreNot() {
        assertFalse(Retry.limited("Instagram", "ERROR: This post was deleted"));
        assertFalse(Retry.limited("TikTok", "ERROR: login required"));   // only Instagram's message is ambiguous
        assertFalse(Retry.limited("X", "Unsupported URL"));
        assertFalse(Retry.limited("Instagram", null));
    }

    @Test
    public void waitsGrow() {
        assertEquals(2 * 60_000L, Retry.wait(1));
        assertEquals(5 * 60_000L, Retry.wait(2));
        assertEquals(10 * 60_000L, Retry.wait(3));
        assertEquals(10 * 60_000L, Retry.wait(9));
        assertEquals(3, Retry.MAX);
    }
}
