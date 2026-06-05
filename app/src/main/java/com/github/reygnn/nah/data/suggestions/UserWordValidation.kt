package com.github.reygnn.nah.data.suggestions

/** Warum ein benutzerdefiniertes Wort abgelehnt wurde — für UI-Feedback. */
enum class UserWordError { TooShort, TooLong, InvalidCharacters, AlreadyExists }

/**
 * Reine, JVM-testbare Validierung für benutzerdefinierte Wörter/Phrasen: 2–50 Zeichen
 * (getrimmt), keine Steuerzeichen/Zeilenumbrüche, keine case-insensitiven Duplikate.
 *
 * Bewusst grosszügig bei den Zeichen — Leerzeichen, Ziffern und Satzzeichen sind
 * erlaubt (z. B. „Hauptstrasse 115", „8050 Zürich", „max@firma.ch"). Das ist gefahrlos,
 * weil das Matching strncmp-artig **vom Anfang** des gespeicherten Eintrags läuft (ein
 * Trie): getippt wird nur das alphanumerische Präfix vor dem Cursor, alles dahinter
 * (Leerzeichen etc.) sitzt im Trie hinter diesem Präfix und fährt beim Einfügen mit.
 * Eine Phrase wird also über den Anfang ihres ersten Wortes gefunden, nie per Teilstring-
 * Suche mitten im Eintrag. Persistenz ist nicht Teil davon.
 */
object UserWordValidation {

    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 50

    /** `null` = in Ordnung. Sonst der Grund der Ablehnung. */
    fun validate(raw: String, existing: Set<String>): UserWordError? {
        val word = raw.trim()
        return when {
            word.length < MIN_LENGTH -> UserWordError.TooShort
            word.length > MAX_LENGTH -> UserWordError.TooLong
            word.any { it.isISOControl() } -> UserWordError.InvalidCharacters
            existing.any { it.equals(word, ignoreCase = true) } -> UserWordError.AlreadyExists
            else -> null
        }
    }
}
