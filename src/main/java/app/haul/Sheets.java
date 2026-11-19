package app.haul;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** The bottom sheets: a new haul (with its clip picker), and one haul opened from the hold. */
final class Sheets {
    static final int REQ_DELETE = 7;

    /** What the user is putting together in the new-haul and clip sheets; kept while moving between the two. */
    static final class Draft {
        String url = "", quality;
        int clipStart = -1, clipEnd = -1;
        Engine.Info info;     // the video's length and picture, read once per link
        String infoUrl = "";

        Draft(String url, String quality) {
            this.url = url == null ? "" : url;
            this.quality = quality;
        }

        boolean clipped() {
            return clipStart >= 0 && clipEnd > clipStart;
        }
    }

    /** Called with the link once a new haul is in the queue. */
    interface Added {
        void on(String url);
    }

    private static Dialog sheet(Activity a, int layout) {
        Dialog d = new Dialog(a, R.style.Theme_Haul_Sheet);
        d.setContentView(layout);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    // ---- HAUL THIS AS ----

    /**
     * HAUL THIS AS, over the Download page once a link is pasted (or found copied): what Haul reads of the link (its
     * picture, title, who posted it, how long), the quality, a section to clip, and Download. draft.url holds the link,
     * or several separated by spaces: those download together, in the quality picked, with no clip. Download hands a
     * single link to Choose (a TikTok photo post, an Instagram carousel or a post already saved ask their question
     * there); added hears of it with the link (or the links, one to a line). Returns the sheet, or null for no link.
     */
    static Dialog add(Activity a, Draft draft, Added added) {
        List<String> urls = Haul.links(draft.url);
        if (urls.isEmpty()) return null;
        boolean many = urls.size() > 1;
        String url = urls.get(0);
        Dialog d = sheet(a, R.layout.sheet_add);

        TextView title = d.findViewById(R.id.addTitle), meta = d.findViewById(R.id.addMeta);
        View box = d.findViewById(R.id.addThumbBox);
        ImageView thumb = d.findViewById(R.id.addThumb);
        box.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Look.tone(a, url)));
        box.setClipToOutline(true);
        if (many) {
            java.util.Set<String> sites = new java.util.LinkedHashSet<>();
            for (String u : urls) sites.add(Haul.site(u));
            ((TextView) d.findViewById(R.id.addSite)).setText(urls.size() + " links");
            ((TextView) d.findViewById(R.id.addTag)).setText("×" + urls.size());
            title.setText(urls.size() + " links, each saved as picked");
            meta.setText(String.join(", ", sites));
        } else {
            ((TextView) d.findViewById(R.id.addSite)).setText(Haul.site(url));
            ((TextView) d.findViewById(R.id.addTag)).setText(Look.tag(Haul.site(url)));
            title.setText(Look.shortLink(url));
            // Read the link while the user picks: the download then starts from what was read, and the sheet shows it.
            Engine.readAhead(a, url);
            java.util.function.Consumer<Engine.Info> show = info -> {
                if (info == null) {
                    meta.setText(Haul.site(url));
                    return;
                }
                if (!info.title.isEmpty()) title.setText(info.title);
                meta.setText(info.duration > 0 ? Haul.site(url) + " · " + Format.clock(info.duration) : Haul.site(url));
                if (!info.thumbnail.isEmpty()) Thumbs.showUrl(thumb, info.thumbnail);
            };
            if (draft.info != null && draft.infoUrl.equals(url)) {
                show.accept(draft.info);
            } else if (Photos.tiktokPhotoId(url) != null) {
                meta.setText("TikTok photos");   // read when Download is tapped, to pick the pictures
            } else {
                meta.setText("Reading the link…");
                new Thread(() -> {
                    Engine.Info info;
                    try {
                        info = Engine.info(a, url);
                    } catch (Exception e) {
                        info = null;
                    }
                    Engine.Info got = info;
                    a.runOnUiThread(() -> {
                        if (got != null) {
                            draft.info = got;
                            draft.infoUrl = url;
                        }
                        if (d.isShowing()) show.accept(got);
                    });
                }, "haul-info").start();
            }
        }

