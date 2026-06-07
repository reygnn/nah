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
        "verschieden" to 600, "besondere" to 590, "eigen" to 580, "genau" to 570, "klar" to 560,
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
        "Stunde" to 700, "Minute" to 690, "Sekunde" to 680, "Morgen" to 670, "Abend" to 710,
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
        "später" to 750,
        "liebe" to 700, "Grüsse" to 690, "kuss" to 680, "hdl" to 670, "lg" to 660,
        "Wochenende" to 650, "Ferien" to 640, "Party" to 630,
        "Kino" to 590, "Film" to 580, "Musik" to 570, "Sport" to 560,

        // --- Expansion to the ~1000 most-used de-CH words (chore/wordlist-1000) ---
        // Same de-CH rules: no ß ("ss"), lowercase keys deduped against the above.
        // Frequencies stay in the 200–870 band so the top function words above
        // keep ranking first.

        // Personal & possessive pronouns (object/dative forms, missing above)
        "mich" to 870, "mir" to 870, "dich" to 850, "dir" to 850,
        "ihm" to 800, "ihn" to 790, "ihnen" to 780, "uns" to 820, "euch" to 760,
        "unser" to 700, "unsere" to 690, "unseren" to 600, "euer" to 560,
        "ihre" to 800, "ihren" to 750, "ihrem" to 700, "ihrer" to 690, "seine" to 790,
        "seinen" to 730, "seinem" to 700, "seiner" to 690, "meine" to 800, "meinen" to 740,
        "meinem" to 700, "meiner" to 690, "deine" to 780, "deinen" to 720, "deinem" to 690,

        // Determiners & indefinites
        "dem" to 880, "den" to 890, "des" to 870, "dies" to 600, "diesen" to 700, "diesem" to 690,
        "man" to 850, "etwas" to 800, "nichts" to 790, "alles" to 810, "alle" to 820, "allem" to 600,
        "jeder" to 760, "jede" to 750, "jedes" to 740, "jeden" to 730, "jedem" to 700,
        "kein" to 810, "keine" to 810, "keiner" to 700, "keinen" to 720, "keinem" to 650,
        "welche" to 660, "welcher" to 650, "welches" to 640, "welchen" to 600,
        "solche" to 600, "solcher" to 540, "einige" to 650, "einigen" to 590,
        "mehrere" to 550, "manche" to 550, "jeweils" to 380, "beim" to 760, "zum" to 820, "zur" to 810,
        "vom" to 760, "ans" to 600, "aufs" to 550, "ins" to 700, "selbst" to 700, "selber" to 650,

        // Interrogatives & connectors (missing above)
        "wer" to 800, "was" to 900, "wen" to 600, "wem" to 560, "wessen" to 380,
        "wieso" to 600, "weshalb" to 560, "welch" to 400,
        "also" to 780, "doch" to 820, "eben" to 600, "halt" to 560, "zwar" to 560,
        "sondern" to 700, "trotzdem" to 600, "deshalb" to 640, "deswegen" to 560, "darum" to 560,
        "falls" to 560, "sofern" to 380, "sowie" to 500, "sowohl" to 420, "entweder" to 520,
        "weder" to 480, "jedoch" to 560, "allerdings" to 520, "ausserdem" to 560, "übrigens" to 500,
        "nämlich" to 560, "schliesslich" to 540, "dennoch" to 500, "somit" to 460, "daher" to 540,

        // Adverbs & particles
        "dabei" to 700, "dafür" to 680, "dagegen" to 560, "damals" to 560, "daran" to 620,
        "darauf" to 660, "daraus" to 540, "darin" to 580, "darüber" to 580, "darunter" to 520,
        "davon" to 640, "davor" to 540, "dazu" to 660, "dadurch" to 540, "hierher" to 420, "hierhin" to 380, "dorthin" to 460, "irgendwie" to 620, "irgendwann" to 560,
        "irgendwas" to 540, "irgendwer" to 440, "nirgendwo" to 420, "überallhin" to 200,
        "ziemlich" to 640, "besonders" to 660, "wirklich" to 720, "eigentlich" to 700, "tatsächlich" to 560,
        "sicherlich" to 540, "wahrscheinlich" to 600, "möglicherweise" to 460, "hoffentlich" to 540,
        "leider" to 660, "endlich" to 620, "plötzlich" to 600, "sofort" to 640, "zuerst" to 620, "zuletzt" to 540, "danach" to 660, "vorher" to 620, "nachher" to 540,
        "zusammen" to 700, "alleine" to 600, "allein" to 660, "wenigstens" to 520, "mindestens" to 560,
        "höchstens" to 480, "ungefähr" to 560, "genug" to 620, "kaum" to 620,
        "beinahe" to 460, "völlig" to 560, "absolut" to 540, "total" to 580,
        "überhaupt" to 620, "sowieso" to 560, "ohnehin" to 420, "weiterhin" to 460, "inzwischen" to 540, "mittlerweile" to 520, "seitdem" to 480, "vorhin" to 460,
        "draussen" to 580, "drinnen" to 540, "drüben" to 460, "oben" to 640, "unten" to 640,
        "vorne" to 600, "hinten" to 580, "links" to 640, "rechts" to 640, "mitten" to 500,
        "rüber" to 420, "rein" to 540, "raus" to 560, "rauf" to 440, "runter" to 480, "herum" to 480, "vorbei" to 560, "entlang" to 440, "quer" to 380, "rückwärts" to 380,
        "vorwärts" to 420, "geradeaus" to 480, "nochmal" to 600, "wiederum" to 420, "ebenfalls" to 540,
        "ebenso" to 520, "genauso" to 560, "anders" to 620, "andersrum" to 300, "stattdessen" to 480,

        // Verbs — infinitives (everyday & travel)
        "tun" to 700, "schlafen" to 600, "aufstehen" to 520, "aufwachen" to 420, "aufhören" to 540,
        "anfangen" to 620, "weitermachen" to 360, "anrufen" to 560, "aussehen" to 520, "einkaufen" to 520,
        "mitkommen" to 460, "mitnehmen" to 480, "ankommen" to 540, "abfahren" to 460, "umsteigen" to 420,
        "einsteigen" to 420, "aussteigen" to 420, "bezahlen" to 580, "bestellen" to 560, "reservieren" to 420,
        "buchen" to 460, "packen" to 460, "reisen" to 520, "fliegen" to 520, "schwimmen" to 460,
        "wandern" to 440, "besuchen" to 540, "einladen" to 460, "feiern" to 460, "tanzen" to 420,
        "singen" to 420, "kochen" to 520, "backen" to 420, "putzen" to 420, "waschen" to 460,
        "aufräumen" to 420, "vergessen" to 580, "erinnern" to 520, "entscheiden" to 560, "überlegen" to 520,
        "vorstellen" to 540, "vorbereiten" to 460, "benutzen" to 520, "verwenden" to 460, "probieren" to 460,
        "schmecken" to 460, "riechen" to 420, "fühlen" to 520, "spüren" to 460, "merken" to 520,
        "bemerken" to 420, "beobachten" to 420, "betrachten" to 400, "beschreiben" to 460, "erklären" to 560,
        "bedeuten" to 520, "gelten" to 460, "passieren" to 560, "geschehen" to 460, "vorkommen" to 420,
        "erscheinen" to 460, "verschwinden" to 420, "bewegen" to 460, "drehen" to 460, "werfen" to 460,
        "fangen" to 420, "schlagen" to 460, "springen" to 420, "steigen" to 460, "sinken" to 400,
        "wachsen" to 460, "sterben" to 500, "retten" to 420, "schützen" to 460, "kämpfen" to 460,
        "gewinnen" to 520, "verbessern" to 420, "verändern" to 460, "ändern" to 520, "wechseln" to 460,
        "tauschen" to 420, "teilen" to 460, "sammeln" to 420, "sparen" to 460, "ausgeben" to 420,
        "verdienen" to 460, "kosten" to 560, "besitzen" to 420, "bekommen" to 560, "kriegen" to 460,
        "schicken" to 460, "senden" to 440, "empfangen" to 400, "melden" to 420, "antworten" to 520,
        "erlauben" to 420, "verbieten" to 400, "bitten" to 500, "danken" to 460, "wünschen" to 520,
        "hoffen" to 520, "fürchten" to 420, "sorgen" to 460, "kümmern" to 420, "freuen" to 520,
        "lachen" to 520, "weinen" to 420, "schreien" to 400, "rufen" to 460, "berichten" to 420,
        "behaupten" to 400, "vermuten" to 400, "empfehlen" to 460, "vorschlagen" to 420,
        "planen" to 460, "organisieren" to 400, "leiten" to 400, "dienen" to 400, "funktionieren" to 460,
        "gelingen" to 400, "beenden" to 460, "anbieten" to 460, "liefern" to 460, "herstellen" to 400,
        "produzieren" to 400, "bauen" to 520, "zerstören" to 400, "reparieren" to 420, "malen" to 420,
        "zeichnen" to 420, "drucken" to 400, "kopieren" to 400, "speichern" to 460, "löschen" to 400,
        "starten" to 460, "stoppen" to 400, "rennen" to 440, "fehlen" to 480, "passen" to 520,
        "wirken" to 460, "betreffen" to 400, "enthalten" to 440, "bestehen" to 480, "verlassen" to 480, "behalten" to 440, "erkennen" to 480, "vergleichen" to 440, "verbinden" to 440,
        "trennen" to 440, "wiederholen" to 440, "üben" to 460, "studieren" to 460,
        "unterrichten" to 380, "rechnen" to 460, "zählen" to 480, "messen" to 420, "wiegen" to 400,
        "schneiden" to 440, "kleben" to 380, "binden" to 380, "drücken" to 460,
        "klicken" to 420, "tippen" to 440, "schauen" to 560, "gucken" to 440, "blicken" to 400,
        "treten" to 420, "klingen" to 420, "klingeln" to 400, "läuten" to 360, "wecken" to 400,

        // Verbs — frequent conjugated & participle forms
        "tut" to 640, "tat" to 480, "getan" to 540, "möchte" to 850, "möchten" to 800,
        "möchtest" to 600, "mag" to 700, "magst" to 440, "könnte" to 760, "könnten" to 650,
        "könntest" to 460, "müsste" to 600, "müssten" to 500, "soll" to 820, "sollst" to 540,
        "sollte" to 720, "sollten" to 620, "solltest" to 440, "wollten" to 640, "wolltest" to 420,
        "würde" to 820, "würden" to 720, "würdest" to 520, "wäre" to 760, "wären" to 650,
        "wärst" to 420, "hätte" to 760, "hätten" to 650, "hättest" to 460, "wurden" to 800,
        "geworden" to 700, "worden" to 660, "gewesen" to 750, "gehabt" to 600, "sei" to 620,
        "seid" to 560, "warst" to 700, "wart" to 520, "habt" to 620, "hast" to 760,
        "gemacht" to 700, "gesagt" to 700, "gegangen" to 600, "gekommen" to 660, "gesehen" to 660,
        "gegeben" to 600, "gefunden" to 560, "genommen" to 540, "gebracht" to 500, "gehalten" to 460,
        "geblieben" to 460, "gelassen" to 460, "gewusst" to 460, "gedacht" to 500, "gesprochen" to 460,
        "geschrieben" to 460, "gelesen" to 460, "gefragt" to 460, "geglaubt" to 420, "gehört" to 520,
        "bekam" to 460, "bekommt" to 520, "nennt" to 460, "nannte" to 400,
        "nennen" to 540, "bleibt" to 600, "blieb" to 500, "nimmt" to 600, "nahm" to 540,
        "lässt" to 560, "liess" to 460, "fährt" to 560, "fuhr" to 460, "läuft" to 520,
        "lief" to 460, "trägt" to 460, "trug" to 420, "hält" to 520, "hielt" to 460,
        "fällt" to 460, "fiel" to 420, "sieht" to 620, "sah" to 560, "findet" to 560,
        "fand" to 500, "denkt" to 520, "dachte" to 460, "heisst" to 620, "hiess" to 460,
        "steht" to 620, "stand" to 560, "liegt" to 620, "lag" to 500, "spricht" to 460,
        "sprach" to 420, "schaut" to 480, "braucht" to 560, "brachte" to 420, "wartet" to 460,
        "spielt" to 480, "arbeitet" to 480, "lebt" to 460, "glaubt" to 460, "fühlt" to 440,

        // Adjectives (missing above)
        "schlecht" to 700, "böse" to 520, "nett" to 580, "freundlich" to 540, "höflich" to 440,
        "ehrlich" to 500, "fleissig" to 440, "faul" to 460, "müde" to 580, "wach" to 460,
        "fertig" to 640, "bereit" to 560, "fähig" to 440, "nötig" to 540, "unmöglich" to 480, "nützlich" to 460, "praktisch" to 520, "wertvoll" to 440, "teuer" to 600,
        "billig" to 520, "günstig" to 500, "gratis" to 460, "reich" to 500, "arm" to 480,
        "glücklich" to 600, "traurig" to 540, "fröhlich" to 480, "zufrieden" to 540, "stolz" to 460,
        "wütend" to 440, "ruhig" to 540, "nervös" to 440, "ängstlich" to 380, "mutig" to 440,
        "krank" to 580, "gesund" to 540, "fit" to 460, "schwanger" to 360, "verletzt" to 420,
        "warm" to 620, "kalt" to 620, "heiss" to 580, "kühl" to 480, "trocken" to 500,
        "nass" to 480, "feucht" to 420, "sauber" to 560, "schmutzig" to 480, "dreckig" to 420,
        "ordentlich" to 420, "hübsch" to 500, "hässlich" to 440, "süss" to 540, "sauer" to 480,
        "bitter" to 420, "salzig" to 380, "scharf" to 480, "lecker" to 520, "frisch" to 540,
        "modern" to 520, "altmodisch" to 360, "berühmt" to 480,
        "bekannt" to 580, "fremd" to 500, "eigenartig" to 360, "seltsam" to 480, "komisch" to 540,
        "lustig" to 540, "ernst" to 500, "egal" to 600, "wahr" to 600,
        "echt" to 600, "real" to 460, "deutlich" to 540, "kompliziert" to 480,
        "komplex" to 400, "logisch" to 420, "normal" to 580, "üblich" to 440, "gewöhnlich" to 440,
        "häufig" to 520, "ständig" to 500, "dauernd" to 440, "schmal" to 440, "breit" to 540, "dick" to 540, "dünn" to 520, "tief" to 540,
        "flach" to 440, "rund" to 500, "eckig" to 360, "spitz" to 400, "stumpf" to 340,
        "glatt" to 440, "rau" to 380, "weich" to 520, "hart" to 560, "fest" to 580,
        "locker" to 460, "eng" to 520, "lose" to 400, "schief" to 380,
        "krumm" to 340, "ähnlich" to 540, "unterschiedlich" to 480, "halb" to 580, "doppelt" to 480, "einzeln" to 480, "einzig" to 520, "gemeinsam" to 540,
        "öffentlich" to 460, "privat" to 480, "persönlich" to 520, "allgemein" to 480, "speziell" to 480,
        "gefährlich" to 520, "harmlos" to 360, "vorsichtig" to 480, "wild" to 480,
        "lebendig" to 420, "tot" to 500, "leise" to 500, "laut" to 580, "still" to 540,
        "dunkel" to 580, "hell" to 560, "bunt" to 460, "farbig" to 400, "blass" to 360,

        // Colours
        "Farbe" to 560, "rot" to 640, "blau" to 620, "grün" to 620, "gelb" to 580,
        "schwarz" to 620, "braun" to 540, "grau" to 540, "rosa" to 480, "lila" to 420,
        "violett" to 360, "golden" to 400, "silbern" to 360, "orange" to 520,

        // Nouns — time & calendar
        "Montag" to 620, "Dienstag" to 600, "Mittwoch" to 600, "Donnerstag" to 590, "Freitag" to 610,
        "Samstag" to 600, "Sonntag" to 600, "Januar" to 540, "Februar" to 520, "März" to 540,
        "April" to 540, "Mai" to 560, "Juni" to 540, "Juli" to 540, "August" to 540,
        "September" to 530, "Oktober" to 530, "November" to 530, "Dezember" to 540, "Frühling" to 500,
        "Sommer" to 560, "Herbst" to 520, "Winter" to 540, "Uhr" to 640, "Datum" to 520,
        "Termin" to 540, "Geburtstag" to 560, "Feiertag" to 460, "Mittag" to 520, "Mitternacht" to 420,
        "Vergangenheit" to 420, "Zukunft" to 520, "Gegenwart" to 380, "Jahrhundert" to 440, "Jahreszeit" to 380,
        "Uhrzeit" to 400, "Verspätung" to 420, "Pause" to 520, "Frist" to 380, "Weile" to 420,

        // Nouns — people & roles
        "Junge" to 560, "Mädchen" to 560, "Baby" to 520, "Herr" to 620, "Dame" to 480,
        "Person" to 600, "Gast" to 520, "Nachbar" to 480, "Kollege" to 520, "Chef" to 520,
        "Lehrer" to 520, "Schüler" to 500, "Student" to 500, "Arzt" to 540, "Patient" to 440,
        "Kunde" to 500, "Gruppe" to 540, "Team" to 480, "Paar" to 500, "Ehemann" to 440,
        "Ehefrau" to 440, "Oma" to 500, "Opa" to 500, "Grossmutter" to 460, "Grossvater" to 440,
        "Enkel" to 400, "Onkel" to 480, "Tante" to 480, "Cousin" to 420, "Verwandte" to 400,
        "Bekannte" to 420, "Nachbarin" to 420, "Kollegin" to 480, "Frauen" to 540, "Männer" to 540,
        "Kinder" to 620, "Mitarbeiter" to 440, "Besucher" to 400, "Fahrer" to 440, "Polizist" to 420,

        // Nouns — places, travel & buildings
        "Dorf" to 500, "Ort" to 540, "Gegend" to 460, "Region" to 440, "Welt" to 600,
        "Erde" to 540, "Himmel" to 520, "Meer" to 520, "See" to 500, "Fluss" to 480,
        "Berg" to 540, "Tal" to 440, "Wald" to 520, "Wiese" to 440, "Feld" to 460,
        "Garten" to 540, "Park" to 520, "Strand" to 500, "Insel" to 480, "Natur" to 500,
        "Umgebung" to 420, "Norden" to 460, "Süden" to 460, "Osten" to 440, "Westen" to 440,
        "Richtung" to 500, "Grenze" to 460, "Kanton" to 480, "Schweiz" to 620, "Deutschland" to 540,
        "Europa" to 480, "Ausland" to 440, "Heimat" to 440, "Zuhause" to 520, "Flughafen" to 520,
        "Flugzeug" to 500, "Zug" to 560, "Bus" to 560, "Tram" to 520, "Taxi" to 500,
        "Schiff" to 460, "Velo" to 540, "Fahrrad" to 500, "Autobahn" to 460, "Brücke" to 460,
        "Tunnel" to 420, "Kreuzung" to 420, "Ampel" to 440, "Parkplatz" to 480, "Garage" to 440,
        "Tankstelle" to 440, "Haltestelle" to 460, "Gleis" to 440, "Perron" to 420, "Ticket" to 500,
        "Fahrkarte" to 440, "Fahrplan" to 440, "Reise" to 540, "Urlaub" to 500, "Ausflug" to 460,
        "Wanderung" to 420, "Gepäck" to 460, "Koffer" to 460, "Tasche" to 540, "Rucksack" to 460,
        "Pass" to 480, "Ausweis" to 440, "Karte" to 560, "Stadtplan" to 380, "Aussicht" to 440,
        "Unterkunft" to 420, "Zelt" to 400, "Camping" to 400, "Rezeption" to 400, "Schlüssel" to 520,
        "Aufzug" to 420, "Lift" to 440, "Treppe" to 480, "Eingang" to 480, "Ausgang" to 480,
        "Tür" to 580, "Fenster" to 540, "Wand" to 500, "Boden" to 500, "Dach" to 460,
        "Keller" to 440, "Balkon" to 440, "Terrasse" to 420, "Küche" to 540, "Bad" to 520,
        "Toilette" to 500, "Schlafzimmer" to 440, "Wohnzimmer" to 460, "Flur" to 400, "Möbel" to 440,
        "Tisch" to 560, "Stuhl" to 520, "Sessel" to 420, "Sofa" to 460, "Bett" to 560,
        "Schrank" to 460, "Regal" to 420, "Lampe" to 460, "Spiegel" to 440, "Teppich" to 400,
        "Kissen" to 420, "Decke" to 460, "Markt" to 480, "Laden" to 480,
        "Apotheke" to 460, "Krankenhaus" to 480, "Spital" to 480, "Bank" to 500, "Post" to 480,
        "Kirche" to 460, "Museum" to 460, "Theater" to 440, "Bibliothek" to 420, "Universität" to 440,

        // Nouns — food & drink
        "Brot" to 560, "Brötli" to 460, "Butter" to 480, "Käse" to 520, "Wurst" to 480,
        "Fleisch" to 500, "Fisch" to 500, "Huhn" to 420, "Milch" to 520, "Kaffee" to 580,
        "Tee" to 540, "Bier" to 520, "Wein" to 500, "Saft" to 480, "Zucker" to 460,
        "Salz" to 480, "Pfeffer" to 380, "Mehl" to 400, "Reis" to 460, "Nudeln" to 460,
        "Kartoffel" to 460, "Gemüse" to 500, "Obst" to 480, "Apfel" to 500, "Banane" to 440,
        "Tomate" to 440, "Gurke" to 400, "Salat" to 480, "Zwiebel" to 400, "Suppe" to 480,
        "Pizza" to 500, "Schokolade" to 500, "Kuchen" to 480, "Eis" to 500, "Frühstück" to 500,
        "Mittagessen" to 480, "Abendessen" to 480, "Mahlzeit" to 420, "Hunger" to 520, "Durst" to 480,
        "Geschmack" to 420, "Rezept" to 440, "Speisekarte" to 400, "Teller" to 460, "Glas" to 500,
        "Tasse" to 480, "Gabel" to 420, "Messer" to 460, "Löffel" to 440, "Flasche" to 480,

        // Nouns — body & health
        "Kopf" to 560, "Gesicht" to 540, "Auge" to 540, "Augen" to 540, "Ohr" to 480,
        "Nase" to 480, "Mund" to 500, "Zahn" to 460, "Zähne" to 460, "Haar" to 480,
        "Haare" to 500, "Hals" to 480, "Schulter" to 440, "Arm" to 500, "Hand" to 580,
        "Hände" to 500, "Finger" to 500, "Bein" to 500, "Fuss" to 520, "Füsse" to 460,
        "Knie" to 420, "Bauch" to 460, "Rücken" to 480, "Brust" to 440, "Herz" to 560,
        "Blut" to 480, "Haut" to 460, "Knochen" to 400, "Körper" to 500, "Gehirn" to 400,
        "Gesundheit" to 460, "Krankheit" to 440, "Schmerz" to 460, "Schmerzen" to 460, "Fieber" to 440,
        "Husten" to 400, "Erkältung" to 400, "Medizin" to 440, "Medikament" to 420, "Notfall" to 460,

        // Nouns — clothing
        "Kleid" to 460, "Hose" to 480, "Hemd" to 440, "Pullover" to 440, "Jacke" to 480,
        "Mantel" to 420, "Schuh" to 460, "Schuhe" to 500, "Socke" to 400, "Socken" to 440,
        "Hut" to 420, "Mütze" to 420, "Schal" to 400, "Handschuh" to 380, "Gürtel" to 400,
        "Brille" to 460, "Ring" to 440, "Kleidung" to 460, "Stoff" to 400, "Knopf" to 380,

        // Nouns — nature, weather & animals
        "Sonne" to 540, "Mond" to 480, "Stern" to 460, "Sterne" to 460, "Wolke" to 440,
        "Regen" to 520, "Schnee" to 500, "Wind" to 500, "Sturm" to 440, "Nebel" to 420,
        "Wetter" to 560, "Klima" to 440, "Temperatur" to 440, "Grad" to 480, "Feuer" to 480,
        "Luft" to 500, "Stein" to 460, "Sand" to 420, "Gras" to 420, "Baum" to 500,
        "Bäume" to 460, "Blume" to 460, "Blatt" to 440, "Tier" to 500, "Tiere" to 500,
        "Hund" to 540, "Katze" to 520, "Vogel" to 460, "Pferd" to 460, "Kuh" to 420,
        "Maus" to 420, "Insekt" to 360,

        // Nouns — abstract, work, money, school, tech
        "Sprache" to 500, "Name" to 580, "Nummer" to 540, "Zahl" to 520, "Wahrheit" to 460,
        "Idee" to 540, "Gedanke" to 480, "Meinung" to 500, "Wunsch" to 480, "Traum" to 500,
        "Gefühl" to 520, "Angst" to 520, "Freude" to 480, "Glück" to 540, "Hoffnung" to 460,
        "Sorge" to 440, "Spass" to 540, "Lust" to 480, "Mut" to 440, "Kraft" to 480,
        "Energie" to 460, "Gefahr" to 460, "Sicherheit" to 460, "Unfall" to 460, "Fehler" to 520,
        "Polizei" to 480, "Feuerwehr" to 400, "Hilfe" to 560, "Preis" to 520, "Rechnung" to 460,
        "Quittung" to 380, "Kasse" to 440, "Konto" to 460, "Münze" to 360, "Franken" to 520,
        "Rappen" to 400, "Euro" to 480, "Bargeld" to 440, "Lohn" to 440, "Beruf" to 480,
        "Job" to 520, "Stelle" to 480, "Firma" to 480, "Unternehmen" to 440, "Fabrik" to 400,
        "Einkauf" to 440, "Angebot" to 460, "Rabatt" to 400, "Werbung" to 420, "Produkt" to 440,
        "Ware" to 400, "Qualität" to 440, "Menge" to 480, "Anzahl" to 440, "Summe" to 420,
        "Hälfte" to 460, "Viertel" to 460, "Prozent" to 460, "Liste" to 480, "Plan" to 500,
        "Projekt" to 460, "Aufgabe" to 480, "Ziel" to 500, "Zweck" to 420, "Sinn" to 500,
        "Erfolg" to 500, "Chance" to 480, "Risiko" to 440, "Vorteil" to 460, "Nachteil" to 440,
        "Unterschied" to 480, "Vergleich" to 420, "Kontakt" to 480, "Gespräch" to 500, "Diskussion" to 420,
        "Streit" to 440, "Lösung" to 480, "Ergebnis" to 480, "Wirkung" to 420, "Ursache" to 420,
        "Tatsache" to 420, "Information" to 480, "Nachricht" to 520, "Bericht" to 440, "Artikel" to 440,
        "Text" to 480, "Satz" to 480, "Buchstabe" to 420, "Thema" to 480, "Geschichte" to 500,
        "Erinnerung" to 440, "Erfahrung" to 460, "Bildung" to 440, "Ausbildung" to 440, "Studium" to 440,
        "Prüfung" to 460, "Note" to 460, "Klasse" to 480, "Unterricht" to 440, "Hausaufgabe" to 400,
        "Heft" to 400, "Stift" to 420, "Papier" to 460, "Bleistift" to 400, "Handy" to 540,
        "Internet" to 500, "Mail" to 480, "Passwort" to 440, "App" to 500, "Programm" to 460,
        "Datei" to 440, "Foto" to 500, "Video" to 480, "Kamera" to 460, "Bildschirm" to 440,
        "Taste" to 420, "Tastatur" to 440, "Drucker" to 400, "Akku" to 440, "Batterie" to 420,
        "Kabel" to 420, "Strom" to 460, "Licht" to 540, "Fernseher" to 440, "Radio" to 440,
    )
}
