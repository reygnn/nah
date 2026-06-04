# Nah

[![API](https://img.shields.io/badge/API-36-brightgreen.svg?style=flat-square)](https://source.android.com/docs/setup/about/build-numbers)

A single-finger-optimised Android keyboard (Input Method Service) for Swiss
German. The letters are re-arranged so the most frequent ones sit close
together — hence the name: *nah* (German for "near").

## What it is

Standard QWERTZ was designed to *scatter* frequent letters — an anti-jam
measure for ten-finger typewriters. For someone typing with a **single index
finger** that is exactly wrong: it maximises how far the finger travels. Nah
flips that. The 29 de-CH letters (`a–z` + `ä ö ü`) are placed by a
travel-optimiser so frequent letters and bigrams cluster centrally:

```
q j c o b f y ä
p v h u r a k ö
x z s i e g l ü      ← ä/ö/ü grouped, right column
⇧ w t n d m ⌫
?123  ,  ␣  .  ⏎
```

The arrangement is found by simulated annealing over de-CH bigram frequencies
(including the space key at word boundaries and the Shift key for capitalised
nouns), minimising total finger travel — roughly **38 % less travel than
QWERTZ-CH** for one finger. The optimiser lives in
[`tools/optimize_layout.py`](tools/optimize_layout.py); the result is baked into
[`OptimizedLayout.kt`](app/src/main/java/com/github/reygnn/nah/layout/OptimizedLayout.kt).

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

- **Travel-optimised de-CH layout**, four letter rows, big keys.
- **Deterministic input** — no autocorrect, no word replacement. One-shot Shift,
  double-tap for Caps Lock, toggleable auto-capitalisation after sentence ends.
- **Symbols / numbers layer** of equal height (no resize jump when switching),
  with `,` `.` space and return in the same positions as the letter layer.
- **Optional suggestion bar** — Trie-backed, fed by a baked-in de-CH word list.
  **Off by default**, and *non-intrusive*: tapping a suggestion only replaces the
  current unfinished word, never finished text.
- **Dead zones** around keys and a bottom inset clearing the system gesture area.
- Dark theme.

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
            ComposeView (ViewTree owners via a FrameLayout wrapper), safeIc { }.
viewmodel/  KeyboardViewModel — StateFlow, the whole typing state machine.
layout/     KeyboardKey / KeyAction, KeyboardLayout (row + weight based),
            OptimizedLayout (letters + symbols).
ui/         KeyboardScreen / TapKey / SuggestionBar (Compose).
settings/   Settings, SettingsRepository (DataStore), SettingsActivity.
data/suggestions/  Trie, GermanWordList, SuggestionRepository.
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
- Clipboard history, theme selection, long-press accents (à / é).

## Status

Pre-1.0, single-maintainer, personal device only (a Pixel 9a). Not for the Play
Store. Versioning lives in `app/build.gradle.kts`.

## License

To be added.
