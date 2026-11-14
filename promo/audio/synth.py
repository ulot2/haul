"""The promo's music and sound effects, synthesised from nothing (no samples, no loops).

Reads the timeline (src/timeline.js, dumped to JSON by tools/render.cjs) and writes 48 kHz stereo WAVs:
  music.wav, sfx.wav, mix.wav        the main cut (42 s)
  teaser.wav                         the teaser, cut from the same stems on beats

The track: 120 BPM, D minor, Dm - Bb - F - C, two bars each. Warm minimal house: a round kick, a clap, soft hats,
an off-beat sub bass, a filtered pad, syncopated chord stabs and a marimba-like motif.

Usage: python3 synth.py timeline.json outdir
"""
import json
import sys
import numpy as np
from scipy import signal

SR = 48000
RNG = np.random.default_rng(7)


# ------------------------------------------------------------------ basics
def secs(n):
    return int(round(n * SR))


def env_ad(n, a, d, curve=4.0):
    """Attack (linear) then exponential-ish decay over n samples."""
    t = np.arange(n) / SR
    att = np.clip(t / max(a, 1e-4), 0, 1)
    dec = np.exp(-curve * np.clip(t - a, 0, None) / max(d, 1e-4))
    return att * dec


def note_hz(name):
    names = {'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'D#': 3, 'Eb': 3, 'E': 4, 'F': 5, 'F#': 6, 'Gb': 6, 'G': 7,
             'G#': 8, 'Ab': 8, 'A': 9, 'A#': 10, 'Bb': 10, 'B': 11}
    p, o = name[:-1], int(name[-1])
    midi = 12 * (o + 1) + names[p]
    return 440.0 * 2 ** ((midi - 69) / 12)


def lp(x, hz, order=2):
    sos = signal.butter(order, min(hz, SR * 0.45), 'low', fs=SR, output='sos')
    return signal.sosfilt(sos, x, axis=0)


def hp(x, hz, order=2):
    sos = signal.butter(order, hz, 'high', fs=SR, output='sos')
    return signal.sosfilt(sos, x, axis=0)


def bp(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, min(hi, SR * 0.45)], 'band', fs=SR, output='sos')
    return signal.sosfilt(sos, x, axis=0)


def sweep_filter(x, f0, f1, kind='band', q=1.2, block=256, curve=None):
    """A filter whose centre moves from f0 to f1 (log scale), processed in blocks with carried state."""
    n = len(x)
    out = np.zeros_like(x)
    nb = (n + block - 1) // block
    zi = None
    for b in range(nb):
        p = b / max(nb - 1, 1)
        if curve is not None:
            p = curve(p)
        fc = f0 * (f1 / f0) ** p
        if kind == 'band':
            lo, hi = fc / (1 + 0.5 / q), fc * (1 + 0.5 / q)
            sos = signal.butter(2, [max(lo, 20), min(hi, SR * 0.45)], 'band', fs=SR, output='sos')
        else:
            sos = signal.butter(2, min(fc, SR * 0.45), kind, fs=SR, output='sos')
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        seg = x[b * block:(b + 1) * block]
        y, zi = signal.sosfilt(sos, seg, zi=zi)
        out[b * block:(b + 1) * block] = y
    return out


def noise(n):
    return RNG.standard_normal(n)


def pan(mono, p):
    """p from -1 (left) to 1 (right), equal power. p may be an array."""
    a = (np.asarray(p) + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], axis=1)


def add(buf, x, t):
    """Mix x (mono or stereo) into buf at time t (seconds)."""
    i = secs(t)
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    if i < 0:
        x = x[-i:]
        i = 0
    j = min(len(buf), i + len(x))
    if j > i:
        buf[i:j] += x[:j - i]


