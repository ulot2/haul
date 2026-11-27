package app.haul;

import java.util.Locale;

/** How numbers read on screen: lengths, sizes, ticket numbers. Plain Java, unit-tested. */
final class Format {
    /** 80 → "1:20", 3725 → "1:02:05". */
    static String clock(int seconds) {
        if (seconds < 0) return "–";
        int h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** 19084288 → "18.2 MB". */
    static String size(long bytes) {
        if (bytes <= 0) return "–";
        if (bytes < 1000 * 1024) return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        double mb = bytes / (1024.0 * 1024);
        if (mb < 100) return String.format(Locale.ROOT, "%.1f MB", mb);
        if (mb < 1000) return Math.round(mb) + " MB";
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024);
    }

    /** A moment to a tenth of a second, for clipping sounds: 12345 (ms) → "0:12.3", 3725000 → "1:02:05.0". */
    static String exact(long millis) {
        long tenths = Math.max(0, Math.round(millis / 100.0));
        int seconds = (int) (tenths / 10);
        return clock(seconds) + "." + (tenths % 10);
    }

    /** 30000 (ms) → "30.0 s", 95500 → "1:35.5". */
    static String span(long millis) {
        return millis < 60_000 ? String.format(Locale.ROOT, "%.1f s", Math.round(millis / 100.0) / 10.0) : exact(millis);
    }

    /** A clock time today, as the phone shows it in 24 hours: "14:32". */
    static String time(long millis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return String.format(Locale.ROOT, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
    }

    /** 148 → "0148". */
    static String ticket(int no) {
        return String.format(Locale.ROOT, "%04d", Math.max(0, no));
    }

    /** "1:20" or "80" (seconds) → 80; -1 when it isn't a time. */
    static int seconds(String text) {
        if (text == null || !text.trim().matches("\\d+(:\\d{1,2}){0,2}")) return -1;
        int total = 0;
        for (String part : text.trim().split(":")) total = total * 60 + Integer.parseInt(part);
        return total;
    }

    /**
     * What happened to a list of links: "Downloading 3 links · 1 already saved · 1 already in line"; when none was
     * new, only what they already were.
     */
    static String many(int added, int saved, int waiting) {
        StringBuilder s = new StringBuilder();
        if (added > 0) s.append("Downloading ").append(added).append(added == 1 ? " link" : " links");
        if (saved > 0) s.append(s.length() > 0 ? " · " : "").append(saved).append(" already saved");
        if (waiting > 0) s.append(s.length() > 0 ? " · " : "").append(waiting).append(" already in line");
        return s.length() == 0 ? "No links" : s.toString();
    }

    private Format() {}
}
