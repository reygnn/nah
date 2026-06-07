package com.github.reygnn.nah

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Prozessweites [Application] — hält allein einen [applicationScope] für Fire-and-forget-Arbeit, die
 * den Tod einer einzelnen Activity/Composition überleben muss. Kein DI-Container, keine sonstige
 * Logik (das Projekt bleibt bei plain `ViewModel` + `StateFlow` + DataStore).
 *
 * Der einzige Nutzer heute ist [com.github.reygnn.nah.settings.DojoActivity]: der `ON_STOP`-Write des
 * Dojo-Bestwerts darf **nicht** an einen `rememberCoroutineScope` hängen — der wird bei genau dem
 * Config-Change (Drehung), der `ON_STOP` auslöst, mit der Composition abgerissen, und der noch nicht
 * angelaufene DataStore-Write ginge verloren. Ein an den Prozess gebundener [SupervisorJob] läuft
 * durch. [Dispatchers.Main.immediate] hält die kurzen Persistenz-Coroutinen auf demselben Thread wie
 * die Composition (der DataStore-`edit` suspendiert und macht seine IO ohnehin intern), sodass der
 * `persisted`-Spiegel in [com.github.reygnn.nah.settings.DojoBestPersistence] single-threaded bleibt.
 */
class NahApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
