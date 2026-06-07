package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.nah.ui.DojoScreen
import com.github.reygnn.nah.ui.NahTheme
import com.github.reygnn.nah.viewmodel.DojoViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Hostet das Tipp-Training ([DojoScreen]). Reiner Glue wie [UserWordsActivity]: der
 * [DojoViewModel] hält den ganzen Spielzustand, hängt aber an keiner InputConnection —
 * das Dojo committet keinen Text, es prüft nur Taps gegen ein Ziel.
 *
 * Persistenz (Bestwert) läuft bewusst hier, nicht im ViewModel: der ViewModel bleibt rein und
 * JVM-testbar, der DataStore-Zugriff hängt an der lebenden Composition. Dadurch gibt es auch keinen
 * an einen toten Activity-Scope gebundenen Callback nach einem Config-Change — die laufende
 * Composition seedet den Bestwert und persistiert jede echte Verbesserung.
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

                // Bestwert laden und persistieren — ein einziger Effekt: erst seeden, dann nur ECHTE
                // Verbesserungen ÜBER den geladenen Stand wegschreiben.
                //
                // Vorher zwei Effekte mit blossem distinctUntilChanged: das schrieb bei JEDEM Öffnen
                // mindestens einmal auf die Platte, obwohl sich nichts geändert hatte — der Collector
                // emittiert beim Start den aktuellen Wert (anfangs (0,0)), und DataStore.edit{} schreibt
                // immer, auch wenn der Wert sich nicht anhebt. Jetzt gleicht der Collector gegen die
                // geladene Basis ab und ruft recordBest nur bei einem tatsächlich besseren Lauf auf
                // (Run-Paar-Ordnung; recordBest setzt dieselbe Regel persistenzseitig als Sicherung durch).
                LaunchedEffect(viewModel) {
                    val best = stats.best.first()
                    // setBest übernimmt nur ein besseres Run-Paar — ein vor dem Laden bereits erspielter
                    // besserer Lauf (das first() suspendiert kurz) überlebt also.
                    viewModel.setBest(best.score, best.streak)
                    var persistedScore = best.score
                    var persistedStreak = best.streak
                    viewModel.state
                        .map { it.bestScore to it.bestStreak }
                        .distinctUntilChanged()
                        .collect { (score, streak) ->
                            // Run-Paar-Ordnung über die geteilte isBetterRun-Regel (dieselbe, die das
                            // Spiel und recordBest benutzen): höherer Score, bei Gleichstand längere Serie.
                            // Score und Serie werden als EIN Paar fortgeschrieben, nicht je für sich maximiert.
                            if (isBetterRun(score, streak, persistedScore, persistedStreak)) {
                                stats.recordBest(score, streak)
                                persistedScore = score
                                persistedStreak = streak
                            }
                        }
                }

                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
