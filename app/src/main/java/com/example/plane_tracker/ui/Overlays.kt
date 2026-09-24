package com.example.plane_tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airport
import com.example.plane_tracker.data.AirportBoard
import com.example.plane_tracker.data.BoardEntry
import com.example.plane_tracker.data.BoardKind
import com.example.plane_tracker.data.EmergencyEvent
import com.example.plane_tracker.data.EmergencyHistoryTracker
import com.example.plane_tracker.data.FlightHistoryStore
import com.example.plane_tracker.data.Metar
import com.example.plane_tracker.data.OpsCategory
import com.example.plane_tracker.data.OpsClassifier
import com.example.plane_tracker.data.RadarFrame
import com.example.plane_tracker.data.WeatherMapper
import com.example.plane_tracker.data.SelectedFlight
import com.example.plane_tracker.data.Vessel
import com.example.plane_tracker.util.GeoMath
import com.example.plane_tracker.util.RouteProgress
import com.example.plane_tracker.util.calculateClockETA
import com.example.plane_tracker.viewmodel.FilterState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// FR24-ish palette
private val PanelBg = Color(0xF0101014)
private val PanelBgLight = Color(0xF51A1D24)
private val Accent = Color(0xFFF5B942)
private val TextPrimary = Color(0xFFE8EEF2)
private val TextSecondary = Color(0xFF9AA7B4)
private val ChipGreen = Color(0xFF38B24A)

// ---------- Formatting helpers ----------

fun formatAltitudeFt(ft: Int): String = "%,d ft".format(ft)
fun formatSpeedKt(kt: Int): String = "$kt kt"
fun formatHeading(deg: Float): String = "${deg.roundToInt()}°"
fun formatClimbFpm(fpm: Int): String = if (fpm > 50) "+%,d ft/min".format(fpm)
    else if (fpm < -50) "%,d ft/min".format(fpm) else "level"

