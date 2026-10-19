package com.example.plane_tracker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val ac = selected.aircraft
    val info = selected.info
    val route = selected.route

    val isEmergencySquawk = ac.squawk in setOf("7700", "7600", "7500")
    val elapsedSec = rememberElapsedSeconds(ac.lastSeen)

    val registration = (info?.registration ?: ac.registration)?.trim().orEmpty()
    val typeCode = (info?.typeCode ?: ac.typeCode)?.trim().orEmpty()
    val manufacturer = info?.manufacturer?.trim().orEmpty()
    val typeDesc = info?.typeDescription?.trim().orEmpty()

    val fullModelName = buildString {
        if (manufacturer.isNotEmpty() && !typeDesc.startsWith(manufacturer, ignoreCase = true)) {
            append(manufacturer).append(" · ")
        }
        when {
            typeDesc.isNotEmpty() -> append(typeDesc)
            typeCode.isNotEmpty() -> append("ICAO Type $typeCode")
            else -> append("Tracked Aircraft")
        }
    }

    val (flagEmoji, resolvedCountry) = remember(ac.icao24, registration, info?.ownerCountry) {
        resolveAircraftCountry(ac.icao24, registration, info?.ownerCountry)
    }

    val callsignRole = remember(ac.callsign, typeCode, selected.opsCategory) {
        resolveCallsignRole(ac.callsign, typeCode, selected.opsCategory)
    }

    val airframeCategory = remember(typeCode, typeDesc) {
        resolveAirframeCategory(typeCode, typeDesc)
    }

    val nearestSector = remember(ac.latitude, ac.longitude) {
        resolveNearestAirportSector(ac.latitude, ac.longitude)
    }

    val flightLevelText = when {
        ac.onGround || ac.altitudeFt <= 0 -> "Ground · 0 m"
        ac.altitudeFt >= 3000 -> "FL%03d · %,d m".format((ac.altitudeFt / 100.0).roundToInt(), ac.altitudeMeters.roundToInt())
        else -> "%,d m AGL".format(ac.altitudeMeters.roundToInt())
    }

    val vsMetersPerSec = "%.1f m/s".format(ac.verticalRateMps)
    val vsTrend = when {
        ac.onGround -> "On ground"
        ac.climbFpm > 300 -> "$vsMetersPerSec · Climb"
        ac.climbFpm < -300 -> "$vsMetersPerSec · Descent"
        else -> "$vsMetersPerSec · Level"
    }

    val machEst = if (!ac.onGround && ac.speedKt >= 220) {
        val localSoundKt = (661.0 - (ac.altitudeFt.coerceIn(0, 40_000) / 40_000.0) * 88.0)
        val mach = ac.speedKt / localSoundKt
        if (mach >= 0.35) " · M%.2f".format(mach) else ""
    } else ""

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.80f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF222C38))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF333D4B), RoundedCornerShape(2.dp))
                )

                // Top Header: Callsign, Badges, Full Model & Action Icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val flightCode = ac.callsign.ifBlank { ac.icao24.uppercase() }
                        val airlineOrOwner = route?.airlineName ?: info?.registeredOwner.orEmpty()

                        // Line 1: Callsign + Ops/Military Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$flagEmoji $flightCode",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            selected.opsCategory?.let { ops ->
                                val opsColor = Color(android.graphics.Color.parseColor(ops.ringColor))
                                Surface(
                                    color = opsColor.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, opsColor.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = "${ops.emoji} ${ops.label}",
                                        color = opsColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (ac.isLadd) {
                                Surface(
                                    color = Color(0xFF2B2118),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Accent)
                                ) {
                                    Text(
                                        text = "LADD",
                                        color = Accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Line 2: Operator / Airline & Mission Role
                        val operatorSubtitle = listOfNotNull(
                            airlineOrOwner.takeIf { it.isNotBlank() },
                            callsignRole
                        ).distinct().joinToString("  ·  ")

                        if (operatorSubtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = operatorSubtitle,
                                color = Color(0xFF9EABB8),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        // Line 3: Full Aircraft Model (never truncated by reg/hex)
                        Text(
                            text = fullModelName,
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(5.dp))

                        // Line 4: Structured Pill Badges (REG, TYPE, HEX, CATEGORY) - Horizontally Scrollable & Interactive
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (registration.isNotEmpty()) {
                                HeaderMetaPill(
                                    text = "REG $registration",
                                    bgColor = Color(0xFF2A2215),
                                    borderColor = Color(0xFF6B5322),
                                    textColor = Accent,
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(registration))
                                        Toast.makeText(context, "Copied registration ($registration)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (typeCode.isNotEmpty()) {
                                HeaderMetaPill(
                                    text = "ICAO $typeCode",
                                    bgColor = Color(0xFF19202B),
                                    borderColor = Color(0xFF2B3648),
                                    textColor = Color(0xFFD0DCE8),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(typeCode))
                                        Toast.makeText(context, "Copied ICAO type ($typeCode)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            HeaderMetaPill(
                                text = "HEX ${ac.icao24.uppercase()}",
                                bgColor = Color(0xFF19202B),
                                borderColor = Color(0xFF2B3648),
                                textColor = Color(0xFF9EABB8),
                                onClick = {
                                    val hexCode = ac.icao24.uppercase()
                                    clipboardManager.setText(AnnotatedString(hexCode))
                                    Toast.makeText(context, "Copied Mode-S hex ($hexCode)", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (airframeCategory.isNotEmpty()) {
                                HeaderMetaPill(
                                    text = airframeCategory,
                                    bgColor = Color(0xFF142634),
                                    borderColor = Color(0xFF23455E),
                                    textColor = Color(0xFF7DD3FC),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(airframeCategory))
                                        Toast.makeText(context, "Copied category ($airframeCategory)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFollow, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = "Follow",
                                tint = if (isFollowing) Accent else Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onReplay, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "History",
                                tint = Color(0xFF9EABB8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
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
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(148.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Aircraft photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        val photoBadgeText = listOfNotNull(
                            typeCode.takeIf { it.isNotEmpty() },
                            registration.takeIf { it.isNotEmpty() },
                            resolvedCountry.takeIf { it.isNotEmpty() }
                        ).joinToString(" · ")

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
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading aircraft & route details…", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Route Section (if commercial route exists) OR Airspace Sector & Position Card
                if (route != null && (route.origin != null || route.destination != null)) {
                    RouteSection(
                        origin = route.origin,
                        destination = route.destination,
                        progress = routeProgress,
                        nearestSector = nearestSector,
                        latitude = ac.latitude,
                        longitude = ac.longitude
                    )
                } else {
                    AirspacePositionSection(
                        nearestSector = nearestSector,
                        latitude = ac.latitude,
                        longitude = ac.longitude,
                        isOnGround = ac.onGround
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Emergency Squawk Alert Banner (if applicable)
                if (isEmergencySquawk) {
                    val emergencyLabel = when (ac.squawk) {
                        "7700" -> "GENERAL EMERGENCY (SQUAWK 7700)"
                        "7600" -> "RADIO COMMUNICATIONS FAILURE (SQUAWK 7600)"
                        "7500" -> "UNLAWFUL INTERFERENCE / HIJACK (SQUAWK 7500)"
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

                // Telemetry Grid Row 1: Altitude, Vertical Rate, Ground Speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        label = "ALTITUDE (BARO)",
                        value = if (ac.onGround) "0 ft" else "%,d ft".format(ac.altitudeFt),
                        subValue = flightLevelText,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "VERTICAL RATE",
                        value = "${if (ac.climbFpm >= 0) "↗ +" else "↘ "}${ac.climbFpm} fpm",
                        subValue = vsTrend,
                        valueColor = when {
                            ac.climbFpm > 150 -> Color(0xFF4ADE80)
                            ac.climbFpm < -150 -> Accent
                            else -> Color.White
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "GROUND SPEED",
                        value = "${ac.speedKt} kt",
                        subValue = "${ac.speedKmh} km/h$machEst",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Telemetry Grid Row 2: Track/Compass, Decoded Squawk, Status/Signal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        label = "TRACK (HDG)",
                        value = "${ac.heading.roundToInt()}° ${aircraftCompassCardinal(ac.heading.toDouble())}",
                        subValue = "True Track",
                        modifier = Modifier.weight(1f)
                    )
                    val squawkCode = (ac.squawk ?: "").ifBlank { "----" }
                    TelemetryTile(
                        label = "SQUAWK",
                        value = squawkCode,
                        subValue = decodeSquawkMeaning(ac.squawk, ac.isMilitary),
                        isEmergency = isEmergencySquawk,
                        modifier = Modifier.weight(1f),
                        onClick = if (squawkCode != "----") {
                            {
                                clipboardManager.setText(AnnotatedString(squawkCode))
                                Toast.makeText(context, "Copied Squawk ($squawkCode)", Toast.LENGTH_SHORT).show()
                            }
                        } else null
                    )
                    val statusText = when {
                        ac.onGround -> "On Ground"
                        ac.climbFpm > 300 -> "Climbing"
                        ac.climbFpm < -300 -> "Descending"
                        else -> "Cruising"
                    }
                    TelemetryTile(
                        label = "STATUS / FEED",
                        value = statusText,
                        subValue = "Updated ${elapsedSec}s ago",
                        valueColor = if (ac.onGround) Color(0xFFB0BEC5) else Color(0xFF4ADE80),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Airframe & Operator Details Strip (2x2 Grid)
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(Color(0xFF161B24), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF222C38), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AircraftSpecItem(
                            label = "AIRFRAME & MODEL",
                            value = fullModelName,
                            modifier = Modifier.weight(1.15f),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(fullModelName))
                                Toast.makeText(context, "Copied model ($fullModelName)", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        val regTypeStr = listOfNotNull(
                            registration.takeIf { it.isNotEmpty() }?.let { "$flagEmoji $it" },
                            typeCode.takeIf { it.isNotEmpty() }?.let { "ICAO $it" }
                        ).joinToString(" · ").ifEmpty { "HEX ${ac.icao24.uppercase()}" }
                        AircraftSpecItem(
                            label = "REGISTRATION / TYPE",
                            value = regTypeStr,
                            alignEnd = true,
                            valueColor = Accent,
                            modifier = Modifier.weight(0.85f),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(regTypeStr))
                                Toast.makeText(context, "Copied ($regTypeStr)", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF222C38))
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val opVal = info?.registeredOwner ?: route?.airlineName ?: "Private / Unlisted"
                        AircraftSpecItem(
                            label = "REGISTERED OPERATOR",
                            value = opVal,
                            modifier = Modifier.weight(1.1f),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(opVal))
                                Toast.makeText(context, "Copied operator ($opVal)", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        val countryRoleVal = listOfNotNull(
                            resolvedCountry.takeIf { it.isNotEmpty() },
                            callsignRole ?: selected.opsCategory?.label ?: airframeCategory.takeIf { it.isNotEmpty() }
                        ).joinToString(" · ").ifEmpty { "Civil Aviation" }
                        AircraftSpecItem(
                            label = "COUNTRY / ROLE",
                            value = countryRoleVal,
                            alignEnd = true,
                            valueColor = Color(0xFF7DD3FC),
                            modifier = Modifier.weight(0.9f),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(countryRoleVal))
                                Toast.makeText(context, "Copied ($countryRoleVal)", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun HeaderMetaPill(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AircraftSpecItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    alignEnd: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = Color(0xFF7E8D9D),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    isEmergency: Boolean = false,
    subValue: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = if (isEmergency) Color(0xFF3B1A1C) else Color(0xFF19202B),
        shape = RoundedCornerShape(8.dp),
        border = if (isEmergency) BorderStroke(1.5.dp, Color(0xFFFF3B30)) else BorderStroke(1.dp, Color(0xFF222C38))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEmergency) "$label · ALERT" else label,
                color = if (isEmergency) Color(0xFFFF6B6B) else Color(0xFF7E8D9D),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = if (isEmergency) Color(0xFFFF453A) else valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subValue.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subValue,
                    color = if (isEmergency) Color(0xFFFF9E9E) else Color(0xFF8A99A8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AirspacePositionSection(
    nearestSector: String,
    latitude: Double,
    longitude: Double,
    isOnGround: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color(0xFF161B24), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF222C38), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.25f)) {
                Text(
                    text = if (isOnGround) "AIRPORT / SURFACE LOCATION" else "AIRSPACE SECTOR (NEAREST REF)",
                    color = Color(0xFF7E8D9D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = nearestSector,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "COORDINATES",
                    color = Color(0xFF7E8D9D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatAircraftDms(latitude, longitude),
                    color = Color(0xFFD0DCE8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "%.4f°, %.4f°".format(latitude, longitude),
                    color = Color(0xFF7E8D9D),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RouteSection(
    origin: Airport?,
    destination: Airport?,
    progress: RouteProgress?,
    nearestSector: String,
    latitude: Double,
    longitude: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color(0xFF161B24), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF222C38), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Origin
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = origin?.iata ?: origin?.icao ?: "—",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    origin?.icao?.takeIf { it != origin.iata }?.let { icao ->
                        Text(
                            text = icao,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Text(
                    text = origin?.municipality ?: origin?.name ?: "Origin",
                    fontSize = 11.sp,
                    color = Color(0xFF8A99A8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Plane badge + total route distance
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF2A2215), CircleShape)
                        .border(1.dp, Color(0xFF6B5322), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✈", color = Accent, fontSize = 15.sp)
                }
                progress?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${(it.totalKm / 1.852).roundToInt()} nm",
                        fontSize = 9.sp,
                        color = Color(0xFF7E8D9D),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Destination
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    destination?.icao?.takeIf { it != destination.iata }?.let { icao ->
                        Text(
                            text = icao,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text(
                        text = destination?.iata ?: destination?.icao ?: "—",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.End
                    )
                }
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

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress?.fraction ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Accent,
            trackColor = Color(0xFF222C38),
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val flownStr = progress?.let {
                "${it.flownKm.roundToInt()} km (${(it.flownKm / 1.852).roundToInt()} nm)"
            } ?: "— km flown"
            val remainingStr = progress?.let {
                "${it.remainingKm.roundToInt()} km (${(it.remainingKm / 1.852).roundToInt()} nm) left"
            } ?: "— km to go"
            val etaStr = progress?.etaMinutes?.let { mins ->
                "${calculateClockETA(mins)} (${mins}m)"
            } ?: "ETA —"

            Text(flownStr, fontSize = 10.sp, color = Color(0xFF8A99A8))
            Text(etaStr, fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold)
            Text(remainingStr, fontSize = 10.sp, color = Color(0xFF8A99A8))
        }

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0xFF222C38))
        Spacer(Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📍 $nearestSector",
                fontSize = 10.sp,
                color = Color(0xFF9EABB8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "%.4f°, %.4f°".format(latitude, longitude),
                fontSize = 10.sp,
                color = Color(0xFF7E8D9D)
            )
        }
    }
}

private fun aircraftCompassCardinal(deg: Double): String {
    val dirs = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )
    val idx = (((deg % 360.0 + 360.0) % 360.0) / 22.5).roundToInt() % 16
    return dirs[idx]
}

private fun formatAircraftDms(lat: Double, lon: Double): String {
    fun toDms(v: Double, posHem: Char, negHem: Char): String {
        val hem = if (v >= 0.0) posHem else negHem
        val absV = kotlin.math.abs(v)
        val deg = absV.toInt()
        val minFull = (absV - deg) * 60.0
        val min = minFull.toInt()
        val sec = ((minFull - min) * 60.0).roundToInt().coerceIn(0, 59)
        return "%02d°%02d'%02d\"%c".format(deg, min, sec, hem)
    }
    return "${toDms(lat, 'N', 'S')} ${toDms(lon, 'E', 'W')}"
}

private fun resolveNearestAirportSector(lat: Double, lon: Double): String {
    if (lat.isNaN() || lon.isNaN()) return "Unknown Airspace"
    val nearest = Airports.byCode.values.minByOrNull {
        GeoMath.distanceMeters(lat, lon, it.lat, it.lon)
    } ?: return "En Route Airspace"

    val distMeters = GeoMath.distanceMeters(lat, lon, nearest.lat, nearest.lon)
    val distNm = distMeters / 1852.0
    if (distNm < 2.0) {
        return "At ${nearest.name} (${nearest.iata}/${nearest.icao})"
    }
    val dLon = Math.toRadians(lon - nearest.lon)
    val lat1 = Math.toRadians(nearest.lat)
    val lat2 = Math.toRadians(lat)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    val bearing = (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    val cardinal = aircraftCompassCardinal(bearing)
    return "%.1f nm %s of %s (%s)".format(distNm, cardinal, nearest.name, nearest.iata)
}

private fun decodeSquawkMeaning(squawk: String?, isMilitary: Boolean): String {
    val sq = squawk?.trim().orEmpty()
    if (sq.length != 4) return "No Mode-A"
    return when {
        sq == "7700" -> "Emergency"
        sq == "7600" -> "Radio Failure"
        sq == "7500" -> "Hijack Alert"
        sq == "7000" -> "VFR Conspicuity"
        sq == "1200" -> "VFR (Americas)"
        sq == "2000" -> "IFR / Oceanic"
        sq == "1000" -> "Mode-S IFR"
        sq == "0000" -> "Discrete / Mil"
        sq == "7001" -> "Military Climb"
        sq == "7004" -> "Aerobatics"
        sq.startsWith("002") || sq.startsWith("003") -> "UK HEMS / Police"
        sq.startsWith("60") || sq.startsWith("61") || sq.startsWith("64") ->
            if (isMilitary) "UK Mil / NATO" else "ATC / Military"
        else -> "ATC Assigned"
    }
}

private fun resolveCallsignRole(callsign: String, typeCode: String, ops: OpsCategory?): String? {
    val cs = callsign.trim().uppercase()
    val type = typeCode.trim().uppercase()
    return when {
        cs.startsWith("TARTN") || cs.startsWith("TARTAN") || cs.startsWith("VOYGR") -> "Air-to-Air Refuelling"
        cs.startsWith("ASCOT") || cs.startsWith("RRR") -> "RAF Air Mobility"
        cs.startsWith("LION") || cs.startsWith("TYPHN") || cs.startsWith("COBRA") || cs.startsWith("VIPER") -> "Fast Jet / QRA"
        cs.startsWith("RCH") -> "USAF Air Mobility"
        cs.startsWith("CNV") || cs.startsWith("NVY") -> "Naval Aviation"
        cs.startsWith("SRG") || cs.startsWith("RESCUE") || cs.startsWith("CGI") -> "Search & Rescue"
        cs.startsWith("HLE") || cs.startsWith("HELIMED") || cs.startsWith("GAMA") -> "HEMS Air Ambulance"
        cs.startsWith("UKP") || cs.startsWith("NPAS") || cs.startsWith("POLICE") -> "Police Air Support"
        type == "A332" && ops == OpsCategory.MILITARY -> "MRTT Tanker / Transport"
        type in setOf("K35R", "KC46", "A400", "C17", "C30J") -> "Military Transport / Tanker"
        else -> null
    }
}

private fun resolveAirframeCategory(typeCode: String, typeDesc: String): String {
    val t = typeCode.uppercase()
    val d = typeDesc.uppercase()
    return when {
        t in setOf("EC35", "EC45", "H135", "H145", "A139", "A169", "AW189", "S92", "R44", "B06", "AS50") ||
            d.contains("HELICOPTER") || d.contains("EUROCOPTER") -> "Rotorcraft"
        t in setOf("A388", "B744", "B748", "B77W", "B772", "B788", "B789", "B78X", "A359", "A35K", "A332", "A333", "A339", "C17", "A400") -> "Heavy"
        t in setOf("F15", "F16", "F35", "EUFI", "TORN", "HAWK") || d.contains("TYPHOON") -> "Fast Jet"
        t in setOf("B738", "B38M", "A320", "A321", "A20N", "A21N", "A319", "E190", "E195") -> "Narrowbody"
        t in setOf("AT76", "DH8D", "PC12", "BE20", "P68") -> "Turboprop"
        t in setOf("C172", "C152", "PA28", "DA40", "DA42", "SR22") -> "Light GA"
        else -> ""
    }
}

private fun resolveAircraftCountry(hex: String, reg: String, ownerCountry: String?): Pair<String, String> {
    val cleanOwner = ownerCountry?.trim().orEmpty()
    val h = hex.trim().lowercase()
    val r = reg.trim().uppercase()

    val country = when {
        cleanOwner.isNotEmpty() -> cleanOwner
        r.startsWith("G-") || r.matches(Regex("Z[A-Z]\\d{3}")) || h.startsWith("40") || h.startsWith("43c") -> "United Kingdom"
        r.startsWith("EI-") || h.startsWith("4ca") -> "Ireland"
        r.startsWith("N") && r.length >= 2 && r[1].isDigit() || h.startsWith("a") -> "United States"
        r.startsWith("C-") || h.startsWith("c0") -> "Canada"
        r.startsWith("D-") || h.startsWith("3c") -> "Germany"
        r.startsWith("F-") || h.startsWith("39") -> "France"
        r.startsWith("PH-") || h.startsWith("48") -> "Netherlands"
        r.startsWith("OO-") || h.startsWith("44") -> "Belgium"
        r.startsWith("EC-") || h.startsWith("34") -> "Spain"
        r.startsWith("I-") || r.startsWith("MM") || h.startsWith("30") -> "Italy"
        r.startsWith("HB-") || h.startsWith("4b") -> "Switzerland"
        r.startsWith("OE-") || h.startsWith("440") -> "Austria"
        r.startsWith("SE-") || h.startsWith("4a") -> "Sweden"
        r.startsWith("LN-") || h.startsWith("47") -> "Norway"
        r.startsWith("OY-") || h.startsWith("45") -> "Denmark"
        r.startsWith("OH-") || h.startsWith("46") -> "Finland"
        r.startsWith("SP-") || h.startsWith("48") -> "Poland"
        else -> ""
    }

    val flag = when (country.lowercase()) {
        "united kingdom", "uk", "great britain" -> "🇬🇧"
        "ireland" -> "🇮🇪"
        "united states", "usa", "us" -> "🇺🇸"
        "canada" -> "🇨🇦"
        "germany" -> "🇩🇪"
        "france" -> "🇫🇷"
        "netherlands" -> "🇳🇱"
        "belgium" -> "🇧🇪"
        "spain" -> "🇪🇸"
        "italy" -> "🇮🇹"
        "switzerland" -> "🇨🇭"
        "austria" -> "🇦🇹"
        "sweden" -> "🇸🇪"
        "norway" -> "🇳🇴"
        "denmark" -> "🇩🇰"
        "finland" -> "🇫🇮"
        "poland" -> "🇵🇱"
        "portugal" -> "🇵🇹"
        "greece" -> "🇬🇷"
        "turkey" -> "🇹🇷"
        "united arab emirates", "uae" -> "🇦🇪"
        "qatar" -> "🇶🇦"
        "australia" -> "🇦🇺"
        else -> "✈"
    }

    return flag to country
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

private data class LifeboatSpecs(
    val organization: String,
    val fleetNo: String?,
    val vesselClass: String,
    val lengthM: Double,
    val beamM: Double,
    val draughtM: Double,
    val maxSpeedKt: Int,
    val propulsion: String
)

@Composable
fun LifeboatDetailsPanel(
    vessel: Vessel,
    metar: Metar? = null,
    isFollowing: Boolean = false,
    onClose: () -> Unit,
    onToggleFollow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (titleFlag, countryName) = vessel.countryFlagAndName
    val shortCountry = when (countryName.trim()) {
        "United Kingdom" -> "UK"
        "United States" -> "USA"
        else -> countryName
    }

    val knownInfo = remember(vessel.mmsi) {
        com.example.plane_tracker.data.KnownLifeboats.getKnownDetails(vessel.mmsi)
    }
    val specs = remember(vessel.name, vessel.mmsi, vessel.shipType) {
        resolveLifeboatSpecs(vessel.name, vessel.mmsi)
    }
    val stationInfo = remember(vessel.mmsi, vessel.latitude, vessel.longitude) {
        resolveStationInfo(vessel.mmsi, vessel.latitude, vessel.longitude, knownInfo)
    }

    val callSign = vessel.callSign.ifBlank { knownInfo?.callSign.orEmpty() }
    val elapsedSec = rememberElapsedSeconds(vessel.lastSeen)
    val isUnderway = vessel.speedKnots >= 0.8 || vessel.navStatus == 0 || vessel.navStatus == 8

    val lengthText = if (vessel.lengthMeters > 0 && vessel.widthMeters > 0) {
        "${vessel.lengthMeters}×${vessel.widthMeters} m"
    } else if (specs.lengthM > 0.0) {
        "%.1f×%.1f m".format(specs.lengthM, specs.beamM)
    } else {
        "—"
    }

    val draughtVal = if (vessel.draught > 0.0) vessel.draught else specs.draughtM
    val draughtText = if (draughtVal > 0.0) "%.1f m".format(draughtVal) else "—"

    val speedKmh = (vessel.speedKnots * 1.852).roundToInt()
    val speedDisplay = if (vessel.speedKnots >= 0.5) {
        "%.1f kt (%d km/h)".format(vessel.speedKnots, speedKmh)
    } else {
        "0.0 kt"
    }

    val courseDisplay = if (vessel.heading in 0.1..359.9) {
        "${vessel.heading.roundToInt()}° ${compassCardinal(vessel.heading)}"
    } else {
        "—"
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
            colors = CardDefaults.cardColors(containerColor = PanelBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF222C38))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF333D4B), RoundedCornerShape(2.dp))
                )

                // Top Header: Emblem, Full Vessel Name, Class + Fleet Badge & Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Custom RNLI-style lifeboat emblem with status dot
                    Box(modifier = Modifier.size(46.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2f
                            drawCircle(color = Color(0xFF152735), radius = r)
                            drawCircle(
                                color = Color(0xFFFF7A1A),
                                radius = r - 1.5.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        Text(
                            text = "🛥",
                            fontSize = 21.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    if (isUnderway) ChipGreen else Color(0xFF38BDF8),
                                    CircleShape
                                )
                                .border(2.dp, PanelBg, CircleShape)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$titleFlag ${vessel.name.ifBlank { "SAR LIFEBOAT ${vessel.mmsi}" }}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            lineHeight = 20.sp,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specs.fleetNo?.let { fleet ->
                                Surface(
                                    color = Color(0xFF2E1A11),
                                    shape = RoundedCornerShape(5.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF7A1A))
                                ) {
                                    Text(
                                        text = fleet,
                                        color = Color(0xFFFF9E5E),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${specs.organization} · ${specs.vesselClass}",
                                color = Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        val metaParts = buildList {
                            add("MMSI ${vessel.mmsi}")
                            if (callSign.isNotBlank()) add("CS $callSign")
                            if (vessel.imoNumber > 0) add("IMO ${vessel.imoNumber}")
                            if (shortCountry.isNotBlank()) add(shortCountry)
                        }
                        Text(
                            text = metaParts.joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFollow, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = if (isFollowing) "Stop following" else "Follow vessel",
                                tint = if (isFollowing) Accent else Color(0xFF9EABB8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9EABB8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Station, Operational Status & Coordinates Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(Color(0xFF161B24), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF222C38), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = "HOME STATION",
                                color = Color(0xFF7E8D9D),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stationInfo.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = if (vessel.destination.isNotBlank()) "DESTINATION" else "STATION RANGE",
                                color = Color(0xFF7E8D9D),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = vessel.destination.ifBlank { stationInfo.second },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF222C38))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isUnderway) Color(0xFF143321) else Color(0xFF132738),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isUnderway) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                            )
                        ) {
                            Text(
                                text = if (isUnderway) "🚨 UNDERWAY · SAR" else "⚓ ${shortNavStatus(vessel)}",
                                color = if (isUnderway) Color(0xFF4ADE80) else Color(0xFF7DD3FC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = "${formatDmsCoordinates(vessel.latitude, vessel.longitude)}  (${
                                "%.4f°, %.4f°".format(
                                    vessel.latitude,
                                    vessel.longitude
                                )
                            })",
                            color = Color(0xFF9EABB8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Telemetry Grid Row 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        label = "SPEED (SOG)",
                        value = speedDisplay,
                        valueColor = if (isUnderway) Color(0xFF4ADE80) else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "COURSE (COG)",
                        value = courseDisplay,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "MAX SPEED",
                        value = if (specs.maxSpeedKt > 0) "${specs.maxSpeedKt} kt (${specs.propulsion})" else "SAR Class",
                        valueColor = Accent,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Telemetry Grid Row 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        label = "HULL (L × B)",
                        value = lengthText,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "DRAUGHT",
                        value = draughtText,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "LAST REPORT",
                        value = formatElapsedSeconds(elapsedSec, vessel.lastSeen),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Marine Weather & Sea Conditions (2x2 Grid so no text truncates)
                metar?.let { wx ->
                    Spacer(Modifier.height(6.dp))
                    val windKt = wx.windSpeedKt ?: 0
                    val windDir = wx.windFromDeg
                    val windCard = if (windDir != null) compassCardinal(windDir.toDouble()) else "VRB"
                    val beaufort = beaufortDescription(windKt)
                    val emoji = WeatherMapper.emoji(wx.condition)
                    val visText = wx.visibility?.let { v -> if (v == "6+") "10km+" else "${v}sm" } ?: ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(Color(0xFF161B24), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF222C38), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MarineWxItem(
                                label = "WEATHER (${wx.icaoId})",
                                value = "$emoji ${wx.tempC?.roundToInt() ?: "--"}°C · ${wx.condition}",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            MarineWxItem(
                                label = "WIND",
                                value = if (windKt == 0) "Calm (0 kt)" else "$windKt kt · ${windDir ?: 0}° ($windCard)",
                                alignEnd = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFF222C38))
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MarineWxItem(
                                label = "SEA STATE",
                                value = beaufort,
                                valueColor = if (windKt >= 22) Accent else Color(0xFF7DD3FC),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            MarineWxItem(
                                label = "PRESSURE / VIS",
                                value = "${wx.altimHpa ?: "--"} hPa" + if (visText.isNotEmpty()) " · Vis $visText" else "",
                                alignEnd = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarineWxItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = Color(0xFF7E8D9D),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shortNavStatus(vessel: Vessel): String = when {
    vessel.navStatus == 0 || vessel.speedKnots >= 0.8 -> "UNDERWAY"
    vessel.navStatus == 1 -> "AT ANCHOR"
    vessel.navStatus == 5 -> "MOORED"
    vessel.navStatus == 14 -> "AIS-SART"
    vessel.speedKnots < 0.5 -> "MOORED"
    else -> "ACTIVE"
}

private fun compassCardinal(deg: Double): String {
    val dirs = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )
    val idx = (((deg % 360.0 + 360.0) % 360.0) / 22.5).roundToInt() % 16
    return dirs[idx]
}

private fun formatDmsCoordinates(lat: Double, lon: Double): String {
    fun toDms(v: Double, posHem: Char, negHem: Char): String {
        val hem = if (v >= 0.0) posHem else negHem
        val absV = kotlin.math.abs(v)
        val deg = absV.toInt()
        val minFull = (absV - deg) * 60.0
        val min = minFull.toInt()
        val sec = ((minFull - min) * 60.0).roundToInt().coerceIn(0, 59)
        return "%02d°%02d'%02d\"%c".format(deg, min, sec, hem)
    }
    return "${toDms(lat, 'N', 'S')} ${toDms(lon, 'E', 'W')}"
}

private fun beaufortDescription(windKt: Int): String = when {
    windKt < 1 -> "Bft 0 · Calm (Glassy)"
    windKt <= 3 -> "Bft 1 · Light Air (Ripples)"
    windKt <= 6 -> "Bft 2 · Light Breeze (Smooth)"
    windKt <= 10 -> "Bft 3 · Gentle Breeze (Slight)"
    windKt <= 16 -> "Bft 4 · Moderate (1–1.5m)"
    windKt <= 21 -> "Bft 5 · Fresh Breeze (2m)"
    windKt <= 27 -> "Bft 6 · Strong Breeze (3m)"
    windKt <= 33 -> "Bft 7 · Near Gale (4m)"
    windKt <= 40 -> "Bft 8 · Gale (5.5m)"
    windKt <= 47 -> "Bft 9 · Severe Gale (7m)"
    else -> "Bft 10+ · Storm (9m+)"
}

private fun formatElapsedSeconds(elapsedSec: Long, lastSeenMs: Long): String {
    if (lastSeenMs <= 0L) return "Just now"
    return when {
        elapsedSec < 60 -> "${elapsedSec}s ago"
        elapsedSec < 3600 -> "${elapsedSec / 60}m ${elapsedSec % 60}s ago"
        else -> "${elapsedSec / 3600}h ${(elapsedSec % 3600) / 60}m ago"
    }
}

private fun resolveLifeboatSpecs(name: String, mmsi: String): LifeboatSpecs {
    val upper = name.uppercase()
    val fleetMatch = Regex("\\b(17|16|14|13|12)-(\\d{2})\\b").find(upper)
    if (fleetMatch != null) {
        val classPrefix = fleetMatch.groupValues[1]
        val fleetNo = fleetMatch.value
        return when (classPrefix) {
            "17" -> LifeboatSpecs("RNLI", fleetNo, "Severn-class All-Weather", 17.0, 5.9, 2.1, 25, "Props")
            "16" -> LifeboatSpecs("RNLI", fleetNo, "Tamar-class All-Weather", 16.3, 5.3, 1.5, 25, "Props")
            "14" -> LifeboatSpecs("RNLI", fleetNo, "Trent-class All-Weather", 14.3, 4.9, 1.4, 25, "Props")
            "13" -> LifeboatSpecs("RNLI", fleetNo, "Shannon-class All-Weather", 13.6, 4.5, 1.0, 25, "Waterjets")
            "12" -> LifeboatSpecs("RNLI", fleetNo, "Mersey-class All-Weather", 11.6, 3.8, 1.1, 17, "Props")
            else -> LifeboatSpecs("RNLI", fleetNo, "All-Weather Lifeboat", 16.0, 5.2, 1.5, 25, "Props")
        }
    }

    val bClassMatch = Regex("\\bB-(\\d{3})\\b").find(upper)
    if (bClassMatch != null || upper.contains("ATLANTIC")) {
        return LifeboatSpecs("RNLI", bClassMatch?.value, "Atlantic 85 (B-class) Inshore", 8.4, 2.9, 0.5, 35, "Outboard")
    }

    val dClassMatch = Regex("\\bD-(\\d{3})\\b").find(upper)
    if (dClassMatch != null) {
        return LifeboatSpecs("RNLI", dClassMatch.value, "IB1 (D-class) Inshore", 5.0, 2.0, 0.4, 25, "Outboard")
    }

    return when {
        upper.contains("KNRM") || mmsi.startsWith("244690") ->
            LifeboatSpecs("KNRM", null, "Dutch SAR Reddingboot", 19.3, 6.5, 1.1, 34, "Waterjets")
        upper.contains("SEENOT") || upper.contains("DGZRS") || mmsi.startsWith("211") ->
            LifeboatSpecs("DGzRS", null, "Seenotrettungskreuzer", 23.1, 6.0, 1.6, 24, "Props")
        upper.contains("SSRS") || upper.contains("RESCUE ") || mmsi.startsWith("265586") ->
            LifeboatSpecs("SSRS", null, "Swedish SAR Rescue Vessel", 15.0, 4.2, 1.0, 34, "Waterjets")
        upper.contains("RS ") || mmsi.startsWith("257") ->
            LifeboatSpecs("Redningsselskapet", null, "Norwegian Redningsskøyte", 17.0, 4.6, 1.2, 32, "Waterjets")
        upper.contains("SNSM") || mmsi.startsWith("227") ->
            LifeboatSpecs("SNSM", null, "Canot Tous Temps SAR", 17.6, 4.4, 1.4, 25, "Props")
        upper.contains("RNLI") || upper.contains("RNLB") || mmsi.startsWith("232") || mmsi.startsWith("235") ->
            LifeboatSpecs("RNLI", null, "Search & Rescue Lifeboat", 15.0, 5.0, 1.4, 25, "SAR")
        else ->
            LifeboatSpecs("SAR Fleet", null, "Search & Rescue Vessel", 14.0, 4.5, 1.2, 25, "SAR")
    }
}

private fun resolveStationInfo(
    mmsi: String,
    lat: Double,
    lon: Double,
    known: com.example.plane_tracker.data.KnownLifeboats.KnownVesselInfo?
): Pair<String, String> {
    val stationNames = mapOf(
        "232001910" to "Harwich, Essex",
        "232002440" to "Spurn Point (Humber)",
        "232001940" to "St Peter Port, Guernsey",
        "232001890" to "Dover, Kent",
        "232003140" to "Margate, Kent",
        "232003141" to "Newhaven, East Sussex",
        "232004399" to "Aberdeen, Scotland",
        "232004401" to "Yarmouth, Isle of Wight",
        "232004402" to "Mallaig, Highland",
        "232004407" to "Torbay, Devon",
        "232004409" to "Portrush, Co. Antrim",
        "235005118" to "Weymouth, Dorset",
        "235005119" to "Tynemouth, North Shields",
        "235005121" to "Plymouth, Devon",
        "235005122" to "Penlee, Cornwall",
        "235007809" to "Buckie, Moray",
        "235007798" to "Tobermory, Isle of Mull",
        "235007797" to "Lochinver, Sutherland",
        "235007796" to "Holyhead, Anglesey",
        "235007795" to "Thurso, Caithness",
        "235010875" to "Rosslare Harbour, Wexford",
        "235010876" to "Castletownbere, Cork",
        "232002450" to "Aran Islands, Galway",
        "232002460" to "Valentia, Kerry",
        "232002470" to "Port Askaig, Islay",
        "232002490" to "Lerwick, Shetland",
        "232003049" to "St Mary's, Isles of Scilly",
        "232003050" to "Barra Island, Hebrides",
        "232003051" to "Kirkwall, Orkney",
        "232003052" to "Aith, Shetland",
        "232003133" to "Ballyglass, Co. Mayo",
        "232003134" to "Stromness, Orkney",
        "232003136" to "Stornoway, Isle of Lewis",
        "232003139" to "Campbeltown, Kintyre",
        "232003142" to "Arranmore, Donegal",
        "235050722" to "Appledore, Devon",
        "232002380" to "Port St Mary, Isle of Man",
        "232002582" to "Donaghadee, Co. Down",
        "232002340" to "Gorleston, Norfolk",
        "235009123" to "Falmouth, Cornwall",
        "235014389" to "Poole, Dorset",
        "235014390" to "Shoreham Harbour, Sussex",
        "235014391" to "Bridlington, East Yorks",
        "235000120" to "Spurn Point (Humber)",
        "235000121" to "Skegness, Lincolnshire",
        "235000122" to "Whitby, North Yorks",
        "235000123" to "Scarborough, North Yorks",
        "235000124" to "Hartlepool, Co. Durham",
        "235002150" to "Cromer, Norfolk",
        "235002151" to "Lowestoft, Suffolk",
        "235002152" to "Aldeburgh, Suffolk",
        "235005880" to "Cleethorpes, Lincs",
        "235005881" to "Hessle (Humber Rescue)",
        "235008100" to "Hoylake, Merseyside",
        "235008101" to "Lytham St Annes, Lancs"
    )

    val stationName = stationNames[mmsi] ?: run {
        val nearestAp = Airports.byCode.values.minByOrNull {
            GeoMath.distanceMeters(lat, lon, it.lat, it.lon)
        }
        if (nearestAp != null && GeoMath.distanceMeters(lat, lon, nearestAp.lat, nearestAp.lon) < 120_000) {
            "${nearestAp.name} Sector"
        } else {
            "Coastal SAR Sector"
        }
    }

    val rangeText = if (known != null && known.defaultLat != 0.0 && known.defaultLon != 0.0) {
        val distM = GeoMath.distanceMeters(lat, lon, known.defaultLat, known.defaultLon)
        val distNm = distM / 1852.0
        if (distNm < 0.15) "On Station (0.0 nm)" else "%.1f nm (%.1f km)".format(distNm, distM / 1000.0)
    } else {
        "Active Coverage"
    }

    return stationName to rangeText
}
