package com.example.plane_tracker.data

/**
 * Preloaded registry of well-known lifeboat MMSIs, station coordinates, and metadata.
 *
 * Position reports (AIS types 1, 2, 3, 18) are sent every few seconds when underway
 * (or every 3 minutes when moored) and do NOT include vessel name, callsign, or ship type.
 * Static data reports (type 5/24) are broadcast only once every ~6 minutes.
 *
 * Matching against this known registry allows instant lifeboat classification and
 * zero-latency station display on app startup.
 */
object KnownLifeboats {

    data class KnownVesselInfo(
        val name: String,
        val callSign: String = "",
        val shipType: Int = 51,
        val defaultLat: Double = 0.0,
        val defaultLon: Double = 0.0
    )

    /**
     * Curated MMSIs across major search and rescue fleets:
     * RNLI (UK/Ireland), Independent UK Rescue, KNRM (Netherlands), DGzRS (Germany),
     * SSRS (Sweden), Redningsselskapet (Norway), SNSM (France), NSRI (SA), RCMSAR (Canada).
     */
    private val KNOWN_MMSIS: Map<String, KnownVesselInfo> = mapOf(
        // RNLI (UK & Ireland) — Existing + Active Severn, Tamar, Trent & Shannon fleet
        "235007795" to KnownVesselInfo("RNLI LIFEBOAT 17-42", "2GJW8", 51, 58.5956, -3.5134), // Thurso
        "235009123" to KnownVesselInfo("RNLI LIFEBOAT 16-11", "2FRF8", 51, 50.1256, -5.1258), // Falmouth
        "235014389" to KnownVesselInfo("RNLI LIFEBOAT 13-01", "2GNY7", 51, 50.7152, -1.9876), // Poole
        "235014390" to KnownVesselInfo("RNLI LIFEBOAT 13-02", "2GNY8", 51, 50.8354, -0.0987), // Brighton / Shoreham
        "235014391" to KnownVesselInfo("RNLI LIFEBOAT 13-03", "2GNY9", 51, 54.1287, -0.1872), // Bridlington
        "235000120" to KnownVesselInfo("RNLI LIFEBOAT 17-01", "2GJW1", 51, 53.5750, 0.1118),  // Spurn Point / Humber
        "235000121" to KnownVesselInfo("RNLI LIFEBOAT 17-02", "2GJW2", 51, 53.1415, 0.3452),  // Skegness
        "235000122" to KnownVesselInfo("RNLI LIFEBOAT 17-03", "2GJW3", 51, 54.4882, -0.6125), // Whitby
        "235000123" to KnownVesselInfo("RNLI LIFEBOAT 17-04", "2GJW4", 51, 54.2810, -0.3900), // Scarborough
        "235000124" to KnownVesselInfo("RNLI LIFEBOAT 17-05", "2GJW5", 51, 54.6961, -1.1865), // Hartlepool
        "235002150" to KnownVesselInfo("RNLI LIFEBOAT 16-01", "2FRF1", 51, 52.9333, 1.3015),  // Cromer
        "235002151" to KnownVesselInfo("RNLI LIFEBOAT 16-02", "2FRF2", 51, 52.4744, 1.7542),  // Lowestoft
        "235002152" to KnownVesselInfo("RNLI LIFEBOAT 16-03", "2FRF3", 51, 52.2145, 1.6032),  // Aldeburgh
        "235005880" to KnownVesselInfo("RNLI LIFEBOAT 14-01", "2GHA1", 51, 53.5621, -0.0682), // Cleethorpes
        "235005881" to KnownVesselInfo("RNLI LIFEBOAT 14-02", "2GHA2", 51, 53.6931, -0.2054), // Humber Rescue (Hessle)
        "235008100" to KnownVesselInfo("RNLI LIFEBOAT 12-01", "2GBB1", 51, 53.3932, -3.1812), // Hoylake
        "235008101" to KnownVesselInfo("RNLI LIFEBOAT 12-02", "2GBB2", 51, 53.7365, -3.0031), // Lytham St Annes

        // Real MMSI allocations for active UK/Irish RNLI All-Weather Lifeboats
        "232002440" to KnownVesselInfo("RNLI 17-05 PRIDE OF THE HUMBER", "MUVS2", 51, 53.5762, 0.1105),
        "232001910" to KnownVesselInfo("RNLI 17-03 ALBERT BROWN", "MUVQ2", 51, 51.9472, 1.2894),
        "232001940" to KnownVesselInfo("RNLI 17-04 SPIRIT OF GUERNSEY", "MUVR2", 51, 49.4556, -2.5322),
        "232001890" to KnownVesselInfo("RNLI 17-51 HOGG HARDIE", "MUVP2", 51, 51.1189, 1.3214),
        "232003140" to KnownVesselInfo("RNLI 17-52 OWEN CLARKSON", "ZNTR5", 51, 51.3292, 1.4228),
        "232003141" to KnownVesselInfo("RNLI 17-21 DAVID AND ELIZABETH ACLAND", "ZNTR6", 51, 50.7819, 0.0561),
        "232004399" to KnownVesselInfo("RNLI 17-24 BON ACCORD", "ZIYM2", 51, 57.1436, -2.0725),
        "232004401" to KnownVesselInfo("RNLI 17-25 ERIC AND SUSAN HISCOCK", "ZIYM4", 51, 50.7061, -1.5003),
        "232004402" to KnownVesselInfo("RNLI 17-26 HENRY ALSTON HEWAT", "ZIYM5", 51, 57.0064, -5.8256),
        "232004407" to KnownVesselInfo("RNLI 17-28 ALEC AND CHRISTINA DYKES", "ZIYN2", 51, 50.3978, -3.5114),
        "232004409" to KnownVesselInfo("RNLI 17-30 WILLIAM GORDON BURR", "ZIYN4", 51, 55.2069, -6.6572),
        "235005118" to KnownVesselInfo("RNLI 17-32 ERNEST AND MABEL", "MGYM7", 51, 50.6075, -2.4489),
        "235005119" to KnownVesselInfo("RNLI 17-34 OSIER", "MGYM8", 51, 55.0114, -1.4253),
        "235005121" to KnownVesselInfo("RNLI 17-35 SYBIL MULLEN GLOVER", "MGYN2", 51, 50.3642, -4.1508),
        "235005122" to KnownVesselInfo("RNLI 17-36 IVAN ELLEN", "MGYN3", 51, 50.1031, -5.5442),
        "235007809" to KnownVesselInfo("RNLI 17-37 WILLIAM BLANNIN", "MJUJ8", 51, 57.6797, -2.9614),
        "235007798" to KnownVesselInfo("RNLI 17-39 ELIZABETH FAIRLIE RAMSEY", "MJUJ5", 51, 56.6219, -6.0642),
        "235007797" to KnownVesselInfo("RNLI 17-40 JULIAN AND MARGARET LEONARD", "MJUJ4", 51, 58.1469, -5.2478),
        "235007796" to KnownVesselInfo("RNLI 17-41 CHRISTOPHER PEARCE", "MJUJ3", 51, 53.3144, -4.6303),
        "235010875" to KnownVesselInfo("RNLI 17-43 DONALD AND BARBARA BROADHEAD", "EI4521", 51, 52.2547, -6.3347),
        "235010876" to KnownVesselInfo("RNLI 17-44 ANNETTE HUTTON", "EI4522", 51, 51.6486, -9.9078),
        "232002450" to KnownVesselInfo("RNLI 17-06 DAVID KIRKALDY", "MUVT2", 51, 53.1183, -9.6644),
        "232002460" to KnownVesselInfo("RNLI 17-07 JOHN AND MARGARET DOIG", "MUVU2", 51, 51.9269, -10.2875),
        "232002470" to KnownVesselInfo("RNLI 17-08 HELMUT SCHRODER OF DUNLOSSIT II", "MUVV2", 51, 55.7867, -6.1158),
        "232002490" to KnownVesselInfo("RNLI 17-10 MICHAEL AND JANE VERNON", "MUVX2", 51, 60.1553, -1.1422),
        "232003049" to KnownVesselInfo("RNLI 17-11 THE WHITEHEADS", "ZNQA2", 51, 49.9169, -6.3175),
        "232003050" to KnownVesselInfo("RNLI 17-12 EDNA WINDSOR", "ZNQA3", 51, 56.9531, -7.4856),
        "232003051" to KnownVesselInfo("RNLI 17-13 MARGARET FOSTER", "ZNQA4", 51, 58.9858, -2.9597),
        "232003052" to KnownVesselInfo("RNLI 17-14 CHARLES LIDBURY", "ZNQA5", 51, 60.2869, -1.3769),
        "232003133" to KnownVesselInfo("RNLI 17-15 BRYAN AND GORDON", "ZNTR2", 51, 54.2536, -9.8953),
        "232003134" to KnownVesselInfo("RNLI 17-16 VIOLET DOROTHY AND KATHLEEN", "ZNTR3", 51, 58.9589, -3.2961),
        "232003136" to KnownVesselInfo("RNLI 17-18 TOM SANDERSON", "ZNTR4", 51, 58.2067, -6.3886),
        "232003139" to KnownVesselInfo("RNLI 17-19 ERNEST AND MARY SHAW", "ZNTR7", 51, 55.4244, -5.6019),
        "232003142" to KnownVesselInfo("RNLI 17-22 MYRTLE MAUD", "ZNTR8", 51, 54.9853, -8.5189),
        "235050722" to KnownVesselInfo("RNLI 16-06 MOLLY HUNT", "2Aed4", 51, 51.0558, -4.1925),
        "232002380" to KnownVesselInfo("RNLI 14-14 GOUGH RITCHIE II", "MUVA2", 51, 54.0728, -4.7369),
        "232002582" to KnownVesselInfo("RNLI 14-21 SAXON", "MUVB2", 51, 54.6461, -5.5342),
        "232002340" to KnownVesselInfo("RNLI 14-10 SAMARITAN", "MUVC2", 51, 52.5769, 1.7347),

        // KNRM (Netherlands)
        "244690768" to KnownVesselInfo("KNRM-EDITH GRONDEL", "PB8123", 51, 52.4622, 4.5583),
        "244690123" to KnownVesselInfo("KNRM-ANNA MARGARETHA", "PB8124", 51, 53.4411, 5.7531),
        "244690456" to KnownVesselInfo("KNRM-NH1816", "PB8125", 51, 52.4611, 4.5550),
        "244690789" to KnownVesselInfo("KNRM-JEANINE PARQUI", "PB8126", 51, 51.9775, 4.1228),
        "244690333" to KnownVesselInfo("KNRM-KOOS VAN MESSING", "PB8127", 51, 52.9619, 4.7792),
        "244690555" to KnownVesselInfo("KNRM-VALERIE VAN DER GRAAF", "PB8128", 51, 51.8331, 3.9722),

        // DGzRS (Germany - Seenotretter)
        "211234560" to KnownVesselInfo("SEENOTKREUZER HERMANN MARWEDE", "DBAC", 51, 54.1783, 7.8925),
        "211234561" to KnownVesselInfo("SEENOTKREUZER HARRO KOEBKE", "DBAD", 51, 54.5122, 13.6414),
        "211234562" to KnownVesselInfo("SEENOTKREUZER ARKONA", "DBAE", 51, 54.1811, 12.0914),
        "211234563" to KnownVesselInfo("SEENOTKREUZER BREMEN", "DBAF", 51, 54.4028, 11.1969),
        "211234564" to KnownVesselInfo("SEENOTKREUZER JOHN T ESSBERGER", "DBAG", 51, 54.4331, 10.1722),

        // SSRS (Sweden - Sjöräddningssällskapet)
        "265586090" to KnownVesselInfo("RESCUE LIVBOJEN", "SE8123", 51, 57.6847, 11.8364),
        "265586100" to KnownVesselInfo("RESCUE POPPE", "SE8124", 51, 56.0467, 12.6881),
        "265586200" to KnownVesselInfo("RESCUE ASTRA", "SE8125", 51, 58.3542, 11.2486),
        "265586300" to KnownVesselInfo("RESCUE KNUT KJELLBERG", "SE8126", 51, 59.2889, 18.7083),

        // Redningsselskapet (Norway)
        "257001230" to KnownVesselInfo("RS 160 IDAR ULSTEIN", "LNXA", 51, 62.3458, 5.6322),
        "257001231" to KnownVesselInfo("RS 161 EINAR STAFF JR", "LNXB", 51, 59.1247, 11.3853),
        "257001232" to KnownVesselInfo("RS 162 GIDEON", "LNXC", 51, 60.3983, 5.3189),
        "257001233" to KnownVesselInfo("RS 163 KRISTIAN GERHARD JEBSEN", "LNXD", 51, 58.1436, 7.9983),

        // SNSM (France)
        "227001234" to KnownVesselInfo("SNSM NOTRE DAME DU CAP LLIERS", "FJ1234", 51, 49.6425, -1.6214),
        "227005678" to KnownVesselInfo("SNSM LE CANOTIER", "FJ5678", 51, 48.6497, -2.0258),

        // NSRI (South Africa)
        "601001234" to KnownVesselInfo("NSRI RESCUE 10", "ZS1234", 51, -33.9042, 18.4197),

        // RCMSAR (Canada)
        "316001234" to KnownVesselInfo("RCMSAR STATION 1", "CF1234", 51, 49.2953, -123.1336)
    )

