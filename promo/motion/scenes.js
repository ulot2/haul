// "From link to Gallery": flat motion graphics in Freight. White and dark grounds, clay for what moves.
// Every function draws one frame of its scene from t (seconds). Canvas: 1080 × 1920.
(function (root) {
  const { clamp, lerp, E, tw, spring, kf, mix, rgba } = root.L;
  const { P, on, SHAPES, morph, pathD, stroke, letters, typed } = root.MG;

  const W = 1080, H = 1920, CX = 540;
  const svg = (inner, extra = '') => `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" style="position:absolute;left:0;top:0;overflow:visible" ${extra}>${inner}</svg>`;
  const at = (x, y, html, extra = '') => `<div style="position:absolute;left:${x}px;top:${y}px;${extra}">${html}</div>`;
  const centered = (y, html, extra = '') => `<div style="position:absolute;left:0;right:0;top:${y}px;display:flex;justify-content:center;${extra}">${html}</div>`;
  /** A 0→1→0 bump lasting d seconds from t0. */
  const bump = (t, t0, d) => { const x = (t - t0) / d; return x <= 0 || x >= 1 ? 0 : Math.sin(Math.PI * x); };

  // ================================================================ 1 · hook: ▶ → ♥ → ↓ → the card (0–2 s, white)
  const S = 380;
  const CARD = { w: 380, h: 146, r: 36 }; // the logo's card, 52 × 20 units with a 5-unit radius
  const KEYS = [
    [-1, SHAPES.play(S * 0.95)],
    [0.5, SHAPES.heart(S * 1.05)],
    [1.0, SHAPES.arrowDown(S * 0.95)],
    [1.5, SHAPES.roundRect(CARD.w, CARD.h, CARD.r)],
  ];
  function hookShape(t) {
    let i = 0;
    while (i + 1 < KEYS.length && t >= KEYS[i + 1][0]) i++;
    if (i === 0) return { pts: KEYS[0][1], punch: 0 };
    const p = E.backOut(clamp((t - KEYS[i][0]) / 0.24), 1.3);
    return { pts: morph(KEYS[i - 1][1], KEYS[i][1], p), punch: KEYS[i][0] };
  }
  function hook(t) {
    if (t >= 2.0) return '';
    const { pts, punch } = hookShape(t);
    const sc = 1 + 0.1 * bump(t, punch, 0.3);
    const rot = t < 1.5 ? 8 * bump(t, punch, 0.35) * (punch === 1.0 ? -1 : 1) : -6 * E.overshoot(clamp((t - 1.5) / 0.3));
    const y = 1080 + (t >= 1.0 && t < 1.5 ? 26 * bump(t, 1.0, 0.3) : 0);
    const u = CARD.w / 52;
    const card = t >= 1.5;
    const sh = card ? 0.2 * tw(t, 1.55, 0.2) : 0;
    const play = card ? tw(t, 1.62, 0.15) : 0;
    const shape = `<g transform="translate(${CX} ${y}) rotate(${rot}) scale(${sc})">
      ${sh > 0 ? `<rect x="${-CARD.w / 2 - 2 * u}" y="${-CARD.h / 2 + 4 * u}" width="${CARD.w}" height="${CARD.h}" rx="${CARD.r}" fill="#000" opacity="${sh}" filter="url(#soft)"/>` : ''}
      <path d="${pathD(pts)}" fill="${P.clay}"/>
      ${play > 0 ? `<path d="M${-7 * u},${-5 * u} L${2 * u},0 L${-7 * u},${5 * u} Z" fill="${P.onClay}" opacity="${play}"/>` : ''}
    </g>`;
    const defs = `<defs><filter id="soft" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="10"/></filter></defs>`;
    const word = (txt, t0, out) => (t >= t0 - 0.01 && (out == null || t < out + 0.2)) ? centered(470, letters(txt, t, t0, { style: 'slam', size: 210, color: P.link, stagger: 0.02, dur: 0.26, out, outDur: 0.09 })) : '';
    return svg(defs + shape) + word('SEE IT.', -1, 0.4) + word('WANT IT.', 0.5, 0.9) + word('HAUL IT.', 1.0, 1.84);
  }

  // ================================================================ 2 · the link (2–4 s, white) and the drop (to 4.4 s)
  const LINK = 'M10,14 L14,10 M8,11 L5.5,13.5 A3.5,3.5 0 0,0 10.5,18.5 L13,16 M16,13 L18.5,10.5 A3.5,3.5 0 0,0 13.5,5.5 L11,8';
  function link(t) {
    if (t < 2.0 || t >= 4.45) return '';
    const g = tw(t, 2.0, 0.42, (x) => E.backOut(x, 1.4));
    let w = lerp(CARD.w, 840, g), h = lerp(CARD.h, 150, g), r = lerp(CARD.r, 75, g);
    let rot = lerp(-6, 0, g), x = CX, y = lerp(1080, 1040, g), sx = 1, sy = 1;
    // Anticipation, then launch.
    const sq = tw(t, 3.22, 0.28, E.out);
    if (t < 3.5) { sy = 1 - 0.2 * sq; sx = 1 + 0.06 * sq; y += 30 * sq; }
    const up = tw(t, 3.5, 0.3, E.in);
    if (t >= 3.5 && t < 3.85) { y = lerp(1070, -420, up); sy = lerp(0.8, 1.45, Math.min(1, up * 3)); sx = lerp(1.06, 0.78, Math.min(1, up * 3)); }
    // Coming back down, into the impact.
    const down = tw(t, 3.85, 0.15, E.in);
    if (t >= 3.85) {
      w = 420; h = 150; r = 75;
      y = lerp(-300, 1000, down); sy = 1.4; sx = 0.8;
      if (t >= 4.0) { const s = spring(t, 4.0, 4, 0.35); sy = lerp(0.6, 1, s); sx = lerp(1.25, 1, s); y = 1000 + (1 - sy) * 40; }
      if (t >= 4.15) { const k = tw(t, 4.15, 0.18, E.in); sx *= 1 - k; sy *= 1 - k; }
    }
    const playA = 1 - tw(t, 2.0, 0.12);
    const u = CARD.w / 52;
    const iconP = tw(t, 2.22, 0.3, E.out);
    const lines = t > 3.52 && t < 3.85 ? [-180, 0, 180].map((dx, i) => `<line x1="${x + dx}" y1="${y + h * 0.9}" x2="${x + dx}" y2="${y + h * 0.9 + 500 * (1 - up) * (0.6 + 0.2 * i)}" stroke="${P.lline}" stroke-width="10" stroke-linecap="round" opacity="${1 - up}"/>`).join('') : '';
    const body = `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${sx} ${sy})">
      <rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="${r}" fill="${P.clay}"/>
      ${playA > 0 ? `<path d="M${-7 * u},${-5 * u} L${2 * u},0 L${-7 * u},${5 * u} Z" fill="${P.onClay}" opacity="${playA}"/>` : ''}
      ${t < 3.85 ? `<g transform="translate(${-w / 2 + 34} -38) scale(3.2)">${stroke(LINK, iconP, P.onClay, 2.2)}</g>` : ''}
    </g>`;
    const url = t >= 2.35 && t < 3.5 ? `<div style="position:absolute;left:${CX - 840 / 2 + 130}px;top:${1040 - 28}px;width:680px;overflow:hidden;transform:translateY(${30 * sq}px) scale(${sx},${sy});transform-origin:-90px 28px">${typed('vm.tiktok.com/ZMh3k9Ra2', t, 2.4, 3.1, { size: 46, color: P.onClay, caret: t < 3.25 })}</div>` : '';
    const cap = t < 3.9 ? centered(470, letters('ANY LINK.', t, 2.02, { style: 'drop', size: 210, color: P.link, stagger: 0.035, out: 3.48 })) : '';
    return svg(lines + body) + url + cap;
  }

  // ================================================================ 3 · impact, dark floods in, four formats (4–6 s)
  function flood(t) {
    if (t < 4.0 || t >= 4.5) return '';
    const r = 2400 * E.expoOut(clamp((t - 4.0) / 0.5));
    const ring = t < 4.6 ? `<circle cx="${CX}" cy="1000" r="${700 * E.expoOut(clamp((t - 4.0) / 0.55))}" fill="none" stroke="${P.clay}" stroke-width="${24 * (1 - clamp((t - 4.0) / 0.55))}"/>` : '';
    return svg(`<circle cx="${CX}" cy="1000" r="${r}" fill="${P.ground}"/>${ring}`);
  }

  const FMT = [
    { x: 300, y: 860, t: 4.5, name: 'VIDEO', sub: 'BEST · 720P' },
    { x: 780, y: 860, t: 4.75, name: 'PHOTOS', sub: 'CAROUSELS TOO' },
    { x: 300, y: 1330, t: 5.0, name: 'AUDIO', sub: 'M4A' },
    { x: 780, y: 1330, t: 5.25, name: 'GIF', sub: 'REAL GIFS' },
  ];
  function glyph(i, t, p) {
    const c = P.ink;
    if (i === 0) return stroke('M-48,-60 L66,0 L-48,60 Z', p, c, 14);
    if (i === 1) return stroke('M-80,60 L-20,-10 L20,30 L45,5 L85,60 Z', p, c, 14) + stroke('M30,-50 m-22,0 a22,22 0 1,0 44,0 a22,22 0 1,0 -44,0', p, c, 12);
    if (i === 2) {
      return [-60, -30, 0, 30, 60].map((x, k) => {
        const hgt = 30 + 50 * Math.abs(Math.sin(t * (5 + k * 1.3) + k));
        return stroke(`M${x},${hgt} L${x},${-hgt}`, p, c, 16);
      }).join('');
    }
    const a = t * 220;
    return `<g transform="rotate(${a})">${stroke('M0,-62 A62,62 0 1,1 -58,22', p, c, 14)}${p > 0.8 ? `<path d="M-80,10 L-56,40 L-34,8 Z" fill="${c}" transform="rotate(10 -58 22)"/>` : ''}</g>`;
  }
  function formats(t) {
    if (t < 4.15 || t >= 6.15) return '';
    const exit = tw(t, 5.85, 0.25, E.in);
    let g = '', html = '';
    FMT.forEach((f, i) => {
      const p = clamp((t - (f.t - 0.32)) / 0.32);
      if (p <= 0) return;
      const e = E.backOut(p, 1.6);
      const x = lerp(CX, f.x, e), y = lerp(1000, f.y, e) - Math.sin(Math.PI * p) * 120;
      const s = lerp(0.15, 1, e) * (1 - exit), rot = (1 - e) * (i % 2 ? 40 : -40);
      const ex = lerp(x, CX, exit), ey = lerp(y, 1000, exit);
      g += `<g transform="translate(${ex} ${ey}) rotate(${rot}) scale(${s})">
        <rect x="-150" y="-150" width="300" height="300" rx="44" fill="${P.surface}" stroke="${P.line}" stroke-width="3"/>
        ${glyph(i, t, tw(t, f.t, 0.35, E.out))}</g>`;
      const lp = tw(t, f.t + 0.05, 0.3, E.expoOut) * (1 - exit);
      html += `<div style="position:absolute;left:${f.x - 200}px;top:${f.y + 175}px;width:400px;text-align:center;opacity:${lp};transform:translateY(${(1 - lp) * 30}px)">
        <div class="cond" style="font-size:72px;line-height:1;color:${P.ink}">${f.name}</div>
        <div class="mono" style="font-size:26px;letter-spacing:.12em;color:${P.muted};margin-top:10px">${f.sub}</div></div>`;
    });
    return svg(g) + html;
  }

  // ================================================================ 4 · the reel of sites (6–8 s, dark)
  const REEL = ['TIKTOK', 'VIMEO', 'TUMBLR', 'TWITCH', 'INSTAGRAM', 'REDDIT', 'BANDCAMP', 'BILIBILI', 'X', 'SOUNDCLOUD', 'FACEBOOK', 'DAILYMOTION', 'YOUTUBE',
    'PINTEREST', 'BLUESKY', 'VIMEO', 'TWITCH', 'REDDIT', 'TUMBLR', 'BANDCAMP', 'SOUNDCLOUD', 'BILIBILI', 'FACEBOOK', 'DAILYMOTION', '+1,000 MORE'];
  const settle = (x) => E.backOut(x, 1.1);
  const REEL_KEYS = [[6.0, -3], [6.25, 0, settle], [6.5, 4, settle], [6.75, 8, settle], [7.0, 12, settle], [7.5, 24, settle]];
  function reel(t) {
    if (t < 5.95 || t >= 8.3) return '';
    const pos = kf(t, REEL_KEYS);
    const vel = Math.abs(kf(t + 1 / 60, REEL_KEYS) - pos) * 60;
    const IH = 230, cy = 1010;
    const vis = t < 6.15 ? tw(t, 5.95, 0.2) : 1;
    let items = '';
    REEL.forEach((name, i) => {
      const y = cy + (i - pos) * IH;
      if (y < cy - IH * 2 || y > cy + IH * 2) return;
      const last = i === REEL.length - 1;
      items += `<div class="cond" style="position:absolute;left:0;right:0;top:${y - IH / 2}px;height:${IH}px;line-height:${IH}px;text-align:center;font-size:${name.length > 9 ? 168 : 200}px;color:${last ? P.clay : P.ink}">${name}</div>`;
    });
    const blur = Math.min(10, vel * 0.6);
    const fade = `linear-gradient(180deg, ${P.ground} 0%, ${rgba(P.ground, 0)} 30%, ${rgba(P.ground, 0)} 70%, ${P.ground} 100%)`;
    const lab = tw(t, 7.55, 0.3, E.expoOut);
    return `<div style="position:absolute;left:0;right:0;top:${cy - IH * 1.5}px;height:${IH * 3}px;overflow:hidden;opacity:${vis}">
        <div style="position:absolute;left:0;right:0;top:${-cy + IH * 1.5}px;height:${H}px;filter:blur(${blur.toFixed(1)}px)">${items}</div>
        <div style="position:absolute;inset:0;background:${fade}"></div></div>
      <div style="position:absolute;left:180px;right:180px;top:${cy - IH / 2 - 6}px;height:3px;background:${P.line};opacity:${vis}"></div>
      <div style="position:absolute;left:180px;right:180px;top:${cy + IH / 2 + 3}px;height:3px;background:${P.line};opacity:${vis}"></div>
      ${centered(cy + IH / 2 + 60, `<span class="mono" style="font-size:28px;letter-spacing:.14em;color:${P.muted};opacity:${lab}">ALL READ BY YT-DLP, ON YOUR PHONE</span>`)}`;
  }

  // ================================================================ 5 · the gallery (8–12 s, white)
  const GT = 272, GG = 24, GL = 108, GTOP = 520;
  const TILE_TONES = ['#EDE8DF', '#E2DCD0', '#D9D2C5', '#E8E3D9', '#DED7CA', '#E5DFD4'];
  const tileXY = (i) => [GL + (i % 3) * (GT + GG) + GT / 2, GTOP + Math.floor(i / 3) * (GT + GG) + GT / 2];
  const SLOT = 4;
  const tilePop = (i) => { const r = Math.floor(i / 3), c = i % 3; return 8.15 + (Math.abs(r - 1.5) + Math.abs(c - 1)) * 0.11; };
  function miniGlyph(i) {
    const k = i % 3;
    if (k === 0) return `<path d="M-14,-18 L20,0 L-14,18 Z" fill="${P.lline}"/>`;
    if (k === 1) return `<path d="M-26,18 L-6,-6 L6,6 L14,-2 L28,18 Z" fill="${P.lline}"/><circle cx="12" cy="-18" r="7" fill="${P.lline}"/>`;
    return `<path d="M-8,14 L-8,-18 L18,-22 L18,10" fill="none" stroke="${P.lline}" stroke-width="6"/><circle cx="-14" cy="14" r="8" fill="${P.lline}"/><circle cx="12" cy="10" r="8" fill="${P.lline}"/>`;
  }
  function gallery(t) {
    if (t < 7.98 || t >= 12.05) return '';
    let g = '';
    const [sx, sy] = tileXY(SLOT);
    for (let i = 0; i < 12; i++) {
      const [x, y] = tileXY(i);
      const p = clamp((t - tilePop(i)) / 0.38);
      if (p <= 0) continue;
      if (i === SLOT) {
        const a = clamp(p * 2) * (1 - tw(t, 10.0, 0.1));
        g += `<rect x="${x - GT / 2 + 4}" y="${y - GT / 2 + 4}" width="${GT - 8}" height="${GT - 8}" rx="30" fill="none" stroke="${P.lline}" stroke-width="5" stroke-dasharray="18 14" opacity="${a}"/>
          <path d="M${x - 26},${y} L${x + 26},${y} M${x},${y - 26} L${x},${y + 26}" stroke="${P.lline}" stroke-width="6" stroke-linecap="round" opacity="${a}"/>`;
        continue;
      }
      const d = Math.hypot(x - sx, y - sy) / 300;
      const rip = 1 - 0.1 * bump(t, 10.0 + d * 0.07, 0.32);
      const s = E.backOut(p, 2) * rip;
      g += `<g transform="translate(${x} ${y}) scale(${s})"><rect x="${-GT / 2}" y="${-GT / 2}" width="${GT}" height="${GT}" rx="30" fill="${TILE_TONES[i % 6]}"/>${miniGlyph(i)}</g>`;
    }
    // The haul drops into the empty slot.
    if (t >= 9.5) {
      const f = tw(t, 9.5, 0.5, E.in);
      let y = lerp(-260, sy, f), rot = lerp(28, 0, f), scx = 1, scy = 1;
      if (t >= 10.0) { const s = spring(t, 10.0, 4.5, 0.38); scy = lerp(0.78, 1, s); scx = lerp(1.14, 1, s); y = sy + (1 - scy) * GT / 2; rot = 0; }
      g += `<g transform="translate(${sx} ${y}) rotate(${rot}) scale(${scx} ${scy})"><rect x="${-GT / 2}" y="${-GT / 2}" width="${GT}" height="${GT}" rx="30" fill="${P.clay}"/>
        <path d="M-26,-36 L40,0 L-26,36 Z" fill="${P.onClay}"/></g>`;
    }
    // Zoom through the ▶ into the next scene.
    const z = t >= 11.5 ? Math.exp(Math.log(70) * E.expoIn(clamp((t - 11.5) / 0.5))) : 1;
    const fx = sx + 4, fy = sy;
    const chips = ['MOVIES/HAUL', 'PICTURES/HAUL', 'MUSIC/HAUL'].map((n, i) => {
      const p = tw(t, 10.5 + i * 0.25, 0.3, E.expoOut);
      return `<span class="mono" style="display:inline-block;opacity:${p};transform:translateY(${(1 - p) * 24}px);font-size:25px;letter-spacing:.08em;color:${P.link};border:2px solid ${P.lline};border-radius:10px;padding:10px 14px;margin:0 6px">${n}</span>`;
    }).join('');
    const cap = letters('STRAIGHT TO\nYOUR GALLERY.', t, 8.25, { style: 'drop', size: 118, color: P.link, stagger: 0.022, out: 11.3 });
    return `<div style="position:absolute;inset:0;transform-origin:${fx}px ${fy}px;transform:scale(${z})">
      ${svg(g)}${centered(GTOP + 4 * (GT + GG) + 30, chips)}</div>${t < 11.6 ? centered(232, cap) : ''}`;
  }

  // ================================================================ 6 · share, keep scrolling, saved (12–16 s, dark)
  const SHARE = 'M12,15 L12,3 M7,8 L12,3 L17,8 M5,13 L5,20 L19,20 L19,13';
  function share(t) {
    if (t < 12.0 || t >= 16.4) return '';
    const cy = 1010;
    const exitP = tw(t, 15.7, 0.3, E.in);
    let g = '';
    // A feed keeps scrolling at the edges.
    const feedA = tw(t, 12.9, 0.4) * (1 - exitP);
    if (feedA > 0) {
      for (const [x, off] of [[-40, 0], [1120, 160]]) {
        for (let k = -1; k < 8; k++) {
          const y = ((k * 330 - (t - 12) * 420 + off) % (330 * 8) + 330 * 8) % (330 * 8) - 330;
          g += `<rect x="${x - 110}" y="${y}" width="220" height="290" rx="30" fill="${P.surface}" opacity="${feedA}"/>`;
        }
      }
    }
    // The share sign draws, is tapped, and folds away.
    const fold = tw(t, 12.72, 0.25, E.in);
    if (fold < 1) {
      const press = 1 - 0.1 * bump(t, 12.45, 0.25);
      const k = 15 * press * (1 - fold);
      g += `<g transform="translate(${CX} ${cy}) rotate(${fold * 90}) scale(${k}) translate(-12 -11.5)">${stroke(SHARE, tw(t, 12.02, 0.4, E.out), P.ink, 2)}</g>`;
    }
    for (let i = 0; i < 3; i++) {
      const q = clamp((t - 12.5 - i * 0.08) / 0.55);
      if (q > 0 && q < 1) g += `<circle cx="${CX}" cy="${cy}" r="${80 + 360 * E.expoOut(q)}" fill="none" stroke="${P.clay}" stroke-width="${10 * (1 - q)}" opacity="${1 - q}"/>`;
    }
    // Progress ring.
    const R = 220;
    if (t >= 12.8) {
      const ringIn = E.backOut(clamp((t - 12.8) / 0.3), 1.5) * (1 - exitP);
      const pr = E.inOut(clamp((t - 13.0) / 1.4));
      const done = tw(t, 14.42, 0.3, (x) => E.backOut(x, 1.8));
      g += `<g transform="translate(${CX} ${cy}) scale(${ringIn})">
        <circle r="${R}" fill="none" stroke="${P.raised}" stroke-width="28"/>
        ${pr > 0 ? `<circle r="${R}" fill="none" stroke="${P.clay}" stroke-width="28" stroke-linecap="round" pathLength="1" stroke-dasharray="${pr} 1" transform="rotate(-90)"/>` : ''}
        ${done > 0 ? `<circle r="${(R + 14) * done}" fill="${P.clay}"/>` : ''}
        ${done > 0 ? stroke('M-90,0 L-25,62 L95,-70', tw(t, 14.55, 0.25, E.out), P.onClay, 34) : ''}
      </g>`;
    }
    const pct = Math.round(100 * E.inOut(clamp((t - 13.0) / 1.4)));
    const numA = t >= 12.95 && t < 14.5 ? tw(t, 12.95, 0.2) * (1 - tw(t, 14.38, 0.1)) : 0;
    const num = numA > 0 ? centered(cy - 80, `<div class="cond" style="font-size:170px;line-height:160px;color:${P.ink};font-variant-numeric:tabular-nums;opacity:${numA}">${pct}%</div>`) : '';
    const lab1 = t >= 12.95 && t < 14.6 ? tw(t, 12.95, 0.3) * (1 - tw(t, 14.42, 0.15)) : 0;
    const lab2 = tw(t, 14.65, 0.3, E.expoOut) * (1 - exitP);
    const labs = centered(cy + R + 80, `<div style="position:relative;width:900px;height:40px">
        <div class="mono" style="position:absolute;inset:0;text-align:center;font-size:28px;letter-spacing:.12em;color:${P.muted};opacity:${lab1}">DOWNLOADING IN THE BACKGROUND</div>
        <div class="mono" style="position:absolute;inset:0;text-align:center;font-size:28px;letter-spacing:.12em;color:${P.ink};opacity:${lab2};transform:translateY(${(1 - lab2) * 20}px)">SAVED TO MOVIES/HAUL · 18.2 MB</div></div>`);
    const cap = (txt, t0, out) => (t >= t0 && t < out + 0.4) ? centered(232, letters(txt, t, t0, { style: 'drop', size: 118, color: P.ink, stagger: 0.022, out })) : '';
    return `<div style="position:absolute;inset:0;transform:scale(${1 - 0.08 * exitP});opacity:${1 - exitP}">${svg(g)}${num}${labs}</div>`
      + cap('SHARE\nTO HAUL.', 12.0, 12.85) + cap('KEEP\nSCROLLING.', 13.0, 14.3) + cap('SAVED.', 14.5, 15.65);
  }

  // ================================================================ 7 · clip the good part, then pick from a carousel (16–20 s, white)
  const BX0 = 90, BX1 = 990, BY = 1010, BH = 130;
  const XA = BX0 + 900 * (80 / 222), XB = BX0 + 900 * (125 / 222);
  function clip(t) {
    if (t < 16.0 || t >= 18.2) return '';
    const grow = tw(t, 16.0, 0.35, E.expoOut);
    const out = tw(t, 17.85, 0.3, E.in);
    const hp = tw(t, 16.3, 0.45, E.fos);
    const xa = lerp(BX0 + 10, XA, hp), xb = lerp(BX1 - 10, XB, hp);
    const lift = tw(t, 17.1, 0.4, (x) => E.backOut(x, 1.6));
    const close = tw(t, 17.15, 0.35, E.fos);
    const gap = (XB - XA) * close / 2;
    let g = '';
    const ticks = (x0, x1, shift) => { let s = ''; for (let x = x0 + 18; x < x1 - 10; x += 30) s += `<line x1="${x + shift}" y1="${BY - BH / 2 + 18}" x2="${x + shift}" y2="${BY + BH / 2 - 18}" stroke="${P.lline}" stroke-width="4"/>`; return s; };
    const cx = (BX0 + BX1) / 2;
    g += `<g transform="translate(${cx} 0) scale(${grow * (1 - out)} 1) translate(${-cx} 0)" opacity="${1 - out}">`;
    if (t < 17.0) {
      g += `<rect x="${BX0}" y="${BY - BH / 2}" width="${BX1 - BX0}" height="${BH}" rx="26" fill="${P.lraised}"/>${ticks(BX0, BX1, 0)}`;
    } else {
      const dim = 1 - 0.45 * close;
      g += `<g opacity="${dim}"><rect x="${BX0 + gap}" y="${BY - BH / 2}" width="${XA - BX0}" height="${BH}" rx="26" fill="${P.lraised}"/>${ticks(BX0, XA, gap)}
        <rect x="${XB - gap}" y="${BY - BH / 2}" width="${BX1 - XB}" height="${BH}" rx="26" fill="${P.lraised}"/>${ticks(XB, BX1, -gap)}</g>`;
    }
    if (t < 17.1) g += `<rect x="${xa}" y="${BY - BH / 2}" width="${xb - xa}" height="${BH}" fill="${P.clay}" opacity="0.28"/>`;
    // Handles
    if (t < 17.1) {
      for (const [x, lab] of [[xa, '1:20'], [xb, '2:05']]) {
        g += `<rect x="${x - 11}" y="${BY - BH / 2 - 22}" width="22" height="${BH + 44}" rx="11" fill="${P.clay}"/>`;
      }
    }
    // Snips
    for (const [x, t0] of [[XA, 16.9], [XB, 17.0]]) {
      const p = tw(t, t0, 0.1, E.out), q = tw(t, t0 + 0.15, 0.2);
      if (p > 0 && q < 1) g += `<line x1="${x}" y1="${BY - 140}" x2="${x}" y2="${BY - 140 + 280 * p}" stroke="${P.link}" stroke-width="8" stroke-linecap="round" opacity="${1 - q}"/>`;
    }
    g += '</g>';
    // The section lifts out like the logo's card.
    if (t >= 17.05) {
      const w = (XB - XA) * lerp(1, 1.9, lift), hh = BH * lerp(1, 1.45, lift);
      const y = BY - 220 * lift - out * 1400, x = (XA + XB) / 2 + out * 300;
      const u = w / 52;
      g += `<g transform="translate(${x} ${y}) rotate(${-6 * lift})">
        <rect x="${-w / 2 - 10}" y="${-hh / 2 + 22}" width="${w}" height="${hh}" rx="${hh * 0.25}" fill="#000" opacity="${0.16 * lift}" filter="url(#soft2)"/>
        <rect x="${-w / 2}" y="${-hh / 2}" width="${w}" height="${hh}" rx="${hh * 0.25}" fill="${P.clay}"/>
        <path d="M${-7 * u},${-5 * u} L${2 * u},0 L${-7 * u},${5 * u} Z" fill="${P.onClay}" opacity="${lift}"/>
        <text x="${w / 2 - 18}" y="${hh / 2 - 18}" text-anchor="end" font-family="JBMono" font-size="26" fill="${P.onClay}" opacity="${lift}">0:45</text></g>`;
    }
    const labs = t < 17.1 ? [[xa, '1:20'], [xb, '2:05']].map(([x, l]) => `<div class="mono" style="position:absolute;left:${x - 60}px;width:120px;text-align:center;top:${BY - BH / 2 - 76}px;font-size:28px;color:${P.clayText};opacity:${hp * (1 - out)}">${l}</div>`).join('') : '';
    const ends = `<div class="mono" style="position:absolute;left:${BX0}px;top:${BY + BH / 2 + 22}px;font-size:26px;color:${P.lmuted};opacity:${grow * (1 - out) * (1 - 0.6 * close)}">0:00</div>
      <div class="mono" style="position:absolute;right:${W - BX1}px;top:${BY + BH / 2 + 22}px;font-size:26px;color:${P.lmuted};opacity:${grow * (1 - out) * (1 - 0.6 * close)}">3:42</div>`;
    const defs = `<defs><filter id="soft2" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="12"/></filter></defs>`;
    const cap = t < 18.15 ? centered(232, letters('CLIP THE\nGOOD PART.', t, 16.05, { style: 'drop', size: 118, color: P.link, stagger: 0.022, out: 17.72 })) : '';
    return svg(defs + g) + labs + ends + cap;
  }

  const PICKED = [0, 2, 3];
  function pick(t) {
    if (t < 18.0 || t >= 20.2) return '';
    let g = '';
    const fan = tw(t, 18.1, 0.4, (x) => E.backOut(x, 1.5));
    const drop = tw(t, 19.5, 0.35, E.in);
    const gather = tw(t, 19.5, 0.4, E.fos);
    const all = tw(t, 18.0, 0.2, E.backOut);
    const order = [0, 4, 1, 3, 2]; // draw the middle card last
    order.forEach((i) => {
      const o = i - 2;
      const chosen = PICKED.indexOf(i);
      const ck = chosen >= 0 ? clamp((t - (18.75 + chosen * 0.25)) / 0.25) : 0;
      let x = CX + o * 175 * fan, y = 1040 + o * o * 20 * fan - 26 * E.out(ck), rot = o * 9 * fan, op = 1;
      if (chosen >= 0) { const tx = CX + (chosen - 1) * 150, tr = (chosen - 1) * 6; x = lerp(x, tx, gather); rot = lerp(rot, tr, gather); y = lerp(y, 1020, gather); }
      else { y += drop * 700; rot += drop * o * 20; op = 1 - drop; }
      const glyph = i % 2 ? `<path d="M-22,-28 L32,0 L-22,28 Z" fill="${P.lline}"/>` : `<path d="M-46,34 L-12,-8 L10,14 L24,0 L50,34 Z" fill="${P.lline}"/><circle cx="18" cy="-34" r="12" fill="${P.lline}"/>`;
      g += `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${all})" opacity="${op}">
        <rect x="-115" y="-145" width="230" height="290" rx="28" fill="${TILE_TONES[(i + 2) % 6]}" stroke="${ck > 0 ? P.clay : P.white}" stroke-width="${ck > 0 ? 8 : 6}"/>${glyph}
        ${ck > 0 ? `<g transform="translate(78 -108) scale(${lerp(1.8, 1, E.backOut(ck, 2))})"><circle r="34" fill="${P.clay}" stroke="${P.white}" stroke-width="5"/>${stroke('M-15,1 L-4,12 L16,-11', 1, P.onClay, 8)}</g>` : `<circle cx="78" cy="-108" r="30" fill="none" stroke="${P.white}" stroke-width="5" opacity="${1 - gather}"/>`}
      </g>`;
    });
    const out = tw(t, 19.85, 0.2, E.in);
    const lab = tw(t, 19.55, 0.3, E.expoOut);
    const cap = t < 19.95 ? centered(232, letters('PICK FROM\nCAROUSELS.', t, 18.05, { style: 'drop', size: 118, color: P.link, stagger: 0.022, out: 19.72 })) : '';
    return `<div style="position:absolute;inset:0;opacity:${1 - out}">${svg(g)}${centered(1290, `<span class="mono" style="font-size:28px;letter-spacing:.12em;color:${P.lmuted};opacity:${lab}">3 OF 5 · INSTAGRAM CAROUSEL</span>`)}</div>${cap}`;
  }

  // ================================================================ 8 · GIFs loop, and the tile (20–24 s, dark)
  const LA = 400, LCY = 1010, LSY = 1.7;
  const lem = (th) => { const s = Math.sin(th), c = Math.cos(th), d = 1 + s * s; return [CX + (LA * c) / d, LCY + LSY * (LA * s * c) / d]; };
  const LEM_D = (() => { const pts = []; for (let i = 0; i <= 240; i++) pts.push(lem((i / 240) * 2 * Math.PI)); return 'M' + pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join('L'); })();
  function gif(t) {
    if (t < 20.0 || t >= 22.1) return '';
    const draw = tw(t, 20.0, 0.45, E.out) - tw(t, 21.8, 0.25, E.in);
    let g = stroke(LEM_D, draw, '#3E3A35', 18);
    const run = (tt) => 2 * Math.PI * 1.35 * E.inOut(clamp((tt - 20.35) / 1.5));
    const vis = tw(t, 20.3, 0.15) * (1 - tw(t, 21.8, 0.2));
    for (let k = 6; k >= 0; k--) {
      const tt = t - k * 0.028;
      const th = run(tt) + Math.PI / 2 * 0;
      const [x, y] = lem(th);
      const [x2, y2] = lem(th + 0.01);
      const ang = Math.atan2(y2 - y, x2 - x) * 180 / Math.PI;
      const a = (k === 0 ? 1 : 0.22 * (1 - k / 7)) * vis;
      g += `<g transform="translate(${x} ${y}) rotate(${ang})" opacity="${a}"><rect x="-80" y="-31" width="160" height="62" rx="15" fill="${P.clay}"/>${k === 0 ? `<path d="M-18,-14 L10,0 L-18,14 Z" fill="${P.onClay}"/>` : ''}</g>`;
    }
    const lab = tw(t, 20.6, 0.3, E.expoOut) * (1 - tw(t, 21.8, 0.2));
    const cap = centered(232, letters('GIFS STAY\nGIFS.', t, 20.05, { style: 'drop', size: 118, color: P.ink, stagger: 0.022, out: 21.75 }));
    return svg(g) + centered(1400, `<span class="mono" style="font-size:28px;letter-spacing:.12em;color:${P.muted};opacity:${lab}">FROM X · SAVED AS A REAL .GIF</span>`) + cap;
  }

  const DL = 'M15 3v13m0 0l-5-5m5 5l5-5M4 17v8h22v-8';
  function tile(t) {
    if (t < 22.0 || t >= 24.0) return '';
    const TS = 420, cy = 1010;
    const box = `M${CX},${cy - TS / 2} H${CX + TS / 2 - 100} A100,100 0 0 1 ${CX + TS / 2},${cy - TS / 2 + 100} V${cy + TS / 2 - 100} A100,100 0 0 1 ${CX + TS / 2 - 100},${cy + TS / 2} H${CX - TS / 2 + 100} A100,100 0 0 1 ${CX - TS / 2},${cy + TS / 2 - 100} V${cy - TS / 2 + 100} A100,100 0 0 1 ${CX - TS / 2 + 100},${cy - TS / 2} Z`;
    const press = 1 - 0.07 * bump(t, 22.7, 0.3);
    const fr = 640 * E.expoOut(clamp((t - 22.75) / 0.4));
    const tx = CX + 70, ty = cy + 80;
    const gl = (c, p) => `<g transform="translate(${CX - 105} ${cy - 105}) scale(7)">${stroke(DL, p, c, 3)}</g>`;
    let g = `<defs><clipPath id="tileclip"><path d="${box}"/></clipPath><clipPath id="floodclip"><circle cx="${tx}" cy="${ty}" r="${fr}"/></clipPath></defs>
      <g transform="translate(${CX} ${cy}) scale(${press}) translate(${-CX} ${-cy})">
        ${stroke(box, tw(t, 22.0, 0.35, E.out), P.ink, 10)}
        ${gl(P.ink, tw(t, 22.15, 0.35, E.out))}
        ${fr > 0 ? `<g clip-path="url(#tileclip)"><g clip-path="url(#floodclip)"><rect x="0" y="0" width="${W}" height="${H}" fill="${P.clay}"/>${gl(P.onClay, 1)}</g></g>` : ''}
      </g>`;
    for (let i = 0; i < 3; i++) {
      const q = clamp((t - 22.85 - i * 0.1) / 0.6);
      if (q > 0 && q < 1) g += `<path d="${box}" fill="none" stroke="${P.clay}" stroke-width="${8 * (1 - q)}" opacity="${1 - q}" transform="translate(${CX} ${cy}) scale(${1 + 0.6 * E.expoOut(q)}) translate(${-CX} ${-cy})"/>`;
    }
    const dot = (() => { const x = (t - 22.63) / 0.4; if (x < 0 || x > 1) return ''; return `<circle cx="${tx}" cy="${ty}" r="${32 * (x < 0.25 ? 1.3 - x : 1 - x * 0.3)}" fill="#fff" opacity="${0.6 * (1 - x)}"/>`; })();
    const lab = tw(t, 23.05, 0.3, E.expoOut);
    const labs = centered(cy + TS / 2 + 60, `<div style="text-align:center;opacity:${lab};transform:translateY(${(1 - lab) * 24}px)"><div class="cond" style="font-size:64px;line-height:1;color:${P.ink}">HAUL</div><div class="mono" style="font-size:28px;letter-spacing:.12em;color:${P.muted};margin-top:12px">DOWNLOAD COPIED LINK</div></div>`);
    const cap = centered(232, letters('COPY. TAP.\nDONE.', t, 22.05, { style: 'drop', size: 118, color: P.ink, stagger: 0.022, out: 23.7 }));
    const kicker = centered(170, `<span class="mono" style="font-size:24px;letter-spacing:.16em;color:${P.muted};opacity:${tw(t, 22.1, 0.3) * (1 - tw(t, 23.7, 0.2))}">QUICK SETTINGS TILE</span>`);
    return svg(g + dot) + labs + cap + kicker;
  }

  /** White iris out of the tile, into the next scene. */
  function iris(t) {
    if (t < 23.75 || t >= 24.0) return '';
    return svg(`<circle cx="${CX + 70}" cy="1090" r="${2400 * E.expoIn(clamp((t - 23.75) / 0.25))}" fill="${P.white}"/>`);
  }

  // ================================================================ 9 · no account, no ads, no server (24–28 s, white)
  const ROWS = [
    { y: 760, t: 24.25, s: 25.75, word: 'ACCOUNT.' },
    { y: 1020, t: 24.75, s: 26.0, word: 'ADS.' },
    { y: 1280, t: 25.25, s: 26.25, word: 'SERVER.' },
  ];
  function rowIcon(i, p) {
    const c = P.link;
    if (i === 0) return stroke('M0,-78 m-36,0 a36,36 0 1,0 72,0 a36,36 0 1,0 -72,0', p, c, 13) + stroke('M-68,70 C-68,0 68,0 68,70', p, c, 13);
    if (i === 1) return stroke('M-70,-50 H70 A18,18 0 0 1 88,-32 V32 A18,18 0 0 1 70,50 H-70 A18,18 0 0 1 -88,32 V-32 A18,18 0 0 1 -70,-50 Z', p, c, 12)
      + stroke('M-46,28 L-24,-28 L-2,28 M-38,10 H-10 M18,-28 V28 H30 A26,28 0 0 0 30,-28 Z', p, c, 9);
    return [-58, 0, 58].map((y) => stroke(`M-72,${y - 22} H72 A8,8 0 0 1 80,${y - 14} V${y + 14} A8,8 0 0 1 72,${y + 22} H-72 A8,8 0 0 1 -80,${y + 14} V${y - 14} A8,8 0 0 1 -72,${y - 22} Z`, p, c, 10)
      + (p > 0.9 ? `<circle cx="50" cy="${y}" r="7" fill="${c}"/>` : '')).join('');
  }
  function nos(t) {
    if (t < 24.0 || t >= 28.05) return '';
    const col = tw(t, 27.72, 0.3, E.in);
    let g = '', html = '';
    ROWS.forEach((r, i) => {
      const p = tw(t, r.t, 0.4, E.out);
      if (t < r.t - 0.02) return;
      const shk = 10 * Math.exp(-(t - r.s) * 14) * Math.sin((t - r.s) * 80) * (t > r.s ? 1 : 0);
      const ix = 250 + shk;
      g += `<g transform="translate(${ix} ${r.y})">${rowIcon(i, p)}${stroke('M-110,90 L110,-90', tw(t, r.s, 0.12, E.out), P.clay, 20)}</g>`;
      html += `<div style="position:absolute;left:${385 + shk}px;top:${r.y - 64}px">${letters('NO ' + r.word, t, r.t + 0.05, { style: 'rise', size: 124, color: P.link, stagger: 0.025, align: 'left' })}</div>`;
      // "NO" in muted: overlay the first two letters' colour by a second, clipped pass would be heavier; tint the whole word instead once struck.
    });
    const ty = t < 26.6 ? '' : centered(1460, `<div style="text-align:center;line-height:1.7">${typed('YT-DLP RUNS ON YOUR PHONE.', t, 26.6, 27.2, { size: 38, color: P.link, caret: t < 27.25 })}<br>${t >= 27.25 ? typed('IT UPDATES ITSELF DAILY.', t, 27.25, 27.7, { size: 38, color: P.lmuted, caret: true }) : ''}</div>`);
    return `<div style="position:absolute;inset:0;transform-origin:${CX}px 1010px;transform:scale(${1 - col});opacity:${1 - col * 0.5}">${svg(g)}${html}${ty}</div>`;
  }

  // ================================================================ 10 · the logo on white (28–34 s)
  const LOGO_T0 = 28.05; // the launch animation; its pick lands on the beat at 28.5
  function logoMark(t, size) {
    const a = t - LOGO_T0;
    const feedY = lerp(104, 0, E.fos(clamp(a / 0.45)));
    const pk = E.overshoot(clamp((a - 0.45) / 0.3));
    const tx = lerp(-8, 0, pk), rot = lerp(0, -6, pk);
    const card = mix('#3A3733', P.clay, E.fos(clamp((a - 0.45) / 0.2)));
    const play = E.fos(clamp((a - 0.56) / 0.15));
    const shadow = 0.45 * E.fos(clamp((a - 0.5) / 0.2));
    const outer = mix('#2A2825', P.surface, E.fos(clamp((a - 0.5) / 0.4)));
    const rr = (x, y, fill, extra = '') => `<rect x="${x}" y="${y}" width="52" height="20" rx="5" fill="${fill}" ${extra}/>`;
    return `<svg width="${size}" height="${size}" viewBox="0 0 108 108" style="display:block">
      <defs><clipPath id="ltile"><rect width="108" height="108" rx="24"/></clipPath>
      <filter id="lsoft" x="-20%" y="-40%" width="140%" height="180%"><feGaussianBlur stdDeviation="0.8"/></filter></defs>
      <rect width="108" height="108" rx="24" fill="${P.surface}"/>
      <g clip-path="url(#ltile)"><g transform="translate(54 54) scale(0.76) translate(-54 -54)"><g transform="translate(0 ${feedY})">
        ${rr(24, -34, outer)}${rr(24, -8, outer)}${rr(24, 18, '#3A3733')}${rr(24, 70, '#3A3733')}${rr(24, 96, outer)}
        <g transform="translate(${tx} 0) rotate(${rot} 58 54)">
          ${rr(30, 48, '#000', `fill-opacity="${shadow}" filter="url(#lsoft)"`)}
          ${rr(32, 44, card)}
          <path d="M51,49 L60,54 L51,59 Z" fill="${P.onClay}" fill-opacity="${play}"/>
        </g></g></g></g></svg>`;
  }
  function endCard(t) {
    if (t < 27.9) return '';
    const size = 560;
    const pop = E.backOut(clamp((t - 27.92) / 0.32), 1.8);
    const lift = tw(t, 28.9, 0.45, E.fos);
    const cy = lerp(1000, 640, lift);
    const push = 1 + 0.03 * tw(t, 29.0, 5, E.out);
    const word = tw(t, 29.0, 0.4, E.expoOut), tag = tw(t, 29.5, 0.4, E.expoOut), cta = tw(t, 30.0, 0.4, E.expoOut), ver = tw(t, 30.25, 0.4, E.expoOut);
    const reveal = (p, html, top, h) => `<div style="position:absolute;left:0;right:0;top:${top}px;height:${h}px;overflow:hidden"><div style="transform:translateY(${(1 - p) * 100}%);opacity:${p}">${html}</div></div>`;
    let y = cy + size / 2 + 60;
    let out = `<div style="position:absolute;left:${CX - size / 2}px;top:${cy - size / 2}px;transform:scale(${pop * push});filter:drop-shadow(0 40px 60px rgba(30,20,8,.22))">${logoMark(t, size)}</div>`;
    out += reveal(word, `<div class="cond" style="text-align:center;font-size:230px;line-height:212px;color:${P.link};letter-spacing:2px">HAUL.</div>`, y, 218);
    y += 236;
    out += reveal(tag, `<div class="cond" style="text-align:center;font-size:78px;line-height:80px;color:${P.link}">ANY LINK, INTO YOUR GALLERY.</div>`, y, 84);
    y += 150;
    out += `<div class="mono" style="position:absolute;left:0;right:0;top:${y}px;text-align:center;font-size:28px;letter-spacing:.14em;color:${P.lmuted};opacity:${cta}">DOWNLOAD FROM</div>`;
    y += 56;
    out += centered(y, `<div class="mono" style="height:112px;padding:0 40px;border-radius:28px;background:${P.clay};color:${P.onClay};display:flex;align-items:center;font-size:36px;font-weight:600">github.com/ulot2/haul/releases</div>`, `opacity:${cta};transform:translateY(${(1 - cta) * 30}px)`);
    y += 150;
    out += `<div class="mono" style="position:absolute;left:0;right:0;top:${y}px;text-align:center;font-size:28px;letter-spacing:.14em;color:${P.lmuted};opacity:${ver}">V1.3.0 · ANDROID · FREE</div>`;
    return out;
  }

  // ================================================================ transitions and frame
  /** Clay panel across the frame: in from the left, out to the right (7.75–8.3). */
  function panel(t) {
    if (t < 7.75 || t >= 8.32) return '';
    const x = t < 8.0 ? lerp(-W, 0, E.expoIn(clamp((t - 7.75) / 0.25))) : lerp(0, W, E.expoOut(clamp((t - 8.0) / 0.32)));
    return `<div style="position:absolute;left:${x}px;top:0;width:${W}px;height:${H}px;background:${P.clay}"></div>`;
  }
  /** Dark doors part to show white (16.0–16.35). */
  function doors(t) {
    if (t < 16.0 || t >= 16.4) return '';
    const p = E.expoIn(clamp((t - 16.0) / 0.35)) * 0.4 + E.fos(clamp((t - 16.0) / 0.35)) * 0.6;
    return `<div style="position:absolute;left:0;right:0;top:${-p * H / 2}px;height:${H / 2}px;background:${P.ground}"></div>
      <div style="position:absolute;left:0;right:0;top:${H / 2 + p * H / 2}px;height:${H / 2}px;background:${P.ground}"></div>`;
  }
  /** A diagonal dark sweep (19.75–20.0) that becomes the next scene's ground. */
  function sweep(t) {
    if (t < 19.75 || t >= 20.0) return '';
    const p = E.expoIn(clamp((t - 19.75) / 0.25));
    const x = lerp(-900, W + 900, p);
    return svg(`<polygon points="${x - 3000},-200 ${x},-200 ${x - 700},${H + 200} ${x - 3700},${H + 200}" fill="${P.ground}"/>`);
  }

  const DARK = [[4.0, 8.0], [12.0, 16.0], [20.0, 24.0]];
  const isDark = (t) => DARK.some(([a, b]) => t >= a && t < b);

  function chrome(t) {
    const fade = 1 - tw(t, 27.7, 0.3);
    if (fade <= 0) return '';
    const dark = isDark(t);
    const c = dark ? P.muted : P.lmuted;
    const scene = Math.min(10, [0, 2, 4, 6, 8, 12, 16, 20, 24, 28].filter((s) => t >= s).length);
    const tc = `00:${String(Math.floor(t)).padStart(2, '0')}.${String(Math.floor((t % 1) * 10))}`;
    const st = `font-size:22px;letter-spacing:.14em;color:${c};opacity:${fade}`;
    return `<div class="mono" style="position:absolute;left:56px;top:64px;${st}">HAUL · MANIFEST</div>
      <div class="mono" style="position:absolute;right:56px;top:64px;${st}">${String(scene).padStart(2, '0')} / 10</div>
      <div class="mono" style="position:absolute;left:56px;bottom:44px;${st}">V1.3.0 · ANDROID</div>
      <div class="mono" style="position:absolute;right:56px;bottom:44px;${st};font-variant-numeric:tabular-nums">${tc}</div>`;
  }

  function frame(t) {
    // Ground: white, except where the dark floods in (impact at 4 s) and the dark scenes.
    let bg = isDark(t) ? P.ground : P.white;
    if (t >= 4.0 && t < 4.5) bg = P.white; // the flood circle draws the dark itself
    if (t >= 11.95 && t < 12.0) bg = P.onClay;
    let html = '';
    html += flood(t) + hook(t) + link(t) + formats(t) + reel(t) + gallery(t) + share(t) + clip(t) + pick(t) + gif(t) + tile(t) + nos(t) + endCard(t);
    html += panel(t) + doors(t) + sweep(t) + iris(t);
    // The 'FROM' over the reel
    if (t >= 6.0 && t < 7.95) html += centered(232, letters('FROM', t, 6.02, { style: 'drop', size: 118, color: P.ink, stagger: 0.03, out: 7.7 }));
    if (t >= 4.3 && t < 5.95) html += centered(232, letters('BEST, 720P,\nAUDIO OR GIF.', t, 4.32, { style: 'drop', size: 118, color: P.ink, stagger: 0.02, out: 5.72 }));
    return { bg, html: html + chrome(t) };
  }

  root.MOTION = { frame, isDark, LOGO_T0 };
})(typeof window !== 'undefined' ? window : globalThis);
