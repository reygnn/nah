package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.LevelBest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DataStore-Round-Trip von [DojoStatsRepository]. Braucht echtes Android-Runtime (DataStore
 * Preferences schreibt eine Datei) → Robolectric mit SDK 36; der **einzige** Robolectric-Test im
 * Projekt, bewusst auf genau diese Grenze beschränkt. Geprüft werden die Zusagen, die die reine
 * ViewModel-Logik nicht abdecken kann: dass Rekorde tatsächlich zurückgelesen werden, dass
 * [DojoStatsRepository.recordBest] pro Stufe monoton ist, dass die Stufen unabhängig sind und dass
 * der Wert eine neue Instanz (Prozess-Neustart) überlebt — der eigentliche Sinn der Persistenz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DojoStatsRepositoryTest {

    private lateinit var repo: DojoStatsRepository

    /** Ein-Stufen-[DojoBest] zum knappen Schreiben in den Tests. */
    private fun best(level: DojoLevel, score: Int, streak: Int) =
        DojoBest(mapOf(level to LevelBest(score, streak)))

    /** Liest den persistierten Rekord einer Stufe zurück. */
    private suspend fun read(level: DojoLevel): LevelBest = repo.best.first().forLevel(level)

    @Before
    fun setUp() = runTest {
        repo = DojoStatsRepository(RuntimeEnvironment.getApplication())
        repo.clear() // isolierter Start je Test (derselbe Application-Context → dieselbe Datei)
    }

    @Test
    fun `frischer Store liefert Null-Rekorde`() = runTest {
        assertEquals(LevelBest(0, 0), read(DojoLevel.WORDS))
    }

    @Test
    fun `recordBest schreibt und liest zurueck`() = runTest {
        repo.recordBest(best(DojoLevel.WORDS, 120, 9))
        assertEquals(LevelBest(120, 9), read(DojoLevel.WORDS))
    }

    @Test
    fun `recordBest hebt Score und Serie pro Stufe unabhaengig an`() = runTest {
        repo.recordBest(best(DojoLevel.WORDS, 120, 9))
        assertEquals(LevelBest(120, 9), read(DojoLevel.WORDS))
        repo.recordBest(best(DojoLevel.WORDS, 50, 3)) // in beiden Feldern schlechter → ignoriert
        assertEquals(LevelBest(120, 9), read(DojoLevel.WORDS))
        // Höhere Serie, niedrigerer Score → die Serie steigt unabhängig auf 20, der Score bleibt 120.
        repo.recordBest(best(DojoLevel.WORDS, 80, 20))
        assertEquals(LevelBest(120, 20), read(DojoLevel.WORDS))
        // Höherer Score, kürzere Serie → der Score steigt auf 200, die Serie bleibt bei 20 (jedes Feld
        // ist ein eigenes Maximum, kein Paar).
        repo.recordBest(best(DojoLevel.WORDS, 200, 1))
        assertEquals(LevelBest(200, 20), read(DojoLevel.WORDS))
    }

    @Test
    fun `Rekorde sind pro Stufe getrennt`() = runTest {
        repo.recordBest(best(DojoLevel.VOWELS, 100, 5))
        repo.recordBest(best(DojoLevel.WORDS, 30, 2)) // andere Stufe → lässt die Vokal-Stufe in Ruhe
        assertEquals(LevelBest(100, 5), read(DojoLevel.VOWELS))
        assertEquals(LevelBest(30, 2), read(DojoLevel.WORDS))
    }

    @Test
    fun `Rekord ueberlebt eine neue Repository-Instanz (Prozess-Neustart)`() = runTest {
        repo.recordBest(best(DojoLevel.WORDS, 77, 4))
        val fresh = DojoStatsRepository(RuntimeEnvironment.getApplication())
        assertEquals(LevelBest(77, 4), fresh.best.first().forLevel(DojoLevel.WORDS))
    }
}