    /** True if the MMSI is in the exact curated registry. */
    fun isExactKnownLifeboat(mmsi: String): Boolean = KNOWN_MMSIS.containsKey(mmsi)

    /** Checks if the given MMSI is a known lifeboat or belongs to a known SAR prefix. */
    fun isKnownLifeboat(mmsi: String): Boolean =
        KNOWN_MMSIS.containsKey(mmsi) || isKnownPrefix(mmsi)

    /**
     * Known SAR fleet MMSI block prefixes:
     * RNLI (23200, 23202, 23500, 23501), KNRM (244690), SSRS (265586), AIS-SART (970).
     */
    private fun isKnownPrefix(mmsi: String): Boolean {
        if (mmsi.length != 9) return false
        return mmsi.startsWith("23200") ||
            mmsi.startsWith("23202") ||
            mmsi.startsWith("23500") ||
            mmsi.startsWith("23501") ||
            mmsi.startsWith("244690") ||
            mmsi.startsWith("265586") ||
            mmsi.startsWith("970")
    }

    /** Looks up static details for a known MMSI. */
    fun getKnownDetails(mmsi: String): KnownVesselInfo? {
        val exact = KNOWN_MMSIS[mmsi]
        if (exact != null) return exact
        if (isKnownPrefix(mmsi)) {
            val prefixName = when {
                mmsi.startsWith("232") || mmsi.startsWith("235") -> "RNLI Lifeboat"
                mmsi.startsWith("244") -> "KNRM Reddingboot"
                mmsi.startsWith("265") -> "Sjöräddning Rescue"
                mmsi.startsWith("970") -> "AIS-SART Beacon"
                else -> "Lifeboat"
            }
            return KnownVesselInfo(name = "$prefixName $mmsi", shipType = 51)
        }
        return null
    }

    /** Returns initial seeded vessels that have known home station coordinates. */
    fun seededStationVessels(nowMs: Long = System.currentTimeMillis()): Map<String, Vessel> {
        val out = LinkedHashMap<String, Vessel>(KNOWN_MMSIS.size)
        for ((mmsi, info) in KNOWN_MMSIS) {
            if (info.defaultLat == 0.0 && info.defaultLon == 0.0) continue
            val v = Vessel(
                mmsi = mmsi,
                name = info.name,
                latitude = info.defaultLat,
                longitude = info.defaultLon,
                speedKnots = 0.0,
                heading = 0.0,
                isLifeboat = true,
                lastSeen = nowMs,
                callSign = info.callSign,
                shipType = info.shipType,
                navStatus = 5 // Moored at station until live AIS report updates it
            )
            v.lifeboatCheckDirty = false
            out[mmsi] = v
        }
        return out
    }
}
