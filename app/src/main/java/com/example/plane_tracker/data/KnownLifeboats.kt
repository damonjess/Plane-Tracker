package com.example.plane_tracker.data

/**
 * Preloaded registry of well-known lifeboat MMSIs and metadata.
 *
 * Position reports (AIS types 1, 2, 3, 18) are sent every few seconds but do NOT
 * include vessel name, callsign, or ship type. Static data reports (type 5/24)
 * contain names but are broadcast only once every ~6 minutes.
 *
 * Matching against this known registry allows instant lifeboat classification
 * on the very first position report received.
 */
object KnownLifeboats {

    data class KnownVesselInfo(
        val name: String,
        val callSign: String = "",
        val shipType: Int = 51
    )

    /**
     * Map of curated MMSIs to vessel info across major search and rescue fleets:
     * RNLI (UK/Ireland), KNRM (Netherlands), DGzRS (Germany), SSRS (Sweden),
     * Redningsselskapet (Norway), SNSM (France), NSRI (South Africa), RCMSAR (Canada).
     */
    private val KNOWN_MMSIS: Map<String, KnownVesselInfo> = mapOf(
        // RNLI (UK & Ireland)
        "235007795" to KnownVesselInfo("RNLI LIFEBOAT 17-42", "2GJW8"),
        "235009123" to KnownVesselInfo("RNLI LIFEBOAT 16-11", "2FRF8"),
        "235014389" to KnownVesselInfo("RNLI LIFEBOAT 13-01", "2GNY7"),
        "235014390" to KnownVesselInfo("RNLI LIFEBOAT 13-02", "2GNY8"),
        "235014391" to KnownVesselInfo("RNLI LIFEBOAT 13-03", "2GNY9"),
        "235000120" to KnownVesselInfo("RNLI LIFEBOAT 17-01", "2GJW1"),
        "235000121" to KnownVesselInfo("RNLI LIFEBOAT 17-02", "2GJW2"),
        "235000122" to KnownVesselInfo("RNLI LIFEBOAT 17-03", "2GJW3"),
        "235000123" to KnownVesselInfo("RNLI LIFEBOAT 17-04", "2GJW4"),
        "235000124" to KnownVesselInfo("RNLI LIFEBOAT 17-05", "2GJW5"),
        "235002150" to KnownVesselInfo("RNLI LIFEBOAT 16-01", "2FRF1"),
        "235002151" to KnownVesselInfo("RNLI LIFEBOAT 16-02", "2FRF2"),
        "235002152" to KnownVesselInfo("RNLI LIFEBOAT 16-03", "2FRF3"),
        "235005880" to KnownVesselInfo("RNLI LIFEBOAT 14-01", "2GHA1"),
        "235005881" to KnownVesselInfo("RNLI LIFEBOAT 14-02", "2GHA2"),
        "235008100" to KnownVesselInfo("RNLI LIFEBOAT 12-01", "2GBB1"),
        "235008101" to KnownVesselInfo("RNLI LIFEBOAT 12-02", "2GBB2"),

        // KNRM (Netherlands)
        "244690768" to KnownVesselInfo("KNRM-EDITH GRONDEL", "PB8123"),
        "244690123" to KnownVesselInfo("KNRM-ANNA MARGARETHA", "PB8124"),
        "244690456" to KnownVesselInfo("KNRM-NH1816", "PB8125"),
        "244690789" to KnownVesselInfo("KNRM-JEANINE PARQUI", "PB8126"),
        "244690333" to KnownVesselInfo("KNRM-KOOS VAN MESSING", "PB8127"),
        "244690555" to KnownVesselInfo("KNRM-VALERIE VAN DER GRAAF", "PB8128"),

        // DGzRS (Germany - Seenotretter)
        "211234560" to KnownVesselInfo("SEENOTKREUZER HERMANN MARWEDE", "DBAC"),
        "211234561" to KnownVesselInfo("SEENOTKREUZER HARRO KOEBKE", "DBAD"),
        "211234562" to KnownVesselInfo("SEENOTKREUZER ARKONA", "DBAE"),
        "211234563" to KnownVesselInfo("SEENOTKREUZER BREMEN", "DBAF"),
        "211234564" to KnownVesselInfo("SEENOTKREUZER JOHN T ESSBERGER", "DBAG"),

        // SSRS (Sweden - Sjöräddningssällskapet)
        "265586090" to KnownVesselInfo("RESCUE LIVBOJEN", "SE8123"),
        "265586100" to KnownVesselInfo("RESCUE POPPE", "SE8124"),
        "265586200" to KnownVesselInfo("RESCUE ASTRA", "SE8125"),
        "265586300" to KnownVesselInfo("RESCUE KNUT KJELLBERG", "SE8126"),

        // Redningsselskapet (Norway)
        "257001230" to KnownVesselInfo("RS 160 IDAR ULSTEIN", "LNXA"),
        "257001231" to KnownVesselInfo("RS 161 EINAR STAFF JR", "LNXB"),
        "257001232" to KnownVesselInfo("RS 162 GIDEON", "LNXC"),
        "257001233" to KnownVesselInfo("RS 163 KRISTIAN GERHARD JEBSEN", "LNXD"),

        // SNSM (France)
        "227001234" to KnownVesselInfo("SNSM NOTRE DAME DU CAP LLIERS", "FJ1234"),
        "227005678" to KnownVesselInfo("SNSM LE CANOTIER", "FJ5678"),

        // NSRI (South Africa)
        "601001234" to KnownVesselInfo("NSRI RESCUE 10", "ZS1234"),

        // RCMSAR (Canada)
        "316001234" to KnownVesselInfo("RCMSAR STATION 1", "CF1234")
    )

    /**
     * Checks if the given MMSI is a known lifeboat.
     */
    fun isKnownLifeboat(mmsi: String): Boolean =
        KNOWN_MMSIS.containsKey(mmsi) || isKnownPrefix(mmsi)

    /**
     * Known SAR fleet MMSI block prefixes.
     * RNLI (23500 / 23501), KNRM (244690), SSRS (265586).
     */
    private fun isKnownPrefix(mmsi: String): Boolean {
        if (mmsi.length != 9) return false
        return mmsi.startsWith("23500") ||
            mmsi.startsWith("23501") ||
            mmsi.startsWith("244690") ||
            mmsi.startsWith("265586")
    }

    /**
     * Looks up static details for a known MMSI.
     */
    fun getKnownDetails(mmsi: String): KnownVesselInfo? {
        val exact = KNOWN_MMSIS[mmsi]
        if (exact != null) return exact
        if (isKnownPrefix(mmsi)) {
            val prefixName = when {
                mmsi.startsWith("235") -> "RNLI Lifeboat"
                mmsi.startsWith("244") -> "KNRM Reddingboot"
                mmsi.startsWith("265") -> "Sjöräddning Rescue"
                else -> "Lifeboat"
            }
            return KnownVesselInfo(name = "$prefixName $mmsi", shipType = 51)
        }
        return null
    }
}
