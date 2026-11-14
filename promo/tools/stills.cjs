// Renders single frames as images: node tools/stills.cjs --format vertical --times 0.5,1.4 --out out/stills --scale 0.5
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { serve, openPage, arg } = require('./common.cjs');

(async () => {
  const format = arg('format', 'vertical');
  const times = arg('times', '0').split(',').map(Number);
  const out = path.resolve(arg('out', path.join(__dirname, '..', 'out', 'stills')));
  const scale = parseFloat(arg('scale', '0.5'));
  const type = arg('type', 'jpeg');
  fs.mkdirSync(out, { recursive: true });
  const server = await serve();
  const browser = await chromium.launch();
  const { page } = await openPage(browser, server.address().port, format, scale);
  for (const t of times) {
    await page.evaluate((t) => window.renderFrame(t), t);
    const file = path.join(out, `${format}-${t.toFixed(2)}.${type === 'png' ? 'png' : 'jpg'}`);
    await page.screenshot({ path: file, type, ...(type === 'jpeg' ? { quality: 88 } : {}) });
    console.log(file);
  }
  await browser.close();
  server.close();
})();