        Button[] q = {d.findViewById(R.id.addBest), d.findViewById(R.id.addSmall), d.findViewById(R.id.addAudio), d.findViewById(R.id.addGif)};
        String[] ids = {Haul.BEST, Haul.SMALL, Haul.AUDIO, Haul.GIF};
        String[] subs = {"H.264", "Smaller", "M4A", "No sound"};
        TextView clipValue = d.findViewById(R.id.addClipValue);
        Runnable mark = () -> {
            for (int k = 0; k < q.length; k++) {
                q[k].setActivated(ids[k].equals(draft.quality));
                q[k].setText(twoLines(Look.qualityName(ids[k]), subs[k]));
            }
            clipValue.setText(clipText(draft));   // Audio clips a sound, not a video
        };
        for (int k = 0; k < q.length; k++) {
            String id = ids[k];
            q[k].setOnClickListener(v -> {
                if (!id.equals(draft.quality)) Look.tick(v);
                draft.quality = id;
                mark.run();
            });
        }
        mark.run();

        View clipRow = d.findViewById(R.id.addClip);
        if (many) {
            clipRow.setVisibility(View.GONE);   // a section is of one video
        } else {
            draft.url = url;
            clipRow.setOnClickListener(v -> {
                d.dismiss();
                clip(a, draft, added);   // comes back to this sheet
            });
        }