@Composable
fun rememberElapsedSeconds(sinceMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        if (sinceMs == 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return if (sinceMs == 0L) 0L else ((now - sinceMs) / 1000).coerceAtLeast(0L)
}

// ---------- Status chip ----------

@Composable
fun StatusChip(
    count: Int,
    source: String,
    lastUpdateMs: Long,
    lifeboatCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val elapsed = rememberElapsedSeconds(lastUpdateMs)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(ChipGreen, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✈ $count  ·  🛥 $lifeboatCount  ·  ${source.lowercase()}  ·  ${elapsed}s ago",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

// ---------- Search ----------

@Composable
fun SearchOverlay(
    query: String,
    aircraftResults: List<Aircraft>,
    airportResults: List<Airports.Entry>,
    onQueryChange: (String) -> Unit,
    onAircraftClick: (Aircraft) -> Unit,
    onAirportClick: (Airports.Entry) -> Unit,
    modifier: Modifier = Modifier,
    lifeboatResults: List<Vessel> = emptyList(),
    onLifeboatClick: (Vessel) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search callsign, hex, airport or lifeboat", color = TextSecondary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PanelBg,
                unfocusedContainerColor = PanelBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        val hasResults = aircraftResults.isNotEmpty() || airportResults.isNotEmpty() || lifeboatResults.isNotEmpty()
        AnimatedVisibility(visible = hasResults, enter = fadeIn(), exit = fadeOut()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PanelBg)
            ) {
                LazyColumn(modifier = Modifier.height((56 * (aircraftResults.size + airportResults.size + lifeboatResults.size).coerceAtMost(6)).dp)) {
                    items(lifeboatResults, key = { "lb-${it.mmsi}" }) { lb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLifeboatClick(lb) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛥", fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lb.name.ifBlank { "Unknown Lifeboat" },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "MMSI ${lb.mmsi}  ·  ${lb.speedKnots.toInt()} kt",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    items(aircraftResults, key = { "ac-${it.icao24}" }) { ac ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAircraftClick(ac) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✈", color = Accent, fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ac.callsign.ifEmpty { ac.icao24.uppercase() },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "${ac.icao24.uppercase()}  ·  ${formatAltitudeFt(ac.altitudeFt)}  ·  ${formatSpeedKt(ac.speedKt)}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    items(airportResults, key = { "ap-${it.iata}" }) { ap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAirportClick(ap) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚑", color = Accent, fontSize = 15.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ap.iata, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(ap.name, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Emergency banner ----------

private val AlertRed = Color(0xF2B3261E)
private val AlertRedSoft = Color(0xFFFFD0CB)

/** Red banner for live emergency squawks (7700/7600/7500). */
@Composable
fun EmergencyBanner(
    emergencies: List<EmergencyEvent>,
    onSelect: (EmergencyEvent) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = emergencies.isNotEmpty(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            emergencies.take(2).forEach { e ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", color = AlertRedSoft, fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onSelect(e) }
                        ) {
                            Text(
                                "Squawk ${e.squawk} · ${e.label}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${e.callsign} · tap to view on map",
                                color = AlertRedSoft,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onDismiss(e.hex) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismiss alert",
                                tint = AlertRedSoft,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Rain radar ----------

private fun formatRadarTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** Rain radar status pill with play/pause and the currently shown frame time. */
@Composable
fun RadarOverlay(
    radarOn: Boolean,
    radarPlaying: Boolean,
    frames: List<RadarFrame>,
    currentIndex: Int,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = radarOn && frames.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBg)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (radarPlaying) "⏸" else "▶",
                    color = Accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onTogglePlay)
                        .padding(4.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Rain radar", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                val frame = frames.getOrNull(currentIndex) ?: frames.lastOrNull()
                frame?.let {
                    Text(formatRadarTime(it.timeMs), color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- Ops aircraft list ----------

private enum class OpsTab { AIRCRAFT, LIFEBOATS }

@Composable
private fun OpsTabRow(selected: OpsTab, onSelect: (OpsTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBgLight)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            OpsTab.AIRCRAFT to ("🚁" to "Aircraft"),
            OpsTab.LIFEBOATS to ("🛥" to "Lifeboats")
        ).forEach { (tab, labels) ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(labels.first, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    labels.second,
                    color = if (active) Accent else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Sheet listing every currently-tracked blue-light / military aircraft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsSheet(
    ops: List<Pair<Aircraft, OpsCategory>>,
    lifeboats: List<Vessel>,
    ready: Boolean,
    onSelect: (Aircraft) -> Unit,
    onClose: () -> Unit,
    onSelectLifeboat: (Vessel) -> Unit = {}
) {
    var tab by remember { mutableStateOf(OpsTab.AIRCRAFT) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = PanelBgLight,
        contentColor = TextPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Emergency services & military", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (ready) "${ops.size} tracked aircraft and ${lifeboats.size} lifeboats in coverage" else "Connecting to the live feed…",
                    color = TextSecondary, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            OpsTabRow(selected = tab, onSelect = { tab = it })
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
            
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (tab == OpsTab.AIRCRAFT) {
                    if (ops.isEmpty()) {
                        Text(
                            if (ready) {
                                "No emergency services or military aircraft in the current feed."
                            } else {
                                "Scanning…"
                            },
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height((56 * ops.size.coerceAtMost(8)).dp)) {
                            items(ops, key = { "ops-${it.first.icao24}" }) { (ac, cat) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(ac) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .background(
                                                Color(android.graphics.Color.parseColor(cat.ringColor)).copy(alpha = 0.18f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(cat.emoji, fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ac.callsign.ifEmpty { ac.icao24.uppercase() },
                                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            listOfNotNull(
                                                cat.label,
                                                ac.typeCode,
                                                ac.registration,
                                                formatAltitudeFt(if (ac.onGround) 0 else ac.altitudeFt)
                                            ).joinToString(" · "),
                                            color = TextSecondary, fontSize = 11.sp, maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (lifeboats.isEmpty()) {
                        Text(
                            if (ready) {
                                "No lifeboats in the current feed."
                            } else {
                                "Scanning…"
                            },
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height((56 * lifeboats.size.coerceAtMost(8)).dp)) {
                            items(lifeboats, key = { "ais-${it.mmsi}" }) { v ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectLifeboat(v) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .background(Color(android.graphics.Color.parseColor("#00bcd4")).copy(alpha = 0.18f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🛥", fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(v.name.ifBlank { "Unknown Lifeboat" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("MMSI: ${v.mmsi}  ·  ${v.speedKnots.toInt()} kt", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---------- Alert history ----------

private fun formatAlertTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

/** Reviewable list of emergency-squawk events from this session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistorySheet(
    entries: List<EmergencyHistoryTracker.Entry>,
    onSelect: (EmergencyHistoryTracker.Entry) -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = PanelBgLight,
        contentColor = TextPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text("Squawk history", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "${entries.count { it.active }} ongoing · ${entries.size} this session",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(
                    "No emergency squawks detected yet. When an aircraft squawks 7700, 7600 or 7500 it will be recorded here.",
                    color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height((64 * entries.size.coerceAtMost(8)).dp)) {
                    items(entries, key = { "alert-${it.hex}-${it.firstSeenMs}" }) { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(e) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (e.active) AlertRed else TextSecondary.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Squawk ${e.squawk} · ${e.label}",
                                    color = if (e.active) AlertRed else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${e.callsign} · ${formatAlertTime(e.firstSeenMs)}" +
                                        if (!e.active) " – ${formatAlertTime(e.lastSeenMs)}" else " · ongoing",
                                    color = TextSecondary, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------- Flight replay ----------

/** Scrubber sheet for replaying a recorded flight track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSheet(
    flight: Aircraft?,
    points: List<FlightHistoryStore.Point>,
    index: Int,
    playing: Boolean,
    onSeek: (Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onClose: () -> Unit
) {
    if (flight == null) return
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = PanelBgLight,
        contentColor = TextPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                flight.callsign.ifEmpty { flight.icao24.uppercase() },
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Text(
                if (points.isEmpty()) "No recording yet — the app stores ~90 minutes per flight."
                else "${points.size} recorded positions · ${formatAlertTime(points.first().ts)} – ${formatAlertTime(points.last().ts)}",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            val point = points.getOrNull(index)
            point?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DataCell("ALTITUDE", formatAltitudeFt((it.altitudeMeters * 3.28084).toInt()))
                    DataCell("SPEED", formatSpeedKt((it.velocityMps * 1.94384).toInt()))
                    DataCell("TIME", formatAlertTime(it.ts))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (points.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (playing) "⏸" else "▶",
                        color = Accent, fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { if (playing) onPause() else onPlay() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { onSeek(it.roundToInt()) },
                        valueRange = 0f..maxOf(1f, points.lastIndex.toFloat()),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------- FR24-style airport page ----------

private enum class AirportTab { GENERAL, DEPARTURES, ARRIVALS, ON_GROUND }

/** Bottom tabs under the airport sheet, mirroring flightradar24's layout. */
@Composable
private fun AirportTabRow(selected: AirportTab, onSelect: (AirportTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            AirportTab.GENERAL to ("📍" to "General"),
            AirportTab.DEPARTURES to ("🛫" to "Departures"),
            AirportTab.ARRIVALS to ("🛬" to "Arrivals"),
            AirportTab.ON_GROUND to ("✈" to "On ground")
        ).forEach { (tab, labels) ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(labels.first, fontSize = 16.sp)
                Text(
                    labels.second,
                    color = if (active) Accent else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportSheet(
    airport: Airports.Entry?,
    metar: Metar?,
    board: AirportBoard?,
    onGround: List<Aircraft>,
    loading: Boolean,
    onSelectAircraft: (Aircraft) -> Unit,
    onClose: () -> Unit
) {
    if (airport == null) return
    var tab by remember(airport.iata) { mutableStateOf(AirportTab.GENERAL) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = PanelBgLight,
        contentColor = TextPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .fillMaxHeight()
        ) {
            // --- Scrollable content: header, photo, weather, tab pages ---
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: name, codes, local time
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(airport.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "${airport.iata} / ${airport.icao}",
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date()) + " local · now",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Satellite photo tile (Esri World Imagery, keyless)
                AsyncImage(
                    model = airportTileUrl(airport.lat, airport.lon),
                    contentDescription = "Satellite view of ${airport.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(150.dp)
                        .background(Color(0xFF10141A), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(10.dp))

                // Weather strip: CONDITIONS / TEMPERATURE / WIND
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WxCell(
                        label = "CONDITIONS",
                        value = metar?.condition ?: "—",
                        emoji = metar?.let { WeatherMapper.emoji(it.condition) },
                        modifier = Modifier.weight(1f)
                    )
                    WxCell(
                        label = "TEMPERATURE",
                        value = metar?.tempC?.let { "${it.roundToInt()}°C" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    WxCell(
                        label = "WIND",
                        value = formatWind(metar),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Raw METAR line
                metar?.rawOb?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

                // Tab page content
                when (tab) {
                    AirportTab.GENERAL -> {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            GeneralRow("ICAO", airport.icao)
                            GeneralRow("IATA", airport.iata)
                            GeneralRow(
                                "Position",
                                "%.4f, %.4f".format(airport.lat, airport.lon)
                            )
                            GeneralRow("Pressure (QNH)", metar?.altimHpa?.let { "$it hPa" } ?: "—")
                            GeneralRow(
                                "Visibility",
                                metar?.visibility?.let { v -> if (v == "6+") "10 km+" else "$v sm" } ?: "—"
                            )
                            GeneralRow(
                                "Dew point",
                                metar?.dewpointC?.let { "${it.roundToInt()}°C" } ?: "—"
                            )
                            GeneralRow("Traffic now", boardSummary(board, onGround))
                        }
                    }
                    AirportTab.DEPARTURES -> BoardList(board?.departures, loading, onSelectAircraft)
                    AirportTab.ARRIVALS -> BoardList(board?.arrivals, loading, onSelectAircraft)
                    AirportTab.ON_GROUND -> GroundList(onGround, onSelectAircraft)
                }

                Spacer(Modifier.height(8.dp))
            }

            // --- Pinned tab bar (always visible, like FR24) ---
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
            AirportTabRow(selected = tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun WxCell(label: String, value: String, modifier: Modifier = Modifier, emoji: String? = null) {
    Column(
        modifier = modifier
            .background(PanelBg, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        if (emoji != null) Text(emoji, fontSize = 18.sp)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatWind(metar: Metar?): String = when {
    metar == null -> "—"
    metar.windSpeedKt == null || metar.windSpeedKt == 0 -> "Calm"
    metar.windFromDeg == null -> "VRB ${metar.windSpeedKt}kt"
    else -> "${metar.windFromDeg}° ${metar.windSpeedKt}kt"
}

private fun boardSummary(board: AirportBoard?, onGround: List<Aircraft>): String {
    val dep = board?.departures?.size ?: 0
    val arr = board?.arrivals?.size ?: 0
    val gnd = onGround.size
    return "$dep dep · $arr arr · $gnd ground"
}

@Composable
private fun GeneralRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BoardList(entries: List<BoardEntry>?, loading: Boolean, onSelect: (Aircraft) -> Unit) {
    when {
        loading -> Text(
            "Scanning nearby traffic…",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp)
        )
        entries.isNullOrEmpty() -> Text(
            "No tracked traffic on this route right now.",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp)
        )
        else -> Column(Modifier.padding(horizontal = 12.dp)) {
            entries.take(8).forEach { e -> BoardRow(e, onSelect) }
        }
    }
}

@Composable
private fun GroundList(aircraft: List<Aircraft>, onSelect: (Aircraft) -> Unit) {
    if (aircraft.isEmpty()) {
        Text(
            "No aircraft on the ground right now.",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp)
        )
    } else {
        Column(Modifier.padding(horizontal = 12.dp)) {
            aircraft.take(8).forEach { ac ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ac) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✈", color = Accent, fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ac.callsign.ifEmpty { ac.icao24.uppercase() },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            listOfNotNull(
                                ac.typeCode,
                                ac.registration
                            ).joinToString(" · ").ifEmpty { ac.icao24.uppercase() },
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(e: BoardEntry, onSelect: (Aircraft) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(e.aircraft) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (e.kind == BoardKind.DEPARTURE) "🛫" else "🛬", fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.aircraft.callsign.ifEmpty { e.aircraft.icao24.uppercase() },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            val sub = listOfNotNull(e.routeLabel, e.airlineName).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${e.distanceKm} km", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                e.etaMinutes?.let { m -> if (m >= 60) "in ${m / 60}h ${m % 60}m" else "in ${m}m" } ?: "—",
                color = Accent,
                fontSize = 11.sp
            )
        }
    }
}

/** Esri World Imagery satellite tile centered on the airport (keyless). */
private fun airportTileUrl(lat: Double, lon: Double): String {
    val z = 14
    val n = Math.pow(2.0, z.toDouble()).toInt()
    val x = ((lon + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
}

// ---------- Map control buttons ----------

@Composable
fun MapControls(
    airportsOn: Boolean,
    labelsOn: Boolean,
    is3D: Boolean,
    radarOn: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCenter: () -> Unit,
    onToggle3D: () -> Unit,
    onToggleAirports: () -> Unit,
    onToggleLabels: () -> Unit,
    onToggleRadar: () -> Unit,
    onOpenOps: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAr: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ControlButton("+", "Zoom in", onZoomIn)
        ControlButton("−", "Zoom out", onZoomOut)
        ControlButton("⌂", "Home view", onCenter)
        ControlButton("3D", "Toggle 3D view", onToggle3D, active = is3D)
        ControlButton("RAD", "Rain radar", onToggleRadar, active = radarOn)
        ControlButton("AP", "Airports", onToggleAirports, active = airportsOn)
        ControlButton("AB", "Callsigns", onToggleLabels, active = labelsOn)
        ControlButton("🚁", "Emergency services & military", onOpenOps)
        ControlButton("⏱", "Squawk history", onOpenAlerts)
        ControlButton("AR", "Sky view: point at the sky", onOpenAr)
        ControlButton("☰", "Filters", onOpenFilters)
    }
}

@Composable
private fun ControlButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                if (active) Accent else PanelBg,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = if (active) Color(0xFF101014) else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Following chip ----------

@Composable
fun FollowingChip(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onCancel),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Accent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF101014), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Following · tap to stop", color = Color(0xFF101014), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Flight details panel ----------

@Composable
fun FlightDetailsPanel(
    selected: SelectedFlight,
    routeProgress: RouteProgress?,
    isLoading: Boolean,
    isFollowing: Boolean,
    onClose: () -> Unit,
    onToggleFollow: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ac = selected.aircraft
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Drag handle
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ac.callsign.ifEmpty { ac.icao24.uppercase() },
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        selected.opsCategory?.let { ops ->
                            Text(
                                "${ops.emoji} ${ops.label}",
                                color = Color(android.graphics.Color.parseColor(ops.ringColor)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val route = selected.route?.routeLabel
                        val subtitle = buildString {
                            selected.info?.let { info ->
                                listOfNotNull(info.registration, info.typeCode).joinToString(" · ").let { if (it.isNotEmpty()) append(it) }
                            }
                            if (!route.isNullOrEmpty()) {
                                if (isNotEmpty()) append("   ")
                                append(route)
                            }
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        selected.route?.airlineName?.let {
                            Text(it, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    // Follow toggle
                    IconButton(onClick = onToggleFollow) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = if (isFollowing) "Stop following" else "Follow aircraft",
                            tint = if (isFollowing) Accent else TextSecondary
                        )
                    }
                    // Flight history replay
                    IconButton(onClick = onReplay) {
                        Text("⏱", color = TextSecondary, fontSize = 16.sp)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Photo
                selected.photoUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Aircraft photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading aircraft details…", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

                // Data grid
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        DataCell("ALTITUDE", formatAltitudeFt(if (ac.onGround) 0 else ac.altitudeFt), Modifier.weight(1f))
                        DataCell("GROUND SPEED", formatSpeedKt(ac.speedKt), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        DataCell("VERTICAL", formatClimbFpm(ac.climbFpm), Modifier.weight(1f))
                        DataCell("TRACK", formatHeading(ac.heading), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        DataCell("SQUAWK", ac.squawk ?: "—", Modifier.weight(1f))
                        DataCell(
                            "STATUS",
                            when {
                                ac.onGround -> "On ground"
                                ac.climbFpm > 300 -> "Climbing"
                                ac.climbFpm < -300 -> "Descending"
                                else -> "Cruising"
                            },
                            Modifier.weight(1f)
                        )
                    }

                    val info = selected.info
                    if (info != null && (info.registeredOwner != null || info.typeDescription != null)) {
                        HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                        Row(Modifier.fillMaxWidth()) {
                            DataCell("AIRCRAFT", info.typeDescription ?: info.typeCode ?: "—", Modifier.weight(1f))
                            DataCell("OPERATOR", info.registeredOwner ?: "—", Modifier.weight(1f))
                        }
                    }

                    // FR24-style route section: origin -> plane -> destination,
                    // live progress bar and flown/remaining strip.
                    val route = selected.route
                    if (route != null && (route.origin != null || route.destination != null)) {
                        HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                        RouteHeader(route.origin, route.destination)
                        RouteProgressBar(routeProgress)
                        RouteStatsStrip(route.origin, route.destination, routeProgress)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- FR24-style route section ----------

@Composable
private fun RouteHeader(
    origin: Airport?,
    destination: Airport?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Origin
        Column(Modifier.weight(1f)) {
            Text(
                origin?.iata ?: "—",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                origin?.municipality ?: origin?.name ?: "",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
            origin?.country?.let {
                Text(it, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }

        // Plane badge between the airports
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✈", color = Color(0xFF101014), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Destination
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                destination?.iata ?: "—",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
            Text(
                destination?.municipality ?: destination?.name ?: "",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.End
            )
            destination?.country?.let {
                Text(it, color = TextSecondary, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun RouteProgressBar(progress: RouteProgress?) {
    if (progress == null) return
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(22.dp)
    ) {
        val barWidth = maxWidth - 22.dp // room for the plane marker at 100%
        // Track
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
        )
        // Flown portion
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(barWidth * progress.fraction)
                .height(4.dp)
                .background(Accent, RoundedCornerShape(2.dp))
        )
        // Plane marker riding the bar, rotated to point along travel direction
        Text(
            "✈",
            color = Accent,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = barWidth * progress.fraction)
        )
    }
}

@Composable
private fun RouteStatsStrip(
    origin: Airport?,
    destination: Airport?,
    progress: RouteProgress?
) {
    val text = if (progress != null) {
        buildString {
            append("${progress.flownKm.roundToInt()} km flown")
            append("  ·  ${progress.remainingKm.roundToInt()} km to go")
            progress.etaMinutes?.let { mins ->
                append("  ·  ${calculateClockETA(mins)}")
            }
        }
    } else {
        // No live position/progress: show total route distance if we can.
        val o = origin?.latitude?.let { lat -> origin.longitude?.let { lon -> lat to lon } }
        val d = destination?.latitude?.let { lat -> destination.longitude?.let { lon -> lat to lon } }
        if (o != null && d != null) {
            val totalKm = GeoMath.distanceMeters(
                o.first, o.second, d.first, d.second
            ) / 1000.0
            "${totalKm.roundToInt()} km total route"
        } else ""
    }
    if (text.isEmpty()) return
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    )
}

// ---------- Filter bottom sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    visible: Boolean,
    filters: FilterState,
    showAirportWx: Boolean,
    onToggleAirportWx: () -> Unit,
    onChange: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PanelBgLight,
        contentColor = TextPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Map filters", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            Text(
                "Altitude band: ${"%,d".format(filters.minAltitudeFt)} ft – ${"%,d".format(filters.maxAltitudeFt)} ft",
                color = TextSecondary, fontSize = 13.sp
            )
            RangeSlider(
                value = filters.minAltitudeFt.toFloat()..filters.maxAltitudeFt.toFloat(),
                onValueChange = { range ->
                    onChange(
                        filters.copy(
                            minAltitudeFt = range.start.roundToInt(),
                            maxAltitudeFt = range.endInclusive.roundToInt()
                        )
                    )
                },
                valueRange = -2000f..50_000f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.height(8.dp))

            FilterToggle("Show airports", filters.showAirports) { onChange(filters.copy(showAirports = it)) }
            FilterToggle("Show callsign labels", filters.showLabels) { onChange(filters.copy(showLabels = it)) }
            FilterToggle("Show flight trail", filters.showTrail) { onChange(filters.copy(showTrail = it)) }
            FilterToggle("Only emergency services", filters.showOnlyOps) { onChange(filters.copy(showOnlyOps = it)) }
            FilterToggle("Airport weather chips", showAirportWx) { onToggleAirportWx() }

            // Ops legend
            AnimatedVisibility(visible = filters.showOnlyOps, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Badge colours", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    OpsCategory.entries.forEach { cat ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(10.dp).background(Color(android.graphics.Color.parseColor(cat.ringColor)), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(cat.label, color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tap a plane for full flight details, trail and route.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ---------- Lifeboat details panel ----------

@Composable
fun LifeboatDetailsPanel(
    vessel: Vessel,
    metar: Metar? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Drag handle
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }

                // Header
                val (flagEmoji, countryName) = vessel.countryFlagAndName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$flagEmoji ${vessel.name.ifBlank { "Unknown Lifeboat" }}",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "🛥 ${vessel.shipTypeText}  ·  MMSI ${vessel.mmsi}",
                            color = Color(0xFF00BCD4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

                // 3-Column Top Stat Strip: Speed | Course | Received
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Speed", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            formatSpeedKt(vessel.speedKnots.toInt()),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Course", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            if (vessel.heading > 0 && vessel.heading <= 360) "${vessel.heading.roundToInt()}°" else "---",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Received", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            formatReceivedTime(vessel.lastSeen),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

                // Destination / Location / Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(Color(0xFF0288D1).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        if (vessel.destination.isNotBlank()) {
                            Text(
                                vessel.destination.uppercase(),
                                color = Color(0xFF4FC3F7),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "$flagEmoji $countryName  ·  ${vessel.navStatusText}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                "$flagEmoji $countryName",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Status: ${vessel.navStatusText}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Data grid (Only real data, NO fake placeholders!)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        DataCell("Type", vessel.shipTypeText, Modifier.weight(1f))
                        DataCell("Status", vessel.navStatusText, Modifier.weight(1f))
                    }
                    val showDraught = vessel.draught > 0.0
                    val showSize = vessel.lengthMeters > 0 && vessel.widthMeters > 0
                    if (showDraught || showSize) {
                        Row(Modifier.fillMaxWidth()) {
                            if (showDraught) {
                                DataCell("Draught", "${vessel.draught} m", Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (showSize) {
                                DataCell("Size", "${vessel.lengthMeters} x ${vessel.widthMeters} m", Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    val showCallSign = vessel.callSign.isNotBlank()
                    val showImo = vessel.imoNumber > 0
                    if (showCallSign || showImo) {
                        Row(Modifier.fillMaxWidth()) {
                            if (showCallSign) {
                                DataCell("Call Sign", vessel.callSign, Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (showImo) {
                                DataCell("IMO", "${vessel.imoNumber}", Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    // Weather row: ONLY shown when real nearest METAR is available (no fake placeholders!)
                    if (metar != null) {
                        HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                        Column {
                            Text("WEATHER AT POSITION", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${metar.tempC?.roundToInt() ?: "—"}°C  ·  Wind ${formatWind(metar)}  ·  ${metar.condition}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatReceivedTime(lastSeenMs: Long): String {
    if (lastSeenMs <= 0L) return "Just now"
    val diffSec = ((System.currentTimeMillis() - lastSeenMs) / 1000).coerceAtLeast(0)
    return when {
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60}m, ${diffSec % 60}s ago"
        else -> {
            val hours = diffSec / 3600
            val mins = (diffSec % 3600) / 60
            "${hours}h, ${mins}m ago"
        }
    }
}
