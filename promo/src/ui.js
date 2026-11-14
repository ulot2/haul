// Freight parts, drawn as HTML strings in dp inside a 390×844 phone screen (scaled up by the device transform).
(function (root) {
  const { clamp, lerp, E, tw, mix, rgba, rng } = root.L;

  const C = {
    ground: '#121110', surface: '#1C1B19', raised: '#2A2825', line: '#36332E',
    ink: '#F4F0E8', muted: '#A8A298', clay: '#C8775A', onClay: '#1E1408', dim: '#3A3733',
    sys: '#232220', sysText: '#C9C3B8', clayLight: '#DDA07F', black: '#0B0B0A',
  };
  const TONES = ['#3B4A44', '#5A3F37', '#2F3C52', '#4B4436', '#3E3550', '#55503F'];

  // The app's own icon paths (res/drawable, 24-unit viewport, stroked).
  const ICONS = {
    download: 'M12,4 L12,16 M7,11 L12,16 L17,11 M4,20 L20,20',
    share: 'M12,15 L12,3 M7,8 L12,3 L17,8 M5,13 L5,20 L19,20 L19,13',
    trash: 'M4,7 L20,7 M9,7 L9,4 L15,4 L15,7 M6,7 L7,20 L17,20 L18,7',
    sliders: 'M4,7 L14,7 M18,7 L20,7 M4,17 L6,17 M10,17 L20,17 M18,7 A2,2 0 1,1 14,7 A2,2 0 1,1 18,7 M10,17 A2,2 0 1,1 6,17 A2,2 0 1,1 10,17',
    play: 'M7,4 L19,12 L7,20 Z',
    photos: 'M4,4 L10,4 L10,10 L4,10 Z M14,4 L20,4 L20,10 L14,10 Z M4,14 L10,14 L10,20 L4,20 Z M14,14 L20,14 L20,20 L14,20 Z',
    music: 'M9,18 L9,5 L20,3 L20,16 M9,18 A3,3 0 1,1 3,18 A3,3 0 1,1 9,18 M20,16 A3,3 0 1,1 14,16 A3,3 0 1,1 20,16',
    scissors: 'M9,6 A3,3 0 1,1 3,6 A3,3 0 1,1 9,6 M9,18 A3,3 0 1,1 3,18 A3,3 0 1,1 9,18 M8.5,8.5 L20,20 M8.5,15.5 L20,4',
    close: 'M6,6 L18,18 M18,6 L6,18',
    back: 'M15,5 L8,12 L15,19',
    check: 'M5,12.5 L10,17.5 L19,7',
    again: 'M4,12 A8,8 0 0,1 18,6.7 L20,9 M20,4 L20,9 L15,9 M20,12 A8,8 0 0,1 6,17.3 L4,15 M4,20 L4,15 L9,15',
    plus: 'M12,5 L12,19 M5,12 L19,12',
    chevron: 'M9,5 L16,12 L9,19',
    link: 'M10,14 L14,10 M8,11 L5.5,13.5 A3.5,3.5 0 0,0 10.5,18.5 L13,16 M16,13 L18.5,10.5 A3.5,3.5 0 0,0 13.5,5.5 L11,8',
    gallery: 'M4,5 L20,5 L20,19 L4,19 Z M4,15 L9,10 L14,15 L16,13 L20,17',
    pause: 'M8,5 L8,19 M16,5 L16,19',
    wifi: 'M2,9 A15,15 0 0,1 22,9 M5,12.5 A10,10 0 0,1 19,12.5 M8.5,16 A5,5 0 0,1 15.5,16 M12,19.5 L12,19.6',
  };
  function icon(name, size = 24, color = C.ink, sw = 2, fill = 'none') {
    const f = name === 'play' ? color : fill;
    return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" style="display:block;flex:none"><path d="${ICONS[name]}" fill="${f}" stroke="${color}" stroke-width="${sw}" stroke-linecap="square" stroke-linejoin="miter"/></svg>`;
  }

  /** The Pull mark (feed cards + picked card), in the 108-unit geometry of ic_launcher_fg.xml. */
  function markSvg(size, { tile = false, dim = '#3A3733', clay = C.clay, play = C.onClay, scale = 0.76, viewBox } = {}) {
    const vb = viewBox || '0 0 108 108';
    const inner = `
      <path d="M29,18 H71 A5,5 0 0 1 76,23 V33 A5,5 0 0 1 71,38 H29 A5,5 0 0 1 24,33 V23 A5,5 0 0 1 29,18 Z" fill="${dim}"/>
      <path d="M29,70 H71 A5,5 0 0 1 76,75 V85 A5,5 0 0 1 71,90 H29 A5,5 0 0 1 24,85 V75 A5,5 0 0 1 29,70 Z" fill="${dim}"/>
      <g transform="rotate(-6 58 54)">
        <path d="M35,48 H77 A5,5 0 0 1 82,53 V63 A5,5 0 0 1 77,68 H35 A5,5 0 0 1 30,63 V53 A5,5 0 0 1 35,48 Z" fill="#000" fill-opacity="0.45"/>
        <path d="M37,44 H79 A5,5 0 0 1 84,49 V59 A5,5 0 0 1 79,64 H37 A5,5 0 0 1 32,59 V49 A5,5 0 0 1 37,44 Z" fill="${clay}"/>
        <path d="M51,49 L60,54 L51,59 Z" fill="${play}"/>
      </g>`;
    const g = scale !== 1 ? `<g transform="translate(54 54) scale(${scale}) translate(-54 -54)">${inner}</g>` : inner;
    return `<svg width="${size}" height="${size}" viewBox="${vb}" style="display:block;flex:none">${tile ? `<rect width="108" height="108" rx="24" fill="${C.surface}"/>` : ''}${g}</svg>`;
  }

  /** Abstract placeholder "post" art: tonal gradient, soft shapes. No real content. */
  function art(seed, w, h, t = 0, opts = {}) {
    const r = rng(seed * 7919 + 13);
    const tone = TONES[seed % TONES.length];
    const top = mix(tone, '#F4F0E8', 0.32 + r() * 0.16);
    const bot = mix(tone, '#050505', 0.3);
    const hues = ['#C2B293', '#8FA897', '#7F94B3', '#A08DB0', '#D1BE95', '#86B0AA', '#C9B8A0', '#E0C9A0'];
    const a = hues[Math.floor(r() * hues.length)], b = hues[Math.floor(r() * hues.length)];
    const sx = 20 + r() * 60, sy = 18 + r() * 40, sr = 18 + r() * 22;
    const drift = Math.sin(t * 0.9 + seed) * 3;
    const hz = 52 + r() * 22;
    const kind = Math.floor(r() * 3);
    let shapes = '';
    if (kind === 0) {
      shapes = `<div style="position:absolute;left:${sx + drift}%;top:${sy}%;width:${sr * 2}%;aspect-ratio:1;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(circle at 40% 40%, ${mix(a, '#fff', 0.35)}, ${rgba(a, 0)} 70%)"></div>
        <div style="position:absolute;left:0;right:0;top:${hz}%;bottom:0;background:linear-gradient(180deg, ${rgba(mix(b, '#000', 0.2), 0.85)}, ${rgba(bot, 1)})"></div>`;
    } else if (kind === 1) {
      shapes = `<div style="position:absolute;left:${10 + drift}%;top:${20 + r() * 20}%;width:80%;height:46%;border-radius:40% 60% 50% 50%;background:radial-gradient(ellipse at 50% 40%, ${rgba(mix(a, '#fff', 0.25), 0.9)}, ${rgba(a, 0)} 70%)"></div>
        <div style="position:absolute;left:${50 - drift}%;top:${55 + r() * 10}%;width:60%;height:40%;border-radius:50%;background:radial-gradient(ellipse, ${rgba(b, 0.8)}, ${rgba(b, 0)} 70%)"></div>`;
    } else {
      shapes = `<div style="position:absolute;inset:0;background:repeating-linear-gradient(${100 + r() * 40}deg, ${rgba(a, 0.0)} 0 14%, ${rgba(a, 0.28)} 14% 15%)"></div>
        <div style="position:absolute;left:${30 + drift}%;top:${40 + r() * 10}%;width:${50 + r() * 20}%;aspect-ratio:1;transform:translate(-30%,-50%);border-radius:50%;background:radial-gradient(circle, ${rgba(mix(b, '#fff', 0.3), 0.85)}, ${rgba(b, 0)} 68%)"></div>`;
    }
    const dimmer = opts.dim ? `<div style="position:absolute;inset:0;background:${rgba(opts.dimColor || C.dim, opts.dim)}"></div>` : '';
    return `<div style="position:absolute;left:0;top:0;width:${w}px;height:${h}px;overflow:hidden;background:linear-gradient(170deg, ${top}, ${bot})">${shapes}${dimmer}</div>`;
  }

  // ---------- Device ----------
  const DEV = { w: 410, h: 864, bezel: 10, sw: 390, sh: 844 };

  function statusBar(color = C.ink) {
    return `<div style="position:absolute;left:0;right:0;top:0;height:34px;z-index:50;pointer-events:none">
      <div class="ar" style="position:absolute;left:26px;top:11px;font-size:14px;font-weight:600;color:${color}">9:41</div>
      <div style="position:absolute;left:189px;top:12px;width:12px;height:12px;border-radius:6px;background:#000"></div>
      <div style="position:absolute;right:24px;top:13px;display:flex;gap:6px;align-items:flex-end">
        <div style="display:flex;gap:2px;align-items:flex-end">${[4, 6, 8, 10].map((h) => `<div style="width:3px;height:${h}px;background:${color};border-radius:1px"></div>`).join('')}</div>
        <div style="width:20px;height:10px;border:1.5px solid ${color};border-radius:3px;box-sizing:border-box;padding:1px"><div style="width:70%;height:100%;background:${color};border-radius:1px"></div></div>
      </div></div>`;
  }

  function device(screenHtml) {
    return `<div style="position:absolute;left:0;top:0;width:${DEV.w}px;height:${DEV.h}px;border-radius:60px;background:#0A0A09;box-shadow:0 0 0 2px #2E2B27, inset 0 0 0 1px #1E1C1A, 0 50px 120px rgba(0,0,0,.55)">
      <div style="position:absolute;right:-4px;top:190px;width:4px;height:70px;border-radius:2px;background:#24221F"></div>
      <div style="position:absolute;right:-4px;top:280px;width:4px;height:110px;border-radius:2px;background:#24221F"></div>
      <div style="position:absolute;left:${DEV.bezel}px;top:${DEV.bezel}px;width:${DEV.sw}px;height:${DEV.sh}px;border-radius:50px;overflow:hidden;background:${C.ground}">
        ${screenHtml}${statusBar()}
        <div style="position:absolute;left:145px;bottom:8px;width:100px;height:4px;border-radius:2px;background:${rgba(C.ink, 0.5)};z-index:50"></div>
      </div></div>`;
  }

  // ---------- Small parts ----------
  const label = (txt, color = C.muted, size = 11, extra = '') =>
    `<span class="mono" style="font-size:${size}px;letter-spacing:.09em;text-transform:uppercase;color:${color};${extra}">${txt}</span>`;

  function btnPrimary(txt, { w = '100%', h = 54, ic = null, size = 17, pressed = 0 } = {}) {
    return `<div style="width:${w};height:${h}px;border-radius:14px;background:${mix(C.clay, '#000', pressed * 0.18)};color:${C.onClay};display:flex;align-items:center;justify-content:center;gap:8px;box-sizing:border-box;transform:scale(${1 - pressed * 0.03})">
      ${ic ? icon(ic, 20, C.onClay, 2.2) : ''}<span class="ar" style="font-size:${size}px;font-weight:800">${txt}</span></div>`;
  }
  function btnOutline(txt, { w = '100%', h = 48, ic = null, size = 16, fill = 0 } = {}) {
    const bg = fill ? mix(C.surface, C.ink, fill) : 'transparent';
    const fg = fill > 0.5 ? C.ground : C.ink;
    return `<div style="width:${w};height:${h}px;border-radius:12px;border:1px solid ${C.line};background:${bg};color:${fg};display:flex;align-items:center;justify-content:center;gap:8px;box-sizing:border-box">
      ${ic ? icon(ic, 20, fg, 2) : ''}<span class="ar" style="font-size:${size}px;font-weight:600">${txt}</span></div>`;
  }
  const handle = () => `<div style="width:40px;height:4px;border-radius:2px;background:${C.raised};margin:10px auto 0"></div>`;

  /** A touch: a soft white dot that lands and fades, like Android's "show taps". */
  function touch(x, y, t, t0) {
    const p = (t - t0) / 0.45;
    if (p < -0.15 || p > 1) return '';
    const pre = clamp((t - (t0 - 0.07)) / 0.07);
    const s = p < 0 ? lerp(1.3, 1, pre) : lerp(1, 0.8, clamp(p));
    const a = p < 0 ? pre * 0.5 : 0.5 * (1 - clamp(p));
    const ring = p > 0 ? `<div style="position:absolute;left:${x}px;top:${y}px;width:${lerp(30, 110, E.out(clamp(p)))}px;aspect-ratio:1;border-radius:50%;transform:translate(-50%,-50%);border:2px solid ${rgba('#FFFFFF', 0.4 * (1 - clamp(p)))}"></div>` : '';
    return `<div style="position:absolute;inset:0;z-index:60;pointer-events:none">${ring}<div style="position:absolute;left:${x}px;top:${y}px;width:38px;height:38px;border-radius:50%;transform:translate(-50%,-50%) scale(${s});background:${rgba('#FFFFFF', a)}"></div></div>`;
  }

  /** How pressed a button is, around a tap at t0 (0..1..0). */
  const press = (t, t0) => (t < t0 - 0.04 ? 0 : t < t0 + 0.08 ? clamp((t - t0 + 0.04) / 0.08) : clamp(1 - (t - t0 - 0.08) / 0.2));

  // ---------- The hold ----------
  function holdHeader({ count = '8 HAULS', wifi = 0 } = {}) {
    return `<div style="position:absolute;left:20px;right:20px;top:52px">
      <div style="display:flex;align-items:center;gap:10px">
        ${markSvg(30, { viewBox: '20 14 68 80', scale: 1 })}
        <div class="cond" style="flex:1;font-size:40px;line-height:1;color:${C.ink};letter-spacing:.5px">HAUL</div>
        <div style="width:44px;height:44px;border-radius:12px;border:1px solid ${C.line};display:flex;align-items:center;justify-content:center;box-sizing:border-box">${icon('sliders', 22)}</div>
      </div>
      <div style="display:flex;align-items:center;margin-top:14px;height:22px">
        <div style="flex:1">${label('THE HOLD · ' + count)}</div>
        ${wifi > 0 ? `<div style="opacity:${wifi};transform:scale(${lerp(1.4, 1, E.backOut(wifi))});border:1px solid ${C.line};border-radius:6px;padding:3px 7px">${label('WI-FI ONLY', C.ink)}</div>` : ''}
      </div>
      <div style="display:flex;gap:8px;margin-top:14px">
        ${['All', 'Videos', 'Photos', 'Audio', 'Failed'].map((f, i) => `<div class="ar" style="height:36px;padding:0 14px;border-radius:18px;display:flex;align-items:center;font-size:14px;font-weight:600;box-sizing:border-box;${i === 0 ? `background:${C.ink};color:${C.ground}` : `border:1px solid ${C.line};color:${C.ink}`}">${f}</div>`).join('')}
      </div></div>`;
  }
  const HOLD_TOP = 196; // where the first row starts

  /** One ticket row. o: {no, title, meta, tag, len, seed, kind, progress, check, gif, size} */
  function row(o, y, t, { alpha = 1, dx = 0 } = {}) {
    const thumb = `<div style="position:relative;width:54px;height:96px;border-radius:8px;overflow:hidden;flex:none;background:${TONES[o.seed % 6]}">
      ${o.kind === 'audio' ? `<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center">${icon('music', 24, '#fff')}</div>` : art(o.seed, 54, 96, t)}
      ${o.gif ? gifLoop(54, 96, t) : ''}
      <div class="mono" style="position:absolute;left:6px;top:6px;font-size:10px;font-weight:600;color:#fff;text-shadow:0 0 3px rgba(0,0,0,.6)">${o.tag}</div>
      ${o.len ? `<div class="mono" style="position:absolute;right:5px;bottom:5px;font-size:10px;color:#fff;background:rgba(0,0,0,.55);border-radius:4px;padding:1px 4px">${o.len}</div>` : ''}
      ${o.check != null ? checkMark(o.check, 24, 'position:absolute;right:4px;top:4px') : ''}
    </div>`;
    let status = '';
    if (o.progress != null && o.progress < 1) {
      status = `<div style="display:flex;align-items:center;gap:10px;margin-top:8px">
        <div style="flex:1;height:4px;border-radius:2px;background:${C.raised}"><div style="width:${o.progress * 100}%;height:4px;border-radius:2px;background:${C.clay}"></div></div>
        <div class="condx" style="font-size:20px;color:${C.ink};width:44px;text-align:right;font-variant-numeric:tabular-nums">${Math.round(o.progress * 100)}%</div></div>`;
    } else if (o.size) {
      status = `<div style="margin-top:6px">${label(o.size)}</div>`;
    }
    return `<div style="position:absolute;left:20px;right:20px;top:${y}px;height:124px;opacity:${alpha};transform:translateX(${dx}px)">
      <div style="display:flex;gap:14px;align-items:center;height:124px">${thumb}
        <div style="flex:1;min-width:0">
          ${label('NO. ' + o.no)}
          <div class="ar" style="font-size:16px;font-weight:600;color:${C.ink};margin-top:3px;line-height:1.25;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${o.title}</div>
          <div class="ar" style="font-size:13px;color:${C.muted};margin-top:3px">${o.meta}</div>
          ${status}
        </div></div>
      <div style="position:absolute;left:0;right:0;bottom:-1px;height:1px;background:${C.line}"></div></div>`;
  }

  /** A clay check (bg_check_on) or an empty ring, with a pop. */
  function checkMark(p, size, pos) {
    if (p <= 0) return `<div style="${pos};width:${size}px;height:${size}px;border-radius:50%;border:2px solid #fff;box-sizing:border-box;background:rgba(0,0,0,.25)"></div>`;
    const s = lerp(0.6, 1, E.backOut(clamp(p)));
    return `<div style="${pos};width:${size}px;height:${size}px;border-radius:50%;border:2px solid #fff;box-sizing:border-box;background:${C.clay};display:flex;align-items:center;justify-content:center;transform:scale(${s})">${icon('check', size * 0.7, C.onClay, 3)}</div>`;
  }

  /** A looping shape for GIF thumbnails: a paper plane gliding in a loop. */
  function gifLoop(w, h, t) {
    const ph = (t * 1.2) % 1;
    const x = w * (0.2 + 0.6 * ph), y = h * (0.55 - 0.25 * Math.sin(ph * Math.PI));
    return `<svg width="${w}" height="${h}" style="position:absolute;left:0;top:0"><path d="M${x - 9},${y + 4} L${x + 9},${y - 2} L${x - 3},${y + 9} Z" fill="#F4F0E8" opacity=".9"/></svg>`;
  }

  // ---------- Dock ----------
  function dockClip({ pressed = 0, link = 'vm.tiktok.com/ZMh3k9Ra2/', site = 'TIKTOK', y = 0, alpha = 1 } = {}) {
    return `<div style="position:absolute;left:12px;right:12px;bottom:${28 - y}px;border-radius:20px;background:${C.surface};border:1px solid ${C.line};padding:14px;box-sizing:border-box;box-shadow:0 12px 30px rgba(0,0,0,.45);opacity:${alpha}">
      ${label('COPIED LINK · ' + site)}
      <div class="ar" style="font-size:15px;font-weight:600;color:${C.ink};margin-top:4px">${link}</div>
      <div style="display:flex;gap:10px;margin-top:12px">${btnOutline('Options', { w: '33%' })}<div style="flex:1">${btnPrimary('Download · Best', { h: 48, ic: 'download', size: 16, pressed })}</div></div></div>`;
  }
  function dockAdd({ y = 0, alpha = 1 } = {}) {
    return `<div style="position:absolute;left:12px;right:12px;bottom:${28 - y}px;border-radius:20px;background:${C.surface};border:1px solid ${C.line};padding:14px;box-sizing:border-box;box-shadow:0 12px 30px rgba(0,0,0,.45);opacity:${alpha}">
      ${btnPrimary('Add a link', { ic: 'plus' })}</div>`;
  }
  function dockSelect(n, { y = 0, alpha = 1 } = {}) {
    return `<div style="position:absolute;left:12px;right:12px;bottom:${28 - y}px;border-radius:20px;background:${C.surface};border:1px solid ${C.line};padding:14px;box-sizing:border-box;box-shadow:0 12px 30px rgba(0,0,0,.45);opacity:${alpha}">
      <div style="display:flex;align-items:center;gap:8px">
        <div class="cond" style="flex:1;font-size:26px;color:${C.ink}">${n} SELECTED</div>
        ${btnOutline('All', { w: '64px', h: 40, size: 14 })}
        <div style="width:40px;height:40px;border-radius:12px;border:1px solid ${C.line};display:flex;align-items:center;justify-content:center;box-sizing:border-box">${icon('close', 20)}</div>
      </div>
      <div style="display:flex;gap:10px;margin-top:12px">${btnOutline('Share', { w: '50%', ic: 'share' })}<div style="flex:1">${btnPrimary('Delete', { h: 48, ic: 'trash', size: 16 })}</div></div></div>`;
  }

  // ---------- Sheets ----------
  function scrim(a) { return `<div style="position:absolute;inset:0;background:rgba(0,0,0,${0.6 * a})"></div>`; }
  function sheet(inner, p, { h = 520 } = {}) {
    const y = lerp(h + 40, 0, p);
    return `<div style="position:absolute;left:0;right:0;bottom:0;height:${h}px;transform:translateY(${y}px);background:${C.surface};border-radius:24px 24px 0 0;box-shadow:0 -10px 40px rgba(0,0,0,.4)">${handle()}<div style="padding:12px 20px 0">${inner}</div></div>`;
  }
  const heading = (txt, size = 32) => `<div class="cond" style="font-size:${size}px;line-height:1;color:${C.ink}">${txt}</div>`;

  /** Segmented choice with a sliding ink fill. pos is a float index. */
  function segments(opts, pos, { h = 44 } = {}) {
    const n = opts.length, gap = 6;
    const cells = opts.map((o, i) => {
      const on = clamp(1 - Math.abs(pos - i));
      const fg = mix(C.ink, C.ground, on);
      return `<div style="flex:1;height:${h}px;border-radius:12px;border:1px solid ${C.line};box-sizing:border-box;display:flex;align-items:center;justify-content:center;position:relative;z-index:1"><span class="ar" style="font-size:15px;font-weight:800;color:${fg}">${o}</span></div>`;
    }).join('');
    const pill = `<div style="position:absolute;top:0;left:calc(${pos} * ((100% - ${gap * (n - 1)}px) / ${n} + ${gap}px));width:calc((100% - ${gap * (n - 1)}px) / ${n});height:${h}px;border-radius:12px;background:${C.ink}"></div>`;
    return `<div style="position:relative;display:flex;gap:${gap}px">${pill}${cells}</div>`;
  }

  function sheetAdd(t, p, segPos) {
    const inner = `${heading('NEW HAUL')}
      <div style="margin-top:18px">${label('LINK')}</div>
      <div style="margin-top:8px;height:50px;border-radius:12px;background:${C.ground};border:1.5px solid ${C.clay};display:flex;align-items:center;padding:0 14px;box-sizing:border-box" class="ar"><span style="font-size:15px;color:${C.ink}">vm.tiktok.com/ZMh3k9Ra2/</span></div>
      <div style="margin-top:18px">${label('QUALITY')}</div>
      <div style="margin-top:8px">${segments(['Best', '720p', 'Audio', 'GIF'], segPos)}</div>
      <div style="display:flex;align-items:center;margin-top:20px;padding:12px 0;border-top:1px solid ${C.line}">
        ${icon('scissors', 20, C.muted)}<div class="ar" style="flex:1;margin-left:12px;font-size:16px;font-weight:600;color:${C.ink}">Clip a section</div>
        <span class="mono" style="font-size:13px;color:${C.muted};margin-right:6px">Whole video</span>${icon('chevron', 18, C.muted)}</div>
      <div style="margin-top:14px">${btnPrimary('Download', { ic: 'download' })}</div>`;
    return sheet(inner, p, { h: 470 });
  }

  function sheetQuality(t, p, segPos, pressed = 0) {
    const inner = `<div style="display:flex;align-items:baseline;justify-content:space-between">${heading('HAUL THIS AS')}${label('TIKTOK')}</div>
      <div class="ar" style="font-size:13px;color:${C.muted};margin-top:8px">Rooftop sunset, ten minutes in ten seconds</div>
      <div style="margin-top:18px;transform:scale(${1 - pressed * 0.02})">${segments(['Best', '720p', 'Audio', 'GIF'], segPos, { h: 52 })}</div>`;
    return sheet(inner, p, { h: 230 });
  }

  function sheetClip(t, p, a, b, total = 222, pressed = 0) {
    const { fmtClock } = root.L;
    const inner = `<div style="display:flex;align-items:center;gap:12px">
        <div style="width:44px;height:44px;border-radius:12px;border:1px solid ${C.line};display:flex;align-items:center;justify-content:center;box-sizing:border-box">${icon('back', 22)}</div>${heading('CLIP A SECTION')}</div>
      <div style="position:relative;margin-top:16px;height:170px;border-radius:12px;overflow:hidden">${art(4, 350, 170, t)}
        <div style="position:absolute;left:10px;top:10px">${label('PREVIEW · ' + fmtClock(total), '#fff')}</div>
        <div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:48px;height:48px;border-radius:24px;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center">${icon('play', 20, '#fff', 1)}</div></div>
      <div style="display:flex;align-items:baseline;justify-content:space-between;margin-top:16px">
        <div class="condx" style="font-size:30px;color:${C.ink};font-variant-numeric:tabular-nums">${fmtClock(a)} – ${fmtClock(b)}</div>${label(fmtClock(b - a) + ' SELECTED')}</div>
      <div style="position:relative;margin-top:12px;height:16px;border-radius:8px;background:${C.raised}">
        <div style="position:absolute;left:${(a / total) * 100}%;width:${((b - a) / total) * 100}%;top:0;height:16px;border-radius:8px;background:${C.clay}"></div></div>
      ${slider('START · ' + fmtClock(a), a / total)}
      ${slider('END · ' + fmtClock(b), b / total)}
      <div style="display:flex;gap:10px;margin-top:18px">${btnOutline('Whole video', { w: '45%' })}<div style="flex:1">${btnPrimary('Use section', { h: 48, size: 16, pressed })}</div></div>`;
    return sheet(inner, p, { h: 610 });
  }
  function slider(txt, v) {
    return `<div style="margin-top:16px">${label(txt)}
      <div style="position:relative;margin-top:12px;height:4px;border-radius:2px;background:${C.raised}">
        <div style="position:absolute;left:0;width:${v * 100}%;height:4px;border-radius:2px;background:${C.clay}"></div>
        <div style="position:absolute;left:${v * 100}%;top:2px;width:22px;height:22px;border-radius:11px;background:${C.clay};transform:translate(-50%,-50%);box-shadow:0 0 0 6px ${rgba(C.clay, 0.18)}"></div></div></div>`;
  }

  function sheetPick(t, p, checks, pressed = 0) {
    const tiles = [0, 1, 2, 3, 4, 5].map((i) => {
      const vid = i === 1 || i === 4;
      const c = checks[i];
      return `<div style="position:relative;aspect-ratio:4/5;border-radius:8px;overflow:hidden;background:${TONES[i]}">${art(20 + i, 112, 140, t)}
        ${vid ? `<div class="mono" style="position:absolute;right:5px;bottom:5px;font-size:10px;color:#fff;background:rgba(0,0,0,.55);border-radius:4px;padding:1px 4px">0:${12 + i * 3}</div><div style="position:absolute;left:6px;bottom:6px">${icon('play', 14, '#fff', 1)}</div>` : ''}
        ${c > 0 ? `<div style="position:absolute;inset:0;border:3px solid ${rgba(C.clay, clamp(c * 2))};border-radius:8px"></div>` : ''}
        ${checkMark(c, 26, 'position:absolute;right:6px;top:6px')}</div>`;
    }).join('');
    const n = checks.filter((c) => c > 0.5).length;
    const inner = `<div style="display:flex;align-items:baseline;justify-content:space-between">${heading('PICK FROM THE POST')}</div>
      <div style="margin-top:8px">${label('INSTAGRAM · 4 PHOTOS, 2 VIDEOS · ' + n + ' OF 6')}</div>
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin-top:14px">${tiles}</div>
      <div style="display:flex;gap:10px;margin-top:16px">${btnOutline('All', { w: '30%' })}<div style="flex:1">${btnPrimary(n ? `Download ${n}` : 'Download', { h: 48, ic: 'download', size: 16, pressed })}</div></div>`;
    return sheet(inner, p, { h: 560 });
  }

  function sheetTiktok(t, p, sel) {
    const opts = [['Video with sound', 'MP4 · 5 PHOTOS · 0:15'], ['Photos', '5 PHOTOS · PICTURES/HAUL'], ['Sound only', 'M4A · MUSIC/HAUL']];
    const ics = ['play', 'photos', 'music'];
    const rows = opts.map(([a, b], i) => {
      const on = clamp(1 - Math.abs(sel - i));
      const bg = mix(C.surface, C.ink, on);
      const fg = mix(C.ink, C.ground, on);
      const fm = mix(C.muted, '#5C574F', on);
      return `<div style="display:flex;align-items:center;gap:14px;height:64px;border-radius:12px;border:1px solid ${C.line};background:${bg};padding:0 16px;margin-top:8px;box-sizing:border-box">
        ${icon(ics[i], 22, fg, 2)}<div style="flex:1"><div class="ar" style="font-size:16px;font-weight:700;color:${fg}">${a}</div>
        <div class="mono" style="font-size:11px;letter-spacing:.06em;color:${fm};margin-top:2px">${b}</div></div></div>`;
    }).join('');
    const inner = `${heading('TIKTOK PHOTOS')}
      <div class="ar" style="font-size:14px;color:${C.muted};margin-top:10px;line-height:1.3">This post is pictures with sound. Save it as:</div>${rows}`;
    return sheet(inner, p, { h: 380 });
  }

  // ---------- Outside the app ----------
  /** Someone else's feed: an abstract post, with placeholder lines. No real app's interface. */
  function post(seed, t, { shareBtn = 0 } = {}) {
    return `<div style="position:absolute;inset:0">${art(seed, 390, 844, t)}
      <div style="position:absolute;left:0;right:0;bottom:0;height:300px;background:linear-gradient(180deg, rgba(0,0,0,0), rgba(0,0,0,.55))"></div>
      <div style="position:absolute;left:20px;bottom:64px;width:240px">
        <div style="width:110px;height:12px;border-radius:6px;background:rgba(255,255,255,.85)"></div>
        <div style="width:220px;height:9px;border-radius:5px;background:rgba(255,255,255,.55);margin-top:12px"></div>
        <div style="width:170px;height:9px;border-radius:5px;background:rgba(255,255,255,.55);margin-top:8px"></div></div>
      <div style="position:absolute;right:18px;bottom:60px;width:48px;height:48px;border-radius:24px;background:rgba(0,0,0,${0.35 + shareBtn * 0.3});display:flex;align-items:center;justify-content:center;transform:scale(${1 - shareBtn * 0.1})">${icon('share', 22, '#fff', 2)}</div></div>`;
  }

  function shareSheet(t, p, pressHaul = 0) {
    const target = (name, inner, hl, pr = 0) => `<div style="display:flex;flex-direction:column;align-items:center;gap:6px;width:72px">
      <div style="width:56px;height:56px;border-radius:${hl ? 16 : 28}px;background:${hl ? C.ground : '#3A3834'};${hl ? `border:1.5px solid ${C.clay};` : ''}display:flex;align-items:center;justify-content:center;box-sizing:border-box;transform:scale(${1 - pr * 0.12})">${inner}</div>
      <span class="ar" style="font-size:12px;color:${hl ? C.ink : C.muted};text-align:center;white-space:nowrap">${name}</span></div>`;
    const haulIc = `<svg width="28" height="28" viewBox="0 0 30 30" fill="none"><path d="M15 3v13m0 0l-5-5m5 5l5-5" stroke="${C.clay}" stroke-width="3" stroke-linecap="square"/><path d="M4 17v8h22v-8" stroke="${C.ink}" stroke-width="3" stroke-linecap="square"/></svg>`;
    const inner = `<div style="display:flex;align-items:center;gap:12px;padding-bottom:16px;border-bottom:1px solid #34322E">
        <div style="width:44px;height:44px;border-radius:10px;overflow:hidden;position:relative">${art(1, 44, 44, t)}</div>
        <div><div class="ar" style="font-size:14px;font-weight:600;color:${C.ink}">vm.tiktok.com/ZMh3k9Ra2/</div><div class="ar" style="font-size:12px;color:${C.muted};margin-top:2px">Share link</div></div></div>
      <div style="display:flex;justify-content:space-between;margin-top:18px">
        ${target('Haul', haulIc, true, pressHaul)}${target('Haul audio', icon('music', 24, C.clay, 2.2), true)}${target('Messages', '', false)}${target('Copy link', icon('link', 22, C.muted, 2), false)}</div>
      <div style="display:flex;justify-content:space-between;margin-top:16px">
        ${target('Nearby', '', false)}${target('Notes', '', false)}${target('Email', '', false)}${target('Print', '', false)}</div>`;
    const y = lerp(420, 0, p);
    return `<div style="position:absolute;left:0;right:0;bottom:0;height:380px;transform:translateY(${y}px);background:${C.sys};border-radius:28px 28px 0 0;padding:12px 22px 0;box-sizing:border-box">
      <div style="width:36px;height:4px;border-radius:2px;background:#4A4742;margin:0 auto 16px"></div>${inner}</div>`;
  }

  function toast(txt, p) {
    if (p <= 0) return '';
    return `<div style="position:absolute;left:50%;bottom:150px;transform:translate(-50%,0) scale(${lerp(0.9, 1, E.backOut(clamp(p * 1.5)))});opacity:${clamp(p * 2)};padding:11px 16px;border-radius:14px;background:${C.ink};color:${C.ground};white-space:nowrap" class="ar"><span style="font-size:14px;font-weight:600">${txt}</span></div>`;
  }

  const notifyIc = (s = 14, c = '#fff') => `<svg width="${s}" height="${s}" viewBox="0 0 108 108"><rect x="16" y="12" width="60" height="24" rx="6" fill="${c}" opacity=".55"/><g transform="rotate(-6 58 54)"><rect x="28" y="42" width="64" height="24" rx="6" fill="${c}"/></g><rect x="16" y="72" width="60" height="24" rx="6" fill="${c}" opacity=".55"/></svg>`;

  function notifHead(when = 'now') {
    return `<div style="display:flex;align-items:center;gap:6px;margin-bottom:6px">${notifyIc(14, C.clayLight)}<span class="ar" style="font-size:12px;color:${C.sysText}">Haul · ${when}</span></div>`;
  }
  function notifProgress(pct, extra = 'TikTok · Rooftop sunset, ten minutes…') {
    return `<div style="border-radius:20px;background:${C.sys};padding:14px 16px;box-shadow:0 10px 30px rgba(0,0,0,.4)">${notifHead()}
      <div class="ar" style="font-size:15px;font-weight:700;color:${C.ink}">Downloading ${Math.round(pct * 100)}%</div>
      <div class="ar" style="font-size:13px;color:${C.sysText};margin-top:2px">${extra}</div>
      <div style="height:4px;border-radius:2px;background:#3A3834;margin-top:10px"><div style="width:${pct * 100}%;height:4px;border-radius:2px;background:${C.clay}"></div></div>
      <div class="ar" style="font-size:13px;font-weight:700;color:${C.clayLight};margin-top:10px">Cancel</div></div>`;
  }
  function notifSaved(title, where = 'Movies/Haul · TikTok', seed = 6, t = 0, actions = ['Share', 'Delete']) {
    return `<div style="border-radius:20px;background:${C.sys};padding:14px 16px;box-shadow:0 10px 30px rgba(0,0,0,.4)">${notifHead()}
      <div style="display:flex;gap:12px"><div style="position:relative;width:40px;height:64px;flex:none;border-radius:6px;overflow:hidden">${art(seed, 40, 64, t)}</div>
      <div style="flex:1;min-width:0"><div class="ar" style="font-size:15px;font-weight:700;color:${C.ink};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${title}</div>
      <div class="ar" style="font-size:13px;color:${C.sysText};margin-top:2px">Saved to ${where}</div>
      <div style="display:flex;gap:18px;margin-top:8px">${actions.map((a) => `<span class="ar" style="font-size:13px;font-weight:700;color:${C.clayLight}">${a}</span>`).join('')}</div></div></div></div>`;
  }
  function notifFailed() {
    return `<div style="border-radius:20px;background:${C.sys};padding:14px 16px">${notifHead('2m')}
      <div class="ar" style="font-size:15px;font-weight:700;color:${C.ink}">Couldn't download from Instagram</div>
      <div class="ar" style="font-size:13px;color:${C.sysText};margin-top:2px">This post needs a signed-in account.</div>
      <div class="ar" style="font-size:13px;font-weight:700;color:${C.clayLight};margin-top:8px">Try again</div></div>`;
  }
  function notifWaiting() {
    return `<div style="border-radius:20px;background:${C.sys};padding:14px 16px">${notifHead('5m')}
      <div style="display:flex;align-items:center;gap:8px"><div class="ar" style="font-size:15px;font-weight:700;color:${C.ink};flex:1">Waiting for Wi-Fi</div>
      <div style="border:1px solid #4A4742;border-radius:6px;padding:2px 6px">${label('WI-FI ONLY', C.ink, 10)}</div></div>
      <div class="ar" style="font-size:13px;color:${C.sysText};margin-top:2px">1 haul waiting · YouTube</div></div>`;
  }

  function shade(t, p, tileOn) {
    const y = lerp(-860, 0, p);
    const tile = (name, sub, on, ic) => `<div style="height:64px;border-radius:20px;background:${on > 0 ? mix('#2E2C29', C.clay, on) : '#2E2C29'};display:flex;align-items:center;gap:10px;padding:0 14px;box-sizing:border-box;color:${on > 0.5 ? C.onClay : C.ink}">
        ${ic}<div style="min-width:0"><div class="ar" style="font-size:14px;font-weight:800">${name}</div><div class="ar" style="font-size:12px;white-space:nowrap;opacity:.85">${sub}</div></div></div>`;
    const dl = (c) => `<svg width="22" height="22" viewBox="0 0 30 30" fill="none"><path d="M15 3v13m0 0l-5-5m5 5l5-5" stroke="${c}" stroke-width="3" stroke-linecap="square"/><path d="M4 17v8h22v-8" stroke="${c}" stroke-width="3" stroke-linecap="square"/></svg>`;
    return `<div style="position:absolute;inset:0;transform:translateY(${y}px);background:#141312;padding:46px 14px 0;box-sizing:border-box">
      <div style="display:flex;align-items:baseline;gap:10px;padding:0 8px"><div class="ar" style="font-size:44px;font-weight:600;color:${C.ink}">9:41</div><div class="ar" style="font-size:14px;color:${C.muted}">Sat, Nov 14</div></div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:14px">
        ${tile('Haul', 'Download copied link', tileOn, dl(tileOn > 0.5 ? C.onClay : C.ink))}
        ${tile('Wi-Fi', 'Home', 0, icon('wifi', 22, C.ink, 2))}
        ${tile('Bluetooth', 'Off', 0, '<div style="width:22px"></div>')}
        ${tile('Torch', 'Off', 0, '<div style="width:22px"></div>')}</div>
      <div style="width:36px;height:4px;border-radius:2px;background:#4A4742;margin:14px auto"></div>
      <div style="display:flex;flex-direction:column;gap:8px">${notifSaved('Rooftop sunset, ten minutes…', 'Movies/Haul · TikTok', 6, t)}${notifFailed()}${notifWaiting()}</div></div>`;
  }

  function player(t, page, pagePos) {
    const pages = [
      `<div style="position:absolute;inset:0">${art(14, 390, 844, t)}</div>`,
      `<div style="position:absolute;inset:0">${art(21, 390, 844, t)}</div>`,
      `<div style="position:absolute;inset:0">${art(16, 390, 844, t)}${gifLoop(390, 844, t)}<div style="position:absolute;left:20px;top:110px;border:1px solid rgba(255,255,255,.6);border-radius:6px;padding:3px 7px">${label('GIF', '#fff', 12)}</div></div>`,
    ];
    const strip = pages.map((pg, i) => `<div style="position:absolute;top:0;left:${(i - pagePos) * 390}px;width:390px;height:844px;overflow:hidden">${pg}</div>`).join('');
    const kind = ['VIDEO · 0:59', 'PHOTO · 1080×1350', 'GIF · 2.1 MB'][page];
    const prog = page === 0 ? 0.2 + t * 0.02 % 0.6 : 0;
    return `<div style="position:absolute;inset:0;background:#000">${strip}
      <div style="position:absolute;left:0;right:0;top:0;height:120px;background:linear-gradient(rgba(0,0,0,.6),rgba(0,0,0,0))"></div>
      <div style="position:absolute;left:12px;right:12px;top:46px;display:flex;align-items:center;gap:10px">
        ${icon('back', 24, '#fff')}<div style="flex:1;min-width:0"><div class="ar" style="font-size:15px;font-weight:600;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Studio tour, three rooms</div>${label(`${page + 1} / 3 · ${kind}`, 'rgba(255,255,255,.8)')}</div>
        ${icon('share', 22, '#fff')}${icon('gallery', 22, '#fff')}</div>
      ${page === 0 || pagePos < 0.5 ? `<div style="position:absolute;left:16px;right:16px;bottom:34px;display:flex;align-items:center;gap:12px;opacity:${clamp(1 - pagePos * 2)}">
        ${icon('pause', 22, '#fff', 2.5)}<div style="flex:1;height:4px;border-radius:2px;background:rgba(255,255,255,.3)"><div style="width:${30 + (t % 2) * 5}%;height:4px;border-radius:2px;background:${C.clay}"></div></div>
        <span class="mono" style="font-size:12px;color:#fff">0:18 / 0:59</span></div>` : ''}
      <div style="position:absolute;left:0;right:0;bottom:16px;display:flex;justify-content:center;gap:6px">${[0, 1, 2].map((i) => `<div style="width:${Math.abs(pagePos - i) < 0.5 ? 18 : 6}px;height:6px;border-radius:3px;background:${Math.abs(pagePos - i) < 0.5 ? '#fff' : 'rgba(255,255,255,.4)'}"></div>`).join('')}</div></div>`;
  }

  root.UI = {
    C, TONES, ICONS, icon, markSvg, art, DEV, device, label, btnPrimary, btnOutline, touch, press,
    holdHeader, HOLD_TOP, row, checkMark, gifLoop, dockClip, dockAdd, dockSelect, scrim, sheet, heading,
    segments, sheetAdd, sheetQuality, sheetClip, sheetPick, sheetTiktok, post, shareSheet, toast,
    notifProgress, notifSaved, notifFailed, notifWaiting, shade, player, notifyIc,
  };
})(typeof window !== 'undefined' ? window : globalThis);
