package com.github.reygnn.nah.data.suggestions

/**
 * Die häufigsten deutschen (de-CH) Wörter mit Frequenzwerten. Höher = häufiger.
 * Portiert aus vuot; dieselbe Liste ist auch das Korpus für `tools/optimize_layout.py`.
 * Kein ß ("ss").
 */
object GermanWordList {

    // Format: "wort" to frequency
    val words: List<Pair<String, Int>> = listOf(
        // Artikel & Pronomen (sehr häufig)
        "der" to 1000, "die" to 1000, "das" to 1000,
        "ein" to 950, "eine" to 950, "einer" to 940,
        "ich" to 900, "du" to 890, "er" to 880, "sie" to 880, "es" to 870,
        "wir" to 860, "ihr" to 850,
        "mein" to 800, "dein" to 790, "sein" to 780,
        "dieser" to 750, "diese" to 750, "dieses" to 740,

        // Verben (häufig)
        "haben" to 890, "werden" to 880,
        "können" to 850, "müssen" to 840, "sollen" to 830, "wollen" to 820, "dürfen" to 810,
        "machen" to 800, "gehen" to 790, "kommen" to 780, "sehen" to 770, "geben" to 760,
        "nehmen" to 750, "finden" to 740, "denken" to 730, "sagen" to 720, "wissen" to 710,
        "lassen" to 700, "stehen" to 690, "liegen" to 680, "heissen" to 670, "bleiben" to 660,
        "bringen" to 650, "halten" to 640, "laufen" to 630, "tragen" to 620, "fahren" to 610,
        "schreiben" to 600, "lesen" to 590, "spielen" to 580, "sprechen" to 570, "fragen" to 560,
        "arbeiten" to 550, "brauchen" to 540, "folgen" to 530, "lernen" to 520, "leben" to 510,
        "glauben" to 500, "führen" to 490, "kennen" to 480, "setzen" to 470, "stellen" to 460,
        "suchen" to 450, "zeigen" to 440, "ziehen" to 430, "scheinen" to 420, "fallen" to 410,
        "gehören" to 400, "entstehen" to 390, "erhalten" to 380, "treffen" to 370, "erreichen" to 360,
        "verstehen" to 350, "verlieren" to 340, "beginnen" to 330, "erzählen" to 320, "versuchen" to 310,
        "entwickeln" to 300, "kaufen" to 290, "verkaufen" to 280, "öffnen" to 270, "schliessen" to 260,
        "helfen" to 250, "hören" to 240, "schaffen" to 230, "lieben" to 220, "warten" to 210,

        // Konjugierte Formen
        "ist" to 950, "sind" to 940, "war" to 930, "waren" to 920, "bin" to 910, "bist" to 900,
        "hat" to 890, "habe" to 880, "hatte" to 870, "hatten" to 860,
        "wird" to 850, "wurde" to 840,
        "kann" to 820, "konnte" to 810, "kannst" to 800,
        "muss" to 790, "musste" to 780, "musst" to 770,
        "will" to 760, "wollte" to 750, "willst" to 740,
        "geht" to 730, "ging" to 720, "gehst" to 710,
        "kommt" to 700, "kam" to 690, "kommst" to 680,
        "macht" to 670, "machte" to 660, "machst" to 650,
        "gibt" to 640, "gab" to 630, "gibst" to 620,
        "sagt" to 610, "sagte" to 600, "sagst" to 590,
        "weiss" to 580, "wusste" to 570, "weisst" to 560,

        // Präpositionen
        "in" to 900, "an" to 890, "auf" to 880, "aus" to 870, "bei" to 860,
        "mit" to 850, "nach" to 840, "für" to 830, "von" to 820, "zu" to 810,
        "über" to 800, "unter" to 790, "vor" to 780, "hinter" to 770, "neben" to 760,
        "zwischen" to 750, "durch" to 740, "ohne" to 730, "gegen" to 720, "um" to 710,

        // Konjunktionen
        "und" to 950, "oder" to 900, "aber" to 890, "denn" to 880, "weil" to 870,
        "dass" to 860, "wenn" to 850, "als" to 840, "ob" to 830, "damit" to 820,
        "obwohl" to 810, "während" to 800, "bevor" to 790, "nachdem" to 780, "sobald" to 770,

        // Adjektive
        "gut" to 800, "neu" to 790, "gross" to 780, "klein" to 770, "alt" to 760,
        "jung" to 750, "lang" to 740, "kurz" to 730, "hoch" to 720, "schön" to 710,
        "schnell" to 700, "langsam" to 690, "stark" to 680, "schwach" to 670, "leicht" to 660,
        "schwer" to 650, "wichtig" to 640, "richtig" to 630, "falsch" to 620, "möglich" to 610,
        "verschieden" to 600, "besonder" to 590, "eigen" to 580, "genau" to 570, "klar" to 560,
        "einfach" to 550, "schwierig" to 540, "voll" to 530, "leer" to 520, "weit" to 510,
        "nah" to 500, "früh" to 490, "spät" to 480, "offen" to 470, "frei" to 460,
        "sicher" to 450, "ganz" to 440, "gleich" to 430, "andere" to 420, "beide" to 410,

        // Adverbien
        "nicht" to 950, "auch" to 940, "nur" to 930, "noch" to 920, "schon" to 910,
        "so" to 900, "dann" to 890, "jetzt" to 880, "hier" to 870, "dort" to 860,
        "heute" to 850, "gestern" to 840, "morgen" to 830, "immer" to 820, "nie" to 810,
        "wieder" to 800, "sehr" to 790, "viel" to 780, "mehr" to 770, "weniger" to 760,
        "fast" to 750, "etwa" to 740, "gerade" to 730, "bald" to 720, "oft" to 710,
        "manchmal" to 700, "selten" to 690, "überall" to 680, "irgendwo" to 670, "nirgends" to 660,
        "warum" to 650, "wann" to 640, "wie" to 630, "wo" to 620, "woher" to 610, "wohin" to 600,

        // Substantive - Zeit
        "Zeit" to 750, "Jahr" to 740, "Tag" to 730, "Woche" to 720, "Monat" to 710,
        "Stunde" to 700, "Minute" to 690, "Sekunde" to 680, "Morgen" to 670, "Abend" to 660,
        "Nacht" to 650, "Anfang" to 640, "Ende" to 630, "Moment" to 620, "Augenblick" to 610,

        // Substantive - Menschen
        "Mensch" to 750, "Mann" to 740, "Frau" to 730, "Kind" to 720, "Leute" to 710,
        "Familie" to 700, "Freund" to 690, "Freundin" to 680, "Eltern" to 670, "Mutter" to 660,
        "Vater" to 650, "Bruder" to 640, "Schwester" to 630, "Sohn" to 620, "Tochter" to 610,

        // Substantive - Orte
        "Haus" to 700, "Wohnung" to 690, "Zimmer" to 680, "Stadt" to 670, "Land" to 660,
        "Strasse" to 650, "Weg" to 640, "Platz" to 630, "Schule" to 620, "Arbeit" to 610,
        "Büro" to 600, "Geschäft" to 590, "Restaurant" to 580, "Hotel" to 570, "Bahnhof" to 560,

        // Substantive - Dinge
        "Ding" to 650, "Sache" to 640, "Teil" to 630, "Seite" to 620, "Bild" to 610,
        "Buch" to 600, "Brief" to 590, "Zeitung" to 580, "Telefon" to 570, "Computer" to 560,
        "Auto" to 550, "Geld" to 540, "Wasser" to 530, "Essen" to 520, "Trinken" to 510,

        // Substantive - Abstrakt
        "Leben" to 700, "Frage" to 680, "Antwort" to 670, "Problem" to 660,
        "Grund" to 650, "Beispiel" to 640, "Fall" to 630, "Art" to 620, "Weise" to 610,
        "Möglichkeit" to 600, "Bedeutung" to 590, "Entwicklung" to 580, "Beziehung" to 570, "Verbindung" to 560,

        // Zahlen
        "eins" to 600, "zwei" to 600, "drei" to 600, "vier" to 600, "fünf" to 600,
        "sechs" to 590, "sieben" to 590, "acht" to 590, "neun" to 590, "zehn" to 590,
        "elf" to 580, "zwölf" to 580, "zwanzig" to 570, "dreissig" to 560, "hundert" to 550,
        "tausend" to 540, "Million" to 530, "erste" to 520, "zweite" to 510, "dritte" to 500,

        // Schweizerdeutsche/Häufige Varianten
        "grüezi" to 400, "merci" to 390, "vielmal" to 380, "tschüss" to 370, "hallo" to 360,
        "bitte" to 700, "danke" to 690, "entschuldigung" to 680, "sorry" to 670,
        "ja" to 900, "nein" to 890, "vielleicht" to 880, "natürlich" to 870,
        "okay" to 850, "stimmt" to 830, "super" to 810,

        // WhatsApp/SMS häufig
        "hey" to 790, "hi" to 780, "bis" to 760,
        "später" to 750, "abend" to 710,
        "liebe" to 700, "grüsse" to 690, "kuss" to 680, "hdl" to 670, "lg" to 660,
        "Wochenende" to 650, "Ferien" to 640, "Party" to 630,
        "Kino" to 590, "Film" to 580, "Musik" to 570, "Sport" to 560,
    )
}
