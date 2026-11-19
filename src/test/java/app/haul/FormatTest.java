package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {
    @Test
    public void clock() {
        assertEquals("0:00", Format.clock(0));
        assertEquals("1:20", Format.clock(80));
        assertEquals("42:10", Format.clock(2530));
        assertEquals("1:02:05", Format.clock(3725));
        assertEquals("–", Format.clock(-1));
    }

    @Test
    public void size() {
        assertEquals("–", Format.size(0));
        assertEquals("12 KB", Format.size(12 * 1024));
        assertEquals("18.2 MB", Format.size(19084288));
        assertEquals("148 MB", Format.size(148L * 1024 * 1024));
        assertEquals("1.50 GB", Format.size(1536L * 1024 * 1024));
    }

    @Test
    public void ticket() {
        assertEquals("0148", Format.ticket(148));
        assertEquals("12345", Format.ticket(12345));
    }

    @Test
    public void seconds() {
        assertEquals(80, Format.seconds("1:20"));
        assertEquals(80, Format.seconds("80"));
        assertEquals(3725, Format.seconds("1:02:05"));
        assertEquals(-1, Format.seconds("soon"));
        assertEquals(-1, Format.seconds(null));
    }

    @Test
    public void whatHappenedToAList() {
        assertEquals("Downloading 3 links", Format.many(3, 0, 0));
        assertEquals("Downloading 1 link · 2 already saved · 1 already in line", Format.many(1, 2, 1));
        assertEquals("2 already saved", Format.many(0, 2, 0));
        assertEquals("No links", Format.many(0, 0, 0));
    }
}
