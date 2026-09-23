package com.example.plane_tracker.data

/**
 * Emergency-squawk history: remembers every 7700/7600/7500 event seen this
 * session (including ones whose aircraft later stopped squawking) so they
 * stay reviewable after the live banner clears. Pure logic, unit-testable.
 */
class EmergencyHistoryTracker {

    data class Entry(
        val hex: String,
        val callsign: String,
        val squawk: String,
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
        /** True while the aircraft is still squawking the emergency. */
        val active: Boolean
    )

    private val entries = LinkedHashMap<String, Entry>()

    /**
     * Merges the latest live emergency list. Ongoing events update their
     * lastSeen + position; events no longer squawking are kept with
     * active = false instead of being forgotten.
     */
    @Synchronized
    fun update(live: List<EmergencyEvent>, now: Long = System.currentTimeMillis()) {
        val liveByHex = live.associateBy { it.hex }
        // Update / insert live events. firstSeenMs is stamped by the tracker
        // (not the event) so ordering is deterministic across identical events.
        liveByHex.forEach { (hex, e) ->
            val existing = entries[hex]
            entries[hex] = Entry(
                hex = hex,
                callsign = e.callsign,
                squawk = e.squawk,
                label = e.label,
                latitude = e.latitude,
                longitude = e.longitude,
                firstSeenMs = existing?.firstSeenMs ?: now,
                lastSeenMs = now,
                active = true
            )
        }
        // Mark vanished events as ended (keep them for review, record end time).
        entries.keys.forEach { hex ->
            if (hex !in liveByHex && entries[hex]!!.active) {
                entries[hex] = entries[hex]!!.copy(active = false, lastSeenMs = now)
            }
        }
        // Bound memory: keep the most recent 50 events.
        while (entries.size > 50) {
            entries.remove(entries.keys.first())
        }
    }

    /** Most recently active events first (ties broken by latest first-seen). */
    @Synchronized
    fun all(): List<Entry> = entries.values.sortedWith(
        compareByDescending<Entry> { it.lastSeenMs }.thenByDescending { it.firstSeenMs }
    )

    @Synchronized
    fun activeCount(): Int = entries.values.count { it.active }
}
