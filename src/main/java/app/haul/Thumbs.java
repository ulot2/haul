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

    /**
     * Shows a full-size photo from the web in a small cell about width pixels wide (the picker, for a TikTok photo
     * post, whose page lists only the photos themselves): decoded at a fraction of its size, so a post of 35 fits in
     * memory. TikTok serves its pictures to its own pages, so the request says it comes from one.
     */
    static void showPhoto(ImageView view, String url, int width) {
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
                con.setRequestProperty("User-Agent", Engine.BROWSER);
                con.setRequestProperty("Referer", "https://www.tiktok.com/");
                byte[] bytes;
                try (InputStream in = con.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[32 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    bytes = out.toByteArray();
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                o.inSampleSize = sample(o.outWidth, width);
                o.inJustDecodeBounds = false;
                b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            } catch (Exception | OutOfMemoryError ignored) {
                // no preview; the tone stays
            } finally {
                if (con != null) con.disconnect();
            }
            deliver(url, b, view);
        });
    }

    /** The largest power of two to shrink a picture by that still leaves it at least as wide as its cell. */
    static int sample(int pictureWidth, int cellWidth) {
        int s = 1;
        while (cellWidth > 0 && pictureWidth / (s * 2) >= cellWidth) s *= 2;
        return s;
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
