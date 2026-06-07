# Nah

[![API](https://img.shields.io/badge/API-36-brightgreen.svg?style=flat-square)](https://source.android.com/docs/setup/about/build-numbers)

A single-finger-optimised Android keyboard (Input Method Service) for Swiss
German. The letters are re-arranged so the most frequent ones sit close
together — hence the name: *nah* (German for "near").

## What it is

Standard QWERTZ was designed to *scatter* frequent letters — an anti-jam
measure for ten-finger typewriters. For someone typing with a **single index
finger** that is exactly wrong: it maximises how far the finger travels. Nah
flips that. The 26 base letters `a–z` are the keys; the umlauts `ä ö ü` live on a
**long-press of their base vowel** (a→ä, o→ö, u→ü), which keeps every row down to a
few big, wide keys. The **vowels are clustered centrally** (a learnability win —
easy to remember, cleanly colourable) and the **consonants placed by a
travel-optimiser** around that fixed vowel block:

```
x qu k o p j y
v  c  h u a l f      ← vowels o/u/i · a/e centre (ä/ö/ü on long-press)
z  m  s i e r b
⇧  w  t n d g ⌫
⎀ SYM , ␣ . ⏎        ← ⎀ paste (always visible, left of SYM)
```

The consonants are placed by simulated annealing over de-CH bigram frequencies
(including the space key at word boundaries and the Shift key for capitalised
nouns), minimising total finger travel — roughly **36 % less travel than
QWERTZ-CH** for one finger, practically tied with the freely-optimised optimum
(the central vowel cluster costs almost nothing). Caveat: that ~36 % rests on
**hand-estimated** word/bigram frequencies (see
[`GermanWordList`](app/src/main/java/com/github/reygnn/nah/data/suggestions/GermanWordList.kt)),
not a measured corpus — read it as an order of magnitude (about a third), not a
precise figure. The direction is robust; the second digit is not. The optimiser lives in
[`tools/optimize_layout.py`](tools/optimize_layout.py); the result is baked into
[`OptimizedLayout.kt`](app/src/main/java/com/github/reygnn/nah/layout/OptimizedLayout.kt).

The **`qu` key** commits `qu` (in German, `q` is virtually always followed by `u`)
— honestly labelled, not autocorrect; a lone `q` is one of its long-press options.

## Why it is the way it is

Nah is the maintainer's third keyboard. The first two died of the same disease —
their *clever* core feature was what made them unusable day-to-day:

- **thumbprint** (dead): a keyboard that floated around the thumb. The moving
  layout destroyed spatial muscle memory; blind typing became impossible.
- **vuot** (alive as a hobby gesture keyboard, *not* a daily driver): nine keys
  with digraphs on swipes. A brutal, arbitrary, all-or-nothing learning curve.

The lesson, as a design lens: **few keys force ambiguity, which is resolved
either by the machine guessing (autocorrect) or by the human learning (a steep
curve).** Reject both, and the only thing left is a **full alphabet, fixed,
deterministic, and cleverly arranged**. That is nah.

Concretely:

1. **Fat-finger friendly** — via key *size*, not key count. Full alphabet, big
   keys, a non-clickable dead zone around every key (a near-miss types *nothing*
   rather than the wrong letter).
2. **No autocorrect.** A tap commits exactly that character. A finished word is
   **never** altered. This is the top rule.
3. **No learning wall.** Labels are always visible — usable from day one by
   hunt-and-peck; only the letter *positions* are new, not the keyboard shape.
   The few long-press extras (umlauts on their base vowel, consonant clusters)
   are flagged by a small corner dot.
4. **Daily driver**, de-CH only (no ß — "ss").

Nah targets a **sighted** user typing by eye. It has **no screen-reader
(TalkBack) support** — keys carry no content descriptions, and the visible
long-press menus rely on a slide-and-release gesture a screen reader can't drive
(so the umlauts and accents that live there aren't reachable that way). This is a
deliberate scope choice for a single-user personal keyboard, not an oversight.

## Features

- **Travel-optimised de-CH layout** with a central vowel cluster, four letter
  rows, big keys.
- **Deterministic input** — no autocorrect, no word replacement. One-shot Shift,
  double-tap for Caps Lock, toggleable auto-capitalisation after sentence ends
  (an auto-armed Shift clears with a single tap, not via Caps).
- **`qu` digraph key** — commits `qu`, honestly labelled (no autocorrect).
- **Long-press alternatives** — holding a key opens a *visible* **vertical** popup:
  **hold and release commits the first item** (no sliding needed), slide up for the
  rest, drag below the key to cancel. Keys that carry a menu show a small corner
  dot. Pre-seeded: vowels → their umlaut then accents (`a` → ä/à/â, `o` → ö/ô/ò,
  `u` → ü/û/ù), lone `q`, `c` → ch/ck, `s` → sch/st/sp, `p` → pf/ph. Freely
  extendable via a small table. (vuot's data idea, but visible — not its invisible
  swipes.)
- **Training-wheel colours** (optional, off by default) — tint vowels and the
  highest-frequency consonants in fixed colours, and dim the rarely-used `x`/`y`,
  while muscle memory settles.
- **Typing dojo** (opened from settings) — a drill for the new letter *positions*,
  the one thing nah makes you relearn. Shows a target, you tap the right key,
  with score / streak / lives. Four levels (vowels → frequent consonants → full
  alphabet → real de-CH words) in *random* or *in-order* mode. Reuses the real
  keyboard rendering, so the geometry — and the muscle memory — is identical;
  no hints, labels stay visible. The frozen layout is only read, never changed.
  (vuot's dojo idea, but drilling tap positions instead of invisible gestures.)
- **Symbols layer** of equal height (no resize jump when switching), with `,` `.`
  space and return in the same positions as the letter layer. `?` and `!` sit on a
  long-press of the period key.
- **Big-key number & phone pads** — numeric and phone fields open straight onto a
  3-column large-key pad (PIN, amount, dial number) instead of the narrow symbol
  row. Every layer reaches every other: a long-press on the layer-toggle key
  offers the pads it can't tap to (NUM / TEL) plus the settings (OPT) — a full mesh.
- **Optional suggestion bar** — prefix-index-backed, fed by a baked-in de-CH word list
  plus your own words/phrases (independently toggleable). **Off by default**, and
  *non-intrusive*: tapping a suggestion only replaces the current unfinished
  prefix, never finished text. (Settings are reached via a long-press on the
  layer-toggle key — see above — not from this strip.)
- **Custom words & phrases** — add your own (letters, digits, spaces — e.g. a
  postal code or `Hauptstrasse 115`), matched strncmp-style from the start.
- **Paste key** — always visible at the bottom-left, inserts the clipboard
  verbatim (no autocorrect, no shift-casing). Dims when the clipboard holds no
  text and updates live when you copy while typing. Reads only metadata for the
  enabled state — no "pasted from clipboard" toast until you actually paste.
- **Return key** performs the field's editor action (search / send / next /
  done) when one is requested, instead of always inserting a newline.
- **Dead zones** around keys, a bottom inset clearing the system gesture area,
  light haptic per tap (respects the system setting).
- Dark theme (Material You dynamic colours).

## Privacy

- **No network code.** The app declares no internet permission.
- **No analytics, no crash reporting, no ads.** Crashes go to `logcat` only.
- **All data stays local** — settings (DataStore) and the word list live on the
  device.

## Architecture & stack

Single `:app` Gradle module, **no DI framework** — a plain `ViewModel` +
`StateFlow`, manual wiring in the IME service.

```
ime/        NahIme : InputMethodService — thin glue, hosts the Compose UI in a
            ComposeView (ViewTree owners via a FrameLayout wrapper), safeIc { },
            window-shown/hidden lifecycle, editor-action + selection plumbing.
            PasteGuard (pure: blocks an async paste landing in a switched field).
viewmodel/  KeyboardViewModel — StateFlow, the whole typing state machine.
            FieldContext (distilled EditorInfo).
layout/     KeyboardKey / KeyAction, KeyboardLayout (row + weight based),
            OptimizedLayout (letters + symbols + big-key number/phone pads),
            KeyAlternatives (long-press).
ui/         KeyboardScreen / TapKey / SuggestionBar / NahColors / NahIcons,
            LongPressGesture (pure long-press state machine) — Compose.
settings/   Settings, SettingsRepository (DataStore), SettingsActivity,
            UserWordsActivity (manage own words).
data/suggestions/  WordIndex (sorted array + binary search), GermanWordList,
            SuggestionRepository, UserWordRepository, UserWordValidation.
tools/      optimize_layout.py — the layout optimiser (reproducible);
            word_index_benchmark.md — suggestion-cost measurement + corpus threshold.
```

- **Kotlin + Jetpack Compose + Material 3**, Compose-only (no XML layouts).
- **AGP 9 + Gradle 9, built-in Kotlin** (no `org.jetbrains.kotlin.android`).
- `minSdk = compileSdk = targetSdk = 36` (Android 16 only), `jvmTarget = JVM_21`.
- Logic is kept out of the Android-runtime class: the ViewModel, layout, and
  WordIndex are plain JVM-testable.

### Testing

Mostly JVM-only unit tests (JUnit 4 + MockK + kotlinx-coroutines-test); Robolectric
only where a test needs real Android runtime (DataStore round-trip in
`DojoStatsRepositoryTest`).
Covered: the layout's travel property (must beat QWERTZ), the ViewModel state
machine (commit / backspace / shift / caps / layer switch / auto-cap / the
"suggestion never replaces finished text" invariant), the WordIndex, and the pure
units lifted out of the Compose/Service layer (PasteGuard, LongPressGesture,
FieldContext, user-word validation, the self-echo dedup). A seed-reproducible
**invariant fuzzer** runs thousands of random op sequences against the hard
invariants (finished text never altered, shown == typed, no stranded shift).

## Building

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease        # unsigned release AAB
```

JDK 21 is required. The release AAB ships **unsigned**; it is signed with a
personal key at install time, not by Gradle.

## Roadmap

Deliberately deferred from v1:

- Clipboard history, theme selection, a bigger optimiser corpus. (The suggestion
  index's per-keystroke cost is measured in
  [`tools/word_index_benchmark.md`](tools/word_index_benchmark.md): comfortable up to ~50k
  words, so corpus growth needs no code change until well beyond that.)

Per-character offset learning (a `MissMap`) was once earmarked as the next
fat-finger win. **Dropped** — in practice the big keys plus the dead zone around
each one already prevent mistypes, so the extra machinery isn't worth it.

## Status

Pre-1.0, single-maintainer, personal device only (a Pixel 9a). Not for the Play
Store. Versioning lives in `app/build.gradle.kts`.

## License

To be added.
