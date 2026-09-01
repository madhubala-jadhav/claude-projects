/**
 * ExpenseInNutshell — design token accessibility checker.
 *
 * ARCHITECT'S ADDITION (not requested by the spec). It exists so every number quoted in
 * design-system.md is reproducible rather than asserted. It is a design tool, not
 * application source code, and ships no runtime dependency.
 *
 * Run:  node design/design-system/contrast-check.js design/design-system/tokens.json
 *
 * Computes:
 *   1. WCAG 2.1 contrast ratios for every semantic foreground/background pair in use.
 *   2. WCAG 1.4.11 non-text contrast for chart series against the chart surface.
 *   3. CIEDE2000 distance between every pair of chart series colours, after simulating
 *      protanopia / deuteranopia / tritanopia with the Machado, Oliveira & Fernandes (2009)
 *      severity-1.0 matrices. Floor used: dE 6.0 (the practical threshold for "two adjacent
 *      fills read as different categories").
 *   4. Relative luminance of each series colour, i.e. the greyscale/print separation.
 */
const fs = require('fs');
const tokensPath = process.argv[2] || require('path').join(__dirname, 'tokens.json');
const T = JSON.parse(fs.readFileSync(tokensPath, 'utf8'));

const hex2rgb = h => { h = h.replace('#', ''); return [0, 2, 4].map(i => parseInt(h.substr(i, 2), 16) / 255); };
const srgb2lin = c => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
const lin2srgb = c => { c = Math.max(0, Math.min(1, c)); return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; };
const lum = h => { const [r, g, b] = hex2rgb(h).map(srgb2lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const ratio = (a, b) => { const L1 = lum(a), L2 = lum(b); return (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05); };
const r2 = (a, b) => Math.round(ratio(a, b) * 100) / 100;

const CVD = {
  protan: [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
  deutan: [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
  tritan: [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
};
function simulate(hex, kind) {
  if (kind === 'normal') return hex;
  const lin = hex2rgb(hex).map(srgb2lin), m = CVD[kind];
  const out = m.map(row => row[0] * lin[0] + row[1] * lin[1] + row[2] * lin[2]).map(lin2srgb);
  return '#' + out.map(c => Math.round(c * 255).toString(16).padStart(2, '0')).join('');
}
function rgb2lab(hex) {
  const [r, g, b] = hex2rgb(hex).map(srgb2lin);
  const X = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
  const Y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b);
  const Z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
  const f = t => (t > 216 / 24389 ? Math.cbrt(t) : (841 / 108) * t + 4 / 29);
  const [fx, fy, fz] = [f(X), f(Y), f(Z)];
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}
function ciede2000(h1, h2) {
  const [L1, a1, b1] = rgb2lab(h1), [L2, a2, b2] = rgb2lab(h2);
  const rad = Math.PI / 180, deg = 180 / Math.PI;
  const C1 = Math.hypot(a1, b1), C2 = Math.hypot(a2, b2), Cb = (C1 + C2) / 2;
  const G = 0.5 * (1 - Math.sqrt(Math.pow(Cb, 7) / (Math.pow(Cb, 7) + Math.pow(25, 7))));
  const ap1 = (1 + G) * a1, ap2 = (1 + G) * a2;
  const Cp1 = Math.hypot(ap1, b1), Cp2 = Math.hypot(ap2, b2);
  const hpf = (b, ap) => { if (b === 0 && ap === 0) return 0; const h = Math.atan2(b, ap) * deg; return h < 0 ? h + 360 : h; };
  const hp1 = hpf(b1, ap1), hp2 = hpf(b2, ap2);
  const dL = L2 - L1, dC = Cp2 - Cp1;
  let dhp = 0;
  if (Cp1 * Cp2 !== 0) { dhp = hp2 - hp1; if (dhp > 180) dhp -= 360; else if (dhp < -180) dhp += 360; }
  const dH = 2 * Math.sqrt(Cp1 * Cp2) * Math.sin((dhp / 2) * rad);
  const Lb = (L1 + L2) / 2, Cpb = (Cp1 + Cp2) / 2;
  let Hpb;
  if (Cp1 * Cp2 === 0) Hpb = hp1 + hp2;
  else {
    const d = Math.abs(hp1 - hp2);
    Hpb = d > 180 ? (hp1 + hp2 + 360) / 2 : (hp1 + hp2) / 2;
    if (d > 180 && hp1 + hp2 >= 360) Hpb -= 360;
  }
  const Tt = 1 - 0.17 * Math.cos((Hpb - 30) * rad) + 0.24 * Math.cos(2 * Hpb * rad)
    + 0.32 * Math.cos((3 * Hpb + 6) * rad) - 0.20 * Math.cos((4 * Hpb - 63) * rad);
  const dTh = 30 * Math.exp(-Math.pow((Hpb - 275) / 25, 2));
  const Rc = 2 * Math.sqrt(Math.pow(Cpb, 7) / (Math.pow(Cpb, 7) + Math.pow(25, 7)));
  const Sl = 1 + (0.015 * Math.pow(Lb - 50, 2)) / Math.sqrt(20 + Math.pow(Lb - 50, 2));
  const Sc = 1 + 0.045 * Cpb, Sh = 1 + 0.015 * Cpb * Tt;
  const Rt = -Math.sin(2 * dTh * rad) * Rc;
  return Math.sqrt(Math.pow(dL / Sl, 2) + Math.pow(dC / Sc, 2) + Math.pow(dH / Sh, 2) + Rt * (dC / Sc) * (dH / Sh));
}

const PAIRS = [
  ['text.primary', 'surface.raised', 4.5, 'body on card'],
  ['text.primary', 'surface.canvas', 4.5, 'body on page'],
  ['text.primary', 'surface.sunken', 4.5, 'table header'],
  ['text.primary', 'surface.accentSubtle', 4.5, 'top-category callout'],
  ['text.primary', 'surface.warningSubtle', 4.5, 'uncategorized row'],
  ['text.primary', 'surface.dangerSubtle', 4.5, 'skipped-file row'],
  ['text.primary', 'surface.successSubtle', 4.5, 'confirmation'],
  ['text.secondary', 'surface.raised', 4.5, 'label on card'],
  ['text.secondary', 'surface.canvas', 4.5, 'label on page'],
  ['text.secondary', 'surface.sunken', 4.5, 'table header label'],
  ['text.muted', 'surface.raised', 4.5, 'caption on card'],
  ['text.muted', 'surface.canvas', 4.5, 'caption on page'],
  ['text.accent', 'surface.raised', 4.5, 'link'],
  ['text.accent', 'surface.canvas', 4.5, 'link on page'],
  ['text.accent', 'surface.accentSubtle', 4.5, 'link inside callout'],
  ['text.danger', 'surface.raised', 4.5, 'error text'],
  ['text.danger', 'surface.dangerSubtle', 4.5, 'error text in row'],
  ['text.warning', 'surface.raised', 4.5, 'warning text'],
  ['text.warning', 'surface.warningSubtle', 4.5, 'warning text in row'],
  ['text.success', 'surface.raised', 4.5, 'success text'],
  ['text.success', 'surface.successSubtle', 4.5, 'success text in row'],
  ['text.inverse', 'accent.default', 4.5, 'primary button label'],
  ['text.inverse', 'accent.hover', 4.5, 'primary button hover label'],
  ['delta.up', 'surface.raised', 4.5, 'MoM increase'],
  ['delta.down', 'surface.raised', 4.5, 'MoM decrease'],
  ['delta.flat', 'surface.raised', 4.5, 'MoM flat'],
  ['border.control', 'surface.raised', 3, 'WCAG 1.4.11 control boundary'],
  ['border.control', 'surface.canvas', 3, 'WCAG 1.4.11'],
  ['border.focus', 'surface.raised', 3, 'focus ring on card'],
  ['border.focus', 'surface.canvas', 3, 'focus ring on page'],
  ['chart.axis', 'chart.surface', 3, 'axis / tick'],
  ['chart.other', 'chart.surface', 3, '"Other" slice'],
  ['chart.sliceGap', 'chart.surface', 3, 'slice boundary stroke — the 1.4.11 relief channel'],
  ['chart.uncategorizedHatch', 'chart.uncategorizedFill', 3, 'hatch ink on its fill'],
];

let failures = 0;
for (const themeKey of ['light', 'dark']) {
  const c = T.color[themeKey];
  const get = p => p.split('.').reduce((o, k) => o[k], c).$value;

  console.log(`\n================ ${themeKey.toUpperCase()} — WCAG contrast ================`);
  for (const [fg, bg, need, note] of PAIRS) {
    const v = r2(get(fg), get(bg));
    const ok = v >= need;
    if (!ok) failures++;
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${v.toFixed(2).padStart(6)}:1  need ${need}   ${fg} on ${bg}  — ${note}`);
  }

  const surf = c.chart.surface.$value;
  const slots = Object.entries(c.chart.series).filter(([k]) => k.startsWith('slot'));
  const all = slots.concat([['other', c.chart.other]]);

  console.log(`\n---- ${themeKey} — chart series vs surface (WCAG 1.4.11 target 3:1) ----`);
  for (const [k, v] of all) {
    const val = r2(v.$value, surf);
    console.log(`${val >= 3 ? 'PASS ' : 'BELOW'}  ${val.toFixed(2).padStart(5)}:1  ${k} ${v.$value}`);
  }

  console.log(`\n---- ${themeKey} — ALL-PAIRS CIEDE2000 under CVD (floor 6.0) ----`);
  for (const kind of ['normal', 'protan', 'deutan', 'tritan']) {
    let worst = Infinity, wp = '';
    const below = [];
    for (let i = 0; i < all.length; i++) {
      for (let j = i + 1; j < all.length; j++) {
        const d = ciede2000(simulate(all[i][1].$value, kind), simulate(all[j][1].$value, kind));
        if (d < worst) { worst = d; wp = `${all[i][0]}/${all[j][0]}`; }
        if (d < 6) below.push(`${all[i][0]}/${all[j][0]}=${d.toFixed(1)}`);
      }
    }
    console.log(`  ${kind.padEnd(7)} worst dE ${worst.toFixed(1).padStart(5)} (${wp}); below floor: ${below.length}`);
    if (below.length) console.log(`           ${below.join(', ')}`);
  }

  console.log(`\n---- ${themeKey} — ADJACENT-pairs in slot order (floor 6.0) ----`);
  for (const kind of ['normal', 'protan', 'deutan', 'tritan']) {
    let worst = Infinity, wp = '';
    for (let i = 0; i < all.length - 1; i++) {
      const d = ciede2000(simulate(all[i][1].$value, kind), simulate(all[i + 1][1].$value, kind));
      if (d < worst) { worst = d; wp = `${all[i][0]}/${all[i + 1][0]}`; }
    }
    console.log(`  ${kind.padEnd(7)} worst adjacent dE ${worst.toFixed(1).padStart(5)} (${wp})`);
  }

  console.log(`\n---- ${themeKey} — greyscale separation (relative luminance x100) ----`);
  console.log('  ' + all.map(([k, v]) => `${k}=${(lum(v.$value) * 100).toFixed(0)}`).join('  '));
}

console.log(`\n================ RESULT: ${failures} WCAG pair(s) below target ================`);
process.exitCode = failures ? 1 : 0;
