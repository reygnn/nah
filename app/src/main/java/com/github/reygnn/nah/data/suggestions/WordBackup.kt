package com.github.reygnn.nah.data.suggestions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Pure, JVM-testable JSON format for backing up both word lists (curated [UserWordRepository] and
 * learned [LearnedWordRepository]) to a user-chosen file and restoring them. I/O (SAF document
 * streams) and persistence live elsewhere — see `UserWordsActivity` and [StringSetWordStore.addAll].
 *
 * Restore **merges**: it only adds entries, never removes or rewrites existing ones, and every entry
 * passes the same [UserWordValidation] as a manual add (invalid ones and case-insensitive duplicates
 * are skipped). Importing the same file twice is therefore a no-op.
 */
@Serializable
data class WordBackup(
    @SerialName("format") val format: String = FORMAT,
    @SerialName("version") val version: Int = VERSION,
    @SerialName("userWords") val userWords: List<String> = emptyList(),
    @SerialName("learnedWords") val learnedWords: List<String> = emptyList(),
) {

    /** Result of [merge]: the new set to persist and how many entries were actually added. */
    data class MergeResult(val words: Set<String>, val added: Int)

    companion object {
        const val FORMAT = "nah-words"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"
        const val FILE_NAME = "nah-words.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Serialises both lists, sorted case-insensitively so diffs between backups stay readable. */
        fun encode(userWords: Set<String>, learnedWords: Set<String>): String =
            json.encodeToString(
                serializer(),
                WordBackup(
                    userWords = userWords.sortedBy { it.lowercase() },
                    learnedWords = learnedWords.sortedBy { it.lowercase() },
                ),
            )

        /** Parses a backup; `null` if it is not valid JSON, not a nah word backup, or from a newer
         *  format version this build doesn't know. */
        fun decode(text: String): WordBackup? {
            val backup = try {
                json.decodeFromString(serializer(), text)
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
            return backup.takeIf { it.format == FORMAT && it.version in 1..VERSION }
        }

        /** Adds every valid, not-yet-present entry of [incoming] to [existing] (trimmed, validated
         *  against the growing set so duplicates within the file are caught too). */
        fun merge(existing: Set<String>, incoming: List<String>): MergeResult {
            val result = existing.toMutableSet()
            var added = 0
            for (raw in incoming) {
                val word = raw.trim()
                if (UserWordValidation.validate(word, result) == null) {
                    result += word
                    added++
                }
            }
            return MergeResult(result, added)
        }
    }
}
