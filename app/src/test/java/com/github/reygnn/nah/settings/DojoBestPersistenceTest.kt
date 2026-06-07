package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.DojoViewModel
import com.github.reygnn.nah.viewmodel.LevelBest
import io.mockk.coVerify
import io.mockk.spyk
import kotlin.random.Random
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
 * Die Boundary-Persistenz-Entscheidung ([DojoBestPersistence]) gegen ein **echtes** DataStore.
 * Der zweite (und letzte) Robolectric-Test im Projekt — wie [DojoStatsRepositoryTest] bewusst auf
 * genau die Android-Runtime-Grenze (DataStore-Datei) beschränkt; die Compose-/Lifecycle-Verdrahtung
 * der [DojoActivity] (Erkennung der Auslöser) bleibt absichtlich ungetestet (Activities werden im
 * Projekt nicht getestet), die testbare Entscheidung sitzt im hierher gezogenen Seam.
 *
 * Geprüft: dass [DojoBestPersistence.seed] den Stand von der Platte in den ViewModel lädt, dass
 * [DojoBestPersistence.persistIfBetter] Score und Serie pro Stufe als **unabhängige Maxima** auf der
 * Platte anhebt und dass der `persisted`-Spiegel die Platte ohne echte Verbesserung in *einem* Feld
 * **gar nicht erst anfasst** (kein `recordBest`-Aufruf, also kein `DataStore.edit{}`, das sonst auch
 * ohne Wertänderung schreibt). Der Spiegel wird per MockK-Spy auf den Aufruf hin verifiziert, der
 * Disk-Roundtrip über echte Lese-/Schreibpfade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DojoBestPersistenceTest {

    private lateinit var repo: DojoStatsRepository
    private lateinit var persistence: DojoBestPersistence

    /** Ein-Stufen-[DojoBest] zum knappen Schreiben/Verifizieren (alle Tests fahren auf einer Stufe). */
    private fun best(score: Int, streak: Int) =
        DojoBest(mapOf(DojoLevel.WORDS to LevelBest(score, streak)))

    @Before
    fun setUp() = runTest {
        repo = DojoStatsRepository(RuntimeEnvironment.getApplication())
        repo.clear() // isolierter Start je Test (derselbe Application-Context → dieselbe Datei)
        persistence = DojoBestPersistence(repo)
    }

    @Test
    fun `seed laedt den gespeicherten Rekord in den ViewModel`() = runTest {
        repo.recordBest(best(120, 9))
        val vm = DojoViewModel(random = Random(0))
        persistence.seed(vm)
        assertEquals(120, vm.state.value.bestFor(DojoLevel.WORDS).score)
        assertEquals(9, vm.state.value.bestFor(DojoLevel.WORDS).streak)
    }

    @Test
    fun `persistIfBetter schreibt einen echten Rekord und liest ihn zurueck`() = runTest {
        persistence.persistIfBetter(best(70, 5))
        assertEquals(LevelBest(70, 5), repo.best.first().forLevel(DojoLevel.WORDS))
    }

    @Test
    fun `persistIfBetter hebt Score und Serie unabhaengig auf der Platte an`() = runTest {
        persistence.persistIfBetter(best(120, 9))
        persistence.persistIfBetter(best(50, 3))   // in beiden Feldern schlechter → bleibt
        assertEquals(LevelBest(120, 9), repo.best.first().forLevel(DojoLevel.WORDS))
        persistence.persistIfBetter(best(80, 20))  // längere Serie → Serie steigt, Score bleibt
        assertEquals(LevelBest(120, 20), repo.best.first().forLevel(DojoLevel.WORDS))
        persistence.persistIfBetter(best(200, 1))  // höherer Score → Score steigt, Serie bleibt
        assertEquals(LevelBest(200, 20), repo.best.first().forLevel(DojoLevel.WORDS))
    }

    @Test
    fun `persistIfBetter beruehrt die Platte nur bei echter Verbesserung in einem Feld (Spiegel)`() = runTest {
        val spyRepo = spyk(DojoStatsRepository(RuntimeEnvironment.getApplication()))
        val p = DojoBestPersistence(spyRepo) // Spiegel startet (0,0); der Store ist in setUp geleert
        p.persistIfBetter(best(120, 9))  // erster Rekord → schreibt
        p.persistIfBetter(best(120, 9))  // in beiden Feldern gleich → kein Write
        p.persistIfBetter(best(50, 3))   // in beiden Feldern schlechter → kein Write
        p.persistIfBetter(best(119, 99)) // längere Serie (99 > 9) → echte Verbesserung → Write
        coVerify(exactly = 1) { spyRepo.recordBest(best(120, 9)) }
        coVerify(exactly = 0) { spyRepo.recordBest(best(50, 3)) }
        coVerify(exactly = 1) { spyRepo.recordBest(best(119, 99)) }
    }

    @Test
    fun `seed merkt sich den geladenen Stand - ein gleicher Lauf beruehrt die Platte nicht`() = runTest {
        repo.recordBest(best(77, 4)) // Platte vorbelegen (nicht über den Spy)
        val spyRepo = spyk(DojoStatsRepository(RuntimeEnvironment.getApplication()))
        val p = DojoBestPersistence(spyRepo)
        p.seed(DojoViewModel(random = Random(0))) // Spiegel = (77,4) von der Platte
        p.persistIfBetter(best(77, 4))            // gleich dem geladenen Stand → kein Write
        coVerify(exactly = 0) { spyRepo.recordBest(any()) }
        p.persistIfBetter(best(77, 5))            // längere Serie → Write
        coVerify(exactly = 1) { spyRepo.recordBest(best(77, 5)) }
    }
}