def reverb_ir(seconds=2.2, bright=6000, seed=3):
    r = np.random.default_rng(seed)
    n = secs(seconds)
    t = np.arange(n) / SR
    e = np.exp(-6.9 * t / seconds)
    ir = np.stack([r.standard_normal(n) * e, r.standard_normal(n) * e], axis=1)
    ir = lp(ir, bright)
    ir[:secs(0.012)] *= np.linspace(0, 1, secs(0.012))[:, None]
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def convolve_st(x, ir):
    out = np.zeros((len(x) + len(ir) - 1, 2))
    for c in range(2):
        out[:, c] = signal.fftconvolve(x[:, c], ir[:, c])
    return out[:len(x)]


# ------------------------------------------------------------------ drums
def kick(big=False):
    n = secs(0.6 if big else 0.42)
    t = np.arange(n) / SR
    f = 46 + 110 * np.exp(-t * 28) + (30 * np.exp(-t * 6) if big else 0)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t * (4.5 if big else 7.5))
    click = lp(noise(n), 4000) * np.exp(-t * 300) * 0.25
    return np.tanh((body + click) * 1.6) * 0.9


def clap():
    n = secs(0.35)
    t = np.arange(n) / SR
    x = bp(noise(n), 900, 2600)
    e = np.zeros(n)
    for k, d in enumerate([0, 0.009, 0.019, 0.03]):
        e += (t >= d) * np.exp(-np.clip(t - d, 0, None) * (90 if k < 3 else 16))
    return x * e * 0.55


def hat(open_=False):
    n = secs(0.25 if open_ else 0.06)
    t = np.arange(n) / SR
    x = hp(noise(n), 7000, 4)
    return x * np.exp(-t * (14 if open_ else 70)) * (0.22 if open_ else 0.16)


def shaker():
    n = secs(0.07)
    t = np.arange(n) / SR
    x = bp(noise(n), 5000, 11000)
    return x * np.sin(np.pi * np.clip(t / 0.07, 0, 1)) ** 2 * 0.09


# ------------------------------------------------------------------ tonal voices
def saw(f, n, detune=0.0):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for d in ([0] if detune == 0 else [-detune, 0, detune]):
        ph = (f * (1 + d) * t + RNG.random()) % 1.0
        out += 2 * ph - 1
    return out / (1 if detune == 0 else 3)


def pad_chord(freqs, dur, cutoff=1400):
    n = secs(dur)
    x = sum(saw(f, n, 0.006) for f in freqs) / len(freqs)
    x = lp(x, cutoff, 2)
    a = np.clip(np.arange(n) / secs(0.35), 0, 1)
    r = np.clip((n - np.arange(n)) / secs(0.4), 0, 1)
    return x * a * r


def stab(freqs, length=0.22, bright=2600):
    n = secs(length + 0.3)
    t = np.arange(n) / SR
    x = sum(saw(f, n, 0.004) for f in freqs) / len(freqs)
    y = sweep_filter(x, bright, 320, kind='low', block=128)
    return y * env_ad(n, 0.004, length, 3.5)


def bass_note(f, length=0.2):
    n = secs(length + 0.05)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) + 0.12 * saw(f, n)
    x = lp(x, 700)
    e = np.clip(t / 0.004, 0, 1) * np.clip((length + 0.05 - t) / 0.05, 0, 1)
    return np.tanh(x * 1.4) * e * 0.55


def mallet(f, length=0.9, amp=1.0):
    """Marimba-like: a sine with a quick FM bloom and a soft wooden overtone."""
    n = secs(length)
    t = np.arange(n) / SR
    idx = 2.2 * np.exp(-t * 30)
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * 4 * t))
    x += 0.25 * np.sin(2 * np.pi * f * 3.98 * t) * np.exp(-t * 18)
    return x * env_ad(n, 0.002, length * 0.55, 4.5) * amp


