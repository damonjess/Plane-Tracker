package com.example.plane_tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persists flight position reports to SQLite so past flights can be replayed
 * with a scrubber. Written every fleet poll (~10s); trimmed to [HOURS_TO_KEEP].
 */
class FlightHistoryStore(context: Context) :
    SQLiteOpenHelper(context, "flight_history.db", null, DB_VERSION) {

    companion object {
        private const val DB_VERSION = 1
        private const val TABLE = "positions"
        private const val HOURS_TO_KEEP = 24
        private const val MAX_ROWS_PER_HEX = 540 // ~90 min at 10s cadence
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                hex TEXT NOT NULL,
                ts INTEGER NOT NULL,
                lon REAL NOT NULL,
                lat REAL NOT NULL,
                alt_m REAL NOT NULL,
                speed_mps REAL NOT NULL,
                vrate_mps REAL NOT NULL,
                heading REAL NOT NULL,
                on_ground INTEGER NOT NULL,
                callsign TEXT,
                PRIMARY KEY (hex, ts)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_hex_ts ON $TABLE(hex, ts DESC)")
        db.execSQL("CREATE INDEX idx_ts ON $TABLE(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** Batch-inserts one poll's worth of positions. Ignores duplicates. */
    fun insertAll(aircraft: List<Aircraft>, ts: Long = System.currentTimeMillis()) {
        if (aircraft.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.compileStatement(
                "INSERT OR REPLACE INTO $TABLE (hex, ts, lon, lat, alt_m, speed_mps, vrate_mps, heading, on_ground, callsign) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)"
            ).use { stmt ->
                for (ac in aircraft) {
                    if (ac.latitude.isNaN() || ac.longitude.isNaN()) continue
                    stmt.bindString(1, ac.icao24)
                    stmt.bindLong(2, ts)
                    stmt.bindDouble(3, ac.longitude)
                    stmt.bindDouble(4, ac.latitude)
                    stmt.bindDouble(5, ac.altitudeMeters)
                    stmt.bindDouble(6, ac.velocityMps)
                    stmt.bindDouble(7, ac.verticalRateMps)
                    stmt.bindDouble(8, ac.heading.toDouble())
                    stmt.bindLong(9, if (ac.onGround) 1 else 0)
                    stmt.bindString(10, ac.callsign)
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** One recorded position of a flight. */
    data class Point(
        val ts: Long,
        val longitude: Double,
        val latitude: Double,
        val altitudeMeters: Double,
        val velocityMps: Double,
        val heading: Double,
        val callsign: String
    )

    /** Full track for one hex, oldest first, within the last [hours]. */
    fun trackFor(hex: String, hours: Int = 2): List<Point> {
        val since = System.currentTimeMillis() - hours * 3_600_000L
        val out = mutableListOf<Point>()
        readableDatabase.rawQuery(
            "SELECT ts, lon, lat, alt_m, speed_mps, heading, callsign FROM $TABLE " +
                "WHERE hex = ? AND ts >= ? ORDER BY ts ASC",
            arrayOf(hex, since.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Point(
                    ts = c.getLong(0),
                    longitude = c.getDouble(1),
                    latitude = c.getDouble(2),
                    altitudeMeters = c.getDouble(3),
                    velocityMps = c.getDouble(4),
                    heading = c.getDouble(5),
                    callsign = c.getString(6) ?: ""
                )
            }
        }
        return out
    }

    /** Hexes with recordings in the last [hours], most recent first. */
    fun recentFlights(hours: Int = 2, limit: Int = 40): List<Triple<String, String, Long>> {
        val since = System.currentTimeMillis() - hours * 3_600_000L
        val out = mutableListOf<Triple<String, String, Long>>()
        readableDatabase.rawQuery(
            "SELECT hex, MAX(callsign) AS cs, MAX(ts) AS last_ts FROM $TABLE " +
                "WHERE ts >= ? GROUP BY hex ORDER BY last_ts DESC LIMIT ?",
            arrayOf(since.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Triple(c.getString(0), c.getString(1) ?: "", c.getLong(2))
            }
        }
        return out
    }

    /** Deletes rows older than the keep window; called opportunistically. */
    fun trimOld() {
        val cutoff = System.currentTimeMillis() - HOURS_TO_KEEP * 3_600_000L
        writableDatabase.execSQL("DELETE FROM $TABLE WHERE ts < ?", arrayOf(cutoff.toString()))
    }
}
