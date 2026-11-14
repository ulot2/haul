// The one timing file. Scenes, captions and every sound cue read from here, so picture and sound stay on the frame.
// 120 BPM: a beat is 0.5 s (30 frames at 60 fps), a bar is 2 s.
(function (root) {
  const BPM = 120;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  /** Time of bar n, beat b (both from 1). */
  const at = (bar, beat = 1) => (bar - 1) * BAR + (beat - 1) * BEAT;

  const DURATION = 42;

  // Burned-in captions. `kicker` is the small manifest label above. `full` captions belong to full-screen scenes.
  const captions = [
    { t0: 0.05, t1: 0.98, text: 'SEEN IT?', full: true },
    { t0: 1.0, t1: 1.98, text: 'KEEP IT.', full: true },
    { t0: 4.0, t1: 8.0, text: 'SHARE ANY POST\nTO HAUL.', kicker: '01 / SHARE SHEET' },
    { t0: 8.0, t1: 10.0, text: 'KEEP\nSCROLLING.', kicker: '02 / IN THE BACKGROUND' },
    { t0: 10.0, t1: 12.0, text: "IT'S IN YOUR\nGALLERY.", kicker: '02 / IN THE BACKGROUND' },
    { t0: 12.0, t1: 14.0, text: 'COPIED\nA LINK?', kicker: '03 / THE HOLD' },
    { t0: 14.0, t1: 16.0, text: 'ONE TAP.', kicker: '03 / THE HOLD' },
    { t0: 16.0, t1: 18.5, text: 'BEST. 720P.\nAUDIO. GIF.', kicker: '04 / QUALITY' },
    { t0: 18.5, t1: 20.0, text: 'OR ASK\nEVERY TIME.', kicker: '04 / QUALITY' },
    { t0: 20.0, t1: 22.0, text: 'CLIP JUST THE\nGOOD PART.', kicker: '05 / CLIP' },
    { t0: 22.0, t1: 24.0, text: 'PICK FROM\nCAROUSELS.', kicker: '06 / INSTAGRAM' },
    { t0: 24.0, t1: 25.0, text: 'PHOTO POSTS,\nYOUR WAY.', kicker: '07 / TIKTOK PHOTOS' },
    { t0: 25.0, t1: 26.0, text: 'X GIFS\nSTAY GIFS.', kicker: '08 / X' },
    { t0: 26.0, t1: 28.0, text: 'WATCH IT\nRIGHT IN HAUL.', kicker: '09 / PLAYER' },
    { t0: 28.0, t1: 30.0, text: 'SHARE OR DELETE\nIN BULK.', kicker: '10 / SELECT' },
    { t0: 30.0, t1: 32.0, text: 'ONE TAP FROM\nQUICK SETTINGS.', kicker: '11 / TILE' },
  ];

  // Sound effects, by time. Types are synthesised in audio/synth.py.
  const sfx = [
    // Hook
    { t: 0.0, type: 'whoosh', gain: 0.8, dur: 0.9 },
    { t: 0.62, type: 'brake', gain: 0.5 },
    { t: 1.0, type: 'thunk', gain: 1.0 },
    // Sites
    { t: 2.0, type: 'swish', gain: 0.5 },
    { t: 2.1, type: 'stamp', gain: 0.6 },
    { t: 2.25, type: 'tick', gain: 0.7 }, { t: 2.5, type: 'tick', gain: 0.7 },
    { t: 2.75, type: 'tick', gain: 0.7 }, { t: 3.0, type: 'tick', gain: 0.7 },
    { t: 3.25, type: 'roll', gain: 0.35, dur: 0.25 },
    { t: 3.5, type: 'stamp', gain: 0.9 },
    { t: 3.8, type: 'whoosh', gain: 0.7, dur: 0.5 },
    // Share sheet
    { t: 4.55, type: 'tap', gain: 0.6 },
    { t: 4.7, type: 'swish', gain: 0.55 },
    { t: 5.5, type: 'tap', gain: 0.8 },
    { t: 5.9, type: 'swish', gain: 0.35, down: true },
    { t: 6.2, type: 'blip', gain: 0.6 },
    // Background
    { t: 8.0, type: 'swipe', gain: 0.35 }, { t: 9.0, type: 'swipe', gain: 0.35 },
    { t: 10.0, type: 'swipe', gain: 0.3 }, { t: 11.0, type: 'swipe', gain: 0.3 },
    { t: 8.25, type: 'drop', gain: 0.5 },
    { t: 8.55, type: 'shimmer', gain: 0.3, dur: 1.45 },
    { t: 10.0, type: 'chime', gain: 0.7 },
    { t: 10.5, type: 'tick', gain: 0.5 }, { t: 10.75, type: 'tick', gain: 0.5 }, { t: 11.0, type: 'tick', gain: 0.5 },
    // Hold
    { t: 11.9, type: 'whoosh', gain: 0.55, dur: 0.35 },
    { t: 12.1, type: 'tick', gain: 0.45 }, { t: 12.35, type: 'tick', gain: 0.45 }, { t: 12.6, type: 'tick', gain: 0.45 },
    { t: 12.85, type: 'tick', gain: 0.45 }, { t: 13.1, type: 'tick', gain: 0.45 },
    { t: 13.1, type: 'swish', gain: 0.4 },
    { t: 14.0, type: 'tap', gain: 0.9 },
    { t: 14.05, type: 'swish', gain: 0.4 },
    { t: 14.4, type: 'shimmer', gain: 0.22, dur: 1.4 },
    { t: 15.8, type: 'chime', gain: 0.45 },
    // Quality
    { t: 16.0, type: 'swish', gain: 0.55 },
    { t: 16.5, type: 'tick', gain: 0.7 }, { t: 17.0, type: 'tick', gain: 0.7 },
    { t: 17.5, type: 'tick', gain: 0.7 }, { t: 18.0, type: 'tick', gain: 0.7 },
    { t: 18.25, type: 'swish', gain: 0.3, down: true },
    { t: 18.5, type: 'swish', gain: 0.5 },
    { t: 19.5, type: 'tap', gain: 0.7 },
    // Clip
    { t: 19.85, type: 'swish', gain: 0.45 },
    { t: 20.25, type: 'slide', gain: 0.4, dur: 1.0 },
    { t: 21.25, type: 'click', gain: 0.6 },
    { t: 21.6, type: 'tap', gain: 0.6 },
    // Carousel
    { t: 22.0, type: 'swish', gain: 0.45 },
    { t: 22.5, type: 'check', gain: 0.7 }, { t: 22.75, type: 'check', gain: 0.7 },
    { t: 23.0, type: 'check', gain: 0.7 }, { t: 23.25, type: 'check', gain: 0.7 },
    { t: 23.6, type: 'tap', gain: 0.6 },
    // TikTok photos and X GIFs
    { t: 23.9, type: 'swish', gain: 0.45 },
    { t: 24.25, type: 'tick', gain: 0.7 }, { t: 24.5, type: 'tick', gain: 0.7 }, { t: 24.75, type: 'tick', gain: 0.7 },
    { t: 25.0, type: 'swish', gain: 0.35, down: true },
    { t: 25.3, type: 'pop', gain: 0.7 },
    // Player
    { t: 25.95, type: 'whoosh', gain: 0.45, dur: 0.3 },
    { t: 26.5, type: 'swipe', gain: 0.5 }, { t: 27.0, type: 'swipe', gain: 0.5 },
    // Select
    { t: 27.95, type: 'whoosh', gain: 0.4, dur: 0.25 },
    { t: 28.25, type: 'check', gain: 0.7 }, { t: 28.5, type: 'check', gain: 0.7 },
    { t: 28.75, type: 'check', gain: 0.7 }, { t: 29.0, type: 'check', gain: 0.7 },
    { t: 29.05, type: 'thunk', gain: 0.35 },
    { t: 29.5, type: 'blip', gain: 0.4 },
    // Tile
    { t: 30.0, type: 'swish', gain: 0.6 },
    { t: 30.75, type: 'tap', gain: 0.9 },
    { t: 31.0, type: 'riser', gain: 0.45, dur: 1.0 },
    // No strings
    { t: 32.0, type: 'whoosh', gain: 0.7, dur: 0.45, down: true },
    { t: 32.5, type: 'stamp', gain: 1.0 }, { t: 33.0, type: 'stamp', gain: 1.0 }, { t: 33.5, type: 'stamp', gain: 1.0 },
    { t: 34.0, type: 'typing', gain: 0.35, dur: 1.4 },
    { t: 35.0, type: 'riser', gain: 0.55, dur: 1.0 },
    { t: 35.6, type: 'whoosh', gain: 0.6, dur: 0.4 },
    // Logo
    { t: 36.0, type: 'whoosh', gain: 0.6, dur: 0.45 },
    { t: 36.5, type: 'thunk', gain: 1.0, big: true },
    { t: 37.0, type: 'tick', gain: 0.5 },
    { t: 37.5, type: 'tick', gain: 0.5 },
    { t: 38.0, type: 'pop', gain: 0.5 },
  ];

  // Music sections, by bar (1-based, inclusive).
  const music = {
    bpm: BPM,
    key: 'D minor',
    sections: [
      { from: 1, to: 1, name: 'hook' },     // filtered pad, riser, the hit on the pick
      { from: 2, to: 2, name: 'build' },    // hats and the motif, filter opening
      { from: 3, to: 16, name: 'groove' },  // full beat
      { from: 17, to: 18, name: 'break' },  // half-time stamps
      { from: 19, to: 19, name: 'logo' },   // the last hit
      { from: 20, to: 21, name: 'tail' },   // the chord rings out
    ],
  };

  // The teaser: pieces of the main timeline, cut on beats. [from, to] in main-timeline seconds.
  const teaser = {
    segments: [
      [0.0, 2.0],   // hook
      [4.5, 6.5],   // share sheet, tap, toast
      [10.0, 11.5], // saved to Movies/Haul
      [36.0, 40.0], // logo, line, CTA
    ],
  };

  const TL = { BPM, BEAT, BAR, at, DURATION, captions, sfx, music, teaser };
  if (typeof module !== 'undefined' && module.exports) module.exports = TL;
  root.TL = TL;
})(typeof window !== 'undefined' ? window : globalThis);