# ------------------------------------------------------------------ the track
CHORDS = {
    'Dm': ['D3', 'F3', 'A3', 'C4', 'E4'],
    'Bb': ['Bb2', 'D3', 'F3', 'A3', 'C4'],
    'F': ['F2', 'A2', 'C3', 'E3', 'G3'],
    'C': ['C3', 'E3', 'G3', 'Bb3', 'D4'],
}
ROOT = {'Dm': 'D2', 'Bb': 'Bb1', 'F': 'F2', 'C': 'C2'}
# Bar -> chord (1-based bars). Hook and build on Dm; the groove cycles Dm Bb F C two bars each.
def chord_of(bar):
    if bar <= 2:
        return 'Dm'
    if bar in (17,):
        return 'Bb'
    if bar in (18,):
        return 'C'
    if bar >= 19:
        return 'Dm'
    return ['Dm', 'Bb', 'F', 'C'][((bar - 3) // 2) % 4]


MOTIF = [  # (beat offset in a 2-bar phrase, note, length in beats)
    (0.0, 'A4', 0.5), (0.75, 'C5', 0.5), (1.5, 'A4', 0.5), (2.0, 'G4', 0.5), (3.0, 'F4', 0.5), (3.5, 'D4', 1.0),
    (4.5, 'F4', 0.5), (5.0, 'G4', 0.5), (5.75, 'A4', 0.5), (6.5, 'C5', 0.5), (7.0, 'D5', 1.0),
]


def build_music(tl, total):
    beat = 60.0 / tl['BPM']
    bar_len = beat * 4
    n = secs(total + 3)
    drums = np.zeros((n, 2))
    bass = np.zeros((n, 2))
    pads = np.zeros((n, 2))
    keys = np.zeros((n, 2))
    fx = np.zeros((n, 2))
    duck = np.ones(n)  # sidechain from the kick

    def kick_at(t, gain=1.0, big=False):
        k = kick(big) * gain
        add(drums, k, t)
        i = secs(t)
        L = secs(0.22)
        if i < n:
            shape = 1 - 0.6 * np.exp(-np.arange(L) / secs(0.06)) * gain
            j = min(n, i + L)
            duck[i:j] = np.minimum(duck[i:j], shape[:j - i])

    nbars = int(np.ceil(total / bar_len))
    for bar in range(1, nbars + 1):
        t0 = (bar - 1) * bar_len
        ch = chord_of(bar)
        freqs = [note_hz(x) for x in CHORDS[ch]]
        root = note_hz(ROOT[ch])

        if bar == 1:
            # Hook: a dark filtered pad under the scroll; the hit on the pick (beat 3).
            p = pad_chord(freqs[:4], 2.2, 500)
            add(pads, pan(p, -0.2) * 0.35 + pan(p, 0.2) * 0.35, t0)
            kick_at(t0 + 2 * beat, 1.0, big=True)
            add(keys, pan(stab(freqs, 0.5, 3200), 0.0) * 0.5, t0 + 2 * beat)
            add(bass, bass_note(root, 0.9) * 0.9, t0 + 2 * beat)
            kick_at(t0 + 3.5 * beat, 0.5)
        elif bar == 2:
            # Build: hats in, the pad opens, a riser into the drop.
            p = sweep_filter(pad_chord(freqs[:4], 2.1, 8000), 500, 2200, kind='low')
            add(pads, pan(p, 0) * 0.45, t0)
            kick_at(t0, 0.8)
            for e in range(8):
                add(drums, pan(hat(), 0.25), t0 + e * beat / 2)
            for s in range(8):
                add(drums, pan(hat() * (0.3 + s * 0.09), -0.2), t0 + 2 * beat + s * beat / 4)
            add(keys, pan(stab(freqs, 0.16), 0.15) * 0.35, t0 + 1.5 * beat)
            add(keys, pan(stab(freqs, 0.16), -0.15) * 0.35, t0 + 3.0 * beat)
        elif 3 <= bar <= 16:
            for b in range(4):
                kick_at(t0 + b * beat, 0.78)
                add(drums, pan(hat(open_=True), 0.18), t0 + b * beat + beat / 2)
                add(bass, bass_note(root, 0.2), t0 + b * beat + beat / 2)
                if b in (1, 3):
                    add(drums, pan(clap(), 0.0), t0 + b * beat)
                for s in (0.25, 0.75):
                    add(drums, pan(shaker(), -0.3), t0 + b * beat + s * beat)
            # Ghost bass note into the next bar
            add(bass, bass_note(root * 2 ** (7 / 12), 0.12) * 0.6, t0 + 3.75 * beat)
            if bar % 2 == 1:
                p = pad_chord(freqs[:4], bar_len * 2, 1200)
                add(pads, pan(p, -0.35) * 0.28 + pan(p, 0.35) * 0.28, t0)
            # Syncopated stabs
            for sb in (0.0, 1.5, 3.0) if bar % 2 else (0.75, 2.5):
                add(keys, pan(stab(freqs, 0.14), 0.25 if sb % 1 else -0.25) * 0.32, t0 + sb * beat)
            # The motif from bar 5 on, in two-bar phrases
            if bar >= 5 and bar % 2 == 1:
                for off, nt, ln in MOTIF:
                    add(keys, pan(mallet(note_hz(nt), ln * beat * 2.4, 0.33), 0.3), t0 + off * beat)
            if bar == 16:
                # Fill: claps rolling into the break
                for s in range(4):
                    add(drums, pan(clap() * (0.4 + 0.15 * s), 0.1), t0 + 3 * beat + s * beat / 4)
        elif bar in (17, 18):
            # Half-time break: the stamps carry it. Low hits on the beats, a big pad.
            p = pad_chord(freqs[:4], bar_len, 900)
            add(pads, pan(p, -0.3) * 0.38 + pan(p, 0.3) * 0.38, t0)
            if bar == 17:
                for b in (1, 2, 3):  # 32.5, 33.0, 33.5 with the stamps
                    kick_at(t0 + b * beat, 0.9, big=True)
                    add(bass, bass_note(root, 0.4) * 0.8, t0 + b * beat)
            else:
                kick_at(t0, 0.7)
                add(drums, pan(clap() * 0.7, 0), t0 + 2 * beat)
                for s in range(8):
                    add(drums, pan(hat() * (0.4 + s * 0.08), 0.2), t0 + 2 * beat + s * beat / 4)
        elif bar == 19:
            # The logo: a lift on the downbeat, the big hit on beat 2 with the thunk.
            kick_at(t0 + beat, 1.0, big=True)
            add(bass, bass_note(root, 1.6) * 0.9, t0 + beat)
            add(keys, pan(stab(freqs, 0.9, 3600), 0) * 0.5, t0 + beat)
            for b in (2, 3):
                kick_at(t0 + b * beat, 0.55)
                add(drums, pan(hat(open_=True) * 0.8, 0.2), t0 + b * beat + beat / 2)
            p = pad_chord(freqs, 6.5, 1800)
            add(pads, pan(p, -0.3) * 0.4 + pan(p, 0.3) * 0.4, t0 + beat)
            for i, nt in enumerate(['D5', 'A4', 'F4', 'D4']):
                add(keys, pan(mallet(note_hz(nt), 1.6, 0.28), 0.3 - i * 0.2), t0 + 2 * beat + i * beat / 2)
        elif bar == 20:
            # Tail: one last soft motif over the ringing chord.
            for i, nt in enumerate(['A4', 'C5', 'D5']):
                add(keys, pan(mallet(note_hz(nt), 2.5, 0.22), -0.2 + i * 0.2), t0 + i * beat)

    # Rises into the drop and into the logo
    for t_end, dur, g in ((4.0, 1.0, 0.25), (36.0, 1.0, 0.22)):
        m = secs(dur)
        r = sweep_filter(noise(m), 400, 7000, 'band', q=2, curve=lambda p: p ** 1.5) * np.linspace(0, 1, m) ** 2
        add(fx, pan(r, 0) * g, t_end - dur)

    pads *= duck[:, None]
    bass *= duck[:, None]
    keys_wet = convolve_st(keys, reverb_ir(2.0, 5500))
    pads_wet = convolve_st(pads, reverb_ir(2.6, 3500, 5))
    drums_wet = convolve_st(hp(drums, 300), reverb_ir(0.9, 6000, 9))
    mix = (drums * 0.9 + drums_wet * 0.08 + bass * 0.85 + pads * 0.6 + pads_wet * 0.25
           + keys * 0.55 + keys_wet * 0.28 + fx)
    return mix[:secs(total)]


# ------------------------------------------------------------------ sound effects
def fx_whoosh(dur=0.6, down=False):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    x = noise(n)
    f0, f1 = (6000, 300) if down else (300, 5000)
    y = sweep_filter(x, f0, f1, 'band', q=1.5)
    e = np.sin(np.pi * p) ** 1.5 * (1 - p * 0.3)
    return pan(y * e * 0.8, np.linspace(-0.6, 0.6, n) * (-1 if down else 1))


def fx_swish(down=False):
    return fx_whoosh(0.28, down) * 0.8


def fx_swipe():
    n = secs(0.16)
    y = sweep_filter(noise(n), 2500, 8000, 'band', q=1.6)
    e = np.sin(np.pi * np.linspace(0, 1, n)) ** 2
    return pan(y * e * 0.6, np.linspace(0.5, -0.5, n))


def fx_thunk(big=False):
    n = secs(0.9 if big else 0.32)
    t = np.arange(n) / SR
    f = (42 if big else 70) + (120 if big else 140) * np.exp(-t * 30)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * (5 if big else 14))
    knock = bp(noise(n), 160, 700) * np.exp(-t * 45) * 0.8
    felt = lp(noise(n), 2500) * np.exp(-t * 220) * 0.4
    x = np.tanh((body + knock + felt) * 1.5)
    st = pan(x, 0)
    if big:
        st = st + convolve_st(st, reverb_ir(1.6, 2500, 11)) * 0.18
    return st


def fx_tick(pitch=1.0):
    n = secs(0.03)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * 2300 * pitch * t) * np.exp(-t * 260)
    clk = hp(noise(n), 3000) * np.exp(-t * 900) * 0.6
    return (tone * 0.5 + clk) * 0.6


