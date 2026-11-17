package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cloud.SavedLapDoc
import com.example.data.cloud.TelemetrySessionDoc
import com.example.telemetry.model.TyreCompound
import com.example.ui.components.ComparisonLapChart
import com.example.ui.components.PitWallCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SpeedTraceOverlayChart
import com.example.ui.components.TyreCompoundBadge
import com.example.ui.components.formatLapTimeMs
import com.example.ui.theme.AsphaltBorder
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.F1Red
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.SectorPurple
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SaveRaceUiState
import com.example.ui.viewmodel.UiState

@Composable
fun HistoryAndCompareScreen(
    sessionsState: UiState<List<TelemetrySessionDoc>>,
    selectedSessionAId: String?,
    selectedSessionBId: String?,
    lapsA: List<SavedLapDoc>,
    lapsB: List<SavedLapDoc>,
    saveState: SaveRaceUiState,
    currentLapsCount: Int,
    onSaveCurrentRace: (String) -> Unit,
    onSelectSessionA: (String?) -> Unit,
    onSelectSessionB: (String?) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var notesInput by remember { mutableStateOf("") }
    var selectedGameFilter by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Save Current Live Race to Cloud
        item {
            PitWallCard(borderColor = F1Red.copy(alpha = 0.6f)) {
                SectionHeader(
                    title = "Salvar Corrida Completa",
                    subtitle = "$currentLapsCount voltas prontas para sincronização no Firestore",
                    accentColor = F1Red,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { if (it.length <= 300) notesInput = it },
                    label = { Text("Notas de setup / clima (opcional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_notes_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSaveCurrentRace(notesInput)
                        notesInput = ""
                    },
                    enabled = saveState !is SaveRaceUiState.Saving,
                    colors = ButtonDefaults.buttonColors(containerColor = F1Red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_full_race_button"),
                ) {
                    if (saveState is SaveRaceUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVANDO NA NUVEM...")
                    } else {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVAR CORRIDA NO HISTÓRICO")
                    }
                }
            }
        }

        // Cloud Sessions Content
        when (sessionsState) {
            is UiState.Loading -> {
                item {
                    PitWallCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TelemetryCyan)
                            Text("Sincronizando histórico na nuvem...", color = TextSecondary)
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    PitWallCard(borderColor = CriticalRed) {
                        Text(
                            text = "Erro ao carregar histórico: ${sessionsState.message}",
                            color = CriticalRed,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            is UiState.Success -> {
                val allSessions = sessionsState.data
                val availableGames = allSessions.map { it.gameTitle.ifBlank { "F1_25" } }.distinct()
                val sessions = if (selectedGameFilter == null) {
                    allSessions
                } else {
                    allSessions.filter { it.gameTitle.ifBlank { "F1_25" } == selectedGameFilter }
                }
                val sessionA = sessions.firstOrNull { it.id == selectedSessionAId } ?: sessions.getOrNull(0)
                val sessionB = sessions.firstOrNull { it.id == selectedSessionBId } ?: sessions.getOrNull(1)

                if (availableGames.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedGameFilter == null,
                                onClick = { selectedGameFilter = null },
                                label = { Text("Todos os Jogos") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.24f),
                                    selectedLabelColor = TelemetryCyan,
                                ),
                            )
                            availableGames.forEach { gameKey ->
                                val gameObj = com.example.telemetry.model.GameTitle.fromName(gameKey)
                                FilterChip(
                                    selected = selectedGameFilter == gameKey,
                                    onClick = {
                                        selectedGameFilter = if (selectedGameFilter == gameKey) null else gameKey
                                    },
                                    label = { Text(gameObj.shortTag) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(gameObj.badgeColorHex).copy(alpha = 0.28f),
                                        selectedLabelColor = TextPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Side-by-side Race Comparison Tool
                item {
                    PitWallCard(borderColor = TelemetryCyan.copy(alpha = 0.55f)) {
                        SectionHeader(
                            title = "Comparação Lado a Lado",
                            subtitle = "Compare voltas, velocidade máxima e consistência entre corridas",
                            accentColor = TelemetryCyan,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Rounded.CompareArrows,
                                    contentDescription = null,
                                    tint = TelemetryCyan,
                                )
                            },
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (sessions.size < 2) {
                            Text(
                                text = "Salve pelo menos 2 corridas acima para comparar métricas e curvas lado a lado.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        } else {
                            // Selector A
                            Text("CORRIDA A (CIANO)", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                sessions.forEachIndexed { idx, s ->
                                    val selected = s.id == sessionA?.id
                                    val gameTag = com.example.telemetry.model.GameTitle.fromName(s.gameTitle).shortTag
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectSessionA(s.id) },
                                        label = { Text("#${idx + 1} [$gameTag] ${s.trackName.substringBefore(" (")}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.25f),
                                            selectedLabelColor = TelemetryCyan,
                                        ),
                                        modifier = Modifier.testTag("compare_a_$idx"),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Selector B
                            Text("CORRIDA B (VERMELHO)", style = MaterialTheme.typography.labelSmall, color = F1Red)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                sessions.forEachIndexed { idx, s ->
                                    val selected = s.id == sessionB?.id
                                    val gameTag = com.example.telemetry.model.GameTitle.fromName(s.gameTitle).shortTag
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectSessionB(s.id) },
                                        label = { Text("#${idx + 1} [$gameTag] ${s.trackName.substringBefore(" (")}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = F1Red.copy(alpha = 0.28f),
                                            selectedLabelColor = TextPrimary,
                                        ),
                                        modifier = Modifier.testTag("compare_b_$idx"),
                                    )
                                }
                            }

                            if (sessionA != null && sessionB != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                SideBySideMetricsTable(sessionA, sessionB)

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "PROGRESSÃO DE VOLTAS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ComparisonLapChart(
                                    lapsA = lapsA,
                                    lapsB = lapsB,
                                    labelA = "A: ${sessionA.trackName.substringBefore(" (")}",
                                    labelB = "B: ${sessionB.trackName.substringBefore(" (")}",
                                )

                                val bestTraceA = lapsA.minByOrNull { it.lapTimeMs }?.speedTrace.orEmpty()
                                val bestTraceB = lapsB.minByOrNull { it.lapTimeMs }?.speedTrace.orEmpty()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "CURVA DE VELOCIDADE (MELHOR VOLTA)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SpeedTraceOverlayChart(traceA = bestTraceA, traceB = bestTraceB)
                            }
                        }
                    }
                }

                // Saved Sessions List
                item {
                    SectionHeader(
                        title = "Corridas Salvas (${sessions.size})",
                        subtitle = "Sincronizadas na sua conta Google",
                        accentColor = SectorPurple,
                    )
                }

                if (sessions.isEmpty()) {
                    item {
                        PitWallCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nenhuma corrida salva ainda. Use o botão acima para gravar sua sessão atual!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        SavedSessionRow(
                            session = session,
                            isSelectedA = session.id == sessionA?.id,
                            isSelectedB = session.id == sessionB?.id,
                            onSelectCompare = {
                                if (selectedSessionAId == null || selectedSessionAId == session.id) {
                                    onSelectSessionA(session.id)
                                } else {
                                    onSelectSessionB(session.id)
                                }
                            },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBySideMetricsTable(
    a: TelemetrySessionDoc,
    b: TelemetrySessionDoc,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonBlack)
            .border(1.dp, AsphaltBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComparisonRow(
            metric = "Jogo",
            valA = com.example.telemetry.model.GameTitle.fromName(a.gameTitle).shortTag,
            valB = com.example.telemetry.model.GameTitle.fromName(b.gameTitle).shortTag,
            aBetter = true,
        )
        ComparisonRow(
            metric = "Melhor Volta",
            valA = formatLapTimeMs(a.bestLapMs),
            valB = formatLapTimeMs(b.bestLapMs),
            aBetter = a.bestLapMs in 1..b.bestLapMs,
        )
        ComparisonRow(
            metric = "Ritmo Médio",
            valA = formatLapTimeMs(a.avgLapMs),
            valB = formatLapTimeMs(b.avgLapMs),
            aBetter = a.avgLapMs in 1..b.avgLapMs,
        )
        ComparisonRow(
            metric = "Vel. Máxima",
            valA = "${a.topSpeedKmh} km/h",
            valB = "${b.topSpeedKmh} km/h",
            aBetter = a.topSpeedKmh >= b.topSpeedKmh,
        )
        ComparisonRow(
            metric = "Consistência",
            valA = "%.1f%%".format(a.consistencyPct),
            valB = "%.1f%%".format(b.consistencyPct),
            aBetter = a.consistencyPct >= b.consistencyPct,
        )
        ComparisonRow(
            metric = "Posição / Boxes",
            valA = "P${a.finishPosition} (${a.pitStops} pit)",
            valB = "P${b.finishPosition} (${b.pitStops} pit)",
            aBetter = a.finishPosition <= b.finishPosition,
        )
    }
}

@Composable
private fun ComparisonRow(
    metric: String,
    valA: String,
    valB: String,
    aBetter: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = valA,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = TelemetryMonoFamily,
            color = if (aBetter) SectorGreen else TelemetryCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = metric,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(
            text = valB,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = TelemetryMonoFamily,
            color = if (!aBetter) SectorGreen else F1Red,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun SavedSessionRow(
    session: TelemetrySessionDoc,
    isSelectedA: Boolean,
    isSelectedB: Boolean,
    onSelectCompare: () -> Unit,
    onDelete: () -> Unit,
) {
    val game = com.example.telemetry.model.GameTitle.fromName(session.gameTitle)
    val gameColor = Color(game.badgeColorHex)
    val borderColor = when {
        isSelectedA -> TelemetryCyan
        isSelectedB -> F1Red
        else -> AsphaltBorder
    }
    PitWallCard(
        borderColor = borderColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectCompare),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                TyreCompoundBadge(compound = TyreCompound.fromCode(session.primaryCompound))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(gameColor.copy(alpha = 0.2f))
                                .border(1.dp, gameColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = game.shortTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = gameColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session.trackName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (isSelectedA || isSelectedB) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background((if (isSelectedA) TelemetryCyan else F1Red).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSelectedA) "A" else "B",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelectedA) TelemetryCyan else F1Red,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Melhor ${formatLapTimeMs(session.bestLapMs)} • ${session.topSpeedKmh} km/h • Consist. ${"%.1f".format(session.consistencyPct)}% • ${session.totalLaps}V",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    if (session.notes.isNotBlank()) {
                        Text(
                            text = "\"${session.notes}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan,
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_session_${session.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remover corrida salva",
                    tint = TextMuted,
                )
            }
        }
    }
}
