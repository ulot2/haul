package app.haul;

import java.util.Locale;

/**
 * Where a download's time went: starting the engine, reading the link, downloading, making the file (a clip, a
 * slideshow, a GIF) and saving it to the Gallery. Shown with a finished download, to see what is worth speeding up.
 * Plain Java, unit-tested.
 */
final class Stages {
    private static final String[] NAMES = {"start", "read", "get", "make", "save"};
    private static final int START = 0, READ = 1, GET = 2, MAKE = 3, SAVE = 4;
    private final long[] spent = new long[NAMES.length];
    private int stage = START;
    private long since;

    Stages(long now) {
        since = now;
    }

    /** The download reported progress: a percent, or one of Engine.Progress's stages. */
    void on(float progress, long now) {
        to(progress >= 0 ? GET : progress == Engine.Progress.READING ? READ : progress == Engine.Progress.WORKING ? MAKE : START, now);
    }

    /** The files are in; they are being copied into the Gallery. */
    void saving(long now) {
        to(SAVE, now);
    }

    private synchronized void to(int next, long now) {
        spent[stage] += Math.max(0, now - since);
        since = now;
        stage = next;
    }

    /** "12.4 s: start 2.1 · read 3.4 · get 6.5 · save 0.4" (seconds), leaving out stages that took no time. */
    synchronized String done(long now) {
        to(stage, now);
        long total = 0;
        StringBuilder parts = new StringBuilder();
        for (int k = 0; k < spent.length; k++) {
            total += spent[k];
            if (spent[k] < 50) continue;
            if (parts.length() > 0) parts.append(" · ");
            parts.append(NAMES[k]).append(' ').append(seconds(spent[k]));
        }
        return seconds(total) + " s" + (parts.length() > 0 ? ": " + parts : "");
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f", millis / 1000.0);
    }
}
