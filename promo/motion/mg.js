// Motion-graphics toolkit: morphable outlines, stroke draw-ons, kinetic type, palettes.
(function (root) {
  const { clamp, lerp, E, mix } = root.L;

  const P = {
    // Dark (Freight)
    ground: '#121110', surface: '#1C1B19', raised: '#2A2825', line: '#36332E', ink: '#F4F0E8', muted: '#A8A298',
    // Light
    white: '#FFFFFF', paper: '#F3F0E9', lraised: '#E8E3D9', lline: '#D6CFC2', link: '#161513', lmuted: '#5C574F',
    clay: '#C8775A', clayText: '#9A4F33', onClay: '#1E1408', dim: '#3A3733',
  };
  /** Colors for things drawn on a white or a dark ground. */
  const on = (dark) => dark
    ? { bg: P.ground, ink: P.ink, muted: P.muted, line: P.line, raised: P.raised, surface: P.surface }
    : { bg: P.white, ink: P.link, muted: P.lmuted, line: P.lline, raised: P.lraised, surface: P.paper };

  // ---------------------------------------------------------------- outlines that morph
  const N = 180;

  function area(pts) {
    let a = 0;
    for (let i = 0; i < pts.length; i++) {
      const [x1, y1] = pts[i], [x2, y2] = pts[(i + 1) % pts.length];
      a += x1 * y2 - x2 * y1;
    }
    return a / 2;
  }
  /** Even points by arc length around a closed polyline, clockwise, starting at the top. */
  function resample(poly, n = N) {
    if (area(poly) < 0) poly = poly.slice().reverse();
    const segs = [];
    let total = 0;
    for (let i = 0; i < poly.length; i++) {
      const a = poly[i], b = poly[(i + 1) % poly.length];
      const l = Math.hypot(b[0] - a[0], b[1] - a[1]);
      segs.push([a, b, l]);
      total += l;
    }
    const out = [];
    let si = 0, acc = 0;
    for (let k = 0; k < n; k++) {
      const d = (k / n) * total;
      while (si < segs.length - 1 && acc + segs[si][2] < d) { acc += segs[si][2]; si++; }
      const [a, b, l] = segs[si];
      const u = l ? (d - acc) / l : 0;
      out.push([lerp(a[0], b[0], u), lerp(a[1], b[1], u)]);
    }
    // Start at the point straight above the centre.
    const cx = out.reduce((s, p) => s + p[0], 0) / n, cy = out.reduce((s, p) => s + p[1], 0) / n;
    let best = 0, bd = Infinity;
    out.forEach(([x, y], i) => {
      const ang = Math.atan2(y - cy, x - cx);
      const d = Math.abs(((ang + Math.PI / 2 + 3 * Math.PI) % (2 * Math.PI)) - Math.PI);
      if (d < bd) { bd = d; best = i; }
    });
    return out.slice(best).concat(out.slice(0, best));
  }

  const dense = (verts, per = 40) => {
    const out = [];
    for (let i = 0; i < verts.length; i++) {
      const a = verts[i], b = verts[(i + 1) % verts.length];
      for (let k = 0; k < per; k++) out.push([lerp(a[0], b[0], k / per), lerp(a[1], b[1], k / per)]);
    }
    return out;
  };

  const SHAPES = {
    circle: (r = 1) => resample(Array.from({ length: 400 }, (_, i) => { const a = (i / 400) * 2 * Math.PI - Math.PI / 2; return [Math.cos(a) * r, Math.sin(a) * r]; })),
    roundRect: (w, h, r) => {
      const pts = [];
      const arc = (cx, cy, a0) => { for (let i = 0; i <= 24; i++) { const a = a0 + (i / 24) * (Math.PI / 2); pts.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]); } };
      arc(w / 2 - r, -h / 2 + r, -Math.PI / 2);
      arc(w / 2 - r, h / 2 - r, 0);
      arc(-w / 2 + r, h / 2 - r, Math.PI / 2);
      arc(-w / 2 + r, -h / 2 + r, Math.PI);
      return resample(dense(pts, 3));
    },
    // ▶, centred on its centroid
    play: (s = 1) => resample(dense([[-0.38 * s, -0.5 * s], [0.62 * s, 0], [-0.38 * s, 0.5 * s]].map(([x, y]) => [x - 0.04 * s, y]))),
    heart: (s = 1) => resample(Array.from({ length: 400 }, (_, i) => {
      const t = (i / 400) * 2 * Math.PI;
      const x = 16 * Math.pow(Math.sin(t), 3);
      const y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
      return [(x / 17) * s * 0.55, (y / 17) * s * 0.55 + 0.04 * s];
    })),
    arrowDown: (s = 1) => resample(dense([
      [-0.16, -0.55], [0.16, -0.55], [0.16, 0.0], [0.42, 0.0], [0, 0.5], [-0.42, 0.0], [-0.16, 0.0],
    ].map(([x, y]) => [x * s, y * s]))),
    check: (s = 1) => resample(dense([
      [-0.5, 0.0], [-0.3, -0.2], [-0.12, -0.02], [0.32, -0.46], [0.52, -0.26], [-0.12, 0.38],
    ].map(([x, y]) => [x * s, y * s]))),
  };

  const morph = (a, b, p) => a.map((pa, i) => [lerp(pa[0], b[i][0], p), lerp(pa[1], b[i][1], p)]);
  const pathD = (pts) => 'M' + pts.map(([x, y]) => `${x.toFixed(2)},${y.toFixed(2)}`).join('L') + 'Z';

  // ---------------------------------------------------------------- strokes that draw themselves
  /** A stroked path that draws from 0 to 1 (p); `d` in its own coordinates. */
  function stroke(d, p, color, width, extra = '') {
    if (p <= 0) return '';
    return `<path d="${d}" pathLength="1" stroke-dasharray="1 1" stroke-dashoffset="${1 - clamp(p)}" fill="none" stroke="${color}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" ${extra}/>`;
  }

  // ---------------------------------------------------------------- kinetic type
  /**
   * Letters that animate one by one. style: 'drop' (fall in with overshoot), 'rise' (from a mask), 'pop' (scale up),
   * 'slam' (big to size). Exit (out > t0) sends letters up and away.
   */
  function letters(text, t, t0, { style = 'drop', size = 120, color = P.ink, font = 'cond', stagger = 0.03, dur = 0.42, out = null, outDur = 0.22, ls = 0, align = 'center' } = {}) {
    const chars = [...text];
    let i = 0;
    const spans = chars.map((ch) => {
      if (ch === '\n') return '<br>';
      const k = i++;
      const p = clamp((t - t0 - k * stagger) / dur);
      const q = out != null ? clamp((t - out - k * stagger * 0.5) / outDur) : 0;
      let tf = '', op = 1;
      if (style === 'drop') {
        const e = E.backOut(p, 2.2);
        tf = `translateY(${(1 - e) * -0.9}em) rotate(${(1 - e) * (k % 2 ? 14 : -14)}deg)`;
        op = clamp(p * 4);
      } else if (style === 'rise') {
        const e = E.expoOut(p);
        tf = `translateY(${(1 - e) * 1.05}em)`;
      } else if (style === 'pop') {
        const e = E.backOut(p, 2.6);
        tf = `scale(${e})`;
        op = clamp(p * 3);
      } else if (style === 'slam') {
        const e = E.expoOut(p);
        tf = `scale(${lerp(2.2, 1, e)})`;
        op = clamp(p * 3);
      }
      if (q > 0) { const eq = E.in(q); tf += ` translateY(${-eq * 1.1}em)`; op *= 1 - eq; }
      const c = ch === ' ' ? '&nbsp;' : ch.replace('&', '&amp;').replace('<', '&lt;');
      return `<span style="display:inline-block;transform:${tf};opacity:${op};transform-origin:50% 80%">${c}</span>`;
    }).join('');
    const cls = font === 'mono' ? 'mono' : font === 'body' ? 'ar' : 'cond';
    const clip = style === 'rise' ? 'overflow:hidden;' : '';
    return `<div class="${cls}" style="${clip}font-size:${size}px;line-height:${size * 0.92}px;color:${color};letter-spacing:${ls}px;text-align:${align};white-space:nowrap">${spans}</div>`;
  }

  /** Text typed one character at a time, with a clay caret. */
  function typed(text, t, t0, t1, { size = 40, color = P.ink, caret = true } = {}) {
    const n = Math.floor(text.length * clamp((t - t0) / (t1 - t0)));
    const on = caret && (t < t1 + 0.3 || Math.floor(t * 4) % 2 === 0);
    return `<span class="mono" style="font-size:${size}px;color:${color};letter-spacing:.04em">${text.slice(0, n)}${on ? `<span style="display:inline-block;width:${size * 0.58}px;height:${size}px;background:${P.clay};vertical-align:-${size * 0.16}px;margin-left:2px"></span>` : ''}</span>`;
  }

  root.MG = { P, on, N, resample, SHAPES, morph, pathD, stroke, letters, typed };
})(typeof window !== 'undefined' ? window : globalThis);
