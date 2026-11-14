"""Music and sound for the second promo ("From link to Gallery"), synthesised from nothing.

A brighter track than the first: 120 BPM in F major (F - C - Dm - Bb), plucked arpeggios, a bouncy octave bass, snaps
and a shaker, with a half-time break for "No account, no ads, no server" and a bell-like ending on the logo.
Sound effects reuse the first promo's synth voices (synth.py) plus a few new ones: morphs, zips, snips, a reel.

Usage: python3 synth_motion.py timeline.json outdir
"""
import json
import sys
import numpy as np
from scipy import signal

import synth as S
from synth import SR, secs, add, pan, noise, lp, hp, bp, sweep_filter, note_hz, mallet, env_ad, reverb_ir, convolve_st

RNG = np.random.default_rng(11)

# ------------------------------------------------------------------ voices
def pluck(f, length=0.35, bright=1.0):
    n = secs(length)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k in range(1, 12):
        if f * k > 9000:
            break
        x += np.sin(2 * np.pi * f * k * t + k) / k * np.exp(-t * (6 + k * 5 / bright))
    return x * np.clip(t / 0.002, 0, 1) * 0.5


def snap():
    n = secs(0.18)
    t = np.arange(n) / SR
    x = bp(noise(n), 1800, 7000) * np.exp(-t * 55)
    body = np.sin(2 * np.pi * 1900 * t) * np.exp(-t * 120) * 0.3
    return (x + body) * 0.5


def kick_tight():
    n = secs(0.3)
    t = np.arange(n) / SR
    f = 50 + 130 * np.exp(-t * 35)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 11)
    click = lp(noise(n), 5000) * np.exp(-t * 400) * 0.3
    return np.tanh((body + click) * 1.7) * 0.85


def bass_bounce(f, length=0.18):
    n = secs(length + 0.04)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 20) + 0.15 * S.saw(f, n)
    x = lp(x, 900)
    e = np.clip(t / 0.003, 0, 1) * np.exp(-t * 6) * np.clip((length + 0.04 - t) / 0.04, 0, 1)
    return np.tanh(x * 1.6) * e * 0.6


def bell(f, length=2.5, amp=1.0):
    n = secs(length)
    t = np.arange(n) / SR
    x = (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 3)
         + 0.25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 6))
    return x * env_ad(n, 0.002, length * 0.5, 3.5) * amp * 0.4


CH = {
    'F': ['F3', 'A3', 'C4', 'E4'], 'C': ['C3', 'E3', 'G3', 'D4'],
    'Dm': ['D3', 'F3', 'A3', 'C4'], 'Bb': ['Bb2', 'D3', 'F3', 'A3'],
}
ROOT = {'F': 'F2', 'C': 'C2', 'Dm': 'D2', 'Bb': 'Bb1'}
PROG = ['F', 'C', 'Dm', 'Bb']
ARP = [0, 1, 2, 3, 2, 1, 2, 3]  # chord-tone order for 16ths, an octave up
BASS = [(0, 0), (0.5, 12), (1.5, 0), (2.0, 12), (2.5, 7), (3.5, 12)]


