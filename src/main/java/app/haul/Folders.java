package app.haul;

/**
 * Where in the Gallery a new download goes, inside Movies/Haul, Pictures/Haul or Music/Haul: all together (ONE), in a
 * folder per site ("TikTok"), or in a folder per creator ("some.one"; the site's, when the post doesn't say who).
 * Plain Java, unit-tested.
 */
final class Folders {
    static final String ONE = "one", SITE = "site", CREATOR = "creator";

    /** The folder under …/Haul for a download, or "" for none: a name Android and the Gallery take as it is. */
    static String sub(String mode, String site, String by) {
        if (SITE.equals(mode)) return clean(site);
        if (CREATOR.equals(mode)) {
            String who = clean(by);
            return who.isEmpty() ? clean(site) : who;
        }
        return "";
    }

    /** A name safe for a folder: no slashes or other characters Android or Windows refuse, no leading dots, at most 40. */
    static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim().replaceFirst("^[.\\s]+", "");
        if (t.length() > 40) t = t.substring(0, 40).trim();
        return t;
    }

    private Folders() {}
}
