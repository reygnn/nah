#!/usr/bin/env python3
"""
Travel-optimizer for the `nah` de-CH single-finger keyboard.

Goal: arrange the 29 letters (a-z + ä ö ü) so that the total finger travel
for one finger is minimised on a de-CH corpus. Standard QWERTZ scatters
frequent letters on purpose (anti-typebar-jam for ten fingers) — pessimal for
one finger. We minimise sum over bigrams of frequency * Euclidean key distance,
including the cost of going to/from the space key at word boundaries.

Corpus: vuot's frequency-weighted de-CH word list (the only curated de-CH
frequency data on hand; no ß, "ss" — consistent with de-CH). Bigram rankings in
German are stable across corpora, so the arrangement is robust; it can be
regenerated with a larger corpus without changing the approach.

Output: the best arrangement per candidate shape, ready to paste into
layout/OptimizedLayout.kt. Run: python3 tools/optimize_layout.py
"""
import re, math, random

random.seed(42)

CORPUS = (
    "/home/user/AndroidStudio_Projects/vuot/app/src/main/java/"
    "com/github/reygnn/vuot/data/suggestions/GermanWordList.kt"
)
LETTERS = list("abcdefghijklmnopqrstuvwxyzäöü")  # 29


def load_words():
    src = open(CORPUS, encoding="utf-8").read()
    return [(w.lower(), int(f)) for w, f in re.findall(r'"([^"]+)"\s+to\s+(\d+)', src)]


def build_bigrams(words):
    """Weighted bigram counts incl. space transitions (' ' = space key)."""
    bg = {}

    def add(a, b, f):
        if a in SYM and b in SYM:
            bg[(a, b)] = bg.get((a, b), 0) + f

    for w, f in words:
        for a, b in zip(w, w[1:]):
            add(a, b, f)
        if w:
            add(w[-1], " ", f)   # last letter -> space
            add(" ", w[0], f)    # space -> first letter of (next) word
    return bg


SYM = set(LETTERS) | {" "}


def make_slots(row_lengths):
    """Centered grid coordinates for letter cells + a bottom-center space key."""
    slots = []
    for r, L in enumerate(row_lengths):
        for c in range(L):
            slots.append((c - (L - 1) / 2.0, float(r)))
    space_pos = (0.0, float(len(row_lengths)))  # one row below, centered
    return slots, space_pos


def travel(pos, bg, space_pos):
    pos = dict(pos)
    pos[" "] = space_pos
    total = sum(bg.values())
    s = 0.0
    for (a, b), c in bg.items():
        (x1, y1), (x2, y2) = pos[a], pos[b]
        s += c * math.hypot(x1 - x2, y1 - y2)
    return s / total


def anneal(slots, space_pos, bg, iters=400000, T0=2.5):
    pos = dict(zip(LETTERS, random.sample(slots, len(LETTERS))))
    cur = travel(pos, bg, space_pos)
    best, bestpos = cur, dict(pos)
    for it in range(iters):
        T = T0 * (1 - it / iters)
        a, b = random.sample(LETTERS, 2)
        pos[a], pos[b] = pos[b], pos[a]
        new = travel(pos, bg, space_pos)
        if new < cur or random.random() < math.exp((cur - new) / max(T, 1e-9)):
            cur = new
            if new < best:
                best, bestpos = new, dict(pos)
        else:
            pos[a], pos[b] = pos[b], pos[a]
    return best, bestpos


def render(pos, row_lengths):
    out = []
    for r, L in enumerate(row_lengths):
        cells = sorted((p[0], ch) for ch, p in pos.items() if int(round(p[1])) == r)
        out.append("  ".join(ch for _, ch in cells))
    return "\n".join(out)


# QWERTZ-CH baseline (staggered 11/11/7) for reference.
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
    qbase = travel(qwertz_pos(), bg, (5.0, 3.0))
    print(f"corpus words: {len(words)}   bigrams (incl. space): {len(bg)}")
    print(f"QWERTZ-CH baseline travel: {qbase:.3f} key-widths/char (incl. space)\n")

    shapes = {
        "3 rows (10/10/9)": [10, 10, 9],
        "4 rows (8/8/8/5)": [8, 8, 8, 5],
        "5 rows (6/6/6/6/5)": [6, 6, 6, 6, 5],
    }
    results = {}
    for name, rl in shapes.items():
        slots, space_pos = make_slots(rl)
        best, bestpos = anneal(slots, space_pos, bg)
        results[name] = (best, bestpos, rl)
        print(f"=== {name} ===")
        print(f"travel {best:.3f}  ({100*best/qbase:.0f}% of QWERTZ, -{100*(1-best/qbase):.0f}%)")
        print(render(bestpos, rl))
        print()


if __name__ == "__main__":
    main()
