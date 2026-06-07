package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.nah.NahApplication
import com.github.reygnn.nah.ui.DojoScreen
import com.github.reygnn.nah.ui.NahTheme
import com.github.reygnn.nah.viewmodel.DojoViewModel
import kotlinx.coroutines.launch

/**
 * Hostet das Tipp-Training ([DojoScreen]). Reiner Glue wie [UserWordsActivity]: der
 * [DojoViewModel] hält den ganzen Spielzustand, hängt aber an keiner InputConnection —
 * das Dojo committet keinen Text, es prüft nur Taps gegen ein Ziel.
 *
 * Persistenz (Bestwert) läuft bewusst hier, nicht im ViewModel: der ViewModel bleibt rein und
 * JVM-testbar, der DataStore-Zugriff hängt an der lebenden Composition. Dadurch gibt es auch keinen
 * an einen toten Activity-Scope gebundenen Callback nach einem Config-Change — die laufende
 * Composition seedet den Bestwert und schreibt ihn beim **Verlassen** (`ON_STOP`) auf die Platte,
 * nicht bei jedem Treffer.
 */
class DojoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stats = DojoStatsRepository(applicationContext)
        // Prozessweiter Scope für den ON_STOP-Write (überlebt das Abreissen der Composition bei einem
        // Config-Change, anders als ein rememberCoroutineScope). Siehe NahApplication.
        val appScope = (application as NahApplication).applicationScope
        setContent {
            NahTheme {
                // viewModel() bindet den DojoViewModel an den ViewModelStore der Activity → der
                // Spielstand (Punkte/Serie/Leben/Ziel) überlebt einen Config-Change (Drehung),
                // statt bei jeder Drehung neu zu starten.
                val viewModel: DojoViewModel = viewModel()

                // Die WANN-schreiben-Entscheidung lebt in DojoBestPersistence (testbar, ohne Compose);
                // hier bleibt nur die Erkennung des Auslösers. Der laufende state.bestScore/bestStreak
                // klettert weiter live fürs Scoreboard, treibt den Disk-Write aber NICHT (früher: ein
                // Write pro neuem Höchststand). `remember`t → ein persisted-Spiegel je Bildschirm.
                val persistence = remember { DojoBestPersistence(stats) }

                LaunchedEffect(viewModel) { persistence.seed(viewModel) }

                // EINZIGER Schreib-Auslöser: ON_STOP deckt jedes reale Verlassen ab (Home/Recents/Back/
                // Screen-off, auch den Config-Change). Eine separate gameOver-Kante gab es früher als
                // Hosenträger gegen Prozesstod *im Vordergrund ohne ON_STOP* (= Crash) — für einen
                // persönlichen Bestwert die Pipeline nicht wert. Bewusst auf dem prozessweiten appScope,
                // NICHT auf einem rememberCoroutineScope: ein Config-Change löst ON_STOP aus und reisst
                // zugleich die Composition (und deren Scope) ab — der noch nicht angelaufene
                // DataStore-Write ginge sonst verloren.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    val best = viewModel.state.value.let { DojoBest(it.bestScore, it.bestStreak) }
                    appScope.launch { persistence.persistIfBetter(best) }
                }

                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
