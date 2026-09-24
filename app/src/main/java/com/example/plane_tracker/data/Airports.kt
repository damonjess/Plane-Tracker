package com.example.plane_tracker.data

import com.example.plane_tracker.util.GeoMath

/** Embedded airport reference for route rendering (major hubs + UK/regional coverage). */
object Airports {
    data class Entry(
        val name: String,
        val iata: String,
        val icao: String,
        val lat: Double,
        val lon: Double
    )

    val byCode: Map<String, Entry> = listOf(
        // London & UK
        Entry("London Heathrow", "LHR", "EGLL", 51.4700, -0.4543),
        Entry("London Gatwick", "LGW", "EGKK", 51.1537, -0.1821),
        Entry("London Stansted", "STN", "EGSS", 51.8850, 0.2350),
        Entry("London Luton", "LTN", "EGGW", 51.8747, -0.3683),
        Entry("London City", "LCY", "EGLC", 51.5053, 0.0553),
        Entry("Manchester", "MAN", "EGCC", 53.3537, -2.2750),
        Entry("Birmingham", "BHX", "EGBB", 52.4539, -1.7480),
        Entry("Edinburgh", "EDI", "EGPH", 55.9500, -3.3725),
        Entry("Glasgow", "GLA", "EGPF", 55.8719, -4.4331),
        Entry("Bristol", "BRS", "EGGD", 51.3827, -2.7191),
        Entry("Newcastle", "NCL", "EGNT", 55.0375, -1.6917),
        Entry("Leeds Bradford", "LBA", "EGNM", 53.8659, -1.6606),
        Entry("Liverpool", "LPL", "EGGP", 53.3336, -2.8497),
        Entry("Belfast International", "BFS", "EGAA", 54.6575, -6.2158),
        Entry("Aberdeen", "ABZ", "EGPD", 57.2019, -2.1978),
        Entry("Doncaster Sheffield", "DSA", "EGCN", 53.4744, -1.0103),
        Entry("Humberside", "HUY", "EGNJ", 53.5789, -0.3497),
        Entry("East Midlands", "EMA", "EGNX", 52.8311, -1.3281),
        Entry("Norwich", "NWI", "EGSH", 52.6757, 1.2828),
        Entry("Southampton", "SOU", "EGHI", 50.9503, -1.3568),
        Entry("Exeter", "EXT", "EGTE", 50.7343, -3.4139),
        Entry("Cardiff", "CWL", "EGFF", 51.3967, -3.3433),
        Entry("Inverness", "INV", "EGPE", 57.4806, -4.0450),
        Entry("Jersey", "JER", "EGJJ", 49.2075, -2.1964),
        Entry("Guernsey", "GCI", "EGJB", 49.4350, -2.6036),
        Entry("Isle of Man", "IOM", "EGNS", 54.0831, -4.6253),
        // Ireland
        Entry("Dublin", "DUB", "EIDW", 53.4213, -6.2701),
        Entry("Shannon", "SNN", "EINN", 52.7020, -8.9250),
        Entry("Cork", "ORK", "EICK", 51.8413, -8.4911),
        // Major European hubs
        Entry("Amsterdam Schiphol", "AMS", "EHAM", 52.3105, 4.7683),
        Entry("Paris Charles de Gaulle", "CDG", "LFPG", 49.0097, 2.5479),
        Entry("Paris Orly", "ORY", "LFPO", 48.7233, 2.3794),
        Entry("Frankfurt", "FRA", "EDDF", 50.0379, 8.5622),
        Entry("Munich", "MUC", "EDDM", 48.3538, 11.7861),
        Entry("Zurich", "ZRH", "LSZH", 47.4647, 8.5492),
        Entry("Geneva", "GVA", "LSGG", 46.2381, 6.1090),
        Entry("Vienna", "VIE", "LOWW", 48.1103, 16.5697),
        Entry("Brussels", "BRU", "EBBR", 50.9014, 4.4844),
        Entry("Copenhagen Kastrup", "CPH", "EKCH", 55.6180, 12.6560),
        Entry("Oslo Gardermoen", "OSL", "ENGM", 60.1976, 11.1004),
        Entry("Stockholm Arlanda", "ARN", "ESSA", 59.6519, 17.9186),
        Entry("Helsinki Vantaa", "HEL", "EFHK", 60.3172, 24.9633),
        Entry("Madrid Barajas", "MAD", "LEMD", 40.4719, -3.5626),
        Entry("Barcelona El Prat", "BCN", "LEBL", 41.2974, 2.0833),
        Entry("Lisbon", "LIS", "LPPT", 38.7756, -9.1354),
        Entry("Rome Fiumicino", "FCO", "LIRF", 41.8003, 12.2389),
        Entry("Milan Malpensa", "MXP", "LIMC", 45.6306, 8.7281),
        Entry("Athens", "ATH", "LGAV", 37.9364, 23.9445),
        Entry("Istanbul Airport", "IST", "LTFM", 41.2753, 28.7519),
        Entry("Warsaw Chopin", "WAW", "EPWA", 52.1657, 20.9671),
        Entry("Prague Vaclav Havel", "PRG", "LKPR", 50.1008, 14.2600),
        Entry("Budapest Ferenc Liszt", "BUD", "LHBP", 47.4298, 19.2611),
        Entry("Reykjavik Keflavik", "KEF", "BIKF", 63.9850, -22.6056),
        // Long haul
        Entry("New York JFK", "JFK", "KJFK", 40.6413, -73.7781),
        Entry("New York Newark", "EWR", "KEWR", 40.6895, -74.1745),
        Entry("Boston Logan", "BOS", "KBOS", 42.3656, -71.0096),
        Entry("Chicago O'Hare", "ORD", "KORD", 41.9742, -87.9073),
        Entry("Miami", "MIA", "KMIA", 25.7959, -80.2870),
        Entry("Atlanta Hartsfield", "ATL", "KATL", 33.6407, -84.4277),
        Entry("Dallas Fort Worth", "DFW", "KDFW", 32.8998, -97.0403),
        Entry("Los Angeles", "LAX", "KLAX", 33.9416, -118.4085),
        Entry("San Francisco", "SFO", "KSFO", 37.6213, -122.3790),
        Entry("Seattle Tacoma", "SEA", "KSEA", 47.4502, -122.3088),
        Entry("Toronto Pearson", "YYZ", "CYYZ", 43.6777, -79.6248),
        Entry("Vancouver", "YVR", "CYVR", 49.1967, -123.1815),
        Entry("Dubai", "DXB", "OMDB", 25.2532, 55.3657),
        Entry("Doha Hamad", "DOH", "OTHH", 25.2731, 51.6081),
        Entry("Abu Dhabi", "AUH", "OMAA", 24.4330, 54.6511),
        Entry("Singapore Changi", "SIN", "WSSS", 1.3644, 103.9915),
        Entry("Hong Kong", "HKG", "VHHH", 22.3080, 113.9185),
        Entry("Beijing Capital", "PEK", "ZBAA", 40.0799, 116.6031),
        Entry("Shanghai Pudong", "PVG", "ZSPD", 31.1443, 121.8083),
        Entry("Tokyo Haneda", "HND", "RJTT", 35.5494, 139.7798),
        Entry("Tokyo Narita", "NRT", "RJAA", 35.7720, 140.3929),
        Entry("Seoul Incheon", "ICN", "RKSI", 37.4602, 126.4407),
        Entry("Delhi Indira Gandhi", "DEL", "VIDP", 28.5562, 77.1000),
        Entry("Mumbai", "BOM", "VABB", 19.0896, 72.8656),
        Entry("Johannesburg OR Tambo", "JNB", "FAOR", -26.1367, 28.2411),
        Entry("Cairo", "CAI", "HECA", 30.1219, 31.4056),
        Entry("Lagos", "LOS", "DNMM", 6.5774, 3.3212),
        Entry("Sao Paulo Guarulhos", "GRU", "SBGR", -23.4356, -46.4731),
        Entry("Mexico City", "MEX", "MMMX", 19.4363, -99.0721),
        Entry("Toronto Billy Bishop", "YTZ", "CYTZ", 43.6270, -79.3961)
    ).associate { it.iata to it }

