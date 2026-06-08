#!/usr/bin/env python3
"""Wegwerf-Helfer: GermanWordList neu nach Wortart gruppieren.

Liest die kuratierte Liste, klassifiziert jedes Paar nach Wortart und schreibt
die Datei frisch sortiert (Wortart-Blöcke; innerhalb je Block Frequenz absteigend,
dann alphabetisch). Garantien per Assertion:
  * Die Multimenge der (Wort, Frequenz)-Paare bleibt EXAKT erhalten (kein Verlust,
    kein Duplikat, keine geänderte Frequenz).
  * Genau die drei gewollten Case-Homographen (leben/Leben, morgen/Morgen, arm/Arm).
  * Kein ß.
Die Reihenfolge in der Liste ist kosmetisch — WordIndex sortiert beim Aufbau neu.
Das Layout-Korpus ist entkoppelt (CLAUDE.md): kein Optimizer-Lauf.

Klassifikation:
  * Grossgeschrieben  -> Substantiv (deutsche Grossschreibung, zuverlässig).
                         Sub-Feld aus dem Quell-Abschnitt (semantisch), Rest "weitere".
  * Kleingeschrieben  -> explizite WORD_BUCKET-Zuordnung (geschlossene Klassen +
                         gemischte Blöcke) hat Vorrang; sonst aus dem (sauberen)
                         Quell-Abschnitt (Verben/Adjektive/Adverbien/Präp/Konj/Zahlen).
  * Unklassifiziert   -> harter Fehler (zwingt zur Vollständigkeit).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "temp" / "GermanWordList.kt.txt"
OUT = ROOT / "app/src/main/java/com/github/reygnn/nah/data/suggestions/GermanWordList.kt"

PAIR_RE = re.compile(r'"((?:[^"\\]|\\.)+)"\s+to\s+(\d+)')

# --- Wortart-Buckets, Reihenfolge = Ausgabe-Reihenfolge ----------------------
B_DET = "Artikel & Determinative"
B_PRON = "Pronomen"
B_PREP = "Präpositionen"
B_CONJ = "Konjunktionen & Subjunktionen"
B_INT = "Interrogativa (Frageworte)"
B_NUM = "Numerale (Zahlwörter)"
B_VERB = "Verben"
B_ADJ = "Adjektive"
B_ADV = "Adverbien & Partikeln"
B_INTJ = "Interjektionen & Grussformeln"
B_NOUN = "Substantive"

BUCKET_ORDER = [B_DET, B_PRON, B_PREP, B_CONJ, B_INT, B_NUM, B_VERB, B_ADJ,
                B_ADV, B_INTJ, B_NOUN]

# Substantiv-Sub-Felder (semantisch), Reihenfolge innerhalb der Substantive
NOUN_SUBFIELD_ORDER = [
    "Zeit & Kalender", "Menschen & Rollen", "Orte, Reise & Gebäude",
    "Essen & Trinken", "Körper & Gesundheit", "Kleidung",
    "Natur, Wetter & Tiere", "Dinge & Gegenstände",
    "Abstrakt: Arbeit, Geld, Schule, Technik", "weitere Alltagsnomen",
]

# --- explizite Zuordnung kleingeschriebener Wörter (Vorrang vor Abschnitt) ---
def _words(s):
    return s.split()

WORD_BUCKET = {}
def _add(bucket, s):
    for w in _words(s):
        assert w not in WORD_BUCKET, f"doppelte WORD_BUCKET-Zuordnung: {w}"
        WORD_BUCKET[w] = bucket

_add(B_DET, """
der die das ein eine einer einen einem eines
dem den des dieser diese dieses diesen diesem dies
jeder jede jedes jeden jedem kein keine keiner keinen keinem
welche welcher welches welchen welchem welch
solche solcher einige einigen mehrere manche manches mancher
beide beiden beider jene jener jenes jenen jenem
derselbe dieselbe dasselbe denselben demselben derjenige
sämtliche etliche jegliche jeweils alle alles allem viel vieles vielen wenige
""")

_add(B_PRON, """
ich sie wir ihr mein dein sein sich
mich mir dich dir ihm ihn ihnen uns euch
unser unsere unseren unserem unserer unseres euer eure euren eurem
ihre ihren ihrem ihrer seine seinen seinem seiner seines
meine meinen meinem meiner meines deine deinen deinem deines
man etwas nichts jemand niemand jemanden jemandem niemanden niemandem jedermann
dessen deren denen selbst selber
""")

_add(B_PREP, """
beim zum zur vom ans aufs ins
seit trotz wegen statt gegenüber
ausser per pro durchs fürs ums übers unterm vors überm
innerhalb ausserhalb oberhalb unterhalb anstatt anstelle aufgrund
angesichts bezüglich hinsichtlich mittels gemäss dank entgegen samt nebst
bis
""")

_add(B_CONJ, """
sondern falls sofern sowie sowohl entweder weder
indem solange sodass soweit sooft ehe beziehungsweise zumal desto umso
wohingegen wenngleich obgleich obschon
""")

_add(B_INT, """
wer was wen wem wessen wieso weshalb
warum wann wie woher wohin
""")

_add(B_ADV, """
also doch eben halt zwar trotzdem deshalb deswegen darum jedoch allerdings
ausserdem übrigens nämlich schliesslich dennoch somit daher nun gar
vielleicht natürlich später
""")

_add(B_INTJ, """
grüezi merci vielmal tschüss hallo bitte danke entschuldigung sorry
nein okay super stimmt hey hdl kuss willkommen tschau liebe
""")

# kleingeschriebene Wörter in Nomen-/Misch-Abschnitten, die offene Klassen sind
_add(B_ADJ, "sonnig")
_add(B_VERB, "abbiegen")

# --- Abschnitt -> Bucket (nur saubere, eindeutige Abschnitte) ----------------
def section_bucket(sec):
    s = sec.lower()
    # Adverb VOR Verb prüfen: "adverbs" enthält den Substring "verb".
    if "adverb" in s or "particle" in s or "time of day" in s:
        return B_ADV
    if "verb" in s or "konjugierte" in s:
        return B_VERB
    if "adjekt" in s or "adjective" in s or "colour" in s:
        return B_ADJ
    if "präpositionen" in s:
        return B_PREP
    if "konjunktionen" in s:
        return B_CONJ
    if "zahlen" in s:
        return B_NUM
    return None  # gemischt/Nomen -> kein Fallback erlaubt

# Quell-Abschnitt -> Substantiv-Sub-Feld
def noun_subfield(sec):
    s = sec.lower()
    if "zeit" in s or "time & calendar" in s:
        return "Zeit & Kalender"
    if "menschen" in s or "people & roles" in s:
        return "Menschen & Rollen"
    if "orte" in s or "places, travel" in s:
        return "Orte, Reise & Gebäude"
    if "food" in s:
        return "Essen & Trinken"
    if "body & health" in s:
        return "Körper & Gesundheit"
    if "clothing" in s:
        return "Kleidung"
    if "nature, weather" in s or s.strip().startswith("weather"):
        return "Natur, Wetter & Tiere"
    if "colour" in s:
        return "Dinge & Gegenstände"
    if "dinge" in s:
        return "Dinge & Gegenstände"
    if "abstrakt" in s or "abstract" in s:
        return "Abstrakt: Arbeit, Geld, Schule, Technik"
    return "weitere Alltagsnomen"


def parse():
    """-> Liste (wort, freq, section) in Quellreihenfolge."""
    text = SRC.read_text(encoding="utf-8")
    start = text.index("listOf(")
    body = text[start:]
    out = []
    section = ""
    for line in body.splitlines():
        stripped = line.strip()
        cm = re.match(r'//\s*(.*)', stripped)
        if cm:
            cand = cm.group(1).strip()
            # Block-Trenner "--- ... ---" und reine "(a)/(b)"-Marker ignorieren
            if cand and not re.fullmatch(r'\(\w\).*', cand):
                cand = cand.strip("- ").strip()
                if cand:
                    section = cand
            continue
        for m in PAIR_RE.finditer(line):
            out.append((m.group(1), int(m.group(2)), section))
    return out


def classify(word, section):
    if word[:1].isupper():
        return B_NOUN
    if word in WORD_BUCKET:
        return WORD_BUCKET[word]
    b = section_bucket(section)
    if b:
        return b
    raise SystemExit(f"UNKLASSIFIZIERT: {word!r} (Abschnitt: {section!r})")


def kt_escape(w):
    return w.replace("\\", "\\\\").replace('"', '\\"')


def emit_lines(pairs, per_line=5, indent="        "):
    """pairs: Liste (wort, freq), bereits sortiert -> Kotlin-Zeilen."""
    items = [f'"{kt_escape(w)}" to {f}' for w, f in pairs]
    lines = []
    for i in range(0, len(items), per_line):
        chunk = items[i:i + per_line]
        comma = "," if i + per_line < len(items) else ","
        lines.append(indent + ", ".join(chunk) + ",")
    return lines


KDOC = '''/**
 * Die häufigsten deutschen (de-CH) Wörter mit Frequenzwerten. Höher = häufiger.
 * Portiert aus vuot. Kein ß ("ss"). Total {total}.
 *
 * Sortiert nach **Wortart** (`tools/sort_wordlist.py`, Wegwerf): geschlossene Klassen
 * zuerst (Determinative, Pronomen, Präpositionen, Konjunktionen, Interrogativa, Numerale),
 * dann die offenen (Verben, Adjektive, Adverbien & Partikeln, Interjektionen) und zuletzt
 * die Substantive (semantisch unterteilt). Innerhalb jedes Blocks: Frequenz absteigend,
 * dann alphabetisch. Die Reihenfolge ist rein kosmetisch — [WordIndex] sortiert beim Aufbau
 * neu; sie ändert weder Vorschläge noch Verhalten.
 *
 * Kuratierung (chore/wordlist-curate + refill): der seltene Nomen-Schwanz wurde gestrichen,
 * die geschlossenen Funktionswort-Klassen ergänzt, alle Zweibuchstaben-Wörter entfernt (sparen
 * ≤1 Anschlag) und durch lange Alltagsnomen ersetzt — die Liste bevorzugt damit bewusst Einträge
 * mit hohem Tasten-Nutzen.
 *
 * Genau drei gewollte Gross-/Klein-Homographen teilen sich einen kleingeschriebenen Key
 * (`leben`/`Leben`, `morgen`/`Morgen`, `arm`/`Arm`) — siehe `GermanWordListTest`.
 *
 * WICHTIG — Layout-Korpus entkoppelt: Diese Liste war ursprünglich auch das Korpus für
 * `tools/optimize_layout.py`. Das Layout ist eingefroren (siehe CLAUDE.md); Kuratierung wie
 * Neusortierung wirken NUR auf die Vorschläge und lösen KEINE Neuberechnung des Layouts aus.
 */'''


def main():
    pairs = parse()
    assert len(pairs) == len(set((w, f) for w, f, _ in pairs)), "Duplikat (Wort,Freq) in Quelle"

    # Integritäts-Vorabprüfung wie GermanWordListTest
    assert not [w for w, _, _ in pairs if "ß" in w], "ß in Quelle gefunden"
    coll = {}
    for w, _, _ in pairs:
        coll.setdefault(w.lower(), set()).add(w)
    homographs = {k for k, v in coll.items() if len(v) > 1}
    assert homographs == {"leben", "morgen", "arm"}, f"unerwartete Homographen: {homographs}"

    # Klassifizieren
    buckets = {b: [] for b in BUCKET_ORDER}
    noun_sub = {sf: [] for sf in NOUN_SUBFIELD_ORDER}
    for w, f, sec in pairs:
        b = classify(w, sec)
        if b == B_NOUN:
            noun_sub[noun_subfield(sec)].append((w, f))
        else:
            buckets[b].append((w, f))

    # Multimengen-Erhalt prüfen
    src_ms = sorted((w, f) for w, f, _ in pairs)
    got = []
    for b in BUCKET_ORDER:
        if b == B_NOUN:
            for sf in NOUN_SUBFIELD_ORDER:
                got += noun_sub[sf]
        else:
            got += buckets[b]
    assert sorted(got) == src_ms, "Multimenge nicht erhalten!"

    sort_key = lambda p: (-p[1], p[0].lower())

    out = []
    out.append("package com.github.reygnn.nah.data.suggestions")
    out.append("")
    out.append(KDOC.replace("{total}", str(len(pairs))))
    out.append("object GermanWordList {")
    out.append("")
    out.append('    // Format: "wort" to frequency')
    out.append("    val words: List<Pair<String, Int>> = listOf(")
    for bi, b in enumerate(BUCKET_ORDER):
        if b == B_NOUN:
            out.append(f"        // === {b} ===")
            for sf in NOUN_SUBFIELD_ORDER:
                ps = sorted(noun_sub[sf], key=sort_key)
                if not ps:
                    continue
                out.append(f"        // {sf} ({len(ps)})")
                out += emit_lines(ps)
                out.append("")
        else:
            ps = sorted(buckets[b], key=sort_key)
            out.append(f"        // === {b} ({len(ps)}) ===")
            out += emit_lines(ps)
            out.append("")
    # letzte Leerzeile vor Klammer entfernen
    while out and out[-1] == "":
        out.pop()
    out.append("    )")
    out.append("}")
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")

    # Report
    print(f"Total: {len(pairs)}")
    for b in BUCKET_ORDER:
        if b == B_NOUN:
            n = sum(len(noun_sub[sf]) for sf in NOUN_SUBFIELD_ORDER)
            print(f"  {b}: {n}")
            for sf in NOUN_SUBFIELD_ORDER:
                if noun_sub[sf]:
                    print(f"      - {sf}: {len(noun_sub[sf])}")
        else:
            print(f"  {b}: {len(buckets[b])}")


if __name__ == "__main__":
    main()
