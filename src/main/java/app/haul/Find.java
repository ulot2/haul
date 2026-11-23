package app.haul;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Searching the hold: every word typed has to be somewhere in what a download knows (its title, caption, creator,
 * site, link and ticket number), in any order. Case and accents don't matter, so "cafe" finds "Café"; a leading "@"
 * is the same as none, so "@some.one" finds the creator some.one. Plain Java, unit-tested.
 */
final class Find {
    /** True when every word of the query is in one of the texts; an empty query matches everything. */
    static boolean matches(String query, String... texts) {
        String q = fold(query).trim();
        if (q.isEmpty()) return true;
        StringBuilder hay = new StringBuilder();
        for (String t : texts) if (t != null) hay.append(fold(t)).append('\n');
        for (String word : q.split("\\s+")) {
            String w = word.replaceFirst("^@+", "");
            if (!w.isEmpty() && hay.indexOf(w) < 0) return false;
        }
        return true;
    }

    /** Lower case, without accents. */
    static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private Find() {}
}