    fun byIata(iata: String?): Entry? = iata?.let { byCode[it.uppercase()] }
    fun byIcao(icao: String?): Entry? = icao?.let { c -> byCode.values.firstOrNull { it.icao.equals(c, ignoreCase = true) } }

    /** Simple case-insensitive search over IATA code and airport name. */
    fun search(query: String, limit: Int = 4): List<Entry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return byCode.values
            .filter {
                it.iata.contains(q, ignoreCase = true) ||
                it.icao.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true)
            }
            .take(limit)
    }

    /** Finds the nearest airport METAR for a given lat/lon position within maxDistMeters (default 150km). */
    fun findNearestMetar(lat: Double, lon: Double, airportWx: Map<String, Metar>, maxDistMeters: Double = 150_000.0): Metar? {
        if (airportWx.isEmpty() || lat == 0.0 || lon == 0.0) return null
        var bestMetar: Metar? = null
        var bestDist = Double.MAX_VALUE
        for ((icao, metar) in airportWx) {
            val ap = byIcao(icao) ?: continue
            val dist = GeoMath.distanceMeters(lat, lon, ap.lat, ap.lon)
            if (dist < bestDist) {
                bestDist = dist
                bestMetar = metar
            }
        }
        return if (bestDist <= maxDistMeters) bestMetar else null
    }
}
