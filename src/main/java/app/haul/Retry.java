package app.haul;

import java.util.Locale;

/**
 * Instagram limits downloads for a while to anyone not signed in (yt-dlp: "rate-limit reached or login required",
 * HTTP 429). Haul doesn't sign in, so instead of failing it waits and tries again by itself: after 2, then 5, then 10
 * minutes. Only then does the download fail. Plain Java, unit-tested.
 */
final class Retry {
    /** How many times a limited download is tried again before it fails. */
    static final int MAX = 3;
    private static final long[] WAIT = {2 * 60_000L, 5 * 60_000L, 10 * 60_000L};

    /** True when a download failed because the site is limiting downloads for now, not for good. */
    static boolean limited(String site, String error) {
        if (error == null) return false;
        String e = error.toLowerCase(Locale.ROOT);
        if (e.contains("http error 429") || e.contains("too many requests")) return true;
        // Instagram says "rate-limit reached or login required" for both a limit and a private post: worth waiting.
        return "Instagram".equals(site) && (e.contains("rate-limit") || e.contains("rate limit") || e.contains("login required")
                || e.contains("please wait a few minutes"));
    }

    /** How long to wait before try number n (1, 2, 3) after a limit. */
    static long wait(int n) {
        return WAIT[Math.max(0, Math.min(WAIT.length - 1, n - 1))];
    }

    private Retry() {}
}
