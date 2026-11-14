// Draws one frame of the main timeline. ?format=vertical (1080×1920) or square (1080×1080).
// Open the page with &t=12.3 to look at one moment, or &play to watch it in real time (no sound).
(function () {
  const FORMATS = {
    vertical: {
      W: 1080, H: 1920, square: false,
      phone: { s: 1.72, cx: 540, top: 450 },
      cap: { align: 'center', y: 128, size: 104, kicker: 24, scrimH: 470, foldersY: 1820 },
      capFull: { align: 'center', y: 250, size: 168, kicker: 24 },
    },
    square: {
      W: 1080, H: 1080, square: true,
      phone: { s: 1.12, cx: 300, top: 70 },
      cap: { align: 'left', x: 590, w: 470, y: 470, size: 64, kicker: 18 },
      capFull: { align: 'center', y: 70, size: 120, kicker: 20 },
    },
  };

  const q = new URLSearchParams(location.search);
  const F = FORMATS[q.get('format') || 'vertical'];
  const stage = document.getElementById('stage');
  stage.style.width = F.W + 'px';
  stage.style.height = F.H + 'px';

  const S = window.SCENES;
  const list = [
    [0, 4.2, S.feedScene],
    [32.0, 36.05, S.typeScene],
    [3.95, 32.5, S.phoneScene],
    [35.9, 99, S.logoScene],
  ];

  // A small jolt when a stamp lands.
  const shakes = window.TL.sfx.filter((c) => c.type === 'stamp' || (c.type === 'thunk' && c.gain >= 1)).map((c) => [c.t, c.type === 'stamp' ? 10 * c.gain : 8]);
  function shake(t) {
    let x = 0, y = 0;
    for (const [t0, a] of shakes) {
      const d = t - t0;
      if (d < 0 || d > 0.3) continue;
      const k = a * Math.exp(-d * 18);
      x += k * Math.sin(d * 90); y += k * Math.cos(d * 70);
    }
    return [x, y];
  }

  window.renderFrame = function (t) {
    let html = '';
    for (const [a, b, fn] of list) if (t >= a && t < b) html += fn(t, F);
    const [sx, sy] = shake(t);
    stage.innerHTML = `<div style="position:absolute;inset:0;transform:translate(${sx}px,${sy}px)">${html}</div>
      <div style="position:absolute;inset:0;pointer-events:none;background:radial-gradient(ellipse at 50% 45%, rgba(0,0,0,0) 55%, rgba(0,0,0,.35) 100%);z-index:30"></div>
      ${t < 2.05 ? `<div style="position:absolute;left:0;right:0;top:0;height:${F.H * (F.square ? 0.32 : 0.3)}px;background:linear-gradient(180deg, rgba(18,17,16,.9), rgba(18,17,16,0));z-index:35;opacity:${Math.min(1, (2.05 - t) / 0.1)}"></div>` : ''}
      ${S.captions(t, F)}`;
  };
  window.FORMAT = F;
  window.ready = document.fonts.ready.then(() => Promise.all([...document.fonts].map((f) => f.load())));

  window.ready.then(() => {
    if (q.has('play')) {
      const t0 = performance.now() - (parseFloat(q.get('t') || '0') * 1000);
      const loop = () => { window.renderFrame(((performance.now() - t0) / 1000) % window.TL.DURATION); requestAnimationFrame(loop); };
      loop();
    } else {
      window.renderFrame(parseFloat(q.get('t') || '0'));
    }
  });
})();
