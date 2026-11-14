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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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

    // ---- New haul ----

    static void add(Activity a, Draft draft, Added added) {
        Dialog d = sheet(a, R.layout.sheet_add);
        EditText link = d.findViewById(R.id.addLink);
        link.setText(draft.url);
        Button[] q = {d.findViewById(R.id.addBest), d.findViewById(R.id.addSmall), d.findViewById(R.id.addAudio), d.findViewById(R.id.addGif)};
        String[] ids = {Haul.BEST, Haul.SMALL, Haul.AUDIO, Haul.GIF};
        String[] subs = {"H.264", "Smaller", "M4A", "No sound"};
        Runnable mark = () -> {
            for (int k = 0; k < q.length; k++) {
                q[k].setActivated(ids[k].equals(draft.quality));
                q[k].setText(twoLines(Look.qualityName(ids[k]), subs[k]));
            }
        };
        for (int k = 0; k < q.length; k++) {
            String id = ids[k];
            q[k].setOnClickListener(v -> {
                draft.quality = id;
                mark.run();
            });
        }
        mark.run();
        TextView clipValue = d.findViewById(R.id.addClipValue);
        clipValue.setText(draft.clipped() ? Format.clock(draft.clipStart) + "–" + Format.clock(draft.clipEnd) : "Whole video");

        d.findViewById(R.id.addClip).setOnClickListener(v -> {
            String url = Haul.firstLink(link.getText());
            if (url == null) {
                link.setError(link.length() == 0 ? "Paste a link first" : "That isn't a link. It should start with https://");
                return;
            }
            if (!url.equals(draft.url)) {
                draft.clipStart = draft.clipEnd = -1;   // a new link: the old section doesn't fit it
            }
            draft.url = url;
            d.dismiss();
            clip(a, draft, added);
        });
        Runnable go = () -> {
            String url = Haul.firstLink(link.getText());
            if (url == null) {
                link.setError(link.length() == 0 ? "Paste a link first" : "That isn't a link. It should start with https://");
                return;
            }
            boolean sameLink = url.equals(draft.url);
            d.dismiss();
            // A TikTok photo post asks how to save it; an Instagram carousel lets the user pick.
            Choose.add(a, url, draft.quality, sameLink ? draft.clipStart : -1, sameLink ? draft.clipEnd : -1, i -> {
                if (i != null) added.on(url);
            });
        };
        d.findViewById(R.id.addGo).setOnClickListener(v -> go.run());
        link.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            go.run();
            return true;
        });
        if (draft.url.isEmpty() && d.getWindow() != null) {
            link.requestFocus();
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    // ---- Clip a section ----

    private static void clip(Activity a, Draft draft, Added added) {
        Dialog d = sheet(a, R.layout.sheet_clip);
        ImageView preview = d.findViewById(R.id.clipPreview);
        TextView previewLabel = d.findViewById(R.id.clipPreviewLabel);
        ProgressBar loading = d.findViewById(R.id.clipLoading);
        TextView status = d.findViewById(R.id.clipStatus);
        View controls = d.findViewById(R.id.clipControls);
        TextView range = d.findViewById(R.id.clipRange), length = d.findViewById(R.id.clipLength);
        TextView startLabel = d.findViewById(R.id.clipStartLabel), endLabel = d.findViewById(R.id.clipEndLabel);
        SeekBar start = d.findViewById(R.id.clipStart), end = d.findViewById(R.id.clipEnd);
        FrameLayout track = d.findViewById(R.id.clipTrack);
        View selection = d.findViewById(R.id.clipSelection);
        Button use = d.findViewById(R.id.clipUse);
        previewLabel.setText(Look.shortLink(draft.url));

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

    /** A saved haul: delete the file too, or only take it off the list. */
    private static void confirmDelete(Activity a, Haul.Item i) {
        String what = Haul.PHOTOS.equals(i.kind) ? (i.count > 1 ? "photos" : "photo") : Haul.AUDIO.equals(i.kind) ? "audio" : (i.count > 1 ? "videos" : "video");
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
