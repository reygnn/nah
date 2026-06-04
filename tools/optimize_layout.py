#!/usr/bin/env python3
"""
Travel-optimizer for the `nah` de-CH single-finger keyboard.

Minimises total finger travel: sum over bigrams of frequency *
(vertically-weighted) Euclidean key distance, including the space key at word
boundaries and the Shift key for capitalised words. (Backspace is deliberately
NOT modelled — which letters precede a correction is layout-dependent and we
have no error data.)

Supports PINNING letters to fixed cells: e.g. grouping ä/ö/ü on the right outer
column is a learnability win (predictable place) at a tiny travel cost, since
the umlauts are rare. The script reports that trade-off.

Corpus: vuot's frequency-weighted de-CH word list. Run: python3 tools/optimize_layout.py
"""
import re, math, random

CORPUS = (
    "/home/user/AndroidStudio_Projects/nah/app/src/main/java/"
    "com/github/reygnn/nah/data/suggestions/GermanWordList.kt"
)
LETTERS = list("abcdefghijklmnopqrstuvwxyzäöü")  # 29
SPACE, SHIFT = " ", "^"
SYM = set(LETTERS) | {SPACE, SHIFT}

VWEIGHT = 1.1        # vertical distance penalty (1.0 = isotropic)
CAP_WEIGHT = 1.0     # weight of the Shift->initial path per capitalised word
N_SEEDS = 16
ITERS = 120_000
SHAPE = [8, 8, 8, 5]

# Group ä/ö/ü on the right outer column (col 7 of the 8-wide rows = x 3.5).
PIN_UMLAUTS = {"ä": (3.5, 0.0), "ö": (3.5, 1.0), "ü": (3.5, 2.0)}

# Current committed nah layout, row-major over SHAPE.
CURRENT_ROWS = "qäcobfüj" + "pvhurakö" + "xzsiegly" + "wtndm"


def load_words():
    src = open(CORPUS, encoding="utf-8").read()
    return [(w, int(f)) for w, f in re.findall(r'"([^"]+)"\s+to\s+(\d+)', src)]


def build_bigrams(words):
    bg = {}

    def add(a, b, f):
        if a in SYM and b in SYM:
            bg[(a, b)] = bg.get((a, b), 0) + f

    for orig, f in words:
        w = orig.lower()
        for a, b in zip(w, w[1:]):
            add(a, b, f)
        if w:
            add(w[-1], SPACE, f)
            add(SPACE, w[0], f)
            if orig[0].isupper():
                add(SHIFT, w[0], int(f * CAP_WEIGHT))
    return bg


def make_slots(row_lengths):
    slots = []
    for r, L in enumerate(row_lengths):
        for c in range(L):
            slots.append((c - (L - 1) / 2.0, float(r)))
    bottom = len(row_lengths) - 1
    space_pos = (0.0, float(len(row_lengths)))
    L_bottom = row_lengths[-1]
    shift_pos = (-(L_bottom / 2.0 + 0.75), float(bottom))
    return slots, space_pos, shift_pos


def travel(pos, bg, fixed):
    p = dict(pos); p.update(fixed)
    s, total = 0.0, 0
    for (a, b), c in bg.items():
        (x1, y1), (x2, y2) = p[a], p[b]
        s += c * math.hypot(x1 - x2, VWEIGHT * (y1 - y2))
        total += c
    return s / total


def anneal(slots, letters, bg, fixed, seed, iters=ITERS, T0=2.5):
    rng = random.Random(seed)
    pos = dict(zip(letters, rng.sample(slots, len(letters))))
    cur = travel(pos, bg, fixed)
    best, bestpos = cur, dict(pos)
    for it in range(iters):
        T = T0 * (1 - it / iters)
        a, b = rng.sample(letters, 2)
        pos[a], pos[b] = pos[b], pos[a]
        new = travel(pos, bg, fixed)
        if new < cur or rng.random() < math.exp((cur - new) / max(T, 1e-9)):
            cur = new
            if new < best:
                best, bestpos = new, dict(pos)
        else:
            pos[a], pos[b] = pos[b], pos[a]
    return best, bestpos


