# Haul promo video

A 42-second motion-graphics promo for Haul 1.3.0, built entirely in code: every frame is drawn by an HTML/SVG page
from one time value, and every sound is synthesised. Nothing is sampled, filmed or licensed. See `STORYBOARD.md`
for the beat sheet.

| File | Format |
|---|---|
| `haul-promo-9x16.mp4` | 1080×1920, 60 fps, 42 s, H.264 + AAC |
| `haul-promo-1x1.mp4` | 1080×1080, 60 fps, 42 s |
| `haul-teaser-9x16.mp4` | 1080×1920, 60 fps, 9.5 s |

| `haul-motion-9x16.mp4` | The second piece, "From link to Gallery": 1080×1920, 60 fps, 34 s |

The renders go to `out/` (not committed).

## The second piece: `motion/`
Flat motion graphics with no phone: shape morphs (▶ → ♥ → ↓ → the logo's card), stroke draw-ons, kinetic type,
a slot-machine reel of sites, a gallery grid, a progress ring, a snipped timeline, a fanned carousel, a GIF loop and
the Quick Settings tile. White and dark grounds alternate, and it ends on white. `motion/mg.js` has the morphable
outlines and the type, `motion/scenes.js` the ten scenes, `motion/timeline.js` the sound cues. The music is
`audio/synth_motion.py`: F major, plucked arpeggios, a bouncy bass and snaps, reusing the voices in `synth.py`.

```sh
node -e "process.stdout.write(JSON.stringify(require('./motion/timeline.js')))" > out/motion-timeline.json
(cd audio && python3 synth_motion.py ../out/motion-timeline.json ../out/audio)
node tools/render.cjs --page promo/motion/index.html --audio out/audio/motion-mix.wav --out out/haul-motion-9x16.mp4
```
The delivered mix was then normalized to -14 LUFS with FFmpeg's two-pass `loudnorm`.

## How it's built
- `src/timeline.js`: the one timing file, at 120 BPM (a beat is 0.5 s, a bar 2 s). It holds the captions, every
  sound cue and the teaser's cut list. Scenes, captions and audio all read from it, so picture and sound stay on
  the frame.
- `src/scenes.js`: the scenes (the feed and the pick, the sites, the phone, "No strings", the logo) and the
  captions. `src/ui.js` has the Freight parts: the phone, the hold's ticket rows, the dock, the sheets, the share
  sheet, notifications and Quick Settings, all drawn from the app's layouts, drawables and colours.
- `src/index.html` loads the app's own font files from `../../src/main/res/font`. The logo uses the 108-unit
  geometry from `ic_launcher_fg.xml`, and the ending plays `avd_splash`'s timings (the feed scrolls 0.45 s with
  fast-out-slow-in, then the pick takes 0.3 s with overshoot).
- `audio/synth.py`: the music (D minor, Dm–B♭–F–C, warm minimal house) and all the sound effects, made with
  NumPy and SciPy. It writes the mix and the teaser's mix, cut from the same stems.
- `tools/render.cjs`: draws every frame in headless Chromium (Playwright), pipes the frames to FFmpeg in parallel
  chunks, then muxes the audio. `tools/stills.cjs` saves single frames.

## Rebuild
Needs Node with Playwright and Chromium, FFmpeg, and Python 3 with NumPy and SciPy.

```sh
cd promo
node -e "process.stdout.write(JSON.stringify(require('./src/timeline.js')))" > out/timeline.json
python3 audio/synth.py out/timeline.json out/audio

node tools/render.cjs --format vertical --cut main   --audio out/audio/mix.wav    --out out/haul-promo-9x16.mp4
node tools/render.cjs --format square   --cut main   --audio out/audio/mix.wav    --out out/haul-promo-1x1.mp4
node tools/render.cjs --format vertical --cut teaser --audio out/audio/teaser.wav --out out/haul-teaser-9x16.mp4
```

For a quick draft, add `--fps 30 --scale 0.5 --quality draft`. To look at one moment, open
`src/index.html?t=12.5` (or `&format=square`) through any static server at the repository root, or add `&play` to
watch it run in real time without sound.