def build_music(total):
    beat = 0.5
    bar = 2.0
    n = secs(total + 3)
    drums = np.zeros((n, 2)); bass = np.zeros((n, 2)); keys = np.zeros((n, 2)); pads = np.zeros((n, 2)); fx = np.zeros((n, 2))
    duck = np.ones(n)

    def kick_at(t, g=1.0, big=False):
        add(drums, (S.kick(True) if big else kick_tight()) * g, t)
        i = secs(t); L = secs(0.2)
        if i < n:
            j = min(n, i + L)
            duck[i:j] = np.minimum(duck[i:j], (1 - 0.55 * g * np.exp(-np.arange(L) / secs(0.05)))[:j - i])

    nbars = int(np.ceil(total / bar))
    for b in range(1, nbars + 1):
        t0 = (b - 1) * bar
        ch = PROG[(b - 1) % 4]
        fr = [note_hz(x) for x in CH[ch]]
        root = note_hz(ROOT[ch])
        groove = 3 <= b <= 12
        intro = b <= 2
        brk = b in (13, 14)
        end = b >= 15

        if intro or groove:
            # Plucked arpeggio: sparse in the intro, 16ths in the groove.
            step = beat / 2
            for s in range(16):
                if intro and s % 2:
                    continue
                f = fr[ARP[s % 8]] * 2
                g = 0.33 if s % 4 == 0 else 0.22
                add(keys, pan(pluck(f, 0.3, 1.0 if groove else 0.7), 0.35 if s % 2 else -0.35) * g, t0 + s * step)
            for k in (1, 3):
                add(drums, pan(snap(), 0.15), t0 + k * beat)
        if intro:
            p = S.pad_chord(fr, 2.1, 900)
            add(pads, pan(p, 0) * 0.3, t0)
            if b == 2:
                for s in range(4):
                    add(drums, pan(S.hat() * (0.5 + 0.15 * s), 0.2), t0 + 3 * beat + s * beat / 4)
        if groove:
            for k in range(4):
                kick_at(t0 + k * beat, 0.85)
                add(drums, pan(S.hat(open_=True) * 0.8, 0.2), t0 + k * beat + beat / 2)
                for s in (0.25, 0.75):
                    add(drums, pan(S.shaker() * 1.2, -0.3), t0 + k * beat + s * beat)
            for off, semi in BASS:
                add(bass, bass_bounce(root * 2 ** (semi / 12)), t0 + off * beat)
            p = S.pad_chord(fr, 2.05, 1600)
            add(pads, pan(p, -0.3) * 0.18 + pan(p, 0.3) * 0.18, t0)
            if b in (6, 10):  # a little lift every other phrase
                for s in range(4):
                    add(drums, pan(snap() * (0.4 + 0.15 * s), -0.1), t0 + 3 * beat + s * beat / 4)
        if brk:
            # Half time: the slashes are the hits.
            p = S.pad_chord(fr, 2.05, 1100)
            add(pads, pan(p, -0.3) * 0.32 + pan(p, 0.3) * 0.32, t0)
            kick_at(t0, 0.8)
            add(drums, pan(snap() * 1.2, 0), t0 + 2 * beat)
            add(bass, bass_bounce(root, 0.8) * 0.8, t0)
            for s in range(0, 8, 2):
                add(keys, pan(pluck(fr[ARP[s]] * 2, 0.5, 0.6), 0.2) * 0.18, t0 + s * beat / 2)
            if b == 14:
                for s in range(8):
                    add(drums, pan(S.hat() * (0.35 + 0.08 * s), 0.2), t0 + 2 * beat + s * beat / 4)
        if end:
            if b == 15:
                fr = [note_hz(x) for x in CH['F']]
                kick_at(t0 + beat, 1.0, big=True)  # 28.5: the pick lands
                add(bass, bass_bounce(note_hz('F1'), 1.8) * 1.0, t0 + beat)
                p = S.pad_chord(fr + [note_hz('G4')], 5.5, 2000)
                add(pads, pan(p, -0.3) * 0.38 + pan(p, 0.3) * 0.38, t0 + beat)
                for i, nt in enumerate(['C6', 'A5', 'F5', 'C5', 'A4']):
                    add(keys, pan(bell(note_hz(nt), 2.6, 0.9), 0.4 - i * 0.2), t0 + 2 * beat + i * beat / 2)
            if b == 16:
                for i, nt in enumerate(['F5', 'G5', 'A5']):
                    add(keys, pan(bell(note_hz(nt), 3.0, 0.6), -0.2 + i * 0.2), t0 + i * beat)

    # Lifts into the drop (4 s) and into the end (28 s)
    for t_end, dur, g in ((4.0, 1.0, 0.22), (28.0, 1.0, 0.25)):
        m = secs(dur)
        r = sweep_filter(noise(m), 400, 7000, 'band', q=2, curve=lambda p: p ** 1.5) * np.linspace(0, 1, m) ** 2
        add(fx, pan(r, 0) * g, t_end - dur)

    pads *= duck[:, None]; bass *= duck[:, None]
    keys_wet = convolve_st(keys, reverb_ir(1.6, 6000, 21))
    pads_wet = convolve_st(pads, reverb_ir(2.4, 3500, 23))
    mix = drums * 0.9 + bass * 0.8 + pads * 0.6 + pads_wet * 0.25 + keys * 0.6 + keys_wet * 0.3 + fx
    return mix[:secs(total)]


