package app.haul;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures for the hold: the saved file's own frame, made by Android from the Gallery (no download needed), and the
 * clip sheet's preview from the web. Loaded off the main thread, kept in a small memory cache.
 */
final class Thumbs {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<String> MISSING = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Shows the picture of a saved video or photo in view, sized about w×h pixels; leaves it empty if there is none. */
    static void show(Context c, ImageView view, String uri, int w, int h) {
        view.setTag(uri);
        Bitmap hit = CACHE.get(uri);
        view.setImageBitmap(hit);
        if (hit != null || MISSING.contains(uri)) return;
        Context app = c.getApplicationContext();
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                b = app.getContentResolver().loadThumbnail(Uri.parse(uri), new Size(w, h), null);
            } catch (Exception ignored) {
                // deleted from the Gallery, or not Haul's any more
            }
            deliver(uri, b, view);
        });
    }

    /** Shows a picture from the web (the clip sheet's preview). */
    static void showUrl(ImageView view, String url) {
        view.setTag(url);
        Bitmap hit = CACHE.get(url);
        view.setImageBitmap(hit);
        if (hit != null || url == null || url.isEmpty() || MISSING.contains(url)) return;
        POOL.execute(() -> {
            Bitmap b = null;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10_000);
                con.setReadTimeout(15_000);
                try (InputStream in = con.getInputStream()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;   // previews are small; thumbnails from sites are often large
                    b = BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception ignored) {
                // no preview; the tone stays
            } finally {
                if (con != null) con.disconnect();
            }
            deliver(url, b, view);
        });
    }

    private static void deliver(String key, Bitmap b, ImageView view) {
        if (b == null) {
            MISSING.add(key);
            return;
        }
        CACHE.put(key, b);
        MAIN.post(() -> {
            if (key.equals(view.getTag())) view.setImageBitmap(b);
        });
    }

    /** A file was deleted: forget its picture. */
    static void forget(String uri) {
        CACHE.remove(uri);
        MISSING.remove(uri);
    }

    private Thumbs() {}
}
