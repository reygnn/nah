package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.nah.ui.DojoScreen
import com.github.reygnn.nah.ui.NahTheme
import com.github.reygnn.nah.viewmodel.DojoViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hostet das Tipp-Training ([DojoScreen]). Reiner Glue wie [UserWordsActivity]: der
 * [DojoViewModel] hält den ganzen Spielzustand, hängt aber an keiner InputConnection —
 * das Dojo committet keinen Text, es prüft nur Taps gegen ein Ziel.
 *
 * Persistenz (Bestwert) läuft bewusst hier, nicht im ViewModel: der ViewModel bleibt rein und
 * JVM-testbar, der DataStore-Zugriff hängt an der lebenden Composition. Dadurch gibt es auch keinen
 * an einen toten Activity-Scope gebundenen Callback nach einem Config-Change — die laufende
 * Composition seedet den Bestwert und schreibt ihn nur an **Run-Grenzen** (Lauf zu Ende /
 * Bildschirm verlassen) auf die Platte, nicht bei jedem Treffer.
 */
class DojoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stats = DojoStatsRepository(applicationContext)
        setContent {
            NahTheme {
                // viewModel() bindet den DojoViewModel an den ViewModelStore der Activity → der
                // Spielstand (Punkte/Serie/Leben/Ziel) überlebt einen Config-Change (Drehung),
                // statt bei jeder Drehung neu zu starten.
                val viewModel: DojoViewModel = viewModel()
                val scope = rememberCoroutineScope()

                // Die WANN-schreiben-Entscheidung lebt in DojoBestPersistence (testbar, ohne Compose);
                // hier bleibt nur die Erkennung der Auslöser. Der laufende state.bestScore/bestStreak
                // klettert weiter live fürs Scoreboard, treibt den Disk-Write aber NICHT mehr (früher:
                // ein Write pro neuem Höchststand). `remember`t → ein persisted-Spiegel je Bildschirm.
                val persistence = remember { DojoBestPersistence(stats) }

                LaunchedEffect(viewModel) {
                    persistence.seed(viewModel)
                    // An der Run-Grenze persistieren: nur die gameOver false→true-Kante treibt den Write,
                    // nicht jeder Treffer dazwischen (persistIfBetter prüft gegen den Spiegel).
                    viewModel.state
                        .map { DojoBest(it.bestScore, it.bestStreak) to it.gameOver }
                        .distinctUntilChanged()
                        .collect { (best, gameOver) -> if (gameOver) persistence.persistIfBetter(best) }
                }

                // Mitten im Lauf verlassen (noch kein Game Over) darf einen frischen Rekord nicht verlieren →
                // bei ON_STOP ebenfalls persistieren. Die Composition lebt bei ON_STOP noch (erst ON_DESTROY
                // reisst sie ab), der rememberCoroutineScope ist also aktiv und der kurze recordBest läuft durch.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    val best = viewModel.state.value.let { DojoBest(it.bestScore, it.bestStreak) }
                    scope.launch { persistence.persistIfBetter(best) }
                }

                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
