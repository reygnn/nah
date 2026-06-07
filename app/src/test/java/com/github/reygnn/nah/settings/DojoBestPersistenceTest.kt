package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoViewModel
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
 * [DojoBestPersistence.persistIfBetter] die Run-Paar-Ordnung auf der Platte durchsetzt und — der Kern
 * des #4-Fix — dass der `persisted`-Spiegel die Platte ohne echte Verbesserung **gar nicht erst
 * anfasst** (kein `recordBest`-Aufruf, also kein `DataStore.edit{}`, das sonst auch ohne Wertänderung
 * schreibt). Der Spiegel wird per MockK-Spy auf den Aufruf hin verifiziert, der Disk-Roundtrip über
 * echte Lese-/Schreibpfade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DojoBestPersistenceTest {

    private lateinit var repo: DojoStatsRepository
    private lateinit var persistence: DojoBestPersistence

    @Before
    fun setUp() = runTest {
        repo = DojoStatsRepository(RuntimeEnvironment.getApplication())
        repo.clear() // isolierter Start je Test (derselbe Application-Context → dieselbe Datei)
        persistence = DojoBestPersistence(repo)
    }

    @Test
    fun `seed laedt den gespeicherten Bestwert in den ViewModel`() = runTest {
        repo.recordBest(120, 9)
        val vm = DojoViewModel(random = Random(0))
        persistence.seed(vm)
        assertEquals(120, vm.state.value.bestScore)
        assertEquals(9, vm.state.value.bestStreak)
    }

    @Test
    fun `persistIfBetter schreibt einen echten Rekord und liest ihn zurueck`() = runTest {
        persistence.persistIfBetter(DojoBest(70, 5))
        assertEquals(DojoBest(70, 5), repo.best.first())
    }

    @Test
    fun `persistIfBetter setzt die Run-Paar-Ordnung auf der Platte durch`() = runTest {
        persistence.persistIfBetter(DojoBest(120, 9))
        persistence.persistIfBetter(DojoBest(50, 3))   // schlechterer Lauf → bleibt
        assertEquals(DojoBest(120, 9), repo.best.first())
        persistence.persistIfBetter(DojoBest(80, 20))  // höhere Serie, kleinerer Score → bleibt
        assertEquals(DojoBest(120, 9), repo.best.first())
        persistence.persistIfBetter(DojoBest(120, 12)) // gleicher Score, längere Serie → schreibt
        assertEquals(DojoBest(120, 12), repo.best.first())
        persistence.persistIfBetter(DojoBest(200, 1))  // höherer Score nimmt seine (kürzere) Serie mit
        assertEquals(DojoBest(200, 1), repo.best.first())
    }

    @Test
    fun `persistIfBetter beruehrt die Platte nicht ohne echte Verbesserung (Spiegel)`() = runTest {
        val spyRepo = spyk(DojoStatsRepository(RuntimeEnvironment.getApplication()))
        val p = DojoBestPersistence(spyRepo) // Spiegel startet (0,0); der Store ist in setUp geleert
        p.persistIfBetter(DojoBest(120, 9))  // erster echter Rekord → schreibt
        p.persistIfBetter(DojoBest(120, 9))  // gleich → kein Write
        p.persistIfBetter(DojoBest(50, 3))   // schlechter → kein Write
        p.persistIfBetter(DojoBest(119, 99)) // kleinerer Score (trotz langer Serie) → kein Write
        coVerify(exactly = 1) { spyRepo.recordBest(120, 9) }
        coVerify(exactly = 0) { spyRepo.recordBest(50, 3) }
        coVerify(exactly = 0) { spyRepo.recordBest(119, 99) }
    }

    @Test
    fun `seed merkt sich den geladenen Stand - ein gleicher Lauf beruehrt die Platte nicht`() = runTest {
        repo.recordBest(77, 4) // Platte vorbelegen (nicht über den Spy)
        val spyRepo = spyk(DojoStatsRepository(RuntimeEnvironment.getApplication()))
        val p = DojoBestPersistence(spyRepo)
        p.seed(DojoViewModel(random = Random(0))) // Spiegel = (77,4) von der Platte
        p.persistIfBetter(DojoBest(77, 4))        // gleich dem geladenen Stand → kein Write
        coVerify(exactly = 0) { spyRepo.recordBest(any(), any()) }
        p.persistIfBetter(DojoBest(77, 5))        // besserer Lauf → Write
        coVerify(exactly = 1) { spyRepo.recordBest(77, 5) }
    }
}
