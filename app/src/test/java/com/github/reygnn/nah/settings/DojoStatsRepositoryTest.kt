package com.github.reygnn.nah.settings

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
 * ViewModel-Logik nicht abdecken kann: dass Bestwerte tatsächlich zurückgelesen werden, dass
 * [DojoStatsRepository.recordBest] monoton ist und dass der Wert eine neue Instanz (Prozess-
 * Neustart) überlebt — der eigentliche Sinn der Persistenz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DojoStatsRepositoryTest {

    private lateinit var repo: DojoStatsRepository

    @Before
    fun setUp() = runTest {
        repo = DojoStatsRepository(RuntimeEnvironment.getApplication())
        repo.clear() // isolierter Start je Test (derselbe Application-Context → dieselbe Datei)
    }

    @Test
    fun `frischer Store liefert Null-Bestwerte`() = runTest {
        assertEquals(DojoBest(0, 0), repo.best.first())
    }

    @Test
    fun `recordBest schreibt und liest zurueck`() = runTest {
        repo.recordBest(120, 9)
        assertEquals(DojoBest(120, 9), repo.best.first())
    }

    @Test
    fun `recordBest hebt Score und Serie unabhaengig an`() = runTest {
        repo.recordBest(120, 9)
        assertEquals(DojoBest(120, 9), repo.best.first())
        repo.recordBest(50, 3) // in beiden Feldern schlechter → ignoriert
        assertEquals(DojoBest(120, 9), repo.best.first())
        // Höhere Serie, niedrigerer Score → die Serie steigt unabhängig auf 20, der Score bleibt 120.
        repo.recordBest(80, 20)
        assertEquals(DojoBest(120, 20), repo.best.first())
        // Höherer Score, kürzere Serie → der Score steigt auf 200, die Serie bleibt bei 20 (jedes Feld
        // ist ein eigenes Maximum, kein Paar).
        repo.recordBest(200, 1)
        assertEquals(DojoBest(200, 20), repo.best.first())
    }

    @Test
    fun `Bestwert ueberlebt eine neue Repository-Instanz (Prozess-Neustart)`() = runTest {
        repo.recordBest(77, 4)
        val fresh = DojoStatsRepository(RuntimeEnvironment.getApplication())
        assertEquals(DojoBest(77, 4), fresh.best.first())
    }
}
