// The scenes. Each draws one frame from the time t (seconds on the main timeline) and the format F.
(function (root) {
  const { clamp, lerp, E, tw, spring, kf, mix, rgba, fmtClock } = root.L;
  const U = root.UI;
  const { C } = U;

  // ---------------------------------------------------------------- 0:00–4:00 the feed, the pick, the sites
  const FEED_TAGS = ['TT', 'IG', 'X', 'YT'];
  const FEED_LENS = ['0:48', '1:12', '0:32', '12:04', '0:15', '2:31', '0:59'];

  function feedScene(t, F) {
    const { W, H } = F;
    const CW = F.square ? 600 : 740;
    const u = CW / 52; // one unit of the logo's 108-unit geometry
    const cx = W / 2, cy = H * (F.square ? 0.56 : 0.55);

    // Scroll: starts at full speed (the hook), brakes to a stop by 1.0 s.
    const travel = 26 * 16; // sixteen cards
    const sp = (x) => travel * (1 - E.quartOut(clamp(x / 1.0)));
    const scroll = sp(t);
    const vel = (sp(t - 1 / 60) - scroll) * u; // px per frame
    const blur = Math.min(26, Math.abs(vel) * 0.3);

    // The pick: the logo's own timings, on the beat at 1.0 s.
    const pk = clamp((t - 1.0) / 0.3);
    const ov = E.overshoot(pk);
    const clayP = tw(t, 1.0, 0.2);
    const playA = tw(t, 1.11, 0.15);
    const shadowA = 0.45 * tw(t, 1.05, 0.2);

    // Sites: the rest of the feed falls away, the picked card rises and becomes a ticket.
    const fall = (k) => tw(t, 2.0 + Math.abs(k) * 0.025, 0.4, E.in);
    const rise = tw(t, 2.0, 0.42, E.fos);
    const exit = tw(t, 3.85, 0.3, E.in);
    const push = 1 + 0.06 * tw(t, 1.0, 1.0, E.out) * (1 - rise);

    let cards = '';
    for (let k = -34; k <= 22; k++) {
      if (k === 0) continue;
      const yU = 26 * k + scroll; // card's offset from the picked slot, in units
      const y = cy + yU * u;
      const f = fall(k);
      if (f >= 1) continue;
      const yy = y + f * H * 0.5;
      if (yy < -400 || yy > H + 400) continue;
      const near = Math.abs(k) === 1 && t > 1.0 ? 1 : 0;
      const tone = near ? '#3A3733' : '#2A2825';
      const seed = ((k % 7) + 7) % 7;
      cards += `<div style="position:absolute;left:${cx - CW / 2}px;top:${yy - 10 * u}px;width:${CW}px;height:${20 * u}px;border-radius:${5 * u}px;overflow:hidden;opacity:${1 - f}">
        ${U.art(seed + 3, CW, 20 * u, t, { dim: 0.82, dimColor: tone })}
        <div class="mono" style="position:absolute;left:${2.2 * u}px;top:${1.8 * u}px;font-size:${2.4 * u}px;font-weight:600;color:${rgba(C.ink, 0.55)}">${FEED_TAGS[seed % 4]} · ${FEED_LENS[seed]}</div>
        <svg style="position:absolute;left:${19 * u}px;top:${5 * u}px" width="${9 * u}" height="${10 * u}" viewBox="0 0 9 10"><path d="M0,0 L9,5 L0,10 Z" fill="${rgba(C.ink, 0.18)}"/></svg></div>`;
    }

    // The picked card.
    const baseX = cx + 8 * u * ov; // steps 8 units right
    const baseY = cy + scroll * u;
    const tx = lerp(baseX, cx, rise), ty = lerp(baseY, H * (F.square ? 0.2 : 0.25), rise);
    const sc = lerp(1, F.square ? 0.56 : 0.66, rise);
    const rot = -6 * ov;
    const stampP = spring(t, 2.12, 7, 0.5);
    const stampA = tw(t, 2.1, 0.06);
    const picked = `<div style="position:absolute;left:${tx}px;top:${ty}px;width:0;height:0;transform:scale(${sc}) rotate(${rot}deg)">
        <div style="position:absolute;left:${-26 * u - 2 * u}px;top:${-10 * u + 4 * u}px;width:${CW}px;height:${20 * u}px;border-radius:${5 * u}px;background:rgba(0,0,0,${shadowA});filter:blur(${0.5 * u}px)"></div>
        <div style="position:absolute;left:${-26 * u}px;top:${-10 * u}px;width:${CW}px;height:${20 * u}px;border-radius:${5 * u}px;overflow:hidden">
          ${U.art(0 + 3, CW, 20 * u, t, { dim: 0.82, dimColor: '#3A3733' })}
          <div class="mono" style="position:absolute;left:${2.2 * u}px;top:${1.8 * u}px;font-size:${2.4 * u}px;font-weight:600;color:${rgba(C.ink, 0.55)}">TT · 0:48</div>
          <svg style="position:absolute;left:${19 * u}px;top:${5 * u}px" width="${9 * u}" height="${10 * u}" viewBox="0 0 9 10"><path d="M0,0 L9,5 L0,10 Z" fill="${rgba(C.ink, 0.18)}"/></svg>
          <div style="position:absolute;inset:0;background:${C.clay};opacity:${clayP}"></div>
          <svg style="position:absolute;left:${19 * u}px;top:${5 * u}px;opacity:${playA}" width="${9 * u}" height="${10 * u}" viewBox="0 0 9 10"><path d="M0,0 L9,5 L0,10 Z" fill="${C.onClay}"/></svg>
          ${stampA > 0 ? `<div class="mono" style="position:absolute;right:${3 * u}px;top:${3.2 * u}px;text-align:right;opacity:${stampA};transform:scale(${lerp(1.7, 1, stampP)});transform-origin:100% 0">
            <div style="font-size:${2.6 * u}px;letter-spacing:.08em;color:${C.onClay};opacity:.75">NO.</div>
            <div style="font-size:${4.6 * u}px;font-weight:600;letter-spacing:.04em;color:${C.onClay};line-height:1">0151</div></div>` : ''}
        </div></div>`;

    // Site list.
    let sites = '';
    if (t >= 2.1) {
      const names = ['TIKTOK', 'INSTAGRAM', 'X', 'YOUTUBE'];
      const fs = F.square ? 88 : 128, lh = F.square ? 92 : 134;
      const bw = F.square ? 560 : 780;
      const left = (W - bw) / 2;
      const top0 = H * (F.square ? 0.36 : 0.4);
      names.forEach((n, i) => {
        const p = tw(t, 2.25 + i * 0.25 - 0.04, 0.26, E.expoOut);
        if (p <= 0) return;
        sites += `<div style="position:absolute;left:${left}px;top:${top0 + i * lh}px;width:${bw}px;height:${lh}px;overflow:hidden">
          <div style="display:flex;align-items:baseline;gap:${F.square ? 24 : 34}px;transform:translateY(${(1 - p) * 100}%)">
          <span class="mono" style="font-size:${F.square ? 22 : 30}px;color:${C.muted};width:${F.square ? 40 : 54}px">0${i + 1}</span>
          <span class="cond" style="font-size:${fs}px;line-height:${lh}px;color:${C.ink}">${n}</span></div></div>`;
      });
      const ruleP = tw(t, 3.2, 0.3, E.fos);
      const ruleY = top0 + 4 * lh + (F.square ? 12 : 18);
      sites += `<div style="position:absolute;left:${left}px;top:${ruleY}px;width:${bw * ruleP}px;height:2px;background:${C.line}"></div>`;
      if (t >= 3.25) {
        const cnt = Math.round(lerp(4, 1000, E.quartOut(clamp((t - 3.25) / 0.25))));
        const st = spring(t, 3.5, 7, 0.45);
        const big = t >= 3.5 ? lerp(1.25, 1, st) : 1;
        sites += `<div style="position:absolute;left:${left}px;top:${ruleY + (F.square ? 14 : 22)}px;display:flex;align-items:baseline;gap:${F.square ? 24 : 34}px;transform:scale(${big});transform-origin:0 50%">
          <span class="mono" style="font-size:${F.square ? 22 : 30}px;color:${C.muted};width:${F.square ? 40 : 54}px">05</span>
          <span class="cond" style="font-size:${fs * 1.08}px;line-height:1;color:${C.ink};font-variant-numeric:tabular-nums">+${cnt.toLocaleString('en-US')} SITES</span></div>`;
      }
    }

    // The counter: posts scrolled past, frozen on the pick and flipped to KEPT: 1.
    let counter = '';
    const cOut = tw(t, 1.9, 0.3, E.in);
    if (cOut < 1) {
      const n = Math.round(1204 * (1 - scroll / travel));
      const flip = tw(t, 1.0, 0.22, E.expoOut);
      const lfs = F.square ? 20 : 26, nfs = F.square ? 96 : 150;
      const cyC = F.square ? H * 0.86 : H * 0.84;
      const block = (lab, num, y, a) => `<div style="position:absolute;left:0;right:0;top:0;transform:translateY(${y}%);opacity:${a};display:flex;flex-direction:column;align-items:center">
          <span class="mono" style="font-size:${lfs}px;letter-spacing:.16em;color:${C.muted}">${lab}</span>
          <span class="cond" style="font-size:${nfs}px;line-height:${nfs * 0.95}px;color:${C.ink};font-variant-numeric:tabular-nums">${num}</span></div>`;
      counter = `<div style="position:absolute;left:0;right:0;bottom:0;height:${H * 0.3}px;background:linear-gradient(0deg, rgba(18,17,16,.92), rgba(18,17,16,0));opacity:${1 - cOut}"></div>
        <div style="position:absolute;left:0;right:0;top:${cyC - nfs * 0.75}px;height:${nfs * 1.4}px;overflow:hidden;z-index:36;opacity:${1 - cOut}">
          ${block('SCROLLED PAST', n.toLocaleString('en-US'), -flip * 100, 1 - flip)}
          ${block('KEPT', '1', (1 - flip) * 100, flip)}</div>`;
    }

    const filter = blur > 0.5 ? `<svg width="0" height="0" style="position:absolute"><filter id="vblur" x="-5%" y="-20%" width="110%" height="140%"><feGaussianBlur stdDeviation="0 ${blur.toFixed(1)}"/></filter></svg>` : '';
    return `<div style="position:absolute;inset:0;transform:translateY(${-exit * H * 0.3}px);opacity:${1 - exit}">
      ${filter}
      <div style="position:absolute;inset:0;transform-origin:${cx + 8 * u}px ${cy}px;transform:scale(${push})">
        <div style="position:absolute;inset:0;${blur > 0.5 ? 'filter:url(#vblur);' : ''}">${cards}</div>
        ${picked}
      </div>${sites}${counter}</div>`;
  }

  // ---------------------------------------------------------------- 0:04–0:32 the phone
  const ROWS = [
    { no: '0151', title: 'Rooftop sunset, ten minutes in ten seconds', meta: 'TikTok · 1080p', size: '18.2 MB · TODAY 09:12', tag: 'TT', len: '0:10', seed: 6 },
    { no: '0150', title: 'Snow leopard, first light', meta: 'X · 1080p', size: '9.6 MB · TODAY 08:55', tag: 'X', len: '0:32', seed: 2 },
    { no: '0149', title: 'Lofi beats to study to, live set', meta: 'YouTube · Audio · M4A', size: '61.0 MB · TODAY 08:40', tag: 'YT', len: '42:10', seed: 3, kind: 'audio' },
    { no: '0148', title: 'Morning routine but make it chaotic', meta: 'TikTok · 1080p', size: '18.2 MB · YESTERDAY', tag: 'TT', len: '0:48', seed: 1 },
    { no: '0147', title: 'Studio tour, three rooms', meta: 'Instagram · 3 videos', size: '44.0 MB · YESTERDAY', tag: 'IG', len: '0:59', seed: 4 },
    { no: '0146', title: 'How this bridge was built in 1932', meta: 'YouTube · 720p', size: '148 MB · NOV 11', tag: 'YT', len: '12:04', seed: 5 },
  ];
  const NEW_ROW = { no: '0152', title: 'Night market, round two', tag: 'TT', seed: 7 };
  const GIF_ROW = { no: '0153', title: 'Paper plane, one take', meta: 'X · GIF', size: '2.1 MB · JUST NOW', tag: 'X', len: 'GIF', seed: 16, gif: true };
  const RH = 125;

  function holdScreen(t) {
    const ins1 = tw(t, 14.05, 0.35, E.fos); // the copied link's row slides in
    const ins2 = tw(t, 25.3, 0.3, E.fos);   // the X GIF's row
    let rows = '';
    let list = [];
    if (t >= 25.3) list.push({ ...GIF_ROW, _in: ins2 });
    if (t >= 14.05) {
      const p = tw(t, 14.4, 1.4, E.inOut);
      list.push(p < 1 ? { ...NEW_ROW, meta: 'TikTok · Best', progress: p, _in: ins1 } : { ...NEW_ROW, meta: 'TikTok · 1080p', size: '24.6 MB · JUST NOW', len: '1:12', _in: ins1 });
    }
    list = list.concat(ROWS);
    // Selection (28–30 s): rings, then clay checks on four rows.
    const selOn = t >= 28.1 && t < 30.2;
    let y = U.HOLD_TOP;
    list.forEach((r, i) => {
      let alpha = 1, dx = 0;
      if (r._in != null) { alpha = r._in; }
      const baseIdx = i - (t >= 25.3 ? 1 : 0) - (t >= 14.05 ? 1 : 0);
      if (r._in == null && t < 14) {
        const p = tw(t, 12.1 + baseIdx * 0.25 - 0.03, 0.3, E.expoOut);
        alpha = p; dx = (1 - p) * 40;
      }
      const o = { ...r };
      if (selOn) o.check = i < 4 ? tw(t, 28.25 + i * 0.25, 0.18, E.linear) : 0;
      const h = r._in != null ? RH * r._in : RH;
      if (r._in != null) {
        rows += `<div style="position:absolute;left:0;right:0;top:${y}px;height:${h}px;overflow:hidden"><div style="position:absolute;left:0;right:0;top:${-RH + h}px;height:${RH}px">${U.row(o, 0, t, { alpha })}</div></div>`;
      } else if (y < 844) {
        rows += U.row(o, y, t, { alpha, dx });
      }
      y += h;
    });
    const count = 6 + (t >= 14.05 ? 1 : 0) + (t >= 25.3 ? 1 : 0);
    const wifi = tw(t, 29.5, 0.3, E.linear);

    // The dock: copied link, then Add a link, then the selection bar.
    let dock = '';
    const dockIn = tw(t, 13.1, 0.4, E.expoOut);
    const swapAdd = tw(t, 14.2, 0.2);
    const swapSel = tw(t, 29.0, 0.25, E.backOut);
    if (t < 14.4) dock += U.dockClip({ pressed: U.press(t, 14.0), y: -(1 - dockIn) * 200, alpha: (1 - swapAdd) * clamp(dockIn * 2) });
    if (t >= 14.2 && t < 29.25) dock += U.dockAdd({ alpha: t < 29 ? swapAdd : 1 - tw(t, 29.0, 0.15) });
    if (t >= 29.0 && t < 30.6) dock += U.dockSelect(4, { y: -(1 - swapSel) * 30, alpha: clamp(swapSel * 2) });

    return `<div style="position:absolute;inset:0;background:${C.ground};z-index:0">
      <div style="position:absolute;left:0;right:0;top:0;bottom:0;overflow:hidden">${rows}</div>
      <div style="position:absolute;left:0;right:0;top:0;height:${U.HOLD_TOP - 2}px;background:${C.ground};z-index:2;clip-path:inset(0 0 0 0)"></div>
      <div style="position:absolute;left:0;right:0;top:0;z-index:3">${U.holdHeader({ count: count + ' HAULS', wifi })}</div>
      <div style="position:absolute;inset:0;z-index:4">${dock}</div></div>`;
  }

  function phoneScreen(t) {
    let h = '';
    // 4.0–12.2: someone else's feed, the share sheet, the notification.
    if (t < 12.25) {
      let pos = 0;
      [8, 9, 10, 11].forEach((s) => { pos += tw(t, s, 0.38, E.fos); });
      const seeds = [1, 8, 9, 10, 11, 12];
      seeds.forEach((sd, i) => {
        const top = (i - pos) * 844;
        if (top > -844 && top < 844) h += `<div style="position:absolute;left:0;top:${top}px;width:390px;height:844px">${U.post(sd, t, { shareBtn: i === 0 ? U.press(t, 4.55) : 0 })}</div>`;
      });
      const sp = t < 5.9 ? tw(t, 4.7, 0.35) : 1 - tw(t, 5.9, 0.3, E.in);
      if (sp > 0) h += U.scrim(sp * 0.7) + U.shareSheet(t, sp, U.press(t, 5.5));
      h += U.touch(348, 760, t, 4.55) + U.touch(58, 600, t, 5.5);
      const toastP = t < 7.4 ? tw(t, 6.2, 0.25) : 1 - tw(t, 7.4, 0.2);
      h += U.toast('Haul: downloading from TikTok', toastP);
      // Notification
      const nIn = tw(t, 8.25, 0.35, E.expoOut), nOut = tw(t, 11.55, 0.3, E.in);
      if (nIn > 0 && nOut < 1) {
        const pct = E.inOut(clamp((t - 8.55) / 1.45));
        const sw = tw(t, 10.0, 0.15);
        h += `<div style="position:absolute;left:10px;right:10px;top:${lerp(-160, 40, nIn) - nOut * 220}px;z-index:30">
          <div style="position:relative">
            <div style="opacity:${1 - sw}">${U.notifProgress(pct)}</div>
            ${sw > 0 ? `<div style="position:absolute;left:0;right:0;top:0;opacity:${sw};transform:scale(${lerp(0.97, 1, E.backOut(sw))})">${U.notifSaved('Rooftop sunset, ten minutes in ten seconds', 'Movies/Haul · TikTok', 6, t)}</div>` : ''}
          </div></div>`;
      }
    }
    // 11.85–32: the app.
    if (t >= 11.85) {
      const up = tw(t, 11.85, 0.4, E.fos);
      h += `<div style="position:absolute;inset:0;transform:translateY(${(1 - up) * 844}px)">${holdScreen(t)}`;
      // New haul sheet (quality)
      if (t >= 16.0 && t < 18.5) {
        const p = t < 18.2 ? tw(t, 16.0, 0.35) : 1 - tw(t, 18.2, 0.25, E.in);
        const seg = kf(t, [[16.0, 0], [16.5, 0], [16.68, 1], [17.0, 1], [17.18, 2], [17.5, 2], [17.68, 3], [18.0, 3], [18.18, 0]]);
        h += U.scrim(p) + U.sheetAdd(t, p, seg);
        const cxs = (i) => 20 + 41.5 + i * 89;
        h += U.touch(cxs(1), 576, t, 16.5) + U.touch(cxs(2), 576, t, 17.0) + U.touch(cxs(3), 576, t, 17.5) + U.touch(cxs(0), 576, t, 18.0);
      }
      // Clip a section
      if (t >= 19.85 && t < 22.1) {
        const p = t < 21.85 ? tw(t, 19.85, 0.3) : 1 - tw(t, 21.85, 0.2, E.in);
        const m = tw(t, 20.25, 1.0, E.fos);
        h += U.scrim(p) + U.sheetClip(t, p, lerp(0, 80, m), lerp(222, 125, m), 222, U.press(t, 21.6));
        h += U.touch(298, 702, t, 21.6);
      }
      // Carousel picker
      if (t >= 21.95 && t < 24.1) {
        const p = t < 23.85 ? tw(t, 22.0, 0.25) : 1 - tw(t, 23.85, 0.2, E.in);
        const order = [0, 1, 3, 5];
        const checks = [0, 0, 0, 0, 0, 0];
        order.forEach((ti, k) => { checks[ti] = tw(t, 22.5 + k * 0.25, 0.2, E.linear); });
        h += U.scrim(Math.max(p, t < 22.2 ? 1 : 0)) + U.sheetPick(t, p, checks, U.press(t, 23.6));
        order.forEach((ti, k) => { h += U.touch(20 + 56 + (ti % 3) * 118.7, 378 + 70 + Math.floor(ti / 3) * 146.8, t, 22.5 + k * 0.25); });
        h += U.touch(262, 705, t, 23.6);
      }
      // TikTok photos
      if (t >= 23.9 && t < 25.25) {
        const p = t < 25.0 ? tw(t, 23.9, 0.25) : 1 - tw(t, 25.0, 0.2, E.in);
        const sel = kf(t, [[23.9, -1], [24.25, -1], [24.3, 0], [24.5, 0], [24.6, 1], [24.75, 1], [24.85, 2]]);
        h += U.scrim(Math.max(p, t < 24.1 ? 1 : 0)) + U.sheetTiktok(t, p, sel);
        h += U.touch(195, 598, t, 24.25) + U.touch(195, 670, t, 24.5) + U.touch(195, 742, t, 24.75);
      }
      h += '</div>';
      // "Haul this as": a share from another app, over its post.
      if (t >= 18.3 && t < 20.05) {
        const inP = tw(t, 18.3, 0.3, E.fos), outP = tw(t, 19.75, 0.3, E.in);
        const x = (1 - inP) * 390 + outP * 390;
        const sp = t < 19.75 ? tw(t, 18.5, 0.3) : 1;
        const seg = kf(t, [[18.5, 0], [19.5, 0], [19.68, 2]]);
        h += `<div style="position:absolute;inset:0;transform:translateX(${x}px)">${U.post(13, t)}${U.scrim(sp * 0.8)}${U.sheetQuality(t, sp, seg, U.press(t, 19.5))}${U.touch(20 + 41.5 + 2 * 89, 844 - 230 + 117, t, 19.5)}</div>`;
      }
      // Player
      if (t >= 25.9 && t < 28.15) {
        const inP = tw(t, 25.9, 0.28, E.fos), outP = tw(t, 27.9, 0.25, E.in);
        const pp = tw(t, 26.5, 0.28, E.fos) + tw(t, 27.0, 0.28, E.fos);
        const page = Math.round(pp);
        h += `<div style="position:absolute;inset:0;transform:translateX(${(1 - inP) * 390 + outP * 390}px)">${U.player(t, page, pp)}`;
        [26.5, 27.0].forEach((s) => {
          const q = (t - s + 0.08) / 0.32;
          if (q > 0 && q < 1) h += `<div style="position:absolute;left:${lerp(320, 70, E.fos(q))}px;top:520px;width:38px;height:38px;border-radius:50%;transform:translate(-50%,-50%);background:rgba(255,255,255,${0.5 * Math.sin(q * Math.PI)});z-index:60"></div>`;
        });
        h += '</div>';
      }
      // Quick Settings
      if (t >= 30.0) {
        const p = tw(t, 30.0, 0.4, E.fos);
        h += U.shade(t, p, tw(t, 30.75, 0.15)) + U.touch(102, 146, t, 30.75);
      }
      // Long-press for selection
      h += U.touch(47, U.HOLD_TOP + 62, t, 28.05);
    }
    return h;
  }

  function phoneScene(t, F) {
    const P = F.phone;
    const enter = tw(t, 4.0, 0.55, E.expoOut);
    const leave = tw(t, 32.0, 0.45, E.in);
    const z0 = kf(t, [[0, 1], [8.25, 1], [8.6, 1.3, E.fos], [11.5, 1.3], [11.9, 1, E.fos], [20.15, 1], [20.45, 1.08, E.fos], [21.85, 1.08], [22.1, 1, E.fos], [25.2, 1], [25.5, 1.15, E.fos], [25.85, 1.15], [26.1, 1, E.fos]]);
    const fy = kf(t, [[0, 432], [8.25, 432], [8.4, 80], [11.5, 80], [11.9, 432], [20.15, 432], [20.3, 560], [21.85, 560], [22.1, 432], [25.2, 432], [25.35, 250], [25.85, 250], [26.1, 432]]);
    const z = (F.square ? 1 + (z0 - 1) * 0.45 : z0) * (1 + 0.012 * Math.sin(t * 0.9));
    const s = P.s * z;
    const fx = U.DEV.w / 2;
    const bx = P.cx - (U.DEV.w / 2) * P.s, by = P.top;
    let X = bx + fx * P.s - fx * s;
    let Y = by + fy * P.s - fy * s;
    Y += (1 - enter) * F.H * 0.9 + leave * F.H * 1.1;
    const rot = (1 - enter) * 8 + leave * 10;
    const dev = U.device(phoneScreen(t));
    // Folder labels under the caption (10.5–11.6)
    let folders = '';
    if (t >= 10.4 && t < 12.0) {
      const items = ['MOVIES/HAUL', 'PICTURES/HAUL', 'MUSIC/HAUL'];
      const out = tw(t, 11.7, 0.25, E.in);
      folders = `<div style="position:absolute;${F.square ? `left:${F.cap.x}px;top:${F.H * 0.66}px;flex-direction:column;align-items:flex-start` : `left:0;right:0;top:${F.cap.foldersY}px;justify-content:center`};display:flex;gap:${F.square ? 10 : 18}px;z-index:20;opacity:${1 - out}">
        ${items.map((n, i) => { const p = tw(t, 10.5 + i * 0.25 - 0.03, 0.22, E.expoOut); return `<div class="mono" style="opacity:${p};transform:translateY(${(1 - p) * 16}px);font-size:${F.square ? 22 : 26}px;letter-spacing:.08em;color:${C.ink};background:${C.ground};border:1.5px solid ${C.line};border-radius:8px;padding:8px 12px">${n}</div>`; }).join('')}</div>`;
    }
    const scrimTop = F.square ? '' : `<div style="position:absolute;left:0;right:0;top:0;height:${F.cap.scrimH}px;background:linear-gradient(180deg, ${C.ground} 0%, ${rgba(C.ground, 0.92)} 60%, ${rgba(C.ground, 0)} 100%);z-index:10"></div>`;
    return `<div style="position:absolute;left:${X}px;top:${Y}px;width:${U.DEV.w}px;height:${U.DEV.h}px;transform-origin:0 0;transform:scale(${s}) rotate(${rot}deg)">${dev}</div>${scrimTop}${folders}`;
  }

  // ---------------------------------------------------------------- 0:32–0:36 no strings
  function typeScene(t, F) {
    const { W, H } = F;
    const fs = F.square ? 150 : 196;
    const lh = fs * 0.98;
    const cy = H * (F.square ? 0.4 : 0.42);
    const lines = [[32.5, 'ACCOUNT.'], [33.0, 'ADS.'], [33.5, 'SERVER.']];
    const exit = tw(t, 35.6, 0.4, E.in);
    let out = '';
    lines.forEach(([t0, word], i) => {
      if (t < t0 - 0.02) return;
      const a = tw(t, t0 - 0.02, 0.05, E.linear);
      const s = lerp(1.7, 1, spring(t, t0 - 0.02, 6.5, 0.48));
      out += `<div class="cond" style="position:absolute;left:0;right:0;top:${cy + (i - 1.5) * lh}px;height:${lh}px;text-align:center;font-size:${fs}px;line-height:${lh}px;opacity:${a};transform:scale(${s})">
        <span style="color:${C.muted}">NO </span><span style="color:${C.ink}">${word}</span></div>`;
    });
    const typed = (txt, t0, t1) => {
      const n = Math.floor(txt.length * clamp((t - t0) / (t1 - t0)));
      return txt.slice(0, n);
    };
    const l1 = 'YT-DLP RUNS ON YOUR PHONE.', l2 = 'IT UPDATES ITSELF DAILY.';
    const s1 = typed(l1, 34.0, 34.65), s2 = typed(l2, 34.75, 35.4);
    const caretOn = Math.floor(t * 4) % 2 === 0 || (t > 34 && t < 35.45);
    const mfs = F.square ? 30 : 40;
    const caret = `<span style="display:inline-block;width:${mfs * 0.6}px;height:${mfs}px;background:${C.clay};vertical-align:-${mfs * 0.15}px;opacity:${caretOn ? 1 : 0}"></span>`;
    if (t >= 34.0) {
      out += `<div class="mono" style="position:absolute;left:0;right:0;top:${cy + 1.5 * lh + (F.square ? 40 : 70)}px;text-align:center;font-size:${mfs}px;line-height:1.6;letter-spacing:.04em;color:${C.ink}">
        <div>${s1}${t < 34.75 ? caret : ''}</div><div style="color:${C.muted}">${s2}${t >= 34.75 ? caret : ''}</div></div>`;
    }
    return `<div style="position:absolute;inset:0;transform:translateY(${-exit * 240}px);opacity:${1 - exit}">${out}</div>`;
  }

  // ---------------------------------------------------------------- 0:36–0:42 the logo
  const LOGO_T0 = 36.05; // the launch animation starts; its pick lands on the beat at 36.5

  function logoMark(t, size) {
    const a = t - LOGO_T0;
    const feedY = lerp(104, 0, E.fos(clamp(a / 0.45)));
    const pk = E.overshoot(clamp((a - 0.45) / 0.3));
    const tx = lerp(-8, 0, pk), rot = lerp(0, -6, pk);
    const card = mix('#3A3733', C.clay, E.fos(clamp((a - 0.45) / 0.2)));
    const play = E.fos(clamp((a - 0.56) / 0.15));
    const shadow = 0.45 * E.fos(clamp((a - 0.5) / 0.2));
    const outer = mix('#2A2825', C.surface, E.fos(clamp((a - 0.5) / 0.4)));
    const rr = (x, y, fill, extra = '') => `<rect x="${x}" y="${y}" width="52" height="20" rx="5" fill="${fill}" ${extra}/>`;
    return `<svg width="${size}" height="${size}" viewBox="0 0 108 108" style="display:block">
      <defs><clipPath id="tile"><rect width="108" height="108" rx="24"/></clipPath>
      <filter id="soft" x="-20%" y="-40%" width="140%" height="180%"><feGaussianBlur stdDeviation="0.8"/></filter></defs>
      <rect width="108" height="108" rx="24" fill="${C.surface}"/>
      <g clip-path="url(#tile)"><g transform="translate(54 54) scale(0.76) translate(-54 -54)"><g transform="translate(0 ${feedY})">
        ${rr(24, -34, outer)}${rr(24, -8, outer)}${rr(24, 18, '#3A3733')}${rr(24, 70, '#3A3733')}${rr(24, 96, outer)}
        <g transform="translate(${tx} 0) rotate(${rot} 58 54)">
          ${rr(30, 48, '#000', `fill-opacity="${shadow}" filter="url(#soft)"`)}
          ${rr(32, 44, card)}
          <path d="M51,49 L60,54 L51,59 Z" fill="${C.onClay}" fill-opacity="${play}"/>
        </g></g></g></g></svg>`;
  }

  function logoScene(t, F) {
    const { W, H } = F;
    const sq = F.square;
    const size = sq ? 380 : 580;
    const pop = tw(t, 35.95, 0.3, E.backOut);
    const lift = tw(t, 36.9, 0.45, E.fos);
    const cy = lerp(H * 0.42, H * (sq ? 0.25 : 0.3), lift);
    const push = 1 + 0.025 * tw(t, 37.0, 5, E.out);
    const fade = (t0, d = 0.35) => tw(t, t0, d, E.expoOut);
    const word = fade(37.0), tag = fade(37.5), cta = fade(38.0), ver = fade(38.25);
    const yT = cy + size / 2;
    const g = sq ? { w: 136, wt: 30, tg: 52, tt: 18, cl: 40, ct: 26, pill: 74, pt: 24, vt: 20 }
                 : { w: 220, wt: 50, tg: 76, tt: 30, cl: 64, ct: 32, pill: 112, pt: 36, vt: 28 };
    const reveal = (p, html, top, h) => `<div style="position:absolute;left:0;right:0;top:${top}px;height:${h}px;overflow:hidden"><div style="transform:translateY(${(1 - p) * 100}%);opacity:${p}">${html}</div></div>`;
    let y = yT + g.wt;
    let out = `<div style="position:absolute;left:${W / 2 - size / 2}px;top:${cy - size / 2}px;transform:scale(${lerp(0.82, 1, pop) * push});opacity:${clamp(pop * 3)};filter:drop-shadow(0 30px 60px rgba(0,0,0,.5))">${logoMark(t, size)}</div>`;
    out += reveal(word, `<div class="cond" style="text-align:center;font-size:${g.w}px;line-height:${g.w * 0.92}px;color:${C.ink};letter-spacing:2px">HAUL.</div>`, y, g.w * 0.95);
    y += g.w * 0.95 + g.tt * 0.4;
    out += reveal(tag, `<div class="cond" style="text-align:center;font-size:${g.tg}px;line-height:${g.tg}px;color:${C.ink}">ANY LINK, INTO YOUR GALLERY.</div>`, y, g.tg * 1.05);
    y += g.tg + g.cl;
    out += `<div style="position:absolute;left:0;right:0;top:${y}px;text-align:center;opacity:${cta}" class="mono"><span style="font-size:${g.ct * 0.85}px;letter-spacing:.14em;color:${C.muted}">DOWNLOAD FROM</span></div>`;
    y += g.ct * 1.6;
    out += `<div style="position:absolute;left:0;right:0;top:${y}px;display:flex;justify-content:center;opacity:${cta};transform:translateY(${(1 - cta) * 30}px)">
      <div class="mono" style="height:${g.pill}px;padding:0 ${g.pill * 0.36}px;border-radius:${g.pill * 0.24}px;background:${C.clay};color:${C.onClay};display:flex;align-items:center;font-size:${g.pt}px;font-weight:600;letter-spacing:.01em">github.com/ulot2/haul/releases</div></div>`;
    y += g.pill + g.vt * 1.2;
    out += `<div style="position:absolute;left:0;right:0;top:${y}px;text-align:center;opacity:${ver}" class="mono"><span style="font-size:${g.vt}px;letter-spacing:.14em;color:${C.muted}">V1.3.0 · ANDROID · FREE</span></div>`;
    return out;
  }

  // ---------------------------------------------------------------- captions
  function captions(t, F) {
    const TL = root.TL;
    let out = '';
    TL.captions.forEach((c, idx) => {
      if (t < c.t0 - 0.01 || t > c.t1 + 0.01) return;
      const full = !!c.full;
      const lay = full ? F.capFull : F.cap;
      const words = c.text.split('\n').map((l) => l.split(' '));
      let wi = 0;
      const exit = tw(t, c.t1 - 0.14, 0.14, E.in);
      const linesHtml = words.map((ws) => `<div style="display:flex;gap:${lay.size * 0.22}px;justify-content:${lay.align === 'left' ? 'flex-start' : 'center'}">${ws.map((w) => {
        const p = tw(t, c.t0 + wi++ * 0.045, 0.34, E.expoOut);
        const y = (1 - p) * 105 - exit * 105;
        return `<span style="display:inline-block;overflow:hidden;padding:0 0.04em;margin:0 -0.04em"><span style="display:inline-block;transform:translateY(${y}%)">${w}</span></span>`;
      }).join('')}</div>`).join('');
      // The kicker stays put while neighbouring captions share it.
      const prev = TL.captions[idx - 1], next = TL.captions[idx + 1];
      let kick = '';
      if (c.kicker) {
        const kin = prev && prev.kicker === c.kicker && Math.abs(prev.t1 - c.t0) < 0.05 ? 1 : tw(t, c.t0, 0.3, E.expoOut);
        const kout = next && next.kicker === c.kicker && Math.abs(next.t0 - c.t1) < 0.05 ? 0 : tw(t, c.t1 - 0.14, 0.14, E.in);
        kick = `<div class="mono" style="font-size:${lay.kicker}px;letter-spacing:.16em;color:${C.muted};margin-bottom:${lay.kicker * 0.7}px;opacity:${kin * (1 - kout)};display:flex;gap:12px;align-items:center;justify-content:${lay.align === 'left' ? 'flex-start' : 'center'}">
          <span style="display:inline-block;width:${lay.kicker * 0.5}px;height:${lay.kicker * 0.5}px;border:2px solid ${C.muted}"></span>${c.kicker}</div>`;
      }
      const pos = lay.align === 'left'
        ? `left:${lay.x}px;width:${lay.w}px;top:${lay.y}px;transform:translateY(-50%)`
        : `left:0;right:0;top:${lay.y}px`;
      out += `<div style="position:absolute;${pos};z-index:40">${kick}<div class="cond" style="font-size:${lay.size}px;line-height:${lay.size * 0.94}px;color:${C.ink};text-align:${lay.align}">${linesHtml}</div></div>`;
    });
    return out;
  }

  root.SCENES = { feedScene, phoneScene, typeScene, logoScene, captions, LOGO_T0 };
})(typeof window !== 'undefined' ? window : globalThis);
