package cz.pid.golemiocompanion.domain.model

import java.text.Normalizer
import kotlin.math.*

data class PidStop(
    val displayName: String,
    val golemioPrimaryName: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val isTrain: Boolean = false,
    val alternativeNames: List<String> = emptyList()
) {
    /**
     * Vrací všechny varianty názvu zastávky (s mezerou i bez mezery za čárkou),
     * aby Golemio GTFS vyhledávání spolehlivě našlo regionální i městské zastávky.
     */
    fun getAllQueryNames(): List<String> {
        val noSpace = golemioPrimaryName.replace(", ", ",")
        val withSpace = golemioPrimaryName.replace(",", ", ").replace("  ", " ")
        return (listOf(golemioPrimaryName, noSpace, withSpace) + alternativeNames)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

object PidStopDirectory {

    val allStops: List<PidStop> = listOf(
        // =====================================================================
        // OBEC ŽĎÁR (U MNICHOVA HRADIŠTĚ / TURNOVA) A BEZPROSTŘEDNÍ OKOLÍ
        // =====================================================================
        PidStop("Žďár", "Žďár", "Žďár a okolí", 50.5397, 15.0817, false, listOf("Žďár,obec", "Žďár,u kovárny")),
        PidStop("Žďár, Doubrava", "Žďár,Doubrava", "Žďár a okolí", 50.5453, 15.0652, false, listOf("Doubrava")),
        PidStop("Žďár, Břehy", "Žďár,Břehy", "Žďár a okolí", 50.5518, 15.0734, false, listOf("Břehy")),
        PidStop("Žďár, Příhrazy", "Žďár,Příhrazy", "Žďár a okolí", 50.5280, 15.0710, false, listOf("Příhrazy")),
        PidStop("Žďár, Žehrov", "Žďár,Žehrov", "Žďár a okolí", 50.5298, 15.0894, false, listOf("Žehrov")),
        PidStop("Žďár, Skokovy", "Žďár,Skokovy", "Žďár a okolí", 50.5342, 15.0991, false, listOf("Skokovy", "Žďár,Skokovy,lázně")),
        PidStop("Loukov, žel.st.", "Loukov,žel.st.", "Žďár a okolí", 50.5607, 15.0489, false, listOf("Loukov, žel. st.", "Loukov žel.st.", "Loukov nádraží")),
        PidStop("Loukov u Mn.Hradiště", "Loukov u Mnichova Hradiště", "Žďár a okolí", 50.5607, 15.0489, true, listOf("Loukov u Mn.Hradiště", "Loukov vlak", "ČD Loukov")),
        PidStop("Loukov", "Loukov", "Žďár a okolí", 50.5568, 15.0531, false, listOf("Loukov,u hřiště", "Loukov,obec")),
        PidStop("Loukov, Na Veselici", "Loukov,Na Veselici", "Žďár a okolí", 50.5627, 15.0569, false, listOf("Loukov, Na Veselici", "Na Veselici")),
        PidStop("Loukov, škola", "Loukov,škola", "Žďár a okolí", 50.5583, 15.0526, false, listOf("Loukov, škola")),
        PidStop("Loukov, záv.", "Loukov,záv.", "Žďár a okolí", 50.5585, 15.0488),
        PidStop("Loukovec", "Loukovec", "Žďár a okolí", 50.5472, 15.0258),
        PidStop("Loukovec, Hubálov", "Loukovec,Hubálov", "Žďár a okolí", 50.5508, 15.0189, false, listOf("Hubálov")),
        PidStop("Březina u Mn.Hradiště", "Březina u Mnichova Hradiště", "Žďár a okolí", 50.5482, 15.0345, isTrain = true, listOf("Březina u Mn. Hradiště", "Březina vlak")),
        PidStop("Březina", "Březina", "Žďár a okolí", 50.5447, 15.0339, isTrain = false),
        PidStop("Svijany", "Svijany", "Žďár a okolí", 50.5732, 15.0558, false, listOf("Svijany,pivovar")),
        PidStop("Svijanský Újezd", "Svijanský Újezd", "Žďár a okolí", 50.5746, 15.0435),
        PidStop("Příšovice", "Příšovice", "Žďár a okolí", 50.5761, 15.0867, false, listOf("Příšovice,kult.dům")),
        PidStop("Přepeře", "Přepeře", "Žďár a okolí", 50.5819, 15.1092, false, listOf("Přepeře,samoobsluha")),
        PidStop("Všeň", "Všeň", "Žďár a okolí", 50.5594, 15.1072, false, listOf("Všeň,škola")),
        PidStop("Všeň, Ploukonice", "Všeň,Ploukonice", "Žďár a okolí", 50.5661, 15.0989, false, listOf("Ploukonice")),
        PidStop("Olešnice", "Olešnice", "Žďár a okolí", 50.5419, 15.1189, false, listOf("Olešnice,rozhl.")),
        PidStop("Kacanovy", "Kacanovy", "Žďár a okolí", 50.5503, 15.1436, false, listOf("Kacanovy,u koupaliště")),
        PidStop("Vyskeř", "Vyskeř", "Žďár a okolí", 50.5306, 15.1586),

        // =====================================================================
        // MNICHOVO HRADIŠTĚ A OKOLÍ
        // =====================================================================
        PidStop("Mnichovo Hradiště, nám.", "Mnichovo Hradiště,nám.", "Mnichovo Hradiště", 50.5273, 14.9714),
        PidStop("Mnichovo Hradiště, žel.st.", "Mnichovo Hradiště,žel.st.", "Mnichovo Hradiště", 50.5222, 14.9792, isTrain = true, listOf("Mnichovo Hradiště")),
        PidStop("Mnichovo Hradiště, Závodí", "Mnichovo Hradiště,Závodí", "Mnichovo Hradiště", 50.5245, 14.9610),
        PidStop("Mnichovo Hradiště, zdrav.stř.", "Mnichovo Hradiště,zdrav.stř.", "Mnichovo Hradiště", 50.5298, 14.9745),
        PidStop("Mnichovo Hradiště, Lidická", "Mnichovo Hradiště,Lidická", "Mnichovo Hradiště", 50.5315, 14.9790),
        PidStop("Mnichovo Hradiště, Dneboh", "Mnichovo Hradiště,Dneboh", "Mnichovo Hradiště", 50.5228, 15.0208, false, listOf("Dneboh")),
        PidStop("Mnichovo Hradiště, Olšina", "Mnichovo Hradiště,Olšina", "Mnichovo Hradiště", 50.5264, 15.0419, false, listOf("Olšina")),
        PidStop("Mnichovo Hradiště, Hoškovice", "Mnichovo Hradiště,Hoškovice", "Mnichovo Hradiště", 50.5381, 15.0069, false, listOf("Hoškovice")),
        PidStop("Mnichovo Hradiště, Lhotice", "Mnichovo Hradiště,Lhotice", "Mnichovo Hradiště", 50.5064, 14.9850, false, listOf("Lhotice")),
        PidStop("Mnichovo Hradiště, Dobrá Voda", "Mnichovo Hradiště,Dobrá Voda", "Mnichovo Hradiště", 50.5097, 15.0022, false, listOf("Dobrá Voda")),
        PidStop("Mnichovo Hradiště, Veselá", "Mnichovo Hradiště,Veselá", "Mnichovo Hradiště", 50.5008, 14.9717, false, listOf("Veselá")),
        PidStop("Mnichovo Hradiště, Hněvousice", "Mnichovo Hradiště,Hněvousice", "Mnichovo Hradiště", 50.5333, 14.9567),
        PidStop("Mnichovo Hradiště, Podolí", "Mnichovo Hradiště,Podolí", "Mnichovo Hradiště", 50.5489, 14.9839),
        PidStop("Mnichovo Hradiště, Sychrov", "Mnichovo Hradiště,Sychrov", "Mnichovo Hradiště", 50.5439, 14.9658),
        PidStop("Klášter Hradiště nad Jizerou", "Klášter Hradiště nad Jizerou", "Mnichovo Hradiště", 50.5256, 14.9469, false, listOf("Klášter Hradiště n.Jiz.")),
        PidStop("Ptýrov", "Ptýrov", "Mnichovo Hradiště", 50.5117, 14.9608),
        PidStop("Boseň", "Boseň", "Mnichovo Hradiště", 50.5119, 15.0278),
        PidStop("Kněžmost", "Kněžmost", "Mnichovo Hradiště", 50.4892, 15.0419, false, listOf("Kněžmost,nám.", "Kněžmost,pošta")),
        PidStop("Bakov nad Jizerou, nám.", "Bakov nad Jizerou,nám.", "Mnichovo Hradiště", 50.4828, 14.9411),
        PidStop("Bakov nad Jizerou, žel.st.", "Bakov nad Jizerou,žel.st.", "Mnichovo Hradiště", 50.4789, 14.9297, isTrain = true, listOf("Bakov nad Jizerou", "Bakov nad Jizerou město")),
        PidStop("Chocnějovice", "Chocnějovice", "Mnichovo Hradiště", 50.5736, 14.9650),
        PidStop("Mohelnice nad Jizerou", "Mohelnice nad Jizerou", "Mnichovo Hradiště", 50.5625, 14.9742),
        PidStop("Jivina", "Jivina", "Mnichovo Hradiště", 50.5469, 14.9481),

        // =====================================================================
        // MLADÁ BOLESLAV A OKOLÍ
        // =====================================================================
        PidStop("Mladá Boleslav, aut.st.", "Mladá Boleslav,aut.st.", "Mladá Boleslav", 50.4150, 14.9094),
        PidStop("Mladá Boleslav, město", "Mladá Boleslav,město", "Mladá Boleslav", 50.4148, 14.9082, isTrain = true, listOf("Mladá Boleslav město")),
        PidStop("Mladá Boleslav, hl.n.", "Mladá Boleslav,hl.n.", "Mladá Boleslav", 50.4055, 14.8920, isTrain = true, listOf("Mladá Boleslav hl.n.")),
        PidStop("Mladá Boleslav, Jičínská", "Mladá Boleslav,Jičínská", "Mladá Boleslav", 50.4192, 14.9218),
        PidStop("Mladá Boleslav, U Sportu", "Mladá Boleslav,U Sportu", "Mladá Boleslav", 50.4225, 14.9126),
        PidStop("Mladá Boleslav, Klaudiánova nem.", "Mladá Boleslav,Klaudiánova nemocnice", "Mladá Boleslav", 50.4182, 14.9073, false, listOf("Mladá Boleslav,nemocnice")),
        PidStop("Mladá Boleslav, Václavkova", "Mladá Boleslav,Václavkova", "Mladá Boleslav", 50.4201, 14.9089),
        PidStop("Mladá Boleslav, Škoda, Hlavní brána", "Mladá Boleslav,Škoda,Hlavní brána", "Mladá Boleslav", 50.4188, 14.9135),
        PidStop("Mladá Boleslav, Škoda, 6.brána", "Mladá Boleslav,Škoda,6.brána", "Mladá Boleslav", 50.4239, 14.9197),
        PidStop("Mladá Boleslav, Škoda, 7.brána", "Mladá Boleslav,Škoda,7.brána", "Mladá Boleslav", 50.4272, 14.9248),
        PidStop("Mladá Boleslav, Česana", "Mladá Boleslav,Česana", "Mladá Boleslav", 50.4098, 14.9195),
        PidStop("Mladá Boleslav, Zalužanská", "Mladá Boleslav,Zalužanská", "Mladá Boleslav", 50.4142, 14.9265),
        PidStop("Mladá Boleslav, Na Radouči", "Mladá Boleslav,Na Radouči", "Mladá Boleslav", 50.4258, 14.8995),
        PidStop("Mladá Boleslav, Rozvoj", "Mladá Boleslav,Rozvoj", "Mladá Boleslav", 50.4285, 14.9062),
        PidStop("Mladá Boleslav, Olympia", "Mladá Boleslav,Olympia", "Mladá Boleslav", 50.4178, 14.9389),
        PidStop("Mladá Boleslav, Debř", "Mladá Boleslav,Debř", "Mladá Boleslav", 50.4436, 14.8967, isTrain = true, listOf("Mladá Boleslav-Debř")),
        PidStop("Mladá Boleslav, Bezděčín", "Mladá Boleslav,Bezděčín", "Mladá Boleslav", 50.3861, 14.8964),
        PidStop("Mladá Boleslav, Čejetičky", "Mladá Boleslav,Čejetičky", "Mladá Boleslav", 50.4086, 14.8894),
        PidStop("Kosmonosy, nám.", "Kosmonosy,nám.", "Mladá Boleslav", 50.4397, 14.9258, false, listOf("Kosmonosy")),
        PidStop("Kosmonosy, Psychiatrická nem.", "Kosmonosy,Psychiatrická nemocnice", "Mladá Boleslav", 50.4375, 14.9231),
        PidStop("Bradlec", "Bradlec", "Mladá Boleslav", 50.4536, 14.9178, false, listOf("Bradlec,obec")),
        PidStop("Josefův Důl", "Josefův Důl", "Mladá Boleslav", 50.4472, 14.9006),
        PidStop("Řepov", "Řepov", "Mladá Boleslav", 50.4067, 14.9606),
        PidStop("Kolomuty", "Kolomuty", "Mladá Boleslav", 50.4044, 14.9806),
        PidStop("Plazy", "Plazy", "Mladá Boleslav", 50.4150, 14.9917),
        PidStop("Březno", "Březno", "Mladá Boleslav", 50.4058, 15.0086, false, listOf("Březno,nám.")),
        PidStop("Dolní Bousov, nám.", "Dolní Bousov,nám.", "Mladá Boleslav", 50.4392, 15.1269, false, listOf("Dolní Bousov")),
        PidStop("Dobrovice, nám.", "Dobrovice,nám.", "Mladá Boleslav", 50.3697, 14.9636, false, listOf("Dobrovice,aut.st.")),
        PidStop("Benátky nad Jizerou, aut.st.", "Benátky nad Jizerou,aut.st.", "Mladá Boleslav", 50.2914, 14.8272),

        // =====================================================================
        // TURNOV A OKOLÍ
        // =====================================================================
        PidStop("Turnov, terminál u žel.st.", "Turnov,terminál u žel.st.", "Turnov", 50.5912, 15.1444, false, listOf("Turnov,aut.nádr.", "Turnov,u žel.st.")),
        PidStop("Turnov, žel.st.", "Turnov,žel.st.", "Turnov", 50.5915, 15.1438, isTrain = true, listOf("Turnov", "Turnov město")),
        PidStop("Turnov, nám.", "Turnov,nám.", "Turnov", 50.5873, 15.1568, false, listOf("Turnov,náměstí Českého ráje")),
        PidStop("Turnov, U masných krámů", "Turnov,U masných krámů", "Turnov", 50.5865, 15.1542),
        PidStop("Turnov, Sobotecká", "Turnov,Sobotecká", "Turnov", 50.5822, 15.1585),
        PidStop("Turnov, nemocnice", "Turnov,nemocnice", "Turnov", 50.5828, 15.1489),
        PidStop("Turnov, Fučíkova", "Turnov,Fučíkova", "Turnov", 50.5901, 15.1482),
        PidStop("Turnov, Ohrazenice", "Turnov,Ohrazenice", "Turnov", 50.5958, 15.1264, false, listOf("Ohrazenice")),
        PidStop("Turnov, Mašov", "Turnov,Mašov", "Turnov", 50.5697, 15.1494, false, listOf("Turnov,Mašov,návrší")),
        PidStop("Turnov, Pelešany", "Turnov,Pelešany", "Turnov", 50.5681, 15.1669),
        PidStop("Turnov, Daliměřice", "Turnov,Daliměřice", "Turnov", 50.5989, 15.1519),
        PidStop("Turnov, Hruštice", "Turnov,Hruštice", "Turnov", 50.5928, 15.1714),
        PidStop("Turnov, Dolánky", "Turnov,Dolánky", "Turnov", 50.6061, 15.1711),
        PidStop("Karlovice, Sedmihorky", "Karlovice,Sedmihorky", "Turnov", 50.5586, 15.1958, isTrain = true, listOf("Sedmihorky")),
        PidStop("Hrubá Skála", "Hrubá Skála", "Turnov", 50.5453, 15.1950),
        PidStop("Rovensko pod Troskami, nám.", "Rovensko pod Troskami,nám.", "Turnov", 50.5364, 15.2589),
        PidStop("Mírová pod Kozákovem", "Mírová pod Kozákovem", "Turnov", 50.5947, 15.2067),
        PidStop("Malá Skála", "Malá Skála", "Turnov", 50.6406, 15.1939),

        // =====================================================================
        // PRAHA (HLAVNÍ PŘESTUPNÍ UZLY PRO LINKY Z MB / MH / TURNOVA)
        // =====================================================================
        PidStop("Praha, Černý Most", "Černý Most", "Praha", 50.1092, 14.5772),
        PidStop("Praha, Hlavní nádraží", "Hlavní nádraží", "Praha", 50.0831, 14.4353, isTrain = true, listOf("Praha hl.n.")),
        PidStop("Praha, Masarykovo nádraží", "Masarykovo nádraží", "Praha", 50.0878, 14.4331, isTrain = true),
        PidStop("Praha, Florenc", "Florenc", "Praha", 50.0908, 14.4392),
        PidStop("Praha, Anděl", "Anděl", "Praha", 50.0715, 14.4036),
        PidStop("Praha, Smíchovské nádraží", "Smíchovské nádraží", "Praha", 50.0613, 14.4087, isTrain = true),
        PidStop("Praha, Nádraží Vysočany", "Vysočanská", "Praha", 50.1117, 14.5022, isTrain = true, listOf("Praha-Vysočany")),
        PidStop("Praha, Letňany", "Letňany", "Praha", 50.1256, 14.5142),
        PidStop("Praha, Můstek", "Můstek", "Praha", 50.0836, 14.4239),
        PidStop("Praha, Dejvická", "Dejvická", "Praha", 50.1006, 14.3925)
    )

    val quickSelectStops: List<PidStop> = listOf(
        allStops.first { it.golemioPrimaryName == "Žďár" },
        allStops.first { it.golemioPrimaryName == "Žďár,Doubrava" },
        allStops.first { it.golemioPrimaryName == "Loukov" },
        allStops.first { it.golemioPrimaryName == "Mnichovo Hradiště,nám." },
        allStops.first { it.golemioPrimaryName == "Mladá Boleslav,aut.st." },
        allStops.first { it.golemioPrimaryName == "Mladá Boleslav,město" },
        allStops.first { it.golemioPrimaryName == "Turnov,terminál u žel.st." },
        allStops.first { it.golemioPrimaryName == "Turnov,nám." },
        allStops.first { it.golemioPrimaryName == "Černý Most" }
    )

    fun searchStops(query: String): List<PidStop> {
        val cleanQuery = normalizeText(query)
        if (cleanQuery.isBlank()) return allStops

        return allStops.filter { stop ->
            normalizeText(stop.displayName).contains(cleanQuery) ||
                normalizeText(stop.golemioPrimaryName).contains(cleanQuery) ||
                normalizeText(stop.region).contains(cleanQuery) ||
                stop.alternativeNames.any { normalizeText(it).contains(cleanQuery) }
        }.sortedWith(
            compareBy<PidStop> {
                !normalizeText(it.displayName).startsWith(cleanQuery)
            }.thenBy { it.displayName }
        )
    }

    fun findNearestStops(
        userLat: Double,
        userLon: Double,
        maxRadiusMeters: Int = 15_000,
        limit: Int = 8
    ): List<NearbyStop> {
        return allStops
            .map { stop ->
                val dist = calculateDistanceMeters(userLat, userLon, stop.latitude, stop.longitude)
                NearbyStop(stop = stop, distanceMeters = dist)
            }
            .sortedBy { it.distanceMeters }
            .let { sorted ->
                val withinRadius = sorted.filter { it.distanceMeters <= maxRadiusMeters }
                if (withinRadius.isNotEmpty()) withinRadius.take(limit) else sorted.take(limit)
            }
    }

    fun findStopOrCreateCustom(queryOrName: String): PidStop {
        val trimmed = queryOrName.trim()
        val norm = normalizeText(trimmed)
        return allStops.firstOrNull {
            normalizeText(it.displayName) == norm ||
                normalizeText(it.golemioPrimaryName) == norm ||
                it.alternativeNames.any { alt -> normalizeText(alt) == norm }
        } ?: PidStop(
            displayName = trimmed,
            golemioPrimaryName = trimmed,
            region = "Vlastní dotaz",
            latitude = 50.5273,
            longitude = 14.9714
        )
    }

    private fun normalizeText(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase().trim(), Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Int {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).roundToInt()
    }
}