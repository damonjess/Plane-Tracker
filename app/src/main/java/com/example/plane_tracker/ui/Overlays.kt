package com.example.plane_tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.plane_tracker.data.SelectedFlight
import com.example.plane_tracker.util.RouteProgress
import com.example.plane_tracker.util.calculateClockETA
import com.example.plane_tracker.viewmodel.FilterState
import kotlinx.coroutines.delay
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
fun StatusChip(count: Int, source: String, lastUpdateMs: Long, modifier: Modifier = Modifier) {
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
                text = "✈ $count  ·  ${source.lowercase()}  ·  ${elapsed}s ago",
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search callsign, hex or airport", color = TextSecondary, fontSize = 14.sp) },
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

        val hasResults = aircraftResults.isNotEmpty() || airportResults.isNotEmpty()
        AnimatedVisibility(visible = hasResults, enter = fadeIn(), exit = fadeOut()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PanelBg)
            ) {
                LazyColumn(modifier = Modifier.height((56 * (aircraftResults.size + airportResults.size).coerceAtMost(6)).dp)) {
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

// ---------- Map control buttons ----------

@Composable
fun MapControls(
    airportsOn: Boolean,
    labelsOn: Boolean,
    is3D: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCenter: () -> Unit,
    onToggle3D: () -> Unit,
    onToggleAirports: () -> Unit,
    onToggleLabels: () -> Unit,
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
        ControlButton("AP", "Airports", onToggleAirports, active = airportsOn)
        ControlButton("AB", "Callsigns", onToggleLabels, active = labelsOn)
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
    origin: com.example.plane_tracker.data.Airport?,
    destination: com.example.plane_tracker.data.Airport?
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
    origin: com.example.plane_tracker.data.Airport?,
    destination: com.example.plane_tracker.data.Airport?,
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
            val totalKm = com.example.plane_tracker.util.GeoMath.distanceMeters(
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
                valueRange = 0f..50_000f,
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
