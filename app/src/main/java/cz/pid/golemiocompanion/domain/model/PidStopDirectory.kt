package cz.pid.golemiocompanion.domain.model

import java.text.Normalizer

object PidStopDirectory {

    private fun norm(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }

    val ALL_STOPS: List<PidStopSuggestion> = listOf(
        // Mladá Boleslav
        PidStopSuggestion(
            name = "Mladá Boleslav město",
            icon = "🚆",
            typeDescription = "Vlaková stanice PID • Vlaky S30 / R43",
            subtitle = "Mladá Boleslav centrum"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,aut.st.",
            icon = "🚌",
            typeDescription = "Autobusové nádraží • Regionální a dálkové linky",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,hl.nádr.",
            icon = "🚆",
            typeDescription = "Hlavní nádraží • Vlaky ČD & Arriva, přestup bus",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,Václava Klementa",
            icon = "🚌",
            typeDescription = "Zastávka MHD & příměstské busy • Škoda Auto Muzeum",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,nám.Republiky",
            icon = "🚌",
            typeDescription = "Zastávka MHD • Náměstí Republiky",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,U Sportu",
            icon = "🚌",
            typeDescription = "Zastávka MHD • Městský stadion",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,Kosmonosy",
            icon = "🚌",
            typeDescription = "Zastávka MHD a příměstské linky",
            subtitle = "Mladá Boleslav / Kosmonosy"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav,Sahara",
            icon = "🚌",
            typeDescription = "Zastávka MHD",
            subtitle = "Mladá Boleslav"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav, Debř",
            icon = "🚆",
            typeDescription = "Železniční zastávka & bus",
            subtitle = "Mladá Boleslav Debř"
        ),
        PidStopSuggestion(
            name = "Mladá Boleslav, Čejetice",
            icon = "🚌",
            typeDescription = "Zastávka MHD",
            subtitle = "Mladá Boleslav Čejetice"
        ),

        // Praha - Železniční uzly
        PidStopSuggestion(
            name = "Praha hl.n.",
            icon = "🚆",
            typeDescription = "Hlavní nádraží Praha • Vlaky S, R, Ex, IC, EC",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Hlavní nádraží",
            icon = "🚇",
            typeDescription = "Metro C & tramvaje • Hlavní nádraží",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Praha Masarykovo n.",
            icon = "🚆",
            typeDescription = "Masarykovo nádraží • Příměstské vlaky linky S",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Praha-Smíchov",
            icon = "🚆",
            typeDescription = "Nádraží Smíchov • Vlaky linky S a rychlíky",
            subtitle = "Praha 5"
        ),
        PidStopSuggestion(
            name = "Praha-Holešovice",
            icon = "🚆",
            typeDescription = "Nádraží Holešovice • Vlaky S a R",
            subtitle = "Praha 7"
        ),
        PidStopSuggestion(
            name = "Praha-Libeň",
            icon = "🚆",
            typeDescription = "Nádraží Libeň • Koridor Kolín / Pardubice",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Praha-Vršovice",
            icon = "🚆",
            typeDescription = "Nádraží Vršovice • Koridor Benešov / Tábor",
            subtitle = "Praha 10"
        ),
        PidStopSuggestion(
            name = "Praha-Vysočany",
            icon = "🚆",
            typeDescription = "Nádraží Vysočany • Směr Lysá n.L., Nymburk, MB",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Praha-Čakovice",
            icon = "🚆",
            typeDescription = "Vlaková stanice S3 • Směr Mladá Boleslav / Všetaty",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Praha-Radotín",
            icon = "🚆",
            typeDescription = "Vlaková stanice S7 • Směr Beroun",
            subtitle = "Praha 16"
        ),

        // Praha - Metro & Klíčové přestupní uzly
        PidStopSuggestion(
            name = "Anděl",
            icon = "🚇",
            typeDescription = "Metro B & uzel tramvají",
            subtitle = "Praha 5 Smíchov"
        ),
        PidStopSuggestion(
            name = "Dejvická",
            icon = "🚇",
            typeDescription = "Metro A & autobusový terminál",
            subtitle = "Praha 6 Dejvice"
        ),
        PidStopSuggestion(
            name = "Černý Most",
            icon = "🚌",
            typeDescription = "Metro B & autobusový terminál (přímé busy MB)",
            subtitle = "Praha 14"
        ),
        PidStopSuggestion(
            name = "Zličín",
            icon = "🚌",
            typeDescription = "Metro B & autobusový terminál (letiště / Plzeň)",
            subtitle = "Praha 5"
        ),
        PidStopSuggestion(
            name = "Nádraží Veleslavín",
            icon = "🚇",
            typeDescription = "Metro A, vlak S a přímý bus na letiště",
            subtitle = "Praha 6"
        ),
        PidStopSuggestion(
            name = "Smíchovské nádraží",
            icon = "🚇",
            typeDescription = "Metro B, vlaky ČD, tramvaje a příměstské busy",
            subtitle = "Praha 5"
        ),
        PidStopSuggestion(
            name = "Florenc",
            icon = "🚇",
            typeDescription = "Metro B/C & ÚAN Florenc",
            subtitle = "Praha 8"
        ),
        PidStopSuggestion(
            name = "Můstek",
            icon = "🚇",
            typeDescription = "Přestupní stanice Metro A & B",
            subtitle = "Praha 1 Centrum"
        ),
        PidStopSuggestion(
            name = "Muzeum",
            icon = "🚇",
            typeDescription = "Přestupní stanice Metro A & C",
            subtitle = "Praha 1 Centrum"
        ),
        PidStopSuggestion(
            name = "I. P. Pavlova",
            icon = "🚇",
            typeDescription = "Metro C & klíčový uzel tramvají",
            subtitle = "Praha 2 Vinohrady"
        ),
        PidStopSuggestion(
            name = "Karlovo náměstí",
            icon = "🚇",
            typeDescription = "Metro B & tramvajový uzel",
            subtitle = "Praha 2 Nové Město"
        ),
        PidStopSuggestion(
            name = "Náměstí Míru",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje",
            subtitle = "Praha 2 Vinohrady"
        ),
        PidStopSuggestion(
            name = "Náměstí Republiky",
            icon = "🚇",
            typeDescription = "Metro B & tramvaje (Palladium)",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Staroměstská",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Malostranská",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje (Pražský hrad)",
            subtitle = "Praha 1"
        ),
        PidStopSuggestion(
            name = "Hradčanská",
            icon = "🚇",
            typeDescription = "Metro A, tramvaje & vlakové nádraží Dejvice",
            subtitle = "Praha 6"
        ),
        PidStopSuggestion(
            name = "Želivského",
            icon = "🚇",
            typeDescription = "Metro A & autobusový terminál",
            subtitle = "Praha 3 / 10"
        ),
        PidStopSuggestion(
            name = "Flora",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje",
            subtitle = "Praha 3"
        ),
        PidStopSuggestion(
            name = "Jiřího z Poděbrad",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje",
            subtitle = "Praha 3 Vinohrady"
        ),
        PidStopSuggestion(
            name = "Strašnická",
            icon = "🚇",
            typeDescription = "Metro A & tramvaje",
            subtitle = "Praha 10"
        ),
        PidStopSuggestion(
            name = "Skalka",
            icon = "🚇",
            typeDescription = "Metro A & příměstské busy",
            subtitle = "Praha 10"
        ),
        PidStopSuggestion(
            name = "Depo Hostivař",
            icon = "🚇",
            typeDescription = "Metro A & autobusový terminál",
            subtitle = "Praha 10"
        ),
        PidStopSuggestion(
            name = "Letňany",
            icon = "🚇",
            typeDescription = "Metro C & příměstské autobusové linky",
            subtitle = "Praha 18"
        ),
        PidStopSuggestion(
            name = "Prosek",
            icon = "🚇",
            typeDescription = "Metro C",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Střížkov",
            icon = "🚇",
            typeDescription = "Metro C",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Ládví",
            icon = "🚇",
            typeDescription = "Metro C & tramvaje / příměstské linky",
            subtitle = "Praha 8"
        ),
        PidStopSuggestion(
            name = "Kobylisy",
            icon = "🚇",
            typeDescription = "Metro C & tramvajový uzel",
            subtitle = "Praha 8"
        ),
        PidStopSuggestion(
            name = "Nádraží Holešovice",
            icon = "🚇",
            typeDescription = "Metro C, vlaky ČD & tramvaje",
            subtitle = "Praha 7"
        ),
        PidStopSuggestion(
            name = "Vltavská",
            icon = "🚇",
            typeDescription = "Metro C, tramvaje & vlak Praha-Bubny",
            subtitle = "Praha 7"
        ),
        PidStopSuggestion(
            name = "Vyšehrad",
            icon = "🚇",
            typeDescription = "Metro C (Kongresové centrum)",
            subtitle = "Praha 4"
        ),
        PidStopSuggestion(
            name = "Pražského povstání",
            icon = "🚇",
            typeDescription = "Metro C & tramvaje",
            subtitle = "Praha 4"
        ),
        PidStopSuggestion(
            name = "Pankrác",
            icon = "🚇",
            typeDescription = "Metro C & Arkády Pankrác",
            subtitle = "Praha 4"
        ),
        PidStopSuggestion(
            name = "Budějovická",
            icon = "🚇",
            typeDescription = "Metro C & autobusový uzel",
            subtitle = "Praha 4"
        ),
        PidStopSuggestion(
            name = "Kačerov",
            icon = "🚇",
            typeDescription = "Metro C, vlaky S8/S88 & busy",
            subtitle = "Praha 4"
        ),
        PidStopSuggestion(
            name = "Roztyly",
            icon = "🚇",
            typeDescription = "Metro C & dálkové autobusy",
            subtitle = "Praha 11"
        ),
        PidStopSuggestion(
            name = "Chodov",
            icon = "🚇",
            typeDescription = "Metro C & Westfield Chodov",
            subtitle = "Praha 11"
        ),
        PidStopSuggestion(
            name = "Opatov",
            icon = "🚇",
            typeDescription = "Metro C & autobusový uzel",
            subtitle = "Praha 11"
        ),
        PidStopSuggestion(
            name = "Háje",
            icon = "🚇",
            typeDescription = "Metro C & konečná autobusů",
            subtitle = "Praha 11"
        ),
        PidStopSuggestion(
            name = "Palmovka",
            icon = "🚇",
            typeDescription = "Metro B & uzel tramvají a busů",
            subtitle = "Praha 8"
        ),
        PidStopSuggestion(
            name = "Vysočanská",
            icon = "🚇",
            typeDescription = "Metro B & přestup na vlaky",
            subtitle = "Praha 9"
        ),
        PidStopSuggestion(
            name = "Rajská zahrada",
            icon = "🚇",
            typeDescription = "Metro B & nová železniční zastávka",
            subtitle = "Praha 14"
        ),
        PidStopSuggestion(
            name = "Nemocnice Motol",
            icon = "🚇",
            typeDescription = "Metro A & FN Motol",
            subtitle = "Praha 5"
        ),

        // Letiště
        PidStopSuggestion(
            name = "Terminál 1",
            icon = "✈️",
            typeDescription = "Letiště Václava Havla • Lety mimo Schengen",
            subtitle = "Praha Ruzyně"
        ),
        PidStopSuggestion(
            name = "Terminál 2",
            icon = "✈️",
            typeDescription = "Letiště Václava Havla • Schengenské lety",
            subtitle = "Praha Ruzyně"
        ),
        PidStopSuggestion(
            name = "Letiště Václava Havla",
            icon = "✈️",
            typeDescription = "Letiště Praha • Linky 59 (trolejbus), 119, 100",
            subtitle = "Praha Ruzyně"
        ),

        // Významná města ve Středočeském kraji PID
        PidStopSuggestion(
            name = "Kladno, aut. nádr.",
            icon = "🚌",
            typeDescription = "Autobusové nádraží Kladno",
            subtitle = "Kladno"
        ),
        PidStopSuggestion(
            name = "Kladno město",
            icon = "🚆",
            typeDescription = "Vlaková stanice PID • Linka S5 / R45",
            subtitle = "Kladno"
        ),
        PidStopSuggestion(
            name = "Kolín, žel.st.",
            icon = "🚆",
            typeDescription = "Železniční uzel • Linky S1, S2, R9, Ex",
            subtitle = "Kolín"
        ),
        PidStopSuggestion(
            name = "Nymburk hl.n.",
            icon = "🚆",
            typeDescription = "Železniční uzel • Linka S2 a rychlíky",
            subtitle = "Nymburk"
        ),
        PidStopSuggestion(
            name = "Poděbrady, žel.st.",
            icon = "🚆",
            typeDescription = "Železniční stanice lázeňského města",
            subtitle = "Poděbrady"
        ),
        PidStopSuggestion(
            name = "Beroun, žel.st.",
            icon = "🚆",
            typeDescription = "Železniční uzel • Linky S7, R16",
            subtitle = "Beroun"
        ),
        PidStopSuggestion(
            name = "Benešov u Prahy",
            icon = "🚆",
            typeDescription = "Železniční stanice • Linka S9 a rychlíky",
            subtitle = "Benešov"
        ),
        PidStopSuggestion(
            name = "Mělník, aut.st.",
            icon = "🚌",
            typeDescription = "Autobusové nádraží",
            subtitle = "Mělník"
        ),
        PidStopSuggestion(
            name = "Brandýs n.L.-St.Bol., nám.",
            icon = "🚌",
            typeDescription = "Centrální zastávka PID",
            subtitle = "Brandýs nad Labem"
        ),
        PidStopSuggestion(
            name = "Lysá nad Labem, žel.st.",
            icon = "🚆",
            typeDescription = "Železniční uzel linky S2 / S22 / S32",
            subtitle = "Lysá nad Labem"
        ),
        PidStopSuggestion(
            name = "Říčany, žel.st.",
            icon = "🚆",
            typeDescription = "Vlaková stanice linky S9",
            subtitle = "Říčany u Prahy"
        ),
        PidStopSuggestion(
            name = "Čelákovice, žel.st.",
            icon = "🚆",
            typeDescription = "Vlaková stanice linky S2 / S22",
            subtitle = "Čelákovice"
        )
    )

