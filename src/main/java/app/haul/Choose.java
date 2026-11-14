package app.haul;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds a link the way it needs, from the dock, the new-haul sheet, the share sheet or the tile:
 * - a TikTok photo post shows its pictures, to pick the ones to save, and asks how: a video of them with the sound,
 *   the pictures themselves with the sound next to them, or just the sound;
 * - an Instagram post with several photos or videos shows them, to pick the ones to download;
 * - anything else goes straight into the queue.
 * TikTok short links (vm.tiktok.com) are looked up first, to tell a photo post from a video.
 */
final class Choose {
    /** The haul added, or null when the user backed out (or it was already downloading). */
    interface Done {
        void on(Haul.Item added);
    }

    private static final int[] TONES = {R.color.tone0, R.color.tone1, R.color.tone2, R.color.tone3, R.color.tone4, R.color.tone5};

    static void add(Activity a, String url, String quality, int clipStart, int clipEnd, Done done) {
        String site = Haul.site(url);
        boolean shortTikTok = "TikTok".equals(site) && Photos.tiktokPhotoId(url) == null && !url.contains("/video/");
        if (shortTikTok) {
            Dialog d = reading(a, "READING THE LINK", "Looking up the TikTok link…", done);
            new Thread(() -> {
                String full = Engine.resolve(url);
                a.runOnUiThread(() -> {
                    if (!d.isShowing()) return;   // backed out
                    d.setOnDismissListener(null);
                    d.dismiss();
                    tiktok(a, url, full.startsWith("http") ? full : url, quality, clipStart, clipEnd, done);
                });
            }, "haul-resolve").start();
            return;
        }
        if ("TikTok".equals(site)) {
            tiktok(a, url, url, quality, clipStart, clipEnd, done);
        } else if ("Instagram".equals(site) && url.contains("/p/") && !Haul.AUDIO.equals(quality) && clipStart < 0) {
            pick(a, url, quality, done);
        } else {
            done.on(put(a, url, quality, clipStart, clipEnd, "", ""));
        }
    }

    private static Haul.Item put(Activity a, String url, String quality, int clipStart, int clipEnd, String pick, String as) {
        Haul.Item i = Downloads.add(a, url, quality, clipStart, clipEnd, pick, as);
        if (i == null) Toast.makeText(a, "Haul is already downloading this", Toast.LENGTH_SHORT).show();
        return i;
    }

    // ---- TikTok photo posts ----

    /** link: as the user gave it; url: where it leads (the same, unless link was a short one). */
    private static void tiktok(Activity a, String link, String url, String quality, int clipStart, int clipEnd, Done done) {
        if (Photos.tiktokPhotoId(url) == null) {
            // A video: keep the link as given, so details read ahead for it (in the dock) are found again.
            done.on(put(a, link, quality, clipStart, clipEnd, "", ""));
            return;
        }
        if (Haul.AUDIO.equals(quality)) {
            done.on(put(a, url, Haul.AUDIO, -1, -1, "", ""));   // "Haul audio": the sound, no question
            return;
        }
        Dialog d = reading(a, "PICK FROM THE POST", "Reading the post…", done);
        new Thread(() -> {
            Post post;
            try {
                post = Engine.readTikTok(a, url);
            } catch (Exception e) {
                post = new Post();   // the pictures can't be shown: the sheet still asks how to save them all
            }
            Post got = post;
            a.runOnUiThread(() -> {
                if (d.isShowing()) grid(a, d, url, quality, got, true, done);   // else: backed out
            });
        }, "haul-read").start();
    }

    // ---- Instagram carousels ----

    private static void pick(Activity a, String url, String quality, Done done) {
        Dialog d = reading(a, "PICK FROM THE POST", "Reading the post…", done);
        new Thread(() -> {
            Post post;
            try {
                post = Post.parse(Engine.read(a, url));
            } catch (Exception e) {
                post = null;
            }
            Post got = post;
            a.runOnUiThread(() -> {
                if (!d.isShowing()) return;   // backed out
                if (got == null || got.entries.size() <= 1) {
                    // One photo or video (or the post couldn't be read here): download it as it is. The details
                    // just read make the download start at once.
                    finish(d, done, put(a, url, quality, -1, -1, "", ""));
                } else {
                    grid(a, d, url, quality, got, false, done);
                }
            });
        }, "haul-read").start();
    }

    // ---- The grid, for both ----

