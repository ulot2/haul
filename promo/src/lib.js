// Timing and drawing helpers. Every frame is a pure function of time, so nothing here keeps state.
(function (root) {
  const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
  const lerp = (a, b, p) => a + (b - a) * p;

  /** CSS-style cubic-bezier easing. */
  function cubicBezier(p1x, p1y, p2x, p2y) {
    const cx = 3 * p1x, bx = 3 * (p2x - p1x) - cx, ax = 1 - cx - bx;
    const cy = 3 * p1y, by = 3 * (p2y - p1y) - cy, ay = 1 - cy - by;
    const sx = (t) => ((ax * t + bx) * t + cx) * t;
    const sy = (t) => ((ay * t + by) * t + cy) * t;
    return (x) => {
      if (x <= 0) return 0;
      if (x >= 1) return 1;
      let lo = 0, hi = 1, t = x;
      for (let i = 0; i < 30; i++) {
        const v = sx(t);
        if (Math.abs(v - x) < 1e-6) break;
        if (v < x) lo = t; else hi = t;
        t = (lo + hi) / 2;
      }
      return sy(t);
    };
  }

  const E = {
    linear: (t) => t,
    // Android's fast_out_slow_in, the app's own curve.
    fos: cubicBezier(0.4, 0, 0.2, 1),
    out: cubicBezier(0, 0, 0.2, 1),
    in: cubicBezier(0.4, 0, 1, 1),
    inOut: cubicBezier(0.65, 0, 0.35, 1),
    expoOut: (t) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)),
    expoIn: (t) => (t <= 0 ? 0 : Math.pow(2, 10 * t - 10)),
    quartOut: (t) => 1 - Math.pow(1 - t, 4),
    // Android's overshoot_interpolator (tension 2), used by the logo's pick.
    overshoot: (t, T = 2) => { t -= 1; return t * t * ((T + 1) * t + T) + 1; },
    backOut: (t, s = 1.70158) => { t -= 1; return t * t * ((s + 1) * t + s) + 1; },
  };

  /** Eased 0..1 progress of a move that starts at t0 and lasts d seconds. */
  const tw = (t, t0, d, ease = E.fos) => ease(clamp((t - t0) / d));

  /** A damped spring from 0 to 1, starting at t0 (for stamps and pops). */
  function spring(t, t0, freq = 9, damp = 0.42) {
    const x = t - t0;
    if (x <= 0) return 0;
    const w = 2 * Math.PI * freq * 0.25;
    return 1 - Math.exp(-damp * w * x) * Math.cos(w * Math.sqrt(1 - damp * damp) * x);
  }

  /** Keyframes: [[t, v, ease?], ...]; the ease on a key shapes the move into it. */
  function kf(t, keys) {
    if (t <= keys[0][0]) return keys[0][1];
    for (let i = 1; i < keys.length; i++) {
      const [t1, v1, e] = keys[i];
      if (t <= t1) {
        const [t0, v0] = keys[i - 1];
        const p = (e || E.fos)(clamp((t - t0) / (t1 - t0)));
        return lerp(v0, v1, p);
      }
    }
    return keys[keys.length - 1][1];
  }

  function hex(c) {
    const n = parseInt(c.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function mix(a, b, p) {
    const A = hex(a), B = hex(b);
    const r = A.map((v, i) => Math.round(lerp(v, B[i], clamp(p))));
    return '#' + r.map((v) => v.toString(16).padStart(2, '0')).join('');
  }
  function rgba(c, a) {
    const [r, g, b] = hex(c);
    return `rgba(${r},${g},${b},${a})`;
  }

  /** Small seeded random, so placeholder art is the same in every frame. */
  function rng(seed) {
    let a = seed >>> 0;
    return () => {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const fmtClock = (s) => {
    const m = Math.floor(s / 60), r = Math.floor(s % 60);
    return m + ':' + (r < 10 ? '0' : '') + r;
  };

  root.L = { clamp, lerp, cubicBezier, E, tw, spring, kf, mix, rgba, rng, fmtClock };
})(typeof window !== 'undefined' ? window : globalThis);
