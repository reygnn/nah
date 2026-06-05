# Nah

[![API](https://img.shields.io/badge/API-36-brightgreen.svg?style=flat-square)](https://source.android.com/docs/setup/about/build-numbers)

A single-finger-optimised Android keyboard (Input Method Service) for Swiss
German. The letters are re-arranged so the most frequent ones sit close
together — hence the name: *nah* (German for "near").

## What it is

Standard QWERTZ was designed to *scatter* frequent letters — an anti-jam
measure for ten-finger typewriters. For someone typing with a **single index
finger** that is exactly wrong: it maximises how far the finger travels. Nah
flips that. The 29 de-CH letters (`a–z` + `ä ö ü`) are arranged with the **vowels
clustered centrally** (a learnability win — easy to remember, cleanly colourable)
and the **consonants placed by a travel-optimiser** around that fixed vowel block:

```
x qu k o p j y ä
v  c  h u a l f ö      ← vowels o/u/i · a/e centre, ä/ö/ü right
z  m  s i e r b ü
⇧  w  t n d g ⌫
?123  ,  ␣  .  ⏎
```

The consonants are placed by simulated annealing over de-CH bigram frequencies
(including the space key at word boundaries and the Shift key for capitalised
nouns), minimising total finger travel — roughly **36 % less travel than
QWERTZ-CH** for one finger, practically tied with the freely-optimised optimum
(the central vowel cluster costs almost nothing). The optimiser lives in
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
4. **Daily driver**, de-CH only (no ß — "ss").

## Features

- **Travel-optimised de-CH layout** with a central vowel cluster, four letter
  rows, big keys.
- **Deterministic input** — no autocorrect, no word replacement. One-shot Shift,
  double-tap for Caps Lock, toggleable auto-capitalisation after sentence ends
  (an auto-armed Shift clears with a single tap, not via Caps).
- **`qu` digraph key** — commits `qu`, honestly labelled (no autocorrect).
- **Long-press alternatives** — holding a key shows a *visible* popup (slide to a
  chip, release to commit); the inverse colour makes it stand out. Pre-seeded:
  lone `q`, `c` → ch/ck, `s` → sch, vowels → accents. Freely extendable via a
  small table. (vuot's data idea, but visible — not its invisible swipes.)
- **Training-wheel colours** (optional, off by default) — tint vowels and the
  highest-frequency consonants in fixed colours while muscle memory settles.
- **Symbols / numbers layer** of equal height (no resize jump when switching),
  with `,` `.` space and return in the same positions as the letter layer.
- **Optional suggestion bar** — Trie-backed, fed by a baked-in de-CH word list
  plus your own words/phrases (independently toggleable). **Off by default**, and
  *non-intrusive*: tapping a suggestion only replaces the current unfinished
  prefix, never finished text. Its reserved strip hosts a settings button.
- **Custom words & phrases** — add your own (letters, digits, spaces — e.g. a
  postal code or `Hauptstrasse 115`), matched strncmp-style from the start.
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
viewmodel/  KeyboardViewModel — StateFlow, the whole typing state machine.
            FieldContext (distilled EditorInfo).
layout/     KeyboardKey / KeyAction, KeyboardLayout (row + weight based),
            OptimizedLayout (letters + symbols), KeyAlternatives (long-press).
ui/         KeyboardScreen / TapKey / SuggestionBar / NahColors / NahIcons (Compose).
settings/   Settings, SettingsRepository (DataStore), SettingsActivity,
            UserWordsActivity (manage own words).
data/suggestions/  Trie, GermanWordList, SuggestionRepository, UserWordRepository,
            UserWordValidation.
tools/      optimize_layout.py — the layout optimiser (reproducible).
```

- **Kotlin + Jetpack Compose + Material 3**, Compose-only (no XML layouts).
- **AGP 9 + Gradle 9, built-in Kotlin** (no `org.jetbrains.kotlin.android`).
- `minSdk = compileSdk = targetSdk = 36` (Android 16 only), `jvmTarget = JVM_21`.
- Logic is kept out of the Android-runtime class: the ViewModel, layout, and
  Trie are plain JVM-testable.

### Testing

JVM-only unit tests (JUnit 4 + MockK + kotlinx-coroutines-test, no Robolectric).
Covered: the layout's travel property (must beat QWERTZ), the ViewModel state
machine (commit / backspace / shift / caps / layer switch / auto-cap / the
"suggestion never replaces finished text" invariant), and the Trie.

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

- **Per-character offset learning** (a `MissMap`, transplantable from thumbprint):
  the keyboard learns where your finger *actually* lands and widens the effective
  hit target — pure geometry, still no autocorrect. The next real fat-finger win.
- Clipboard history, theme selection, a bigger optimiser corpus.

## Status

Pre-1.0, single-maintainer, personal device only (a Pixel 9a). Not for the Play
Store. Versioning lives in `app/build.gradle.kts`.

## License

To be added.
