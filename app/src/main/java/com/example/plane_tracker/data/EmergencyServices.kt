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

    /** Call-sign, registration, operator and owner patterns in priority order. */
    private val patterns: List<Pair<Regex, OpsCategory>> = listOf(
        Regex(
            "royal national lifeboat|rnli|rnli\\d*|life ?boat|reddingboot|reddingsboot|knrm|dgzrs|seenotrett.*|seenotkreuzer|seenotretter|" +
                "snsm|redningsselskapet|redningsskøyte|sjöräddningssällskapet|\\bssrs\\b|\\bnsri\\b|rcmsar|marine rescue|volunteer marine rescue|sea rescue|\\bpolmarine\\b|\\bpolmar\\b|water police",
            IGNORE_CASE
        ) to OpsCategory.LIFEBOAT,
        Regex(
            "coastguard|coast guard|h\\.?m\\.? coastguard|his majesty.?s coastguard|her majesty.?s coastguard|hmcg|" +
                "kustwacht|salvamento|sasemar|irish coast guard|us coast guard|\\buscg\\b|" +
                "search and rescue|\\bsar\\b|rescue helicopter|bristow|2excel|" +
                "\\bRESCUE\\b|rescue\\s*\\d+|\\bSAR\\b|\\bCGC\\b|\\bCGI\\d+\\b|\\bSRG\\b|G-RESA|C-GFMX|C-GFMQ|C-GCFK|G-MCG[A-Z]",
            IGNORE_CASE
        ) to OpsCategory.COASTGUARD,
        Regex(
            "air ?ambulance|airambulance|medevac|med-evac|air-ambulance|medivac|" +
                "helimed|\\bhle\\d*|\\bhmd\\d*|\\bheli\\d*|\\bhmed\\d*|scaa|gama|g-sasc|" +
                "christoph|christophorus|\\bchx\\d*|adac|drf|luftrettung|rettungsflug|notarzt|gallus|rega|" +
                "\\bdoc\\d*|\\bdoc\\b|norsk luftambulanse|\\bnla\\b|swensk|svensk luftambulans|\\bsla\\b|avincis|lufttransport|" +
                "\\breach\\d*|med-trans|medtrans|air evac|airevac|survival flight|life flight|lifeflight|" +
                "stat medevac|statmedevac|mercy air|careflite|skylife|airlife|flight for life|boston medflight|mayo one|lifeguard|life star|" +
                "midlands air|thames valley|great western|yorkshire air|north west air|east anglian|kent surrey|devon air|cornwall air|dorset and somerset|hampshire and isle|wiltshire air|lincs & notts|wales air|northern air|" +
                "G-NWAA|G-SCAA|G-HEMN|G-SASC|G-TAAA|G-RFAA|G-KSAB|G-MAAA|G-LNAB|G-NWAE|G-NWAC|G-NWAY|G-YAAI|G-YAAC|G-EHAA|G-TVAA|G-GWAA|G-DAAA|G-CNAB|G-DSAA|G-HIOW|G-WAAO|G-EAAA|G-KSSC|G-MDBA|G-PICU|G-SAAI|" +
                "LN-OOO|LN-OTK|LN-OUL|LN-OEA|SE-JSK|SE-JSS|SE-JXA|" +
                "D-HZSQ|D-HZSJ|D-HJLC|D-HXFS|D-HNHA|D-BADA|D-HFJA|OE-XVS|OE-XHY|HB-TIG",
            IGNORE_CASE
        ) to OpsCategory.AIR_AMBULANCE,
        Regex(
            "national police|npas|\\bpolice\\b|constabulary|police aviation|police department|metropolitan police|met police|air support unit|air support division|\\bpolice ?\\d*|" +
                "\\bukp ?\\d*|\\bukp\\b|\\bmps\\b|\\bgarda\\d*|\\bgardai\\b|\\bgasu\\b|politie|luchtvaartpolitie|pirol|polizei|bundespolizei|\\bbpol\\b|polizeihubschrauber|" +
                "\\bedelweiss ?\\d{1,2}\\b|libelle|hummel|passat|sperber|phönix|phoenix|flugsad|ikarus|habicht|bussard|pelikan|flugpolizei|polis|polismyndigheten|politi|politiet|poliisi|rigspolitiet|" +
                "guardia civil|gendarmerie|police nationale|polizia|polizia di stato|carabinieri|guardia di finanza|\\bgdf\\b|policia|polícia militar|polícia civil|polícia federal|policja|" +
                "trooper|state police|state patrol|highway patrol|sheriff'?s?|lapd|nypd|cpd|\\bdps\\b|\\bchp\\b|\\blasd\\b|\\bbso\\b|\\bpbso\\b|\\bpolair\\d*|rcmp|gendarmerie royale|ontario provincial police|\\bopp\\b|surete du quebec|\\bsq\\b|saps|" +
                "G-?MPS[A-Z]|G-?POL[A-Z]|G-?NPA[A-Z]|G-?NWO[A-Z]|G-?SUA[A-Z]|G-?GMP[A-Z]|G-?SYP[A-Z]|G-?WMP[A-Z]|G-?TVP[A-Z]|G-?DVP[A-Z]|G-?HMP[A-Z]|G-?COP[A-Z]|G-?PSN[A-Z]|G-?VPNI|G-?RPA[A-Z]|G-?DPAS|G-?AASU|G-?PASU|G-?SPOL|" +
                "PH-PX[A-Z]|D-HX[A-Z]{2}|D-HV[A-Z]{2}|D-HBP[A-Z]|D-HEPS|D-HPOL|D-HUTH|DHYAC|OE-BX[A-Z]|SE-JP[A-Z]|SE-HP[A-Z]|F-MJ[A-Z]{2}|LN-ORW|LN-ORX|LN-RWP|" +
                "\\blaw enforcement\\b",
            IGNORE_CASE
        ) to OpsCategory.POLICE,
        Regex(
            "fire ?service|fire ?and ?rescue|fire ?support|fire ?dept|fire ?department|fire ?brigade|fire ?fighting|firefighting|aerial firefighting|" +
                "firehawk|helitack|water ?bomber|air ?tanker|tanker ?\\d+|lead ?plane|smokejumper|fire ?attack|cal ?fire|calfire|usfs|us forest service|" +
                "rfs|nsw rfs|cfa|qfes|dfes|bc wildfire|alberta wildfire|vigili del fuoco|securite civile|sécurité civile|bomberos|bombeiros|infoca|" +
                "fire boss|coulson|bridger aerospace|10 tanker|erickson|conair|neptune aviation|aero ?flite|cl-?215|cl-?415|canadair",
            IGNORE_CASE
        ) to OpsCategory.FIRE,
        Regex(
            "royal air force|\\braf\\b|royal navy|\\brn\\b|army air corps|british army|royal marines|" +
                "us air force|\\busaf\\b|us navy|us army|us marines|" +
                "luftwaffe|armee de l|aeronautica militare|fuerza aerea|poland.*air force|siły powietrzne|air force|military|armed forces",
            IGNORE_CASE
        ) to OpsCategory.MILITARY,
        Regex("search and rescue|\\bsar\\b|rescue helicopter", IGNORE_CASE) to OpsCategory.COASTGUARD
    )

    /**
     * Call-sign codes broadcast **only** by military operators (RRR640 = RAF,
     * NVY806 = Royal Navy, CFC2908 = Canadian Forces ...). Many military
     * airframes have no owner record in adsbdb, so these classify immediately
     * instead of waiting for a lookup that will never resolve.
     */
    private val militaryCallsignCodes = setOf(
        "RRR", "ASCOT", "NVY", "CFC", "RCH", "GAF", "IAM", "FAF", "HAF",
        "TUAF", "BAF", "NAF", "PLF", "ROF", "SVF", "AME", "DAF", "HUF", "CNV"
    )

    /** UK military tail blocks (ZM712, ZJ130, ZZ338) and Italian MM serials. */
    private val militaryTail = Regex("Z[A-Z]\\d{3}|X[WXSZT]\\d{3}|MM\\d{4,6}", IGNORE_CASE)

    /** True when [callsign] is a military call-sign code, with or without its number. */
    private fun isMilitaryCallsign(callsign: String): Boolean {
        val cs = callsign.trim().uppercase()
        if (cs.isEmpty()) return false
        return militaryCallsignCodes.any { code ->
            cs == code ||
                (cs.length > code.length && cs.startsWith(code) &&
                    cs.substring(code.length).all { it.isDigit() })
        }
    }

    /** True when [registration] is a whole military tail (never a civil G-, N-, D- reg). */
    private fun isMilitaryTail(registration: String): Boolean {
        val reg = registration.trim()
        return reg.isNotEmpty() && militaryTail.matches(reg)
    }

    /**
     * Classifies an aircraft checking callsign, registration, owner name and dbFlags.
     *
     * @param knownMilitary true when the hex is known military from an external
     *   source (e.g. adsb.lol's curated /v2/mil feed), which keeps military
     *   detection working when the fleet record itself carries no dbFlags.
     */
    fun classify(
        aircraft: Aircraft,
        owner: String?,
        knownMilitary: Boolean = false
    ): OpsCategory? {
        val callsign = aircraft.callsign.trim()
        val reg = aircraft.registration?.trim().orEmpty()
        val ownerClean = owner?.trim().orEmpty()
        val combinedText = "$callsign $reg $ownerClean".trim()

        if (combinedText.isNotEmpty()) {
            for ((regex, category) in patterns) {
                if (regex.containsMatchIn(combinedText)) {
                    return category
                }
            }
        }
        if (knownMilitary || aircraft.isMilitary ||
            isMilitaryCallsign(callsign) || isMilitaryTail(reg)
        ) {
            return OpsCategory.MILITARY
        }
        return null
    }
}
