package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.github.reygnn.nah.ui.DojoScreen
import com.github.reygnn.nah.ui.NahTheme
import com.github.reygnn.nah.viewmodel.DojoViewModel

/**
 * Hostet das Tipp-Training ([DojoScreen]). Reiner Glue wie [UserWordsActivity]: der
 * [DojoViewModel] hält den ganzen Spielzustand, hängt aber an keiner InputConnection —
 * das Dojo committet keinen Text, es prüft nur Taps gegen ein Ziel.
 */
class DojoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NahTheme {
                // remember überlebt Rekomposition; ein Config-Change (Drehung) baut die Activity neu
                // und startet die Runde frisch — für ein kurzes Drill akzeptabel, kein persistenter Stand.
                val viewModel = remember { DojoViewModel() }
                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
