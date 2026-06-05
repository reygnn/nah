# TODO

Offene, bewusst zurückgestellte Punkte. Erledigtes/Verworfenes steht in
`CLAUDE.md` (Abschnitt „Fast-Follow"), nicht hier.

## Barrierefreiheit: Long-Press-Alternativen für TalkBack unerreichbar

**Befund (Code-Review).** Die Tasten sind für TalkBack sauber annotiert
(`role = Button`, `onClick`-Semantik, `contentDescription` für die Space-Taste —
siehe `ui/TapKey.kt`). Das **Alternativen-Popup** beim Gedrückthalten
(`ch`/`ck`/`sch`, Akzente, das einzelne `q`) ist dagegen rein gestengetrieben
(`awaitEachGesture`, Chip-Auswahl per Fingerposition in Pixeln). Die Chips tragen
**keine** Semantik. Ein TalkBack-Nutzer kann diese Zeichen damit gar nicht
erreichen — eine inkonsequente Lücke gegenüber dem sonst gepflegten A11y-Anspruch.

**Warum offen.** nah ist ein persönlicher Ein-Finger-Daily-Driver; der primäre
Nutzer verwendet TalkBack nicht. Niedrige Priorität, aber dokumentiert statt
stillschweigend übergangen.

**Mögliche Richtung (wenn angegangen).**
- Den Long-Press-Tasten eine TalkBack-Aktion (`CustomAccessibilityAction` /
  `semantics { customActions = … }`) je Alternative geben, sodass die Auswahl
  ohne Schiebegeste möglich ist; ODER
- die Alternativen als reguläre, fokussierbare Elemente exponieren, sobald das
  Popup offen ist (Fokus-Reihenfolge: Chips vor dem Schliessen).
- Entscheiden, ob das überhaupt im Scope ist — alternativ hier als „bewusst nicht
  unterstützt" festhalten und nach `CLAUDE.md` → „Verworfen" verschieben.

**Betroffen.** `app/src/main/java/com/github/reygnn/nah/ui/TapKey.kt`
(`AlternativesPopup`, `chipIndexFor`, die `awaitEachGesture`-Schleife).
