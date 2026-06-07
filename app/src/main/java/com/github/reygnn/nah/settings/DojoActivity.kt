package com.github.reygnn.nah.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
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
                // viewModel() bindet den DojoViewModel an den ViewModelStore der Activity → der
                // Spielstand (Punkte/Serie/Leben/Ziel) überlebt einen Config-Change (Drehung),
                // statt bei jeder Drehung neu zu starten.
                val viewModel: DojoViewModel = viewModel()
                DojoScreen(viewModel = viewModel)
            }
        }
    }
}
