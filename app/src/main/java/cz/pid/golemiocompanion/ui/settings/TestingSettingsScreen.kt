package cz.pid.golemiocompanion.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.pid.golemiocompanion.ui.components.liquidGlass
import cz.pid.golemiocompanion.ui.trip.TripViewModel

@Composable
fun TestingSettingsScreen(
    viewModel: TripViewModel,
    onRequestFloatingPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labSettings by viewModel.labSettingsFlow.collectAsStateWithLifecycle()
    var apiKeyDraft by remember(labSettings.golemioApiKey) {
        mutableStateOf(labSettings.golemioApiKey)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "api_token_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 22.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Golemio & PID OpenAPI Klíč (X-Access-Token)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Vložte svůj Golemio API klíč", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.onApiKeyUpdated(apiKeyDraft) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Uložit API klíč", color = Color.White)
                }
            }
        }

        item(key = "lab_warning_badge") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, tintColor = Color(0xFFF59E0B), surfaceAlphaTop = 0.24f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Varování",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Laboratoř (Tyto funkce nemusí fungovat spolehlivě nebo mohou padat)",
                        color = Color(0xFFFDE68A),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Experimentální telemetrické a vykreslovací algoritmy pro testování v terénu.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item(key = "lab_1_dead_reckoning") {
            LabFeatureSwitchGlassCard(
                title = "Dead Reckoning polohy v tunelech / výpadcích",
                badgeText = "Experimentální",
                badgeColor = Color(0xFFFBBF24),
                description = "Pokud API přestane posílat GPS aktualizace (např. tunel Blanka nebo ztráta signálu), aplikace se pokusí dopočítávat polohu autobusu na mapě matematicky podle plánované rychlosti trasy. Varování: může ukazovat falešnou polohu, pokud autobus stojí v zácpě.",
                checked = labSettings.deadReckoningInTunnels,
                onCheckedChange = { viewModel.onLabToggleChanged("dead_reckoning", it) }
            )
        }

        item(key = "lab_2_input_gateway") {
            LabFeatureSwitchGlassCard(
                title = "Přímý telemetrický stream (Input Gateway bridge)",
                badgeText = "Nestabilní",
                badgeColor = Color(0xFFF87171),
                description = "Pokus o agresivní dotazování nezpracovaných telemetrických endpointů namísto zpožděné agregované cache. Může vracet chyby HTTP 403/429 nebo způsobit rate limiting klíče.",
                checked = labSettings.rawInputGatewayStream,
                onCheckedChange = { viewModel.onLabToggleChanged("raw_gateway", it) }
            )
        }

        item(key = "lab_3_phantom_ai") {
            LabFeatureSwitchGlassCard(
                title = "Fantomová predikce zpoždění (AI Heuristika)",
                badgeText = "Experimentální",
                badgeColor = Color(0xFFFBBF24),
                description = "Algoritmus, který na základě počasí z Golemio API a aktuální špičky uměle navyšuje odhadované zpoždění předtím, než ho oficiálně zaznamená dispečink PID. Může být nepřesné.",
                checked = labSettings.phantomAiDelayPrediction,
                onCheckedChange = { viewModel.onLabToggleChanged("phantom_ai", it) }
            )
        }

        item(key = "lab_4_forced_120hz") {
            LabFeatureSwitchGlassCard(
                title = "Vynucený 120Hz/144Hz agresivní render",
                badgeText = "Nestabilní",
                badgeColor = Color(0xFFF87171),
                description = "Vynucení maximální snímkové frekvence a vypnutí jakéhokoliv omezování překreslování v Compose. Zvyšuje spotřebu baterie a může zahřívat telefon.",
                checked = labSettings.forced120HzAggressiveRender,
                onCheckedChange = { viewModel.onLabToggleChanged("forced_120hz", it) }
            )
        }

        item(key = "lab_5_emergency_600m") {
            LabFeatureSwitchGlassCard(
                title = "Doporučit krizový přestup z okolních zastávek",
                badgeText = "Beta",
                badgeColor = Color(0xFF38BDF8),
                description = "Při kliknutí na \"Ujel mi\" prohledá všechny zastávky v okruhu 600 metrů a navrhne alternativní linky vedoucí do stejného cíle.",
                checked = labSettings.emergencyTransfer600m,
                onCheckedChange = { viewModel.onLabToggleChanged("emergency_600m", it) }
            )
        }

        item(key = "lab_6_pip_overlay") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabFeatureSwitchGlassCard(
                    title = "Plovoucí mini-odpočet (Picture-in-Picture / Overlay)",
                    badgeText = "Nestabilní",
                    badgeColor = Color(0xFFF87171),
                    description = "Pokus o zobrazení odpočtu v plovoucí bublině přes ostatní aplikace při minimalizaci. Může být ukončeno agresivní správou paměti systému Android.",
                    checked = labSettings.floatingPipMiniCountdown,
                    onCheckedChange = { viewModel.onLabToggleChanged("floating_pip", it) }
                )

                if (labSettings.floatingPipMiniCountdown) {
                    OutlinedButton(
                        onClick = onRequestFloatingPip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Otestovat přechod do plovoucího PiP okna ihned", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabFeatureSwitchGlassCard(
    title: String,
    badgeText: String,
    badgeColor: Color,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                cornerRadius = 20.dp,
                tintColor = if (checked) badgeColor else Color(0xFF64748B)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        color = badgeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = badgeColor
                )
            )
        }

        Text(
            text = description,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}