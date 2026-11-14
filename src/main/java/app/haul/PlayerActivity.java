package app.haul;

import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

/**
 * Haul's own player: a haul's saved files, one at a time, so a download can be checked without opening the Gallery.
 * Videos and audio play with Haul's controls (and loop, the way short videos are watched); photos show fitted, and GIFs
 * move. A post with several files swipes left and right. Open with EXTRA_ID (and EXTRA_INDEX for a file of a post).
 */
public class PlayerActivity extends Activity {
    static final String EXTRA_ID = "id", EXTRA_INDEX = "index";
    private static final long HIDE_AFTER = 3000;

    private Haul.Item item;
    private final List<String> files = new ArrayList<>();
    private int index;
    private VideoView video;
    private ImageView image;
    private View audio, top, bottom;
    private TextView title, count, time, error, audioTitle;
    private ImageButton play;
    private SeekBar seek;
    private boolean dragging, playable;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick, hide = () -> bars(false);

    static Intent open(android.content.Context c, String id, int index) {
        return new Intent(c, PlayerActivity.class).putExtra(EXTRA_ID, id).putExtra(EXTRA_INDEX, index);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_player);
        MainActivity.edgeToEdge(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        video = findViewById(R.id.playerVideo);
        image = findViewById(R.id.playerImage);
        audio = findViewById(R.id.playerAudio);
        audioTitle = findViewById(R.id.playerAudioTitle);
        top = findViewById(R.id.playerTop);
        bottom = findViewById(R.id.playerBottom);
        title = findViewById(R.id.playerTitle);
        count = findViewById(R.id.playerCount);
        time = findViewById(R.id.playerTime);
        error = findViewById(R.id.playerError);
        play = findViewById(R.id.playerPlay);
        seek = findViewById(R.id.playerSeek);

        View root = findViewById(R.id.playerRoot);
        int topPad = top.getPaddingTop(), bottomPad = bottom.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] b = MainActivity.bars(insets);
            top.setPadding(top.getPaddingLeft(), topPad + b[1], top.getPaddingRight(), top.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(), bottomPad + b[3]);
            return insets;
        });

        item = Haul.get(this, getIntent().getStringExtra(EXTRA_ID));
        if (item != null) {
            for (String u : item.uris) if (!u.isEmpty() && !"null".equals(u)) files.add(u);
            if (files.isEmpty() && !item.uri.isEmpty()) files.add(item.uri);
        }
        if (files.isEmpty()) {
            finish();
            return;
        }
        index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), files.size() - 1));
        title.setText(Look.title(item));

        findViewById(R.id.playerBack).setOnClickListener(v -> finish());
        findViewById(R.id.playerShare).setOnClickListener(v -> {
            String type = getContentResolver().getType(Uri.parse(files.get(index)));
            List<Uri> one = new ArrayList<>();
            one.add(Uri.parse(files.get(index)));
            Sheets.open(this, Downloads.shareIntent(one, type == null ? item.mime : type, item.title));
        });
        findViewById(R.id.playerGallery).setOnClickListener(v -> {
            Uri u = Uri.parse(files.get(index));
            String type = getContentResolver().getType(u);
            Sheets.open(this, new Intent(Intent.ACTION_VIEW).setDataAndType(u, type == null ? item.mime : type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        });
        play.setOnClickListener(v -> {
            if (video.isPlaying()) video.pause();
            else video.start();
            showPlay();
            later();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean byUser) {
                if (byUser) time.setText(Format.clock(p / 1000) + " / " + Format.clock(video.getDuration() / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                main.removeCallbacks(hide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                video.seekTo(bar.getProgress());
                later();
            }
        });

        // Tap: show or hide the bars. Swipe sideways: the next or previous file of the post.
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                bars(top.getVisibility() != View.VISIBLE);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(b.getX() - a.getX()) < 80) return false;
                show(index + (vx < 0 ? 1 : -1));
                return true;
            }
        });
        root.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));
        video.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));
        image.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));

        video.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            seek.setMax(Math.max(1, mp.getDuration()));
            video.start();
            showPlay();
            main.post(tick);
            later();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            failed();
            return true;
        });
        show(index);
    }

    /** Shows file k of the post (wrapping around at either end). */
    private void show(int k) {
        if (files.isEmpty()) return;
        index = (k + files.size()) % files.size();
        count.setText(files.size() > 1 ? (index + 1) + " / " + files.size() : "");
        count.setVisibility(files.size() > 1 ? View.VISIBLE : View.GONE);
        error.setVisibility(View.GONE);
        main.removeCallbacks(tick);
        video.stopPlayback();
        Uri u = Uri.parse(files.get(index));
        String type = getContentResolver().getType(u);
        if (type == null) type = item.mime;
        if (type.startsWith("image/")) {
            playable = false;
            video.setVisibility(View.GONE);
            audio.setVisibility(View.GONE);
            bottom.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            picture(u);
            bars(true);
        } else {
            playable = true;
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
            boolean sound = type.startsWith("audio/");
            audio.setVisibility(sound ? View.VISIBLE : View.GONE);
            audioTitle.setText(Look.title(item));
            // Audio still plays through the video view (it is a MediaPlayer); the card covers the empty picture.
            video.setVisibility(View.VISIBLE);
            video.setVideoURI(u);
            bars(true);
        }
    }

    /** A photo, fitted to the screen; a GIF moves. Decoded at about screen size, so large photos don't run out of memory. */
    private void picture(Uri u) {
        int maxSide = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        new Thread(() -> {
            Drawable d;
            try {
                d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(getContentResolver(), u), (decoder, info, src) -> {
                    int side = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (side > maxSide) decoder.setTargetSampleSize(Math.max(1, side / maxSide));
                });
            } catch (Exception e) {
                d = null;
            }
            Drawable got = d;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (got == null) {
                    failed();
                    return;
                }
                image.setImageDrawable(got);
                if (got instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) got).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    ((AnimatedImageDrawable) got).start();
                }
            });
        }, "haul-picture").start();
    }

    private void failed() {
        main.removeCallbacks(tick);
        video.setVisibility(View.GONE);
        image.setVisibility(View.GONE);
        audio.setVisibility(View.GONE);
        bottom.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        error.setText("Haul can't open this file. It may have been deleted from the Gallery.");
        bars(true);
    }

    private void tick() {
        if (!dragging && video.getDuration() > 0) {
            seek.setProgress(video.getCurrentPosition());
            time.setText(Format.clock(video.getCurrentPosition() / 1000) + " / " + Format.clock(video.getDuration() / 1000));
        }
        main.postDelayed(tick, 250);
    }

    private void showPlay() {
        play.setImageResource(video.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /** Shows or hides the bars; the bottom one only for video and audio. */
    private void bars(boolean on) {
        top.setVisibility(on ? View.VISIBLE : View.GONE);
        bottom.setVisibility(on && playable ? View.VISIBLE : View.GONE);
        if (on) later();
    }

    /** Hides the bars after a moment while a video plays, so they don't sit over it. */
    private void later() {
        main.removeCallbacks(hide);
        boolean videoPlaying = playable && audio.getVisibility() != View.VISIBLE && video.isPlaying();
        if (videoPlaying) main.postDelayed(hide, HIDE_AFTER);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video.isPlaying()) video.pause();
        showPlay();
        main.removeCallbacks(hide);
        bars(true);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        video.stopPlayback();
        super.onDestroy();
    }
}
