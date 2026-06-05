package com.github.reygnn.nah.data.suggestions

/** Warum ein benutzerdefiniertes Wort abgelehnt wurde — für UI-Feedback. */
enum class UserWordError { TooShort, TooLong, InvalidCharacters, AlreadyExists }

/**
 * Reine, JVM-testbare Validierung für benutzerdefinierte Wörter (Logik aus vuot
 * übernommen): 2–50 Zeichen, Buchstaben UND/ODER Ziffern (ein einzelnes Token, z. B.
 * eine PLZ wie „8050") ODER eine plausible E-Mail-Adresse, keine case-insensitiven
 * Duplikate. Bewusst KEINE Leerzeichen/Sonderzeichen: das Matching läuft über ein
 * zusammenhängendes alphanumerisches Präfix vor dem Cursor — eine Phrase mit
 * Leerzeichen wäre so gar nicht auffindbar. Persistenz ist nicht Teil davon.
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
            !isValidEntry(word) -> UserWordError.InvalidCharacters
            existing.any { it.equals(word, ignoreCase = true) } -> UserWordError.AlreadyExists
            else -> null
        }
    }

    private fun isValidEntry(text: String): Boolean =
        if (text.contains('@')) isValidEmail(text) else text.all { it.isLetterOrDigit() }

    private fun isValidEmail(text: String): Boolean {
        val atIndex = text.indexOf('@')
        if (atIndex < 1) return false
        if (text.count { it == '@' } != 1) return false
        val afterAt = text.substring(atIndex + 1)
        if (!afterAt.contains('.')) return false
        if (afterAt.startsWith('.') || afterAt.endsWith('.')) return false
        if (text.contains("..")) return false
        return text.all { it.isLetterOrDigit() || it in "@.-_" }
    }
}