def fx_check():
    n = secs(0.09)
    t = np.arange(n) / SR
    f = 1500 + 900 * np.clip(t / 0.03, 0, 1)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(n, 0.001, 0.06, 5)
    return tone * 0.4 + np.pad(fx_tick(1.2), (0, n - secs(0.03))) * 0.6


def fx_tap():
    n = secs(0.08)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * 520 * t) * np.exp(-t * 60)
    clk = lp(noise(n), 3500) * np.exp(-t * 500)
    return (tone * 0.7 + clk * 0.5) * 0.7


def fx_pop():
    n = secs(0.12)
    t = np.arange(n) / SR
    f = 380 + 700 * np.clip(t / 0.04, 0, 1)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(n, 0.002, 0.07, 4) * 0.55


def fx_blip():
    a = mallet(note_hz('A5'), 0.25, 0.5)
    b = mallet(note_hz('D6'), 0.35, 0.5)
    out = np.zeros(secs(0.45))
    out[:len(a)] += a
    out[secs(0.07):secs(0.07) + len(b)] += b
    return out * 0.6


def fx_chime():
    out = np.zeros((secs(1.4), 2))
    add(out, pan(mallet(note_hz('D6'), 0.9, 0.5), -0.2), 0)
    add(out, pan(mallet(note_hz('A6'), 1.1, 0.45), 0.2), 0.11)
    return out + convolve_st(out, reverb_ir(1.2, 7000, 13)) * 0.2


