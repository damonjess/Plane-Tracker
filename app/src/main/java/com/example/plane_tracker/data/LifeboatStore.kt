package com.example.plane_tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLite store for persisting discovered lifeboats and their last known positions
 * across app runs so returning vessels render immediately on launch.
 */
class LifeboatStore(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "LifeboatStore"
        private const val DB_NAME = "lifeboats_cache.db"
        private const val DB_VERSION = 2
        private const val TABLE = "lifeboats"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                mmsi TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                call_sign TEXT NOT NULL,
                ship_type INTEGER NOT NULL,
                is_lifeboat INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                latitude REAL NOT NULL DEFAULT 0.0,
                longitude REAL NOT NULL DEFAULT 0.0,
                speed_knots REAL NOT NULL DEFAULT 0.0,
                heading REAL NOT NULL DEFAULT 0.0,
                nav_status INTEGER NOT NULL DEFAULT 15
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_lifeboat_mmsi ON $TABLE(mmsi)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN speed_knots REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN heading REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN nav_status INTEGER NOT NULL DEFAULT 15")
                return
            } catch (_: Exception) {
                // Fall back to recreating the table if alter fails
            }
        }
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** Loads all stored lifeboats into a map of mmsi -> Vessel. */
    fun loadAllLifeboats(): Map<String, Vessel> {
        val result = mutableMapOf<String, Vessel>()
        try {
            readableDatabase.rawQuery(
                "SELECT mmsi, name, call_sign, ship_type, is_lifeboat, last_seen, " +
                    "latitude, longitude, speed_knots, heading, nav_status " +
                    "FROM $TABLE WHERE is_lifeboat = 1",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsi = cursor.getString(0) ?: continue
                    val knownFallback = KnownLifeboats.getKnownDetails(mmsi)
                    val rawLat = cursor.getDouble(6)
                    val rawLon = cursor.getDouble(7)
                    val lat = if (!rawLat.isNaN() && rawLat in -90.0..90.0 && rawLat != 0.0) {
                        rawLat
                    } else {
                        knownFallback?.defaultLat ?: 0.0
                    }
                    val lon = if (!rawLon.isNaN() && rawLon in -180.0..180.0 && rawLon != 0.0) {
                        rawLon
                    } else {
                        knownFallback?.defaultLon ?: 0.0
                    }
                    val vessel = Vessel(
                        mmsi = mmsi,
                        name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: knownFallback?.name.orEmpty(),
                        callSign = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: knownFallback?.callSign.orEmpty(),
                        shipType = cursor.getInt(3).takeIf { it > 0 } ?: (knownFallback?.shipType ?: 51),
                        isLifeboat = cursor.getInt(4) == 1,
                        lastSeen = cursor.getLong(5),
                        latitude = lat,
                        longitude = lon,
                        speedKnots = cursor.getDouble(8).takeIf { !it.isNaN() && it in 0.0..102.2 } ?: 0.0,
                        heading = cursor.getDouble(9).takeIf { !it.isNaN() && it in 0.0..359.9 } ?: 0.0,
                        navStatus = cursor.getInt(10)
                    )
                    vessel.lifeboatCheckDirty = false
                    result[mmsi] = vessel
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load lifeboats from DB: ${e.message}")
        }
        return result
    }

    /** Saves or updates a single vessel's details and position. */
    fun saveVessel(vessel: Vessel) {
        if (!vessel.isLifeboat || vessel.mmsi.isBlank()) return
        try {
            val db = writableDatabase
            db.compileStatement(
                "INSERT OR REPLACE INTO $TABLE " +
                    "(mmsi, name, call_sign, ship_type, is_lifeboat, last_seen, latitude, longitude, speed_knots, heading, nav_status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.bindString(1, vessel.mmsi)
                stmt.bindString(2, vessel.name)
                stmt.bindString(3, vessel.callSign)
                stmt.bindLong(4, vessel.shipType.toLong())
                stmt.bindLong(5, if (vessel.isLifeboat) 1L else 0L)
                stmt.bindLong(6, vessel.lastSeen)
                stmt.bindDouble(7, if (vessel.latitude.isNaN()) 0.0 else vessel.latitude)
                stmt.bindDouble(8, if (vessel.longitude.isNaN()) 0.0 else vessel.longitude)
                stmt.bindDouble(9, if (vessel.speedKnots.isNaN()) 0.0 else vessel.speedKnots)
                stmt.bindDouble(10, if (vessel.heading.isNaN()) 0.0 else vessel.heading)
                stmt.bindLong(11, vessel.navStatus.toLong())
                stmt.executeInsert()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save vessel ${vessel.mmsi}: ${e.message}")
        }
    }

    /** Batch-saves a collection of vessels in a single transaction. */
    fun saveAll(vessels: Collection<Vessel>) {
        val lifeboats = vessels.filter { it.isLifeboat && it.mmsi.isNotBlank() }
        if (lifeboats.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.compileStatement(
                    "INSERT OR REPLACE INTO $TABLE " +
                        "(mmsi, name, call_sign, ship_type, is_lifeboat, last_seen, latitude, longitude, speed_knots, heading, nav_status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).use { stmt ->
                    for (vessel in lifeboats) {
                        stmt.bindString(1, vessel.mmsi)
                        stmt.bindString(2, vessel.name)
                        stmt.bindString(3, vessel.callSign)
                        stmt.bindLong(4, vessel.shipType.toLong())
                        stmt.bindLong(5, if (vessel.isLifeboat) 1L else 0L)
                        stmt.bindLong(6, vessel.lastSeen)
                        stmt.bindDouble(7, if (vessel.latitude.isNaN()) 0.0 else vessel.latitude)
                        stmt.bindDouble(8, if (vessel.longitude.isNaN()) 0.0 else vessel.longitude)
                        stmt.bindDouble(9, if (vessel.speedKnots.isNaN()) 0.0 else vessel.speedKnots)
                        stmt.bindDouble(10, if (vessel.heading.isNaN()) 0.0 else vessel.heading)
                        stmt.bindLong(11, vessel.navStatus.toLong())
                        stmt.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch save lifeboats: ${e.message}")
        }
    }
}
