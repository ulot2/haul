// "From link to Gallery": timing for the second promo. 120 BPM, a beat is 0.5 s, a bar is 2 s; 34 s in all.
(function (root) {
  const BPM = 120;
  const DURATION = 34;

  // Sound cues. `note` pitches a pop; `shake` jolts the frame.
  const sfx = [
    // 1 · hook
    { t: 0.0, type: 'slam', gain: 0.7 },
    { t: 0.5, type: 'morph', gain: 0.6 }, { t: 0.5, type: 'slam', gain: 0.6 },
    { t: 1.0, type: 'morph', gain: 0.6 }, { t: 1.0, type: 'thunk', gain: 0.8, shake: 6 },
    { t: 1.5, type: 'morph', gain: 0.6 }, { t: 1.52, type: 'pop', gain: 0.5 },
    // 2 · the link
    { t: 2.0, type: 'stretch', gain: 0.6 },
    { t: 2.22, type: 'zip', gain: 0.4 },
    { t: 2.4, type: 'typing', gain: 0.4, t1: 3.1, chars: 23 },
    { t: 3.22, type: 'squash', gain: 0.6 },
    { t: 3.5, type: 'launch', gain: 0.8 },
    // 3 · impact and formats
    { t: 3.85, type: 'fall', gain: 0.6 },
    { t: 4.0, type: 'impact', gain: 1.0, shake: 16 },
    { t: 4.15, type: 'scatter', gain: 0.6 },
    { t: 4.5, type: 'note', note: 'C5', gain: 0.6 }, { t: 4.75, type: 'note', note: 'D5', gain: 0.6 },
    { t: 5.0, type: 'note', note: 'F5', gain: 0.6 }, { t: 5.25, type: 'note', note: 'A5', gain: 0.6 },
    { t: 5.85, type: 'suck', gain: 0.5 },
    // 4 · the reel
    { t: 6.0, type: 'reel', gain: 0.5, t1: 6.25 }, { t: 6.25, type: 'stop', gain: 0.6 },
    { t: 6.27, type: 'reel', gain: 0.45, t1: 6.5 }, { t: 6.5, type: 'stop', gain: 0.6 },
    { t: 6.52, type: 'reel', gain: 0.45, t1: 6.75 }, { t: 6.75, type: 'stop', gain: 0.6 },
    { t: 6.77, type: 'reel', gain: 0.45, t1: 7.0 }, { t: 7.0, type: 'stop', gain: 0.6 },
    { t: 7.02, type: 'reel', gain: 0.55, t1: 7.5 }, { t: 7.5, type: 'stamp', gain: 0.9, shake: 10 },
    // 5 · gallery
    { t: 7.75, type: 'whoosh', gain: 0.7, dur: 0.3 }, { t: 8.0, type: 'whoosh', gain: 0.5, dur: 0.35 },
    { t: 8.15, type: 'ripplepops', gain: 0.45 },
    { t: 9.5, type: 'fall', gain: 0.5, dur: 0.5 },
    { t: 10.0, type: 'thunk', gain: 1.0, big: true, shake: 10 },
    { t: 10.02, type: 'bloom', gain: 0.5 },
    { t: 10.5, type: 'tick', gain: 0.6 }, { t: 10.75, type: 'tick', gain: 0.6 }, { t: 11.0, type: 'tick', gain: 0.6 },
    { t: 11.5, type: 'dive', gain: 0.8, dur: 0.5 },
    // 6 · share, keep scrolling, saved
    { t: 12.02, type: 'zip', gain: 0.5 },
    { t: 12.45, type: 'tap', gain: 0.8 }, { t: 12.5, type: 'bloom', gain: 0.5 },
    { t: 12.72, type: 'morph', gain: 0.5 },
    { t: 13.0, type: 'shimmer', gain: 0.35, dur: 1.4 },
    { t: 14.42, type: 'pop', gain: 0.6 }, { t: 14.55, type: 'chime', gain: 0.7 },
    { t: 15.75, type: 'whoosh', gain: 0.5, dur: 0.3 },
    // 7 · clip and pick
    { t: 16.0, type: 'doors', gain: 0.7 },
    { t: 16.3, type: 'slide', gain: 0.4, dur: 0.45 },
    { t: 16.9, type: 'snip', gain: 0.7 }, { t: 17.0, type: 'snip', gain: 0.7 },
    { t: 17.1, type: 'lift', gain: 0.6 },
    { t: 17.85, type: 'whoosh', gain: 0.45, dur: 0.3 },
    { t: 18.05, type: 'riffle', gain: 0.6 },
    { t: 18.75, type: 'check', gain: 0.8 }, { t: 19.0, type: 'check', gain: 0.8 }, { t: 19.25, type: 'check', gain: 0.8 },
    { t: 19.5, type: 'swish', gain: 0.4, down: true },
    { t: 19.75, type: 'whoosh', gain: 0.7, dur: 0.3 },
    // 8 · GIFs and the tile
    { t: 20.0, type: 'zip', gain: 0.5, dur: 0.45 },
    { t: 20.35, type: 'loop', gain: 0.35, dur: 1.5 },
    { t: 21.8, type: 'suck', gain: 0.4 },
    { t: 22.0, type: 'zip', gain: 0.5, dur: 0.35 },
    { t: 22.7, type: 'tap', gain: 0.9 }, { t: 22.75, type: 'flood', gain: 0.6 },
    { t: 22.85, type: 'bloom', gain: 0.45 },
    { t: 23.75, type: 'whoosh', gain: 0.6, dur: 0.3 },
    // 9 · no strings
    { t: 24.25, type: 'zip', gain: 0.45, dur: 0.4 }, { t: 24.75, type: 'zip', gain: 0.45, dur: 0.4 }, { t: 25.25, type: 'zip', gain: 0.45, dur: 0.4 },
    { t: 25.75, type: 'slash', gain: 0.9, shake: 6 }, { t: 26.0, type: 'slash', gain: 0.9, shake: 6 }, { t: 26.25, type: 'slash', gain: 1.0, shake: 8 },
    { t: 26.6, type: 'typing', gain: 0.35, t1: 27.2, chars: 26 }, { t: 27.25, type: 'typing', gain: 0.3, t1: 27.7, chars: 24 },
    { t: 27.72, type: 'suck', gain: 0.6 },
    // 10 · logo
    { t: 27.95, type: 'pop', gain: 0.6 },
    { t: 28.05, type: 'whoosh', gain: 0.4, dur: 0.4 },
    { t: 28.5, type: 'thunk', gain: 1.0, big: true },
    { t: 29.0, type: 'tick', gain: 0.5 }, { t: 29.5, type: 'tick', gain: 0.5 }, { t: 30.0, type: 'pop', gain: 0.5 },
  ];

  const teaser = { segments: [[0, 2], [3.5, 5.5], [8.0, 10.5], [28.0, 32.0]] };

  const TL = { BPM, DURATION, sfx, teaser, captions: [] };
  if (typeof module !== 'undefined' && module.exports) module.exports = TL;
  root.TL = TL;
})(typeof window !== 'undefined' ? window : globalThis);
