package com.github.reygnn.nah.settings

import com.github.reygnn.nah.viewmodel.DojoViewModel
import kotlinx.coroutines.flow.first

/**
 * Die **Entscheidung**, wann der Dojo-Bestwert auf die Platte geht — aus [DojoActivity] gezogen, damit
 * sie ohne Compose/Lifecycle testbar ist (die Activity bleibt dünne Verdrahtung, die nur die *Auslöser*
 * — Run-Ende-Kante, `ON_STOP` — erkennt und hierher delegiert). Kein Android-UI, nur ein
 * [DojoStatsRepository]; jede Methode ist direkt awaitbar (deterministisch testbar gegen echtes DataStore).
 *
 * Hält den **`persisted`-Spiegel** dessen, was diese Sitzung schon geschrieben wurde: so berührt
 * [persistIfBetter] die Platte nur bei einer echten Verbesserung — `DataStore.edit{}` schreibt die Datei
 * sonst auch ohne Wertänderung. Eine Instanz pro Bildschirm (in der Activity `remember`t). Der einfache
 * `var` braucht keine Synchronisation: nicht weil nie nebenläufig aufgerufen würde (zwei `persistIfBetter`
 * können am `recordBest`-Suspend-Punkt interleaven), sondern weil [DojoStatsRepository.recordBest] selbst
 * idempotent-monoton ist — der schlimmste Ausgang ist ein redundanter Write, nie ein verlorener Rekord.
 * Score und Serie sind **unabhängige
 * Maxima** (dieselbe Sicht wie im Spiel und in [DojoStatsRepository.recordBest]): ein Write fällt an,
 * sobald auch nur eines der beiden Felder den Spiegel übertrifft.
 */
class DojoBestPersistence(private val repo: DojoStatsRepository) {

    private var persisted = DojoBest()

    /**
     * Lädt den gespeicherten Bestwert in den [viewModel] (über [DojoViewModel.setBest], das nur ein
     * besseres Run-Paar übernimmt — ein vor dem Laden bereits erspielter besserer Lauf überlebt das
     * kurze Suspendieren von [first]) und merkt sich den geladenen Stand als Spiegel.
     */
    suspend fun seed(viewModel: DojoViewModel) {
        val loaded = repo.best.first()
        viewModel.setBest(loaded.score, loaded.streak)
        persisted = loaded
    }

    /**
     * Schreibt [best], sobald auch nur Score ODER Serie den gespiegelten Stand echt übertrifft, und
     * zieht dann den Spiegel feldweise nach. Aufzurufen an den Run-Grenzen (Lauf zu Ende / Bildschirm
     * verlassen), nicht pro Treffer. Idempotent: ein in beiden Feldern gleich gutes oder schlechteres
     * Paar berührt die Platte nicht.
     */
    suspend fun persistIfBetter(best: DojoBest) {
        if (best.score > persisted.score || best.streak > persisted.streak) {
            repo.recordBest(best.score, best.streak)
            persisted = DojoBest(
                maxOf(persisted.score, best.score),
                maxOf(persisted.streak, best.streak),
            )
        }
    }
}
