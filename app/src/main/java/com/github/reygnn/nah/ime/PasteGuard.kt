package com.github.reygnn.nah.ime

/**
 * Entscheidet, ob ein **asynchron aufgelöster** Einfüge-Inhalt noch ins ursprünglich anvisierte Feld
 * committen darf. Hintergrund: Der Zwischenablage-Inhalt wird off-main aufgelöst (ein Content-URI-Clip
 * kann den `ContentResolver` eines fremden Prozesses abfragen und spürbar dauern); zwischen der
 * Einfüge-Geste und dem Commit kann der Fokus auf ein FREMDES Feld wechseln. Ohne Schutz landete der
 * Text dort — Fehlcommit und, bei sensiblem Inhalt, ein Privacy-Leck.
 *
 * Mechanik: eine Epoch zählt jeden ECHTEN Feldwechsel hoch (neues Feld ODER Feld-Ende), NICHT einen
 * reinen Restart desselben Feldes (Config-Change/Rotation/Editor-`restartInput` — dasselbe Feld; ein
 * laufender Paste gehört weiterhin dorthin). [beginPaste] merkt die Epoch zur Geste, [mayCommit] lässt
 * den Commit nur zu, wenn die Epoch seither unverändert ist.
 *
 * **Bewusst nur auf dem Main-Thread benutzt** (Lifecycle-Callbacks und der Main-Thread-Commit von
 * `requestPaste`) → keine Synchronisierung nötig. Rein und ohne Android-Abhängigkeit, damit diese
 * Entscheidungslogik JVM-testbar ist (der IME-Service selbst wird konventionsgemäss nicht getestet).
 */
class PasteGuard {

    private var epoch = 0

    /**
     * Feldstart. Ein ECHTER Feldwechsel ([restarting] == false) entwertet einen laufenden Paste; ein
     * reiner Restart desselben Feldes ([restarting] == true) nicht. **Aus `onStartInput` aufzurufen** —
     * das feuert synchron mit der `currentInputConnection`-Umschaltung des Frameworks, anders als das an
     * die View-Sichtbarkeit gekoppelte `onStartInputView` (das bei kurz verstecktem Fenster zu spät käme,
     * sodass ein in dieser Lücke landender Paste gegen die schon umgeschaltete IC committen würde).
     */
    fun onFieldStarted(restarting: Boolean) {
        if (!restarting) epoch++
    }

    /** Feld-Ende: ein noch laufender Paste gehört in kein Feld mehr → entwerten. */
    fun onFieldFinished() {
        epoch++
    }

    /** Merkt die aktuelle Epoch zum Zeitpunkt der Einfüge-Geste; das Ergebnis später an [mayCommit]. */
    fun beginPaste(): Int = epoch

    /** Darf der mit [token] begonnene Paste noch committen? Nur wenn seither kein echter
     *  Feldwechsel/Feld-Ende dazwischenkam. */
    fun mayCommit(token: Int): Boolean = token == epoch
}