# ------------------------------------------------------------------ new effects
def mixl(*xs):
    """Sum stereo or mono sounds of different lengths."""
    xs = [x if x.ndim == 2 else pan(x, 0) for x in xs]
    n = max(len(x) for x in xs)
    out = np.zeros((n, 2))
    for x in xs:
        out[:len(x)] += x
    return out


def glide(f0, f1, dur, amp=0.5, decay=8):
    n = secs(dur)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * decay) * np.clip(t / 0.003, 0, 1) * amp


def fx_morph():
    return mixl(glide(300, 900, 0.22, 0.45, 9), bp(noise(secs(0.22)), 1500, 5000) * np.exp(-np.arange(secs(0.22)) / SR * 20) * 0.15)


def fx_slam():
    n = secs(0.25)
    t = np.arange(n) / SR
    return mixl(S.fx_thunk(), bp(noise(n), 600, 3000) * np.exp(-t * 30) * 0.4)


def fx_zip(dur=0.3):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    y = sweep_filter(noise(n), 1200, 7000, 'band', q=3)
    return pan(y * np.sin(np.pi * p) * 0.5, np.linspace(-0.3, 0.3, n))


def fx_squash():
    return pan(glide(260, 120, 0.25, 0.5, 10), 0)


def fx_launch():
    return mixl(S.fx_whoosh(0.4) * 0.9, glide(200, 1400, 0.3, 0.35, 4))


def fx_fall(dur=0.2):
    return mixl(glide(1800, 300, dur, 0.25, 2), S.fx_whoosh(dur, down=True) * 0.6)


def fx_impact():
    big = S.fx_thunk(True)
    n = len(big)
    t = np.arange(n) / SR
    boom = np.sin(2 * np.pi * 38 * t) * np.exp(-t * 3) * 0.6
    crash = hp(noise(n), 4000) * np.exp(-t * 5) * 0.12
    return mixl(big, boom + crash)


def fx_note(nt):
    return mixl(mallet(note_hz(nt), 0.5, 0.5), S.fx_pop() * 0.4)


def fx_suck():
    w = S.fx_whoosh(0.3, down=False)
    return w[::-1] * 0.8


def fx_stop():
    return mixl(S.fx_tick(0.7), glide(180, 90, 0.13, 0.5, 18))


def fx_bloom():
    n = secs(0.9)
    t = np.arange(n) / SR
    x = sum(np.sin(2 * np.pi * f * t + i) for i, f in enumerate([note_hz('F4'), note_hz('C5'), note_hz('A5')]))
    x *= env_ad(n, 0.02, 0.5, 4) * 0.2
    st = pan(x, 0)
    return st + convolve_st(st, reverb_ir(1.2, 5000, 31)) * 0.4


def fx_dive(dur=0.5):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    y = sweep_filter(noise(n), 200, 9000, 'band', q=1.5, curve=lambda q: q ** 2)
    tone = glide(120, 1600, dur, 0.25, 0)
    return pan((y * 0.8 + tone) * p ** 2, 0)


def fx_doors():
    return mixl(S.fx_whoosh(0.35) * 0.8, glide(90, 60, 0.2, 0.5, 12))


def fx_snip():
    n = secs(0.09)
    t = np.arange(n) / SR
    x = hp(noise(n), 3500) * np.exp(-t * 90) + np.sin(2 * np.pi * 3200 * t) * np.exp(-t * 150) * 0.4
    out = np.zeros(secs(0.12))
    out[:n] += x
    out[secs(0.025):secs(0.025) + n] += x * 0.7
    return pan(out * 0.6, 0.15)


def fx_lift():
    return mixl(glide(400, 1200, 0.35, 0.4, 5), S.fx_swish() * 0.4)


def fx_riffle():
    out = np.zeros((secs(0.45), 2))
    for i in range(5):
        add(out, pan(bp(noise(secs(0.05)), 1500, 6000) * np.exp(-np.arange(secs(0.05)) / SR * 70) * 0.5, -0.4 + i * 0.2), i * 0.07)
    return out


def fx_flood():
    return mixl(glide(200, 700, 0.4, 0.35, 3), S.fx_swish() * 0.5)


def fx_slash():
    return mixl(S.fx_stamp(), S.fx_whoosh(0.18) * 0.6)


