package cz.pid.golemiocompanion.domain.model

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import kotlin.math.*

data class PidStop(
    val displayName: String,
    val golemioPrimaryName: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val isTrain: Boolean = false,
    val alternativeNames: List<String> = emptyList(),
    val aswNode: String? = null,
    val cis: String? = null,
    val municipality: String? = null,
    val district: String? = null,
    val trafficType: String? = null,
    val lines: List<String> = emptyList()
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

    private val defaultStops: List<PidStop> = listOf(
        PidStop("Žďár, Doubrava", "Žďár,Doubrava", "Žďár (MB)", 50.55488, 15.068211, false, listOf("Žďár, Doubrava", "Doubrava"), aswNode = "6403", cis = "42448", lines = listOf("715")),
        PidStop("Žďár, Břehy, obecní úřad", "Žďár,Břehy,obecní úřad", "Žďár (MB)", 50.559948, 15.061342, false, listOf("Žďár, Břehy", "Břehy"), aswNode = "6402", lines = listOf("715")),
        PidStop("Žďár, Příhrazská", "Žďár,Příhrazská", "Žďár (MB)", 50.547077, 15.073006, false, listOf("Žďár, Příhrazy", "Příhrazy"), aswNode = "6404", lines = listOf("715")),
        PidStop("Žďár, u kostela", "Žďár,u kostela", "Žďár (MB)", 50.543106, 15.084044, false, listOf("Žďár", "u kostela"), aswNode = "6405", lines = listOf("715")),
        PidStop("Žďár, Žehrov", "Žďár,Žehrov", "Žďár (MB)", 50.53012, 15.100525, false, listOf("Žehrov", "Skokovy"), aswNode = "6406", lines = listOf("715")),
        PidStop("Loukov u Mnichova Hradiště", "Loukov u Mnichova Hradiště", "Loukov (MB)", 50.55868, 15.056465, true, listOf("Loukov u Mn.Hradiště", "Loukov, žel.st.", "Loukov vlak", "ČD Loukov"), aswNode = "9441", lines = listOf("S35")),
        PidStop("Loukov, žel.st.", "Loukov,žel.st.", "Loukov (MB)", 50.558075, 15.056908, true, listOf("Loukov, žel. st.", "Loukov nádraží", "Loukov vlak"), aswNode = "9441", lines = listOf("345", "715", "717", "XS35")),
        PidStop("Loukov, Na Veselici", "Loukov,Na Veselici", "Loukov (MB)", 50.552807, 15.049558, false, listOf("Na Veselici"), aswNode = "6393", lines = listOf("715", "717", "733")),
        PidStop("Loukov, škola", "Loukov,škola", "Loukov (MB)", 50.560165, 15.035648, false, listOf("Loukov, škola"), aswNode = "6392", lines = listOf("715", "733")),
        PidStop("Loukovec", "Loukovec", "Loukovec (MB)", 50.562058, 15.015609, false, emptyList(), aswNode = "6390", lines = listOf("715", "733")),
        PidStop("Loukovec, Hubálov", "Loukovec,Hubálov", "Loukovec (MB)", 50.559105, 15.013259, false, listOf("Hubálov"), aswNode = "6391", lines = listOf("715", "733")),
        PidStop("Mnichovo Hradiště, nám.", "Mnichovo Hradiště,nám.", "Mnichovo Hradiště (MB)", 50.5224, 14.974665, false, listOf("Mnichovo Hradiště náměstí", "Mnichovo Hradiště"), aswNode = "3253", cis = "21570"),
        PidStop("Mnichovo Hradiště, žel.st.", "Mnichovo Hradiště,žel.st.", "Mnichovo Hradiště (MB)", 50.526276, 14.975498, true, listOf("Mnichovo Hradiště nádraží", "Mnichovo Hradiště vlak"), aswNode = "9442"),
        PidStop("Mladá Boleslav, aut.st.", "Mladá Boleslav,aut.st.", "Mladá Boleslav (MB)", 50.419975, 14.916528, false, listOf("Mladá Boleslav autobusové nádraží", "Mladá Boleslav aut.st."), aswNode = "2987", cis = "21373"),
        PidStop("Mladá Boleslav hl.n.", "Mladá Boleslav hl.n.", "Mladá Boleslav (MB)", 50.4102, 14.8965, true, listOf("Mladá Boleslav hlavní nádraží"), aswNode = "9438"),
        PidStop("Turnov, Terminál u žel.st.", "Turnov,Terminál u žel.st.", "Turnov (SM)", 50.58701, 15.140575, false, listOf("Turnov terminál", "Turnov nádraží"), aswNode = "9443", cis = "58512"),
        PidStop("Turnov", "Turnov", "Turnov (SM)", 50.58498, 15.144416, true, listOf("Turnov vlak", "Turnov nádraží"), aswNode = "9443"),
        PidStop("Praha, Hlavní nádraží", "Hlavní nádraží", "Praha (AB)", 50.0831, 14.4352, true, listOf("Hlavní nádraží", "Praha hl.n.", "Praha, hl.n."), aswNode = "280")
    )

    @Volatile
    private var loadedStops: List<PidStop> = defaultStops

    // Před-normalizovaný vyhledávací index pro bleskurychlé hledání
    private class IndexedStop(
        val stop: PidStop,
        val normDisplay: String,
        val normPrimary: String,
        val normRegion: String,
        val normLines: List<String>,
        val normAlts: List<String>
    )

    @Volatile
    private var indexedStops: List<IndexedStop> = buildIndexedStops(defaultStops)

    val allStops: List<PidStop>
        get() = loadedStops

    val quickSelectStops: List<PidStop>
        get() = listOf(
            findStopByNameOrNull("Žďár, Doubrava") ?: defaultStops[0],
            findStopByNameOrNull("Loukov u Mnichova Hradiště") ?: defaultStops[5],
            findStopByNameOrNull("Loukov, žel.st.") ?: defaultStops[6],
            findStopByNameOrNull("Žďár, Břehy, obecní úřad") ?: defaultStops[1],
            findStopByNameOrNull("Mnichovo Hradiště, nám.") ?: defaultStops[11],
            findStopByNameOrNull("Mladá Boleslav, aut.st.") ?: defaultStops[13],
            findStopByNameOrNull("Turnov, Terminál u žel.st.") ?: defaultStops[15],
            findStopByNameOrNull("Hlavní nádraží") ?: defaultStops[17]
        )

    private fun buildIndexedStops(stops: List<PidStop>): List<IndexedStop> {
        return stops.map { s ->
            IndexedStop(
                stop = s,
                normDisplay = normalizeText(s.displayName),
                normPrimary = normalizeText(s.golemioPrimaryName),
                normRegion = normalizeText(s.region),
                normLines = s.lines.map { normalizeText(it) },
                normAlts = s.alternativeNames.map { normalizeText(it) }
            )
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (loadedStops.size > 100) return // Již načteno

        try {
            val list = mutableListOf<PidStop>()
            context.assets.open("pid_stops.tsv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trimEnd('\r', '\n')
                        if (trimmed.isNotBlank()) {
                            val parts = trimmed.split('\t')
                            if (parts.size >= 12) {
                                val dName = parts[0]
                                val gPrimary = parts[1]
                                val reg = parts[2]
                                val lat = parts[3].toDoubleOrNull() ?: 0.0
                                val lon = parts[4].toDoubleOrNull() ?: 0.0
                                val isTrain = parts[5] == "1"
                                val asw = parts[6].takeIf { it.isNotEmpty() }
                                val cis = parts[7].takeIf { it.isNotEmpty() }
                                val muni = parts[8].takeIf { it.isNotEmpty() }
                                val dist = parts[9].takeIf { it.isNotEmpty() }
                                val tType = parts[10].takeIf { it.isNotEmpty() }
                                val lns = if (parts[11].isNotEmpty()) parts[11].split(';') else emptyList()
                                val alts = if (parts.size >= 13 && parts[12].isNotEmpty()) parts[12].split(';') else emptyList()

                                list.add(
                                    PidStop(
                                        displayName = dName,
                                        golemioPrimaryName = gPrimary,
                                        region = reg,
                                        latitude = lat,
                                        longitude = lon,
                                        isTrain = isTrain,
                                        alternativeNames = alts,
                                        aswNode = asw,
                                        cis = cis,
                                        municipality = muni,
                                        district = dist,
                                        trafficType = tType,
                                        lines = lns
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (list.isNotEmpty()) {
                loadedStops = list
                indexedStops = buildIndexedStops(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun findStopByNameOrNull(name: String): PidStop? {
        val norm = normalizeText(name)
        return indexedStops.firstOrNull {
            it.normDisplay == norm || it.normPrimary == norm || it.normAlts.contains(norm)
        }?.stop
    }

    fun searchStops(query: String): List<PidStop> {
        val cleanQuery = normalizeText(query)
        if (cleanQuery.isBlank()) return quickSelectStops

        return indexedStops
            .mapNotNull { item ->
                val score = calculateMatchScore(item, cleanQuery)
                if (score > 0) Pair(item.stop, score) else null
            }
            .sortedWith(
                compareBy<Pair<PidStop, Int>> { -it.second }
                    .thenBy { it.first.displayName.length }
                    .thenBy { it.first.displayName }
            )
            .map { it.first }
    }

    private fun calculateMatchScore(item: IndexedStop, q: String): Int {
        // Přesná shoda
        if (item.normDisplay == q) return 1000
        if (item.normPrimary == q) return 900
        if (item.normAlts.contains(q)) return 800

        // Shoda čísla linky (např. hledání "764", "715", "S3")
        if (item.normLines.contains(q)) return 700

        // Začíná na hledaný řetězec
        if (item.normDisplay.startsWith(q)) return 600
        if (item.normPrimary.startsWith(q)) return 550
        if (item.normAlts.any { it.startsWith(q) }) return 500

        // Obsahuje hledaný řetězec
        if (item.normDisplay.contains(q)) return 300
        if (item.normPrimary.contains(q)) return 250
        if (item.normAlts.any { it.contains(q) }) return 200

        // Region / obec
        if (item.normRegion.startsWith(q)) return 150
        if (item.normRegion.contains(q)) return 100

        return 0
    }

    fun findNearestStops(
        userLat: Double,
        userLon: Double,
        maxRadiusMeters: Int = 20_000,
        limit: Int = 8
    ): List<NearbyStop> {
        return loadedStops
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
        return findStopByNameOrNull(norm) ?: PidStop(
            displayName = trimmed,
            golemioPrimaryName = trimmed,
            region = "Vlastní dotaz",
            latitude = 50.5273,
            longitude = 14.9714
        )
    }

    fun normalizeText(input: String): String {
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