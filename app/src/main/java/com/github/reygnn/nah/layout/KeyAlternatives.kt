package com.github.reygnn.nah.layout

/**
 * Alternativen pro Buchstabe für das Long-Press-Popup. Vuots Datenidee (Liste von
 * Alternativen, auch Mehrzeichen-Digraphen), aber NICHT vuots unsichtbare Swipe-
 * Geste — die Auswahl ist sichtbar (siehe `ui/TapKey`). Frei erweiterbar: nur hier
 * einen Eintrag ergänzen.
 *
 * `'q'` steht für die qu-Taste und bietet das einzelne `q` an (damit niemand ohne
 * lone-q dasteht). Buchstaben ohne Eintrag haben kein Popup, nur den Tap.
 *
 * Die **Umlaute ä/ö/ü** haben keine eigene Taste mehr (Fat-Finger: weniger, dafür
 * breitere Tasten) — sie liegen als *erste* Alternative auf ihrem Grundvokal (a→ä,
 * o→ö, u→ü), das raffreie Mapping. Die franz. Akzente bleiben als seltenere Einträge
 * dahinter.
 */
object KeyAlternatives {
    val map: Map<Char, List<String>> = mapOf(
        'q' to listOf("q"),                 // qu-Taste → einzelnes q
        'c' to listOf("ch", "ck"),          // häufige Konsonanten-Cluster
        's' to listOf("sch", "st", "sp"),
        'p' to listOf("pf", "ph"),
        'a' to listOf("ä", "à", "â"),       // Umlaut zuerst, dann franz. Akzente
        'e' to listOf("é", "è", "ê"),
        'i' to listOf("î", "ì"),
        'o' to listOf("ö", "ô", "ò"),
        'u' to listOf("ü", "û", "ù"),
    )

    fun forChar(char: Char): List<String> = map[char].orEmpty()
}
