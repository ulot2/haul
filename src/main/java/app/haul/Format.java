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

    private Format() {}
}
