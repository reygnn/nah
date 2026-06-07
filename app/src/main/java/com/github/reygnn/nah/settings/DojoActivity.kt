package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.nah.ui.DojoScreen
import com.github.reygnn.nah.ui.NahTheme
import com.github.reygnn.nah.viewmodel.DojoViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

                // Spiegel dessen, was diese Sitzung bereits auf der Platte steht. Persistiert wird nur,
                // wenn der anzeigte Bestwert das echt übertrifft — DataStore.edit{} schreibt die Datei
                // IMMER (auch ohne Wertänderung), ein ungeprüfter Aufruf würde also unnötig die Platte
                // berühren. Der laufende state.bestScore/bestStreak klettert weiter live fürs Scoreboard;
                // das treibt den Disk-Write aber NICHT mehr (früher: ein Write pro neuem Höchststand).
                val persisted = remember { mutableStateOf(DojoBest()) }

                LaunchedEffect(viewModel) {
                    val loaded = stats.best.first()
                    // setBest übernimmt nur ein besseres Run-Paar — ein vor dem Laden bereits erspielter
                    // besserer Lauf (das first() suspendiert kurz) überlebt also.
                    viewModel.setBest(loaded.score, loaded.streak)
                    persisted.value = loaded
                    // An der Run-Grenze persistieren: wenn ein Lauf ENDET (gameOver false→true). Nur diese
                    // Kante treibt den Write, nicht jeder Treffer dazwischen. Geleert wird gegen `persisted`,
                    // damit ein Game Over ohne neuen Rekord nichts schreibt; recordBest setzt dieselbe
                    // Run-Paar-Regel persistenzseitig nochmals als Sicherung durch (isBetterRun).
                    viewModel.state
                        .map { DojoBest(it.bestScore, it.bestStreak) to it.gameOver }
                        .distinctUntilChanged()
                        .collect { (best, gameOver) ->
                            if (gameOver && isBetterRun(best.score, best.streak, persisted.value.score, persisted.value.streak)) {
                                stats.recordBest(best.score, best.streak)
                                persisted.value = best
                            }
                        }
                }

                // Mitten im Lauf verlassen (noch kein Game Over) darf einen frischen Rekord nicht verlieren →
                // bei ON_STOP ebenfalls persistieren. Gegen `persisted` geprüft, damit blosses Öffnen/Schliessen
                // ohne neuen Bestwert nichts schreibt. Die Composition lebt bei ON_STOP noch (erst ON_DESTROY
                // reisst sie ab), der rememberCoroutineScope ist also aktiv und der kurze recordBest läuft durch.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    val best = viewModel.state.value.let { DojoBest(it.bestScore, it.bestStreak) }
                    if (isBetterRun(best.score, best.streak, persisted.value.score, persisted.value.streak)) {
                        persisted.value = best
                        scope.launch { stats.recordBest(best.score, best.streak) }
                    }
                }

                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