    fun search(query: String, limit: Int = 7): List<PidStopSuggestion> {
        val qClean = norm(query)
        if (qClean.length < 2) return emptyList()

        val tokens = qClean.split(" ").filter { it.isNotBlank() }

        // Speciální zkratky
        val isMestoQuery = qClean.contains("mesto") || qClean == "vlak mesto" || qClean == "mb mesto"
        val isMbQuery = qClean.contains("boles") || qClean.contains("mlad") || qClean == "mb"

        return ALL_STOPS.mapNotNull { stop ->
            val nameNorm = norm(stop.name)
            val subNorm = norm(stop.subtitle)
            val descNorm = norm(stop.typeDescription)
            val combined = "$nameNorm $subNorm $descNorm"

            // Skórování shody
            var score = 0

            if (nameNorm == qClean) {
                score += 100
            } else if (nameNorm.startsWith(qClean)) {
                score += 50
            } else if (nameNorm.contains(qClean)) {
                score += 30
            } else {
                val allTokensMatch = tokens.all { token -> combined.contains(token) }
                if (allTokensMatch) {
                    score += 20
                }
            }

            // Zvýhodnění pro Mladá Boleslav město při dotazu "mesto" nebo "mb"
            if (isMestoQuery && stop.name.contains("město", ignoreCase = true)) {
                score += 60
            }
            if (isMbQuery && stop.name.contains("Boleslav", ignoreCase = true)) {
                score += 25
            }

            if (score > 0) Pair(stop, score) else null
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .distinctBy { it.name }
        .take(limit)
    }
}

