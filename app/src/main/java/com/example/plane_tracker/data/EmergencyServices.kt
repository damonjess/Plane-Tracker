package com.example.plane_tracker.data

import kotlin.text.RegexOption.IGNORE_CASE

/**
 * Classifies aircraft into blue-light / special-operation categories from
 * adsbdb registered-owner names. Pure functions so they're unit-testable.
 */
enum class OpsCategory(val label: String, val emoji: String, val ringColor: String) {
    COASTGUARD("Coastguard", "🚁", "#ff9800"),
    AIR_AMBULANCE("Air ambulance", "🚁", "#ff1744"),
    POLICE("Police", "🚁", "#2196f3"),
    MILITARY("Military", "🪖", "#9c27b0"),
    FIRE("Fire support", "🚁", "#e65100"),
    LIFEBOAT("Lifeboat", "🛥", "#00bcd4")
}

object OpsClassifier {

    /** Owner-name patterns, checked in priority order (most specific first). */
    private val patterns: List<Pair<Regex, OpsCategory>> = listOf(
        Regex("coastguard|coast guard|h\\.?m\\.? coastguard|his majesty.?s coastguard|her majesty.?s coastguard|hmcg|sar helicopter", IGNORE_CASE) to OpsCategory.COASTGUARD,
        Regex("royal national lifeboat|rnli", IGNORE_CASE) to OpsCategory.LIFEBOAT,
        Regex("air ?ambulance|airambulance", IGNORE_CASE) to OpsCategory.AIR_AMBULANCE,
        Regex("national police air service|npas|\\bpolice\\b|constabulary|police aviation", IGNORE_CASE) to OpsCategory.POLICE,
        Regex("fire ?service|fire ?and ?rescue|fire ?support", IGNORE_CASE) to OpsCategory.FIRE,
        Regex(
            "royal air force|\\braf\\b|royal navy|\\brn\\b|army air corps|british army|royal marines|" +
                "us air force|\\busaf\\b|us navy|us army|us marines|" +
                "luftwaffe|armee de l|aeronautica militare|fuerza aerea|poland.*air force|siły powietrzne",
            IGNORE_CASE
        ) to OpsCategory.MILITARY,
        Regex("search and rescue|\\bsar\\b|rescue helicopter", IGNORE_CASE) to OpsCategory.COASTGUARD
    )

    /**
     * Classifies an aircraft. Owner match wins over dbFlags (so a US Coast
     * Guard C-130 shows as coastguard, not generic military).
     */
    fun classify(aircraft: Aircraft, owner: String?): OpsCategory? {
        val ownerClean = owner?.trim().orEmpty()
        if (ownerClean.isNotEmpty()) {
            for ((regex, category) in patterns) {
                if (regex.containsMatchIn(ownerClean)) return category
            }
        }
        if (aircraft.isMilitary) return OpsCategory.MILITARY
        return null
    }
}