    /**
     * The post's items in a grid, all chosen; a tap leaves one out or puts it back. A TikTok photo post (tiktok) also
     * asks how to save the chosen pictures, and may come with no items at all, when its page couldn't be read: then
     * the sheet only asks how, and the download takes every picture.
     */
    private static void grid(Activity a, Dialog d, String url, String quality, Post post, boolean tiktok, Done done) {
        d.findViewById(R.id.pickReading).setVisibility(View.GONE);
        TextView hint = d.findViewById(R.id.pickHint);
        hint.setVisibility(View.VISIBLE);
        int total = post.entries.size();
        int videos = 0;
        for (Post.Entry e : post.entries) if (e.video) videos++;
        int photos = total - videos;
        if (!tiktok) {
            hint.setText(String.format(Locale.ROOT, "%d %s · %d %s · tap to leave out", photos, photos == 1 ? "photo" : "photos",
                    videos, videos == 1 ? "video" : "videos"));
        } else if (total == 0) {
            hint.setText("Couldn't show the pictures · all will be saved");
        } else {
            hint.setText(String.format(Locale.ROOT, "%d %s%s%s", photos, photos == 1 ? "photo" : "photos",
                    post.sound ? " · with sound" : "", total > 1 ? " · tap to leave out" : ""));
        }
        ScrollView scroll = d.findViewById(R.id.pickScroll);
        scroll.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        // At most about half the screen of pictures (less for TikTok, which asks more below them); the rest scrolls.
        int screen = a.getResources().getDisplayMetrics().heightPixels;
        int maxHeight = tiktok ? screen * 2 / 5 : screen / 2;
        GridLayout grid = d.findViewById(R.id.pickGrid);
        int gap = Sheets.dp(a, 6);
        int width = a.getResources().getDisplayMetrics().widthPixels - Sheets.dp(a, 40);
        int cell = (width - 2 * gap) / 3, cellHeight = cell * 5 / 4;
        boolean[] chosen = new boolean[total];
        java.util.Arrays.fill(chosen, true);
        View[] checks = new View[total];
        View[] cells = new View[total];
        Button all = d.findViewById(R.id.pickAll), go = d.findViewById(R.id.pickGo);
        if (total <= 1) all.setVisibility(View.GONE);   // nothing to choose between

        // How to save a TikTok photo post; the last answer is offered first the next time.
        String[] as = {tiktok ? Haul.prefs(a).getString("tiktok_as", Haul.AS_SLIDESHOW) : ""};
        Button asVideo = d.findViewById(R.id.asVideo), asPhotos = d.findViewById(R.id.asPhotos), asSound = d.findViewById(R.id.asSound);
        TextView note = d.findViewById(R.id.pickAsNote);

        Runnable mark = () -> {
            boolean soundOnly = Haul.AUDIO.equals(as[0]);
            int n = 0;
            for (int k = 0; k < total; k++) {
                if (chosen[k]) n++;
                checks[k].setBackgroundResource(chosen[k] ? R.drawable.bg_check_on : R.drawable.bg_check_off);
                ((ImageView) checks[k]).setImageResource(chosen[k] ? R.drawable.ic_check : 0);
                cells[k].setAlpha(chosen[k] ? 1f : 0.45f);
            }
            scroll.setAlpha(soundOnly ? 0.35f : 1f);   // the pictures don't matter for the sound alone
            boolean ready = n > 0 || soundOnly || total == 0;
            go.setEnabled(ready);
            go.setAlpha(ready ? 1f : 0.5f);
            go.setText(soundOnly ? "Download sound" : total == 0 ? "Download" : n == total ? "Download all " + n : "Download " + n);
            all.setText(n == total ? "Select none" : "Select all");
            all.setEnabled(!soundOnly);
            all.setAlpha(soundOnly ? 0.5f : 1f);
            if (!tiktok) return;
            asVideo.setActivated(Haul.AS_SLIDESHOW.equals(as[0]));
            asPhotos.setActivated(Haul.AS_PHOTOS.equals(as[0]));
            asSound.setActivated(soundOnly);
            String with = total > 0 && !post.sound ? "" : ", with the sound";
            note.setText(soundOnly ? "Just the sound, as M4A, in Music/Haul."
                    : Haul.AS_PHOTOS.equals(as[0])
                    ? "Each chosen picture in Pictures/Haul" + (with.isEmpty() ? "." : ", and the sound in Music/Haul.")
                    : "One video of the chosen pictures in turn" + with + ", in Movies/Haul.");
        };
        if (tiktok) {
            if (!Haul.AS_PHOTOS.equals(as[0]) && !Haul.AUDIO.equals(as[0])) as[0] = Haul.AS_SLIDESHOW;
            d.findViewById(R.id.pickAs).setVisibility(View.VISIBLE);
            Button[] ways = {asVideo, asPhotos, asSound};
            String[] values = {Haul.AS_SLIDESHOW, Haul.AS_PHOTOS, Haul.AUDIO};
            for (int k = 0; k < ways.length; k++) {
                String value = values[k];
                ways[k].setOnClickListener(v -> {
                    as[0] = value;
                    mark.run();
                });
            }
        }

        for (int k = 0; k < total; k++) {
            Post.Entry e = post.entries.get(k);
            FrameLayout box = new FrameLayout(a);
            box.setBackgroundResource(R.drawable.bg_thumb);
            box.setBackgroundTintList(ColorStateList.valueOf(a.getColor(TONES[k % TONES.length])));
            box.setClipToOutline(true);
            box.setContentDescription((e.video ? "Video " : "Photo ") + e.index);
            ImageView pic = new ImageView(a);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (tiktok) Thumbs.showPhoto(pic, e.thumbnail, cell);
            else Thumbs.showUrl(pic, e.thumbnail);
            if (e.video) {
                TextView len = new TextView(a, null, 0, R.style.Haul_Mono);
                len.setText(e.duration > 0 ? "▶ " + Format.clock(e.duration) : "▶");
                len.setTextColor(0xFFFFFFFF);
                len.setTextSize(10);
                len.setBackgroundResource(R.drawable.bg_len);
                len.setPadding(Sheets.dp(a, 4), Sheets.dp(a, 1), Sheets.dp(a, 4), Sheets.dp(a, 1));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
                lp.setMargins(gap, gap, gap, gap);
                box.addView(len, lp);
            }
            ImageView check = new ImageView(a);
            check.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            check.setImageTintList(ColorStateList.valueOf(a.getColor(R.color.on_clay)));
            check.setPadding(Sheets.dp(a, 3), Sheets.dp(a, 3), Sheets.dp(a, 3), Sheets.dp(a, 3));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Sheets.dp(a, 26), Sheets.dp(a, 26), Gravity.TOP | Gravity.END);
            clp.setMargins(gap, gap, gap, gap);
            box.addView(check, clp);
            checks[k] = check;
            cells[k] = pic;
            int at = k;
            box.setOnClickListener(v -> {
                if (Haul.AUDIO.equals(as[0])) return;
                chosen[at] = !chosen[at];
                mark.run();
            });
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cell;
            glp.height = cellHeight;
            glp.setMargins(0, 0, k % 3 == 2 ? 0 : gap, gap);
            grid.addView(box, glp);
        }
        int rows = (total + 2) / 3;
        if (rows * (cellHeight + gap) > maxHeight) {
            scroll.getLayoutParams().height = maxHeight;
            scroll.requestLayout();
        }
        all.setOnClickListener(v -> {
            boolean everything = true;
            for (boolean c : chosen) everything &= c;
            java.util.Arrays.fill(chosen, !everything);
            mark.run();
        });
        go.setOnClickListener(v -> {
            List<Integer> picked = new ArrayList<>();
            for (int k = 0; k < total; k++) if (chosen[k]) picked.add(post.entries.get(k).index);
            String items = picked.size() == total ? "" : Post.items(picked);
            if (!tiktok) {
                finish(d, done, put(a, url, quality, -1, -1, items, ""));
                return;
            }
            Haul.prefs(a).edit().putString("tiktok_as", as[0]).apply();
            if (Haul.AUDIO.equals(as[0])) finish(d, done, put(a, url, Haul.AUDIO, -1, -1, "", ""));
            else finish(d, done, put(a, url, quality, -1, -1, items, as[0]));
        });
        d.findViewById(R.id.pickButtons).setVisibility(View.VISIBLE);
        mark.run();
    }

    // ---- Sheets ----

    /** A sheet that, closed by the user (back, or a tap outside), reports that nothing was added. */
    private static Dialog sheet(Activity a, int layout, Done done) {
        Dialog d = new Dialog(a, R.style.Theme_Haul_Sheet);
        d.setContentView(layout);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        d.setOnDismissListener(x -> done.on(null));
        return d;
    }

    private static Dialog reading(Activity a, String title, String status, Done done) {
        Dialog d = sheet(a, R.layout.sheet_pick, done);
        ((TextView) d.findViewById(R.id.pickTitle)).setText(title);
        ((TextView) d.findViewById(R.id.pickStatus)).setText(status);
        spin(d.findViewById(R.id.pickLoader));
        d.show();
        return d;
    }

    /** Starts Haul's loading sign (the Pull card stepping out and back) in an image view showing avd_loader. */
    static void spin(ImageView v) {
        if (v.getDrawable() instanceof android.graphics.drawable.Animatable) {
            ((android.graphics.drawable.Animatable) v.getDrawable()).start();
        }
    }

    /** Closes the sheet and reports what was added (instead of the "nothing" a plain dismiss reports). */
    private static void finish(Dialog d, Done done, Haul.Item added) {
        d.setOnDismissListener(null);
        d.dismiss();
        done.on(added);
    }

    private Choose() {}
}