def fx_drop():
    n = secs(0.2)
    t = np.arange(n) / SR
    f = 900 - 400 * np.clip(t / 0.1, 0, 1)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(n, 0.003, 0.12, 4)
    sw = fx_swish(down=True) * 0.5
    sw[:n] += pan(tone * 0.35, 0)
    return sw


def fx_shimmer(dur=1.4):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    x = sweep_filter(noise(n), 3000, 9000, 'band', q=3)
    t = np.arange(n) / SR
    sp = sum(np.sin(2 * np.pi * f * (1 + 0.3 * p) * t) * 0.15 for f in (1760, 2637, 3520))
    e = p ** 1.2 * np.clip((1 - p) / 0.08, 0, 1)
    return pan((x * 0.5 + sp) * e * 0.5, np.sin(t * 9) * 0.4)


def fx_stamp():
    n = secs(0.5)
    t = np.arange(n) / SR
    thud = fx_thunk()[:, 0]
    slap = bp(noise(n), 900, 3800) * np.exp(-t * 70) * 0.7
    x = np.zeros(n)
    x[:len(thud)] += thud
    x += slap
    st = pan(np.tanh(x * 1.3), 0)
    return st + convolve_st(st, reverb_ir(0.8, 4000, 17)) * 0.12


def fx_flip():
    n = secs(0.14)
    t = np.arange(n) / SR
    x = bp(noise(n), 1200, 5000) * np.exp(-t * 40)
    f = 700 + 900 * np.clip(t / 0.05, 0, 1)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(n, 0.001, 0.08, 4)
    return pan(x * 0.3 + tone * 0.4, 0.1)


