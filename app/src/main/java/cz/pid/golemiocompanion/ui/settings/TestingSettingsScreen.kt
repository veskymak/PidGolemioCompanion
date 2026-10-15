package cz.pid.golemiocompanion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var golemioKeyInput by remember(settings.golemioApiKey) {
        mutableStateOf(settings.golemioApiKey)
    }
    var cartoKeyInput by remember(settings.cartoApiKey) {
        mutableStateOf(settings.cartoApiKey)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hlavička obrazovky
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "Laboratoř funkcí & API",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nastavení klíčů Golemio, CARTO map a přepínače chování odpočtu a GPS.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }

        // Karta: Golemio API klíč
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(cornerRadius = 20.dp, borderAlpha = 0.22f)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Golemio API klíč",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (settings.golemioApiKey.isNotBlank()) {
                        Surface(
                            color = Color(0xFF16A34A).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Aktivní",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFDC2626).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Chybí klíč (HTTP 401)",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Potřebný pro stahování reálných odjezdů a zpoždění z api.golemio.cz. Klíč získáte zdarma na golemio.cz po registraci.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = golemioKeyInput,
                    onValueChange = {
                        golemioKeyInput = it
                        repository.setGolemioApiKey(it)
                    },
                    placeholder = {
                        Text(
                            "Vložte Golemio API klíč…",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (golemioKeyInput.isNotEmpty()) {
                            IconButton(onClick = {
                                golemioKeyInput = ""
                                repository.setGolemioApiKey("")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Vymazat",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Karta: CARTO Basemaps API klíč
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(cornerRadius = 20.dp, borderAlpha = 0.22f)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "CARTO Basemaps API klíč",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = Color(0xFF9333EA).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Bez vodoznaku",
                            color = Color(0xFFD8B4FE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Odstraňuje vodoznak 'API KEY REQUIRED' z mapových podkladů Carto Voyager (PID Lítačka styl).",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = cartoKeyInput,
                    onValueChange = {
                        cartoKeyInput = it
                        repository.setCartoApiKey(it)
                    },
                    placeholder = {
                        Text(
                            "Vložte CARTO API klíč…",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (cartoKeyInput.isNotEmpty() && cartoKeyInput != LabPreferencesRepository.DEFAULT_CARTO_KEY) {
                            IconButton(onClick = {
                                cartoKeyInput = LabPreferencesRepository.DEFAULT_CARTO_KEY
                                repository.setCartoApiKey(LabPreferencesRepository.DEFAULT_CARTO_KEY)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Reset na výchozí",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Přepínače funkcí
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

        // Posuvník poloměru vyhledávání
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

        // Rezerva pro spodní plovoucí navbar
        Spacer(modifier = Modifier.height(120.dp))
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