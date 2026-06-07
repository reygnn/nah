package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                // hier bleibt nur die Erkennung der Auslöser. Die laufenden state.bests klettern weiter
                // live fürs Scoreboard, treiben den Disk-Write aber NICHT (früher: ein Write pro neuem
                // Höchststand). `remember`t → ein persisted-Spiegel je Bildschirm.
                val persistence = remember { DojoBestPersistence(stats) }
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) { persistence.seed(viewModel) }

                // ZWEI Schreib-Auslöser, beide bewusst auf dem prozessweiten appScope (NICHT einem
                // rememberCoroutineScope): ein noch nicht angelaufener DataStore-Write soll das Abreissen
                // der Composition überleben.
                //
                // (1) ON_STOP deckt jedes reale Verlassen ab (Home/Recents/Back/Screen-off, auch den
                //     Config-Change, der die Composition und ihren Scope zugleich abreisst).
                // (2) Die Game-Over-Kante deckt die Lücke, die ON_STOP offen lässt: ein Prozesstod *im
                //     Vordergrund* (Crash) nach einem beendeten Lauf. Game Over ist eine echte Run-Grenze
                //     und der Moment, in dem ein Rekord am ehesten final ist — nur ein Crash MITTEN im
                //     noch laufenden Lauf verliert dann etwas, und ein nicht zu Ende gespielter Lauf ist
                //     weit verschmerzbarer als ein abgeschlossener. persistIfBetter ist idempotent, ein
                //     anschliessendes ON_STOP schreibt also nicht doppelt.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    appScope.launch { persistence.persistIfBetter(DojoBest(viewModel.state.value.bests)) }
                }
                LaunchedEffect(state.gameOver) {
                    if (state.gameOver) {
                        appScope.launch { persistence.persistIfBetter(DojoBest(viewModel.state.value.bests)) }
                    }
                }

                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