def fx_loop(dur=1.5):
    n = secs(dur)
    t = np.arange(n) / SR
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * t / (dur / 1.35) * 1.0)
    y = bp(noise(n), 800, 3000) * lfo * 0.25
    return pan(y * np.sin(np.pi * t / dur), np.sin(2 * np.pi * t / (dur / 1.35)) * 0.6)


def build_sfx(tl, total):
    n = secs(total + 3)
    buf = np.zeros((n, 2))
    pent = ['C5', 'D5', 'F5', 'G5', 'A5', 'C6']
    for c in tl['sfx']:
        t, g, k = c['t'], c.get('gain', 0.5), c['type']
        d = c.get('dur', 0.3)
        x = None
        if k == 'typing':
            n_ch = c['chars']
            for i in range(n_ch):
                add(buf, pan(S.fx_tick(0.55 + 0.12 * RNG.random()), 0.15 * RNG.standard_normal()) * g * (0.7 + 0.3 * RNG.random()), t + i * (c['t1'] - t) / n_ch)
            continue
        if k == 'reel':
            m = int((c['t1'] - t) / 0.03)
            for i in range(m):
                add(buf, pan(S.fx_tick(0.9 + 0.1 * RNG.random()), 0.2) * g * 0.6, t + i * 0.03)
            continue
        if k == 'ripplepops':
            # The twelve tiles of the grid, rippling out from the middle (matches tilePop in scenes.js).
            pops = sorted(8.15 + (abs(i // 3 - 1.5) + abs(i % 3 - 1)) * 0.11 for i in range(12) if i != 4)
            for i, pt in enumerate(pops):
                add(buf, pan(np.pad(S.fx_pop(), (0, 0)), (-1) ** i * 0.3) * g * 0.6, pt)
                add(buf, pan(mallet(note_hz(pent[i % 6]), 0.3, 0.25), (-1) ** i * 0.3) * g, pt)
            continue
        if k == 'scatter':
            for i in range(4):
                add(buf, S.fx_swipe() * g * 0.7, t + i * 0.06)
            continue
        x = {
            'slam': fx_slam, 'morph': fx_morph, 'zip': lambda: fx_zip(d), 'squash': fx_squash, 'launch': fx_launch,
            'fall': lambda: fx_fall(c.get('dur', 0.2)), 'impact': fx_impact, 'suck': fx_suck, 'stop': fx_stop,
            'bloom': fx_bloom, 'dive': lambda: fx_dive(d), 'doors': fx_doors, 'snip': fx_snip, 'lift': fx_lift,
            'riffle': fx_riffle, 'flood': fx_flood, 'slash': fx_slash, 'loop': lambda: fx_loop(d),
            'stretch': lambda: mixl(S.fx_swish() * 0.8, glide(300, 600, 0.2, 0.3, 6)),
            'note': lambda: fx_note(c['note']),
            'thunk': lambda: S.fx_thunk(c.get('big', False)), 'pop': lambda: pan(S.fx_pop(), 0),
            'tick': lambda: pan(S.fx_tick(), 0), 'tap': lambda: pan(S.fx_tap(), 0), 'check': lambda: pan(S.fx_check(), 0.1),
            'chime': S.fx_chime, 'stamp': S.fx_stamp, 'whoosh': lambda: S.fx_whoosh(d, c.get('down', False)),
            'swish': lambda: S.fx_swish(c.get('down', False)), 'shimmer': lambda: S.fx_shimmer(d), 'slide': lambda: S.fx_slide(d),
        }[k]()
        if x.ndim == 1:
            x = pan(x, 0)
        add(buf, x * g, t)
    return buf[:secs(total)]


def main():
    tl = json.load(open(sys.argv[1]))
    out = sys.argv[2]
    total = tl['DURATION']
    music = build_music(total + 3)
    sfx = build_sfx(tl, total + 3)
    mix = S.master(music[:secs(total)] * 0.5 + sfx[:secs(total)] * 1.0, 2.0)
    S.write_wav(f'{out}/motion-mix.wav', mix)
    segs = tl['teaser']['segments']
    S.write_wav(f'{out}/motion-teaser.wav', S.master(S.cut(music, segs) * 0.5 + S.cut(sfx, segs) * 1.0, 1.0))
    print('ok', len(mix) / SR)


if __name__ == '__main__':
    main()
