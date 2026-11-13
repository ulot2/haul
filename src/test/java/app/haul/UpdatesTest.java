package app.haul;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdatesTest {
    @Test
    public void comparesVersionNumbers() {
        assertTrue(Updates.newer("1.2.0", "1.1.9"));
        assertTrue(Updates.newer("1.10.0", "1.9.0"));
        assertTrue(Updates.newer("2.0.0", "1.99.99"));
        assertFalse(Updates.newer("1.2.0", "1.2.0"));
        assertFalse(Updates.newer("1.1.0", "1.2.0"));
    }

    @Test
    public void devBuildsAreNotOfferedTheirOwnRelease() {
        assertTrue(Updates.newer("1.2.0", "1.1.0-dev.14"));
        assertFalse(Updates.newer("1.1.0", "1.1.0-dev.14"));
        assertTrue(Updates.newer("0.1.0", "0.0.0-local"));
    }
}
