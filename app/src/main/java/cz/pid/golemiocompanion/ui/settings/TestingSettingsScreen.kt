package cz.pid.golemiocompanion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.data.local.LabSettings
import cz.pid.golemiocompanion.ui.components.liquidGlassCard
import kotlin.math.roundToInt

@Composable
fun TestingSettingsScreen(
    settings: LabSettings,
    repository: LabPreferencesRepository,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Laboratoř funkcí",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Všechny přepínače níže jsou plně funkční a okamžitě ovlivňují chování odpočtu, GPS a mapy.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        LabSwitchCard(
            title = "PiP (Obraz v obraze) plovoucí odpočet",
            description = "Při minimalizaci aplikace během aktivního sledování spoje přepne odpočet do plovoucího okna.",
            checked = settings.enablePipCountdown,
            onCheckedChange = { repository.setEnablePipCountdown(it) }
        )

        LabSwitchCard(
            title = "Haptické varování před odjezdem (3m / 1m / 30s)",
            description = "Zavibruje telefonem přesně 3 minuty, 1 minutu a 30 sekund před odjezdem sledovaného spoje.",
            checked = settings.enableSmartDepartureAlerts,
            onCheckedChange = { repository.setEnableSmartDepartureAlerts(it) }
        )

        LabSwitchCard(
            title = "Přesné sekundy přímo v seznamu odjezdů",
            description = "Místo zaokrouhlených minut (a < 1 min) zobrazuje na všech kartách v tabuli živý odpočet v sekundách (MM:SS).",
            checked = settings.showExactSecondsInBoard,
            onCheckedChange = { repository.setShowExactSecondsInBoard(it) }
        )

        LabSwitchCard(
            title = "Automaticky vybrat nejbližší zastávku po startu",
            description = "Po získání GPS polohy při spuštění aplikace rovnou přepne odjezdovou tabuli na nejbližší zastávku v okolí.",
            checked = settings.autoSelectNearestStopOnStart,
            onCheckedChange = { repository.setAutoSelectNearestStopOnStart(it) }
        )

        LabSwitchCard(
            title = "Rozšířené štítky zpoždění na mapě",
            description = "Zobrazí přímo ve štítku vozidla na mapě i zpoždění v minutách (např. 315 +2m).",
            checked = settings.showExtendedMapBadges,
            onCheckedChange = { repository.setShowExtendedMapBadges(it) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard()
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Poloměr vyhledávání blízkých zastávek: ${settings.nearbyRadiusKm} km",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Určuje maximální vzdálenost pro doporučení nejbližších zastávek podle GPS.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Slider(
                    value = settings.nearbyRadiusKm.toFloat(),
                    onValueChange = { repository.setNearbyRadiusKm(it.roundToInt()) },
                    valueRange = 1f..25f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun LabSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7)
            )
        )
    }
}