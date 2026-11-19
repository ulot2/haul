package app.haul;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FindTest {
    private static final String[] PASTA = {"Pasta, 3 ways", "Pasta, 3 ways 🍝\n#recipe #dinner", "some.cook", "Instagram",
            "https://www.instagram.com/p/DfpcTxGSftw/", "", "No. 0148"};

    @Test
    public void everyWordSomewhereInAnyOrder() {
        assertTrue(Find.matches("pasta", PASTA));
        assertTrue(Find.matches("RECIPE pasta", PASTA));
        assertTrue(Find.matches("#dinner instagram", PASTA));
        assertFalse(Find.matches("pasta tiktok", PASTA));
    }

    @Test
    public void creatorsLinksAndTicketNumbers() {
        assertTrue(Find.matches("@some.cook", PASTA));
        assertTrue(Find.matches("DfpcTxGSftw", PASTA));
        assertTrue(Find.matches("148", PASTA));
        assertTrue(Find.matches("0148", PASTA));
    }

    @Test
    public void accentsAndCaseDontMatter() {
        assertTrue(Find.matches("cafe", "Best Café in Lisbon"));
        assertTrue(Find.matches("CAFÉ", "best cafe in lisbon"));
    }

    @Test
    public void nothingTypedShowsEverything() {
        assertTrue(Find.matches("", PASTA));
        assertTrue(Find.matches("   ", PASTA));
        assertTrue(Find.matches(null, PASTA));
        assertFalse(Find.matches("x", (String) null));
    }
}