def best_of_seeds(row_lengths, bg, pinned=None):
    pinned = pinned or {}
    slots, space_pos, shift_pos = make_slots(row_lengths)
    pinned_slots = set(pinned.values())
    free_slots = [s for s in slots if s not in pinned_slots]
    free_letters = [l for l in LETTERS if l not in pinned]
    fixed = {SPACE: space_pos, SHIFT: shift_pos}
    fixed.update(pinned)
    best, bestpos = None, None
    for seed in range(N_SEEDS):
        c, p = anneal(free_slots, free_letters, bg, fixed, seed)
        if best is None or c < best:
            best, bestpos = c, p
    full = dict(bestpos); full.update(pinned)
    return best, full


def render(pos, row_lengths):
    out = []
    for r, L in enumerate(row_lengths):
        cells = sorted((p[0], ch) for ch, p in pos.items() if int(round(p[1])) == r)
        out.append("  ".join(ch for _, ch in cells))
    return "\n".join(out)


def current_pos():
    slots, _, _ = make_slots(SHAPE)
    return dict(zip(CURRENT_ROWS, slots))


def qwertz_pos():
    rows = [("qwertzuiopü", 0.0, 0.0), ("asdfghjklöä", 1.0, 0.25), ("yxcvbnm", 2.0, 0.75)]
    pos = {}
    for chars, y, xoff in rows:
        for i, ch in enumerate(chars):
            pos[ch] = (xoff + i, y)
    return pos


def main():
    words = load_words()
    bg = build_bigrams(words)
    _, space_pos, shift_pos = make_slots(SHAPE)
    fixed = {SPACE: space_pos, SHIFT: shift_pos}

    q = travel(qwertz_pos(), bg, {SPACE: (5.0, 3.0), SHIFT: (-1.0, 2.0)})
    cur = travel(current_pos(), bg, fixed)
    free_best, _ = best_of_seeds(SHAPE, bg)
    pin_best, pin_pos = best_of_seeds(SHAPE, bg, PIN_UMLAUTS)

    print(f"corpus: {len(words)} words, {len(bg)} transitions  (VWEIGHT={VWEIGHT})\n")
    print(f"QWERTZ-CH (Ref):            {q:.4f}")
    print(f"nah AKTUELL:                {cur:.4f}  ({100*cur/q:.0f}% QWERTZ)")
    print(f"frei optimiert (best):      {free_best:.4f}  ({100*free_best/q:.0f}% QWERTZ)")
    print(f"äöü rechts gepinnt (best):  {pin_best:.4f}  ({100*pin_best/q:.0f}% QWERTZ)")
    print(f"  → Kosten des Gruppierens ggü. frei: {100*(pin_best/free_best-1):.1f}%")
    print(f"  → ggü. aktuellem Layout:            {100*(pin_best/cur-1):+.1f}%\n")

    # Minimal-invasive Variante: nur ä/ü an die rechte Spalte, die verdrängten
    # (seltenen) j/y nach innen; ö liegt schon rechts. Sonst alles wie aktuell.
    minswap_rows = "qjcobfyä" + "pvhurakö" + "xzsieglü" + "wtndm"
    slots, _, _ = make_slots(SHAPE)
    minswap_pos = dict(zip(minswap_rows, slots))
    ms = travel(minswap_pos, bg, fixed)

    print("VOLL re-optimiert, äöü rechts gepinnt:")
    print(render(pin_pos, SHAPE))
    cur_p = current_pos()
    same_pin = sum(1 for ch in LETTERS if cur_p[ch] == pin_pos[ch])
    print(f"  Reise {pin_best:.4f} ({100*(pin_best/cur-1):+.1f}% ggü. aktuell), "
          f"{29 - same_pin}/29 Buchstaben wandern.\n")

    print("MINIMAL: nur Umlaute rechts gestapelt, Rest wie aktuell:")
    print(render(minswap_pos, SHAPE))
    same_ms = sum(1 for ch in LETTERS if cur_p[ch] == minswap_pos[ch])
    print(f"  Reise {ms:.4f} ({100*(ms/cur-1):+.1f}% ggü. aktuell), "
          f"{29 - same_ms}/29 Buchstaben wandern.")


if __name__ == "__main__":
    main()
