package com.example.plane_tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
private val PanelBg = Color(0xFF11161F)
private val PanelBgLight = Color(0xFF161B22)
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            .size(40.dp)
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
    val info = selected.info
    val route = selected.route

    val isEmergencySquawk = ac.squawk in setOf("7700", "7600", "7500")

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF222C38))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF333D4B), RoundedCornerShape(2.dp))
                )

                // Compact Top Bar Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left side: Callsign, Airline, Model & Hex
                    Column(modifier = Modifier.weight(1f)) {
                        val flightCode = ac.callsign.ifBlank { "UNKNOWN" }
                        val airline = route?.airlineName ?: info?.registeredOwner.orEmpty()
                        val aircraftModel = info?.typeDescription ?: info?.typeCode ?: ac.typeCode ?: "Aircraft"
                        val registration = info?.registration ?: ac.registration
                        val modelAndReg = if (!registration.isNullOrBlank()) {
                            "$aircraftModel · $registration"
                        } else {
                            aircraftModel
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = flightCode,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (airline.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = airline,
                                    color = Color(0xFF9EABB8),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(3.dp))

                        // Model, Reg & Hex all together on one horizontal line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = modelAndReg,
                                color = Color(0xFFF5B942), // Accent yellow/orange
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "·",
                                color = Color(0xFF5A6978),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "HEX ${ac.icao24.uppercase()}",
                                color = Color(0xFF7E8D9D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        selected.opsCategory?.let { ops ->
                            Text(
                                text = "${ops.emoji} ${ops.label}",
                                color = Color(android.graphics.Color.parseColor(ops.ringColor)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Right side: Action icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFollow, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = "Follow",
                                tint = if (isFollowing) Color(0xFFF5B942) else Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onReplay, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "History",
                                tint = Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Aircraft Photo section
                selected.photoUrl?.let { url ->
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Aircraft photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Floating Type/Registration badge overlaid bottom-left
                        val photoBadgeText = buildString {
                            val type = ac.typeCode ?: info?.typeCode
                            val reg = info?.registration ?: ac.registration
                            if (!type.isNullOrBlank()) append(type)
                            if (!reg.isNullOrBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(reg)
                            }
                        }
                        if (photoBadgeText.isNotEmpty()) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = photoBadgeText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Floating photographer / photo credit overlay bottom-right
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📷 Planespotters",
                                color = Color(0xFFB0BEC5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
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

                Spacer(Modifier.height(8.dp))

                // Route & Progress Section
                if (route != null && (route.origin != null || route.destination != null)) {
                    RouteSection(route.origin, route.destination, routeProgress)
                    Spacer(Modifier.height(8.dp))
                }

                // Telemetry Tiles Section
                Column {
                    // Emergency Squawk Alert Banner (if applicable)
                    if (isEmergencySquawk) {
                        val emergencyLabel = when (ac.squawk) {
                            "7700" -> "EMERGENCY (7700)"
                            "7600" -> "RADIO FAILURE (7600)"
                            "7500" -> "HIJACK (7500)"
                            else -> "SQUAWK ALERT (${ac.squawk})"
                        }
                        Surface(
                            color = Color(0xFF3B1A1C),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFFF3B30)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🚨", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = emergencyLabel,
                                    color = Color(0xFFFF453A),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Row 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryTile(
                            label = "ALTITUDE",
                            value = "${if (ac.onGround) 0 else ac.altitudeFt} ft",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "V/S",
                            value = "${if (ac.climbFpm >= 0) "↗ +" else "↘ "}${ac.climbFpm} fpm",
                            valueColor = if (ac.climbFpm > 100) Color(0xFF4ADE80) else if (ac.climbFpm < -100) Color(0xFFF5B942) else Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "SPEED",
                            value = "${ac.speedKt} kt",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryTile(
                            label = "TRACK",
                            value = "${ac.heading.toInt()}°",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "SQUAWK",
                            value = (ac.squawk ?: "").ifBlank { "----" },
                            isEmergency = isEmergencySquawk,
                            modifier = Modifier.weight(1f)
                        )
                        val statusText = when {
                            ac.onGround -> "On ground"
                            ac.climbFpm > 300 -> "Climbing"
                            ac.climbFpm < -300 -> "Descending"
                            else -> "Cruising"
                        }
                        TelemetryTile(
                            label = "STATUS",
                            value = statusText.ifBlank { "En Route" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3
                    if (info != null || ac.registration != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryTile(
                                label = "AIRCRAFT",
                                value = info?.typeDescription ?: info?.typeCode ?: ac.typeCode ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = "OPERATOR",
                                value = info?.registeredOwner ?: route?.airlineName ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    isEmergency: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = if (isEmergency) Color(0xFF3B1A1C) else Color(0xFF19202B),
        shape = RoundedCornerShape(8.dp),
        border = if (isEmergency) BorderStroke(1.5.dp, Color(0xFFFF3B30)) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEmergency) "$label · ALERT" else label,
                color = if (isEmergency) Color(0xFFFF6B6B) else Color(0xFF7E8D9D),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = if (isEmergency) Color(0xFFFF453A) else valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RouteSection(
    origin: Airport?,
    destination: Airport?,
    progress: RouteProgress?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(Color(0xFF161B24), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Origin -> Plane Icon -> Destination
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Origin
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = origin?.iata ?: "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = origin?.municipality ?: origin?.name ?: "Origin",
                    fontSize = 11.sp,
                    color = Color(0xFF8A99A8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Plane badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2A2215), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✈", color = Color(0xFFF5B942), fontSize = 16.sp)
            }

            // Destination
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = destination?.iata ?: "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.End
                )
                Text(
                    text = destination?.municipality ?: destination?.name ?: "Destination",
                    fontSize = 11.sp,
                    color = Color(0xFF8A99A8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = { progress?.fraction ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFFF5B942),
            trackColor = Color(0xFF222C38),
        )

        Spacer(Modifier.height(8.dp))

        // Distance / ETA Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val flownStr = progress?.let { "${it.flownKm.roundToInt()} km flown" } ?: "— km flown"
            val remainingStr = progress?.let { "${it.remainingKm.roundToInt()} km to go" } ?: "— km to go"
            val etaStr = progress?.etaMinutes?.let { mins ->
                "${calculateClockETA(mins)} (${mins}m)"
            } ?: "ETA —"

            Text(flownStr, fontSize = 11.sp, color = Color(0xFF8A99A8))
            Text(etaStr, fontSize = 11.sp, color = Color(0xFFF5B942), fontWeight = FontWeight.SemiBold)
            Text(remainingStr, fontSize = 11.sp, color = Color(0xFF8A99A8))
        }
    }
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
    isFollowing: Boolean = false,
    onClose: () -> Unit,
    onToggleFollow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Resolve country and shorten long names so they don't wrap awkwardly
    val (titleFlag, countryName) = vessel.countryFlagAndName
    val shortCountry = when (countryName.trim()) {
        "United Kingdom" -> "UK"
        "United States" -> "USA"
        else -> countryName
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11141A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                // Drag handle pill at the top
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF333D4B), RoundedCornerShape(2.dp))
                )

                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top // Anchors icons and avatar at the top if title wraps
                ) {
                    // Lifeboat icon container
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF2E1A11), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF6E391F), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚤", fontSize = 22.sp)
                    }

                    Spacer(Modifier.width(12.dp))

                    // Title + MMSI & Country
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                    ) {
                        Text(
                            text = "$titleFlag ${vessel.name.ifBlank { "UNKNOWN VESSEL" }}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, // Allows 2 lines so full station / vessel names fit
                            lineHeight = 20.sp,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "MMSI ${vessel.mmsi}${if (shortCountry.isNotBlank()) "  ·  $shortCountry" else ""}",
                            color = Color(0xFF9EABB8),
                            fontSize = 13.sp,
                            maxLines = 1, // Keeps the MMSI & Country on one clean line
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // Action buttons (Pin / Follow & Close)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFollow,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = if (isFollowing) "Stop following" else "Follow vessel",
                                tint = if (isFollowing) Accent else Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Stats Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed column
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Speed", color = Color(0xFF8A99A8), fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${vessel.speedKnots.roundToInt()} kts",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Course column
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Course", color = Color(0xFF8A99A8), fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (vessel.heading in 1.0..360.0) "${vessel.heading.roundToInt()}°" else "—",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Status column (shortened so long AIS strings don't crowd the card)
                        Column(modifier = Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Status", color = Color(0xFF8A99A8), fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            val shortStatus = when {
                                vessel.navStatusText.contains("engine", ignoreCase = true) -> "Underway"
                                vessel.navStatusText.contains("moored", ignoreCase = true) -> "Moored"
                                vessel.navStatusText.contains("anchor", ignoreCase = true) -> "At Anchor"
                                else -> vessel.navStatusText.ifBlank { "Active" }
                            }
                            Text(
                                text = shortStatus,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Updated column
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Updated", color = Color(0xFF8A99A8), fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatReceivedTime(vessel.lastSeen),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Weather & Sea conditions (compact row at bottom)
                metar?.let { wx ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Wind: ${wx.windSpeedKt ?: 0} kts (${wx.windFromDeg ?: 0}°)  ·  ${wx.tempC ?: "--"}°C",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp
                        )
                        Text(
                            "SAR Active",
                            color = Color(0xFF4FC3F7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatReceivedTime(lastSeenMs: Long): String {
    if (lastSeenMs <= 0L) return "Just now"
    val diffSec = ((System.currentTimeMillis() - lastSeenMs) / 1000).coerceAtLeast(0)
    return when {
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        else -> {
            val hours = diffSec / 3600
            val mins = (diffSec % 3600) / 60
            "${hours} h, ${mins} m ago"
        }
    }
}
