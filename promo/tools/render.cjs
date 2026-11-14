// Renders a cut to MP4: every frame drawn by the page, captured by Playwright, encoded by FFmpeg, muxed with the mix.
//   node tools/render.cjs --format vertical|square --cut main|teaser --fps 60 --scale 1 --workers 4
//                         --quality draft|final --audio out/audio/mix.wav --out out/haul-promo-9x16.mp4
const path = require('path');
const fs = require('fs');
const { spawn, execFileSync } = require('child_process');
const { chromium } = require('playwright');
const { serve, openPage, arg } = require('./common.cjs');
const TL = require('../src/timeline.js');

const format = arg('format', 'vertical');
const cut = arg('cut', 'main');
const fps = parseInt(arg('fps', '60'), 10);
const scale = parseFloat(arg('scale', '1'));
const workers = parseInt(arg('workers', '4'), 10);
const quality = arg('quality', 'final');
const audio = arg('audio', null);
const out = path.resolve(arg('out', `out/haul-${cut}-${format}.mp4`));
const tmp = path.resolve(path.dirname(out), `.parts-${path.basename(out, '.mp4')}`);

// Output frame index -> time on the main timeline.
function frameTimes() {
  if (cut === 'teaser') {
    const times = [];
    for (const [a, b] of TL.teaser.segments) {
      const n = Math.round((b - a) * fps);
      for (let i = 0; i < n; i++) times.push(a + i / fps);
    }
    return times;
  }
  const n = Math.round(TL.DURATION * fps);
  return Array.from({ length: n }, (_, i) => i / fps);
}

async function renderChunk(browser, port, times, file) {
  const { page } = await openPage(browser, port, format, scale);
  const crf = quality === 'final' ? '14' : '20';
  const preset = quality === 'final' ? 'slow' : 'veryfast';
  const ff = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-i', '-',
    '-c:v', 'libx264', '-preset', preset, '-crf', crf, '-pix_fmt', 'yuv420p', '-profile:v', 'high', '-r', String(fps), file],
    { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = new Promise((res, rej) => ff.on('close', (c) => (c === 0 ? res() : rej(new Error('ffmpeg ' + c)))));
  for (const t of times) {
    await page.evaluate((t) => window.renderFrame(t), t);
    const buf = await page.screenshot(quality === 'final' ? { type: 'png' } : { type: 'jpeg', quality: 90 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  await done;
  await page.close();
}

(async () => {
  const t0 = Date.now();
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp, { recursive: true });
  const times = frameTimes();
  const server = await serve();
  const port = server.address().port;
  const per = Math.ceil(times.length / workers);
  const files = [];
  const browsers = await Promise.all(Array.from({ length: workers }, () => chromium.launch()));
  await Promise.all(browsers.map((b, w) => {
    const chunk = times.slice(w * per, (w + 1) * per);
    const file = path.join(tmp, `part${w}.mp4`);
    files.push(file);
    return chunk.length ? renderChunk(b, port, chunk, file) : Promise.resolve();
  }));
  await Promise.all(browsers.map((b) => b.close()));
  server.close();
  const list = path.join(tmp, 'list.txt');
  fs.writeFileSync(list, files.filter((f) => fs.existsSync(f)).map((f) => `file '${f}'`).join('\n'));
  const args = ['-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', list];
  if (audio) args.push('-i', path.resolve(audio), '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-shortest');
  args.push('-c:v', 'copy', '-movflags', '+faststart', out);
  execFileSync('ffmpeg', args, { stdio: 'inherit' });
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`${out}  ${times.length} frames in ${((Date.now() - t0) / 1000).toFixed(0)} s`);
})().catch((e) => { console.error(e); process.exit(1); });
