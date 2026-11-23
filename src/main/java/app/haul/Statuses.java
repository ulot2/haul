package app.haul;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * WhatsApp statuses. When a status is viewed, WhatsApp keeps a copy in a hidden folder of its own on the phone, for
 * about a day: Android/media/com.whatsapp/WhatsApp/Media/.Statuses (WhatsApp Business: com.whatsapp.w4b, "WhatsApp
 * Business"). Since Android 11 an app reads another app's folder only once the user allows it in Android's folder
 * picker, which Haul opens inside that folder. Plain Java, unit-tested; StatusesActivity does the Android part.
 */
final class Statuses {
    static final String WHATSAPP = "com.whatsapp", BUSINESS = "com.whatsapp.w4b";
    /** A saved status's link in the hold: not a web link, so it is never downloaded again or offered to copy. */
    static final String SCHEME = "whatsapp-status:";
    private static final String STORAGE = "com.android.externalstorage.documents";

    /** The statuses folder as Android's file provider names it ("primary:Android/media/…/.Statuses"). */
    static String folderId(String app) {
        return BUSINESS.equals(app)
                ? "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
                : "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses";
    }

    /** Where the folder picker opens: inside the statuses folder, which it wouldn't show (its name starts with a dot). */
    static String pickerStart(String app) {
        return "content://" + STORAGE + "/document/" + encode(folderId(app));
    }

    /**
     * True for a folder the user picked that holds statuses: the one above, or where WhatsApp kept them before
     * Android 11 ("primary:WhatsApp/Media/.Statuses").
     */
    static boolean isStatusFolder(String treeDocumentId) {
        return treeDocumentId != null && treeDocumentId.endsWith("/Media/.Statuses");
    }

    /** Which WhatsApp a statuses folder belongs to. */
    static String appOf(String treeDocumentId) {
        String id = String.valueOf(treeDocumentId);
        return id.contains("com.whatsapp.w4b") || id.contains("WhatsApp Business") ? BUSINESS : WHATSAPP;
    }

    /** "WhatsApp" or "WhatsApp Business". */
    static String name(String app) {
        return BUSINESS.equals(app) ? "WhatsApp Business" : "WhatsApp";
    }

    /**
     * What a file in the folder is, by its type (as the provider gives it) or else its name: Haul.VIDEO, Haul.PHOTOS
     * or Haul.AUDIO (a voice status); null for anything else (".nomedia", a half-written file).
     */
    static String kindOf(String name, String mime) {
        if (name == null || name.startsWith(".")) return null;
        String m = mimeOf(name, mime);
        if (m.startsWith("video/")) return Haul.VIDEO;
        if (m.startsWith("image/")) return Haul.PHOTOS;
        if (m.startsWith("audio/")) return Haul.AUDIO;
        return null;
    }

    /** The file's type: the provider's, unless it gave none (or a vague one); else from the name's extension. */
    static String mimeOf(String name, String mime) {
        if (mime != null && mime.contains("/") && !mime.equals("application/octet-stream")) return mime;
        String n = String.valueOf(name).toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp4") || n.endsWith(".3gp")) return n.endsWith(".mp4") ? "video/mp4" : "video/3gpp";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".opus") || n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".m4a") || n.endsWith(".aac")) return "audio/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        return "";
    }

    /** A saved status's link in the hold: which WhatsApp, and the file's name (unique within the folder). */
    static String link(String app, String name) {
        return SCHEME + app + "/" + name;
    }

    /** True for a haul that is a saved status, not a download. */
    static boolean isStatus(String url) {
        return url != null && url.startsWith(SCHEME);
    }

    /** How a file's name reads as a document id in a URI: everything but letters and digits escaped, spaces as %20. */
    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private Statuses() {}
}
