package com.github.reygnn.nah.data.suggestions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    // No default: a JSON file without a format tag ({} or some foreign file) must fail to decode
    // instead of passing as an empty nah backup.
    @SerialName("format") val format: String,
    @SerialName("version") val version: Int = VERSION,
    /** Export time, ISO 8601 with offset (e.g. `2026-10-18T10:45:12+02:00`). Informational only —
     *  restore ignores it; `null` in hand-written files. */
    @SerialName("exportedAt") val exportedAt: String? = null,
    @SerialName("userWords") val userWords: List<String> = emptyList(),
    @SerialName("learnedWords") val learnedWords: List<String> = emptyList(),
) {

    /** Result of [merge]: the new set to persist and how many entries were actually added. */
    data class MergeResult(val words: Set<String>, val added: Int)

    companion object {
        const val FORMAT = "nah-words"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"
        const val FILE_PREFIX = "nah_words_"
        const val FILE_EXTENSION = ".json"
        /** Same filename timestamp pattern as Kolibri's backup export (`yyyy-MM-dd_HHmmss`). */
        const val FILE_DATE_PATTERN = "yyyy-MM-dd_HHmmss"
        /** Import read cap. A real backup is a few KB (entries are ≤ 50 chars); anything larger
         *  is not ours and must not be pulled into memory whole. */
        const val MAX_FILE_BYTES = 1_000_000

        // Locale.ROOT: ASCII digits and Gregorian year regardless of the device locale (as in
        // Kolibri's FilenameBuilder); only the time zone is device-local.
        private val fileDateFormatter = DateTimeFormatter.ofPattern(FILE_DATE_PATTERN, Locale.ROOT)

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** `nah_words_2026-10-18_104512.json` — the suggested name for an export at [now] in [zone]. */
        fun fileName(now: Instant, zone: ZoneId): String =
            FILE_PREFIX + fileDateFormatter.format(now.atZone(zone)) + FILE_EXTENSION

        /** Serialises both lists, sorted case-insensitively so diffs between backups stay readable,
         *  stamped with the export time [now] in [zone]. */
        fun encode(
            userWords: Set<String>,
            learnedWords: Set<String>,
            now: Instant,
            zone: ZoneId,
        ): String =
            json.encodeToString(
                serializer(),
                WordBackup(
                    format = FORMAT,
                    exportedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        .format(ZonedDateTime.ofInstant(now, zone).withNano(0)),
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
