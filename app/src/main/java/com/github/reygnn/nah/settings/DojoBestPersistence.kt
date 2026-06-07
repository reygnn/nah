package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoLevel
import com.github.reygnn.nah.viewmodel.DojoViewModel
import com.github.reygnn.nah.viewmodel.LevelBest
import kotlinx.coroutines.flow.first

/**
 * Die **Entscheidung**, wann die Dojo-Rekorde auf die Platte gehen — aus [DojoActivity] gezogen, damit
 * sie ohne Compose/Lifecycle testbar ist (die Activity bleibt dünne Verdrahtung, die nur die *Auslöser*
 * — Run-Ende-Kante, `ON_STOP` — erkennt und hierher delegiert). Kein Android-UI, nur ein
 * [DojoStatsRepository]; jede Methode ist direkt awaitbar (deterministisch testbar gegen echtes DataStore).
 *
 * Hält den **`persisted`-Spiegel** dessen, was diese Sitzung schon geschrieben wurde: so berührt
 * [persistIfBetter] die Platte nur bei einer echten Verbesserung — `DataStore.edit{}` schreibt die Datei
 * sonst auch ohne Wertänderung. Eine Instanz pro Bildschirm (in der Activity `remember`t). Der einfache
 * `var` braucht keine Synchronisation, und die Begründung ruht auf ZWEI gestapelten Eigenschaften:
 * (1) [DojoStatsRepository.recordBest] hebt jedes Feld jeder Stufe nur an (feldweise monoton), und
 * (2) `DataStore.edit{}` auf bereits gleiche/kleinere Werte ist ein wertneutraler No-op. Zwei
 * `persistIfBetter` können am `recordBest`-Suspend-Punkt interleaven und beide den (barrierefrei
 * gelesenen) `persisted`-Spiegel veraltet sehen — der schlimmste Ausgang ist dann ein **redundanter,
 * wertneutraler Write**, nie ein verlorener Rekord. Rekorde sind pro Stufe je zwei **unabhängige
 * Maxima** (dieselbe Sicht wie im Spiel und in [DojoStatsRepository.recordBest]): ein Write fällt an,
 * sobald auch nur ein Feld irgendeiner Stufe den Spiegel übertrifft.
 */
class DojoBestPersistence(private val repo: DojoStatsRepository) {

    private var persisted = DojoBest()

    /**
     * Lädt die gespeicherten Rekorde in den [viewModel] (über [DojoViewModel.setBests], das jedes Feld
     * jeder Stufe nur anhebt — ein vor dem Laden bereits erspielter besserer Lauf überlebt das kurze
     * Suspendieren von [first]) und merkt sich den geladenen Stand als Spiegel.
     */
    suspend fun seed(viewModel: DojoViewModel) {
        val loaded = repo.best.first()
        viewModel.setBests(loaded.byLevel)
        persisted = loaded
    }

    /**
     * Schreibt [best], sobald auch nur ein Feld irgendeiner Stufe den gespiegelten Stand echt übertrifft,
     * und zieht dann den Spiegel feldweise nach. Aufzurufen an den Run-Grenzen (Lauf zu Ende / Bildschirm
     * verlassen), nicht pro Treffer. Idempotent: ein in keinem Feld besseres [best] berührt die Platte nicht.
     */
    suspend fun persistIfBetter(best: DojoBest) {
        val isBetter = DojoLevel.entries.any { level ->
            val b = best.forLevel(level)
            val p = persisted.forLevel(level)
            b.score > p.score || b.streak > p.streak
        }
        if (!isBetter) return
        repo.recordBest(best)
        persisted = DojoBest(
            DojoLevel.entries.associateWith { level ->
                val b = best.forLevel(level)
                val p = persisted.forLevel(level)
                LevelBest(maxOf(p.score, b.score), maxOf(p.streak, b.streak))
            },
        )
    }
}