        Button go = d.findViewById(R.id.addGo);
        go.setText(many ? "Download " + urls.size() + " links" : "Download");
        go.setOnClickListener(v -> {
            Look.confirm(v);
            d.dismiss();
            if (many) {
                // A list: all of it in this quality, with no questions (a carousel comes whole, a TikTok photo post
                // as its pictures and sound).
                Toast.makeText(a, Downloads.addAll(a, urls, draft.quality), Toast.LENGTH_LONG).show();
                added.on(String.join("\n", urls));
                return;
            }
            // A TikTok photo post asks how to save it; an Instagram carousel lets the user pick; a post already saved
            // says so.
            Choose.add(a, url, draft.quality, draft.clipStart, draft.clipEnd, i -> {
                if (i != null) added.on(url);
            });
        });
        d.show();
        return d;
    }

    // ---- Clip a section ----

    /** What the sheet's clip row says: the section, or the whole video (or sound, for Audio). */
    private static String clipText(Draft draft) {
        if (draft.clipped()) return Format.clock(draft.clipStart) + "–" + Format.clock(draft.clipEnd);
        return Haul.AUDIO.equals(draft.quality) ? "Whole sound" : "Whole video";
    }

    private static void clip(Activity a, Draft draft, Added added) {
        Dialog d = sheet(a, R.layout.sheet_clip);
        ImageView preview = d.findViewById(R.id.clipPreview);
        TextView previewLabel = d.findViewById(R.id.clipPreviewLabel);
        ImageView loading = d.findViewById(R.id.clipLoading);
        Choose.spin(loading);
        TextView status = d.findViewById(R.id.clipStatus);
        View controls = d.findViewById(R.id.clipControls);
        TextView range = d.findViewById(R.id.clipRange), length = d.findViewById(R.id.clipLength);
        TextView startLabel = d.findViewById(R.id.clipStartLabel), endLabel = d.findViewById(R.id.clipEndLabel);
        SeekBar start = d.findViewById(R.id.clipStart), end = d.findViewById(R.id.clipEnd);
        FrameLayout track = d.findViewById(R.id.clipTrack);
        View selection = d.findViewById(R.id.clipSelection);
        Button use = d.findViewById(R.id.clipUse);
        previewLabel.setText(Look.shortLink(draft.url));
        if (Haul.AUDIO.equals(draft.quality)) ((Button) d.findViewById(R.id.clipWhole)).setText("Whole sound");

        Runnable back = () -> {
            d.dismiss();
            add(a, draft, added);
        };
        d.findViewById(R.id.clipBack).setOnClickListener(v -> back.run());
        d.findViewById(R.id.clipWhole).setOnClickListener(v -> {
            draft.clipStart = draft.clipEnd = -1;
            back.run();
        });
        use.setOnClickListener(v -> {
            draft.clipStart = start.getProgress();
            draft.clipEnd = end.getProgress();
            back.run();
        });

        Runnable draw = () -> {
            int total = Math.max(1, start.getMax());
            int s = start.getProgress(), e = end.getProgress();
            range.setText(Format.clock(s) + " – " + Format.clock(e));
            length.setText(Format.clock(e - s) + " long");
            startLabel.setText("Start · " + Format.clock(s));
            endLabel.setText("End · " + Format.clock(e));
            int w = track.getWidth();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selection.getLayoutParams();
            lp.leftMargin = w * s / total;
            lp.width = Math.max(2, w * (e - s) / total);
            selection.setLayoutParams(lp);
            use.setEnabled(e > s);
        };
        SeekBar.OnSeekBarChangeListener moved = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean byUser) {
                // Keep start before end: moving one past the other pushes the other along.
                if (bar == start && p >= end.getProgress()) end.setProgress(Math.min(end.getMax(), p + 1));
                if (bar == end && p <= start.getProgress()) start.setProgress(Math.max(0, p - 1));
                draw.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        };

        java.util.function.Consumer<Engine.Info> ready = info -> {
            loading.setVisibility(View.GONE);
            if (info == null || info.duration <= 1) {
                status.setText(info == null ? "Haul couldn't read this link. Check it, or download the whole video."
                        : "Haul couldn't read this video's length, so it can't clip it.");
                return;
            }
            Thumbs.showUrl(preview, info.thumbnail);
            previewLabel.setText("Preview · " + Format.clock(info.duration));
            if (!info.title.isEmpty()) status.setText(info.title);
            else status.setVisibility(View.GONE);
            controls.setVisibility(View.VISIBLE);
            start.setMax(info.duration);
            end.setMax(info.duration);
            boolean fits = draft.clipped() && draft.clipEnd <= info.duration;
            start.setProgress(fits ? draft.clipStart : 0);
            end.setProgress(fits ? draft.clipEnd : info.duration);
            start.setOnSeekBarChangeListener(moved);
            end.setOnSeekBarChangeListener(moved);
            track.post(draw);
        };

        d.show();
        if (draft.info != null && draft.infoUrl.equals(draft.url)) {
            ready.accept(draft.info);
            return;
        }
        String url = draft.url;
        new Thread(() -> {
            Engine.Info info;
            try {
                info = Engine.info(a, url);
            } catch (Exception e) {
                info = null;
            }
            Engine.Info got = info;
            a.runOnUiThread(() -> {
                if (got != null) {
                    draft.info = got;
                    draft.infoUrl = url;
                }
                if (d.isShowing()) ready.accept(got);
            });
        }, "haul-info").start();
    }

    // ---- One haul ----

    static void item(Activity a, Haul.Item i) {
        Dialog d = sheet(a, R.layout.sheet_item);
        Look.picture(a, d.findViewById(R.id.sheetThumbBox), d.findViewById(R.id.sheetThumb), d.findViewById(R.id.sheetThumbIcon),
                d.findViewById(R.id.sheetTag), i, 252, 450);
        boolean done = Haul.DONE.equals(i.status), active = i.active();
        boolean failed = Haul.FAILED.equals(i.status) || Haul.CANCELED.equals(i.status);
        String state = done ? "Saved" : Haul.CANCELED.equals(i.status) ? "Canceled" : failed ? "Failed"
                : Haul.WORKING.equals(i.status) ? "Downloading" : "Waiting";
        ((TextView) d.findViewById(R.id.sheetNo)).setText("No. " + Format.ticket(i.no) + " · " + state);
        ((TextView) d.findViewById(R.id.sheetTitle)).setText(Look.title(i));
        ((TextView) d.findViewById(R.id.sheetSite)).setText(i.site);
        TextView error = d.findViewById(R.id.sheetError);
        if (failed && !i.error.isEmpty()) {
            error.setVisibility(View.VISIBLE);
            error.setText(i.error);
        }

        List<String[]> facts = new ArrayList<>();
        if (done) {
            facts.add(new String[]{"Length", i.duration > 0 ? Format.clock(i.duration) : "–"});
            facts.add(new String[]{"Quality", Look.what(i)});
            facts.add(new String[]{"Size", Format.size(i.size)});
            facts.add(new String[]{"Saved", Look.when(a, i.doneAt)});
            facts.add(new String[]{"Album", Downloads.album(i)});
            facts.add(new String[]{"From", i.site});
            if (!i.by.isEmpty()) facts.add(new String[]{"By", i.by});
            if (i.posted > 0) facts.add(new String[]{"Posted", Look.day(a, i.posted)});
            if (!i.took.isEmpty()) facts.add(new String[]{"Took", i.took});
        } else {
            facts.add(new String[]{"Quality", Look.what(i)});
            facts.add(new String[]{"Added", Look.when(a, i.time)});
        }
        LinearLayout grid = d.findViewById(R.id.sheetFacts);
        for (int k = 0; k < facts.size(); k += 2) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (k > 0) row.setPadding(0, dp(a, 12), 0, 0);
            for (int j = k; j < k + 2; j++) row.addView(fact(a, j < facts.size() ? facts.get(j) : null),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            grid.addView(row);
        }

        if (!i.caption.isEmpty()) {
            d.findViewById(R.id.sheetCaptionBox).setVisibility(View.VISIBLE);
            TextView caption = d.findViewById(R.id.sheetCaption);
            caption.setText(i.caption);
            caption.setOnClickListener(v -> caption.setMaxLines(caption.getMaxLines() == 6 ? Integer.MAX_VALUE : 6));
            d.findViewById(R.id.sheetCopyCaption).setOnClickListener(v -> {
                a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Caption", i.caption));
                // Not offered back in the dock: a caption may hold a link, and it isn't one the user copied to download.
                String inside = Haul.firstLink(i.caption);
                if (inside != null) Haul.prefs(a).edit().putString("last_filled", inside).apply();
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(a, "Caption copied", Toast.LENGTH_SHORT).show();
            });
        }

        Button primary = d.findViewById(R.id.sheetPrimary);
        if (done) {
            primary.setText(Haul.PHOTOS.equals(i.kind) ? "Open" : "Play");
            primary.setOnClickListener(v -> {
                d.dismiss();
                a.startActivity(PlayerActivity.open(a, i.id, 0));   // Haul's own player; it can open the Gallery too
            });
        } else if (failed) {
            primary.setText("Try again");
            primary.setOnClickListener(v -> {
                d.dismiss();
                Downloads.retry(a, i.id);
            });
        } else {
            // Still going: no big button. Cancel is the small one at the end of the row below, so a tap meant to
            // look at a download can't stop it.
            primary.setVisibility(View.GONE);
        }

        if (done && soundOf(i) != null) {
            Button cutButton = d.findViewById(R.id.sheetCut);
            cutButton.setVisibility(View.VISIBLE);
            cutButton.setOnClickListener(v -> {
                d.dismiss();
                cut(a, i);
            });
        }

        d.findViewById(R.id.actShare).setOnClickListener(v -> {
            if (!done) {
                Toast.makeText(a, "Nothing saved to share yet", Toast.LENGTH_SHORT).show();
                return;
            }
            d.dismiss();
            open(a, Downloads.shareIntent(i));
        });
        d.findViewById(R.id.actCopy).setOnClickListener(v -> {
            a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Link", i.url));
            Haul.prefs(a).edit().putString("last_filled", i.url).apply();   // don't offer it back in the dock
            d.dismiss();
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(a, "Link copied", Toast.LENGTH_SHORT).show();
        });
        if (Statuses.isStatus(i.url)) {
            // A saved WhatsApp status has no link: nothing to copy, and nothing to download again.
            d.findViewById(R.id.actCopy).setVisibility(View.GONE);
            d.findViewById(R.id.actAgain).setVisibility(View.GONE);
        }
        Button again = d.findViewById(R.id.actAgain);
        again.setEnabled(!active);
        again.setAlpha(active ? 0.4f : 1f);
        again.setOnClickListener(v -> {
            d.dismiss();
            Downloads.retry(a, i.id);
        });
        Button remove = d.findViewById(R.id.actRemove);
        remove.setText(active ? "Cancel" : done ? "Delete" : "Remove");
        remove.setOnClickListener(v -> {
            d.dismiss();
            if (active) Downloads.cancel(a, i.id);
            else if (done) confirmDelete(a, i);
            else Haul.remove(a, i.id);
        });
        d.show();
    }

    // ---- Clip the sound of a saved haul ----

    /** One step of the sliders, in milliseconds; − and + move one step. */
    private static final int STEP = 100;

    /**
     * The saved file whose sound can be clipped: the sound or video itself, or the sound that came with a TikTok
     * post's photos (saved last); null when there is none.
     */
    static String soundOf(Haul.Item i) {
        if (!Haul.DONE.equals(i.status) || i.uris.isEmpty()) return null;
        if (Haul.AUDIO.equals(i.kind) || Haul.VIDEO.equals(i.kind)) return i.uris.get(0);
        if (Haul.PHOTOS.equals(i.kind) && i.sound) return i.uris.get(i.uris.size() - 1);
        return null;
    }

    /**
     * CLIP THE SOUND: start and end to a tenth of a second (a slider for the rough place, − and + for the exact one),
     * heard with Play before it's saved. Saving queues the clip, which FFmpeg makes from the saved file.
     */
    static void cut(Activity a, Haul.Item i) {
        String source = soundOf(i);
        if (source == null) return;
        Dialog d = sheet(a, R.layout.sheet_cut);
        ((TextView) d.findViewById(R.id.cutTitle)).setText(Look.title(i));
        ImageView loading = d.findViewById(R.id.cutLoading);
        Choose.spin(loading);
        TextView status = d.findViewById(R.id.cutStatus);
        View controls = d.findViewById(R.id.cutControls);
        SeekBar start = d.findViewById(R.id.cutStart), end = d.findViewById(R.id.cutEnd);
        TextView range = d.findViewById(R.id.cutRange), length = d.findViewById(R.id.cutLength);
        TextView startLabel = d.findViewById(R.id.cutStartLabel), endLabel = d.findViewById(R.id.cutEndLabel);
        Button play = d.findViewById(R.id.cutPlay), save = d.findViewById(R.id.cutSave);

        android.media.MediaPlayer[] player = {null};
        android.os.Handler ticks = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable stop = () -> {
            ticks.removeCallbacksAndMessages(null);
            try {
                if (player[0] != null && player[0].isPlaying()) player[0].pause();
            } catch (IllegalStateException ignored) {
            }
            play.setText("Play the section");
        };
        Runnable draw = () -> {
            long s = (long) start.getProgress() * STEP, e = (long) end.getProgress() * STEP;
            range.setText(Format.exact(s) + " – " + Format.exact(e));
            length.setText(Format.span(e - s));
            startLabel.setText("Start · " + Format.exact(s));
            endLabel.setText("End · " + Format.exact(e));
            save.setEnabled(e > s);
            save.setAlpha(e > s ? 1f : 0.4f);
        };
        SeekBar.OnSeekBarChangeListener moved = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean byUser) {
                // Keep start before end: moving one past the other pushes the other along.
                if (bar == start && p >= end.getProgress()) end.setProgress(Math.min(end.getMax(), p + 1));
                if (bar == end && p <= start.getProgress()) start.setProgress(Math.max(0, p - 1));
                stop.run();   // what was playing is no longer the section
                draw.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        };
        int[][] nudges = {{R.id.cutStartLess, -1}, {R.id.cutStartMore, 1}, {R.id.cutEndLess, -1}, {R.id.cutEndMore, 1}};
        for (int[] n : nudges) {
            SeekBar bar = n[0] == R.id.cutStartLess || n[0] == R.id.cutStartMore ? start : end;
            d.findViewById(n[0]).setOnClickListener(v -> bar.setProgress(Math.max(0, Math.min(bar.getMax(), bar.getProgress() + n[1]))));
        }

        play.setOnClickListener(v -> {
            if (player[0] != null && player[0].isPlaying()) {
                stop.run();
                return;
            }
            long from = (long) start.getProgress() * STEP, to = (long) end.getProgress() * STEP;
            try {
                if (player[0] == null) {
                    android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    mp.setDataSource(a, Uri.parse(source));
                    mp.prepare();
                    player[0] = mp;
                }
                player[0].seekTo(from, android.media.MediaPlayer.SEEK_CLOSEST);
                player[0].start();
            } catch (Exception e) {
                Toast.makeText(a, "Haul couldn't play this file", Toast.LENGTH_SHORT).show();
                return;
            }
            play.setText("Stop");
            // Stops at the end of the section, as the clip will.
            Runnable watch = new Runnable() {
                @Override
                public void run() {
                    android.media.MediaPlayer mp = player[0];
                    if (mp == null) return;
                    if (!mp.isPlaying() || mp.getCurrentPosition() >= to) stop.run();
                    else ticks.postDelayed(this, 40);
                }
            };
            ticks.postDelayed(watch, 40);
        });
        save.setOnClickListener(v -> {
            stop.run();
            Downloads.cut(a, i, source, (long) start.getProgress() * STEP, (long) end.getProgress() * STEP);
            d.dismiss();
        });
        d.setOnDismissListener(x -> {
            ticks.removeCallbacksAndMessages(null);
            if (player[0] != null) {
                player[0].release();
                player[0] = null;
            }
        });
        d.show();

        // How long it is, and whether it has sound at all, from the file itself.
        new Thread(() -> {
            long millis = 0;
            boolean sound = false;
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            try {
                r.setDataSource(a, Uri.parse(source));
                String dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                millis = dur == null ? 0 : Long.parseLong(dur);
                sound = "yes".equals(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
            } catch (Exception ignored) {
                // deleted, or unreadable: said below
            } finally {
                try {
                    r.release();
                } catch (Exception ignored) {
                }
            }
            long total = millis;
            boolean hasSound = sound;
            a.runOnUiThread(() -> {
                if (!d.isShowing()) return;
                loading.setVisibility(View.GONE);
                if (total < 2 * STEP) {
                    status.setText("Haul couldn't open this file. It may have been deleted from the Gallery.");
                    return;
                }
                if (!hasSound) {
                    status.setText("This video has no sound.");
                    return;
                }
                status.setVisibility(View.GONE);
                controls.setVisibility(View.VISIBLE);
                int max = (int) (total / STEP);
                start.setMax(max);
                end.setMax(max);
                start.setProgress(0);
                end.setProgress(Math.min(max, 30_000 / STEP));   // 30 seconds to start with: about a ringtone's length
                start.setOnSeekBarChangeListener(moved);
                end.setOnSeekBarChangeListener(moved);
                draw.run();
            });
        }, "haul-cut").start();
    }

    /** A saved haul: delete the file too, or only take it off the list. */
    private static void confirmDelete(Activity a, Haul.Item i) {
        String what = Haul.PHOTOS.equals(i.kind) ? (i.photos() > 1 ? "photos" : "photo") + (i.sound ? " and sound" : "") : Haul.AUDIO.equals(i.kind) ? "audio" : (i.count > 1 ? "videos" : "video");
        new AlertDialog.Builder(a)
                .setTitle(Look.title(i))
                .setMessage("Delete the " + what + " from your Gallery too, or only take it off this list?")
                .setPositiveButton("Delete " + what, (x, y) -> {
                    for (String u : i.uris) Thumbs.forget(u);
                    List<Uri> refused = Downloads.delete(a, i);
                    if (!refused.isEmpty() && Build.VERSION.SDK_INT >= 30) {
                        // Saved before a reinstall, so no longer Haul's: Android asks the user itself.
                        try {
                            a.startIntentSenderForResult(MediaStore.createDeleteRequest(a.getContentResolver(), refused)
                                    .getIntentSender(), REQ_DELETE, null, 0, 0, 0);
                        } catch (Exception e) {
                            Toast.makeText(a, "Removed from Haul. Delete the file in your Gallery.", Toast.LENGTH_LONG).show();
                        }
                    } else if (!refused.isEmpty()) {
                        Toast.makeText(a, "Removed from Haul. Delete the file in your Gallery.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Only from the list", (x, y) -> Haul.remove(a, i.id))
                .setNeutralButton("Keep", null)
                .show();
    }

    private static View fact(Activity a, String[] kv) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        if (kv == null) return box;
        TextView k = new TextView(a, null, 0, R.style.Haul_Label);
        k.setText(kv[0]);
        TextView v = new TextView(a, null, 0, R.style.Haul_Mono);
        v.setTextSize(14);
        v.setTextColor(a.getColor(R.color.ink));
        v.setPadding(0, dp(a, 3), dp(a, 8), 0);
        v.setText(kv[1]);
        box.addView(k);
        box.addView(v);
        return box;
    }

    /** "Best" over a smaller, lighter "Highest H.264", in one button. */
    static CharSequence twoLines(String label, String sub) {
        android.text.SpannableString s = new android.text.SpannableString(label + "\n" + sub);
        int from = label.length() + 1;
        s.setSpan(new android.text.style.RelativeSizeSpan(0.78f), from, s.length(), 0);
        s.setSpan(new android.text.style.TypefaceSpan("sans-serif"), from, s.length(), 0);
        return s;
    }

    static void open(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, "Nothing on this phone can open that, or the file was deleted", Toast.LENGTH_LONG).show();
        }
    }

    static int dp(Activity a, int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }

    private Sheets() {}
}
