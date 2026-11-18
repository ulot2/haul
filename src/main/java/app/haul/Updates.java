package app.haul;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks GitHub for a newer Haul release, at most every 6 hours. The result goes into prefs for MainActivity. */
final class Updates {
    private static final String LATEST = "https://api.github.com/repos/ulot2/haul/releases/latest";

    /** Checks when the last successful check was over 6 hours ago; after a failed one (offline), 15 minutes later. */
    static void checkSoon(Context c, Runnable done) {
        long now = System.currentTimeMillis();
        if (now - Haul.prefs(c).getLong("update_checked", 0) < 6 * 3600_000L) return;
        if (now - Haul.prefs(c).getLong("update_tried", 0) < 15 * 60_000L) return;
        Haul.prefs(c).edit().putLong("update_tried", now).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            if (!check(app)) return;
            Haul.prefs(app).edit().putLong("update_checked", System.currentTimeMillis()).apply();
            done.run();
        }).start();
    }

    private static boolean check(Context c) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(LATEST).openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(15_000);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            JSONObject release;
            try (InputStream in = con.getInputStream()) {
                release = new JSONObject(Haul.readAll(in));
            } finally {
                con.disconnect();
            }
            String version = release.getString("tag_name").replaceFirst("^v", "");
            String apk = "";
            String wanted = apkName(android.os.Process.is64Bit());
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (wanted.equals(a.optString("name"))) apk = a.optString("browser_download_url");
            }
            boolean newer = !apk.isEmpty() && newer(version, installed(c));
            Haul.prefs(c).edit()
                    .putString("update_version", newer ? version : "")
                    .putString("update_url", newer ? apk : "")
                    .apply();
            return true;
        } catch (Exception ignored) {
            return false;   // offline or GitHub unreachable: try again at the next check
        }
    }

    /**
     * The newer version to offer, or "" when there is none. What the last check found is kept until the next check (6
     * hours on), and the phone may have installed it since: then it is no longer newer, and is forgotten here.
     */
    static String offered(Context c) {
        String found = Haul.prefs(c).getString("update_version", "");
        if (found.isEmpty() || newer(found, installed(c))) return found;
        Haul.prefs(c).edit().putString("update_version", "").putString("update_url", "").apply();
        return "";
    }

    /**
     * The release file for this kind of Haul: Haul.apk when it runs 64-bit, Haul-32bit.apk when it runs 32-bit (a phone
     * with a 32-bit Android, which can't install the 64-bit one). An update stays the kind that is installed.
     */
    static String apkName(boolean is64Bit) {
        return is64Bit ? "Haul.apk" : "Haul-32bit.apk";
    }

    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    /** Compares the numbers before any "-" suffix: "1.2.0" is newer than "1.1.0-dev.14". */
    static boolean newer(String latest, String current) {
        int[] a = parts(latest), b = parts(current);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    private static int[] parts(String v) {
        int[] out = new int[3];
        String[] p = (v == null ? "" : v).split("-")[0].split("\\.");
        for (int i = 0; i < 3 && i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private Updates() {}
}