def fx_brake():
    n = secs(0.4)
    t = np.arange(n) / SR
    f = 260 * np.exp(-t * 5)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6)
    y = sweep_filter(noise(n), 3000, 200, 'band', q=1.2) * np.exp(-t * 7)
    return pan(tone * 0.4 + y * 0.5, 0)


def fx_riser(dur=1.0):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    y = sweep_filter(noise(n), 300, 8000, 'band', q=2.5, curve=lambda q: q ** 1.4)
    t = np.arange(n) / SR
    f = 200 * (8 ** p)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.15
    return pan((y + tone) * p ** 2 * 0.7, 0)


def fx_slide(dur=1.0):
    n = secs(dur)
    p = np.linspace(0, 1, n)
    y = sweep_filter(noise(n), 1800, 700, 'band', q=4)
    e = np.sin(np.pi * p) * (1 - p) ** 0.5
    return pan(y * e * 0.5, np.linspace(-0.4, 0.4, n))


def ease_quart_out(x):
    return 1 - (1 - x) ** 4


def scroll_tick_times():
    """One tick per card passing in the hook's feed (matches feedScene's scroll curve)."""
    travel = 26 * 16
    ts = np.linspace(0, 1.0, 6000)
    pos = travel * ease_quart_out(ts)
    out, last = [], 0
    for t, s in zip(ts, pos):
        k = int(s // 26)
        if k > last:
            out.append(t)
            last = k
    return out


def build_sfx(tl, total):
    n = secs(total + 3)
    buf = np.zeros((n, 2))
    for c in tl['sfx']:
        t, g, kind = c['t'], c.get('gain', 0.5), c['type']
        dur = c.get('dur', 0.6)
        down = c.get('down', False)
        if kind == 'whoosh':
            x = fx_whoosh(dur, down)
        elif kind == 'swish':
            x = fx_swish(down)
        elif kind == 'swipe':
            x = fx_swipe()
        elif kind == 'thunk':
            x = fx_thunk(c.get('big', False))
        elif kind == 'tick':
            x = pan(fx_tick(1 + 0.04 * RNG.standard_normal()), 0.1 * RNG.standard_normal())
        elif kind == 'check':
            x = pan(fx_check(), 0.1)
        elif kind == 'tap':
            x = pan(fx_tap(), 0)
        elif kind == 'pop':
            x = pan(fx_pop(), 0)
        elif kind == 'blip':
            x = pan(fx_blip(), 0)
        elif kind == 'chime':
            x = fx_chime()
        elif kind == 'drop':
            x = fx_drop()
        elif kind == 'shimmer':
            x = fx_shimmer(dur)
        elif kind == 'stamp':
            x = fx_stamp()
        elif kind == 'flip':
            x = fx_flip()
        elif kind == 'brake':
            x = fx_brake()
        elif kind == 'riser':
            x = fx_riser(dur)
        elif kind == 'slide':
            x = fx_slide(dur)
        elif kind == 'click':
            x = pan(fx_tick(0.6), 0)
        elif kind == 'roll':
            for i in range(8):
                add(buf, pan(fx_tick(0.8 + i * 0.05), 0.2) * g * 0.8, t + i * dur / 8)
            continue
        elif kind == 'scrollticks':
            for i, st in enumerate(scroll_tick_times()):
                add(buf, pan(fx_tick(0.85 + 0.02 * i), (-1) ** i * 0.25) * g, t + st)
            continue
        elif kind == 'typing':
            for line_t0, line_t1, chars in ((34.0, 34.65, 26), (34.75, 35.4, 24)):
                for i in range(chars):
                    add(buf, pan(fx_tick(0.55 + 0.1 * RNG.random()), 0.15 * RNG.standard_normal()) * g * (0.7 + 0.3 * RNG.random()),
                        line_t0 + i * (line_t1 - line_t0) / chars)
            continue
        else:
            raise ValueError(kind)
        add(buf, x * g, t)
    return buf[:secs(total)]


# ------------------------------------------------------------------ mix and master
def master(x, fade_out=1.5):
    x = hp(x, 28)
    # Gentle bus compression: an RMS follower pulling peaks down.
    level = np.sqrt(lp(np.mean(x ** 2, axis=1), 8, 1).clip(1e-9))
    gain = np.minimum(1, (0.25 / level) ** 0.35)
    x = x * gain[:, None]
    n = len(x)
    f = secs(fade_out)
    x[n - f:] *= np.linspace(1, 0, f)[:, None] ** 2
    x[:secs(0.004)] *= np.linspace(0, 1, secs(0.004))[:, None]
    x = np.tanh(x * 1.1) / np.tanh(1.1)
    return x / max(1e-9, np.max(np.abs(x))) * 0.89


def write_wav(path, x):
    from scipy.io import wavfile
    wavfile.write(path, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def cut(stem, segments, xfade=0.012):
    """Concatenate pieces of a stem (seconds), with short crossfades at the cuts."""
    pieces = [stem[secs(a):secs(b)].copy() for a, b in segments]
    f = secs(xfade)
    for p in pieces:
        p[:f] *= np.linspace(0, 1, f)[:, None]
        p[-f:] *= np.linspace(1, 0, f)[:, None]
    return np.concatenate(pieces)


def main():
    tl = json.load(open(sys.argv[1]))
    out = sys.argv[2]
    total = tl['DURATION']
    music = build_music(tl, total + 4)
    sfx = build_sfx(tl, total + 4)
    write_wav(f'{out}/music.wav', music[:secs(total)] / np.max(np.abs(music)) * 0.9)
    write_wav(f'{out}/sfx.wav', sfx[:secs(total)] / np.max(np.abs(sfx)) * 0.9)
    mix = master(music[:secs(total)] * 0.5 + sfx[:secs(total)] * 1.0, 1.8)
    write_wav(f'{out}/mix.wav', mix)
    # Teaser: the same stems, cut on beats.
    segs = tl['teaser']['segments']
    tm = cut(music, segs)
    ts = cut(sfx, segs)
    write_wav(f'{out}/teaser.wav', master(tm * 0.5 + ts * 1.0, 1.0))
    print('ok', len(mix) / SR, 's; teaser', len(tm) / SR, 's')


if __name__ == '__main__':
    main()
