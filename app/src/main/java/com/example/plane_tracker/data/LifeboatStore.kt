package com.example.plane_tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLite store for persisting discovered lifeboats across app runs.
 *
 * Vessel metadata (name, callsign, ship type) is broadcast infrequently via
 * AIS static reports. Caching classified lifeboats locally ensures returning
 * vessels are recognized and displayed immediately on subsequent app launches.
 */
class LifeboatStore(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "LifeboatStore"
        private const val DB_NAME = "lifeboats_cache.db"
        private const val DB_VERSION = 1
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
                last_seen INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_lifeboat_mmsi ON $TABLE(mmsi)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** Loads all stored lifeboats into a map of mmsi -> Vessel. */
    fun loadAllLifeboats(): Map<String, Vessel> {
        val result = mutableMapOf<String, Vessel>()
        try {
            readableDatabase.rawQuery(
                "SELECT mmsi, name, call_sign, ship_type, is_lifeboat, last_seen FROM $TABLE WHERE is_lifeboat = 1",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsi = cursor.getString(0) ?: continue
                    val vessel = Vessel(
                        mmsi = mmsi,
                        name = cursor.getString(1) ?: "",
                        callSign = cursor.getString(2) ?: "",
                        shipType = cursor.getInt(3),
                        isLifeboat = cursor.getInt(4) == 1,
                        lastSeen = cursor.getLong(5)
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

    /** Saves or updates a single vessel's details. */
    fun saveVessel(vessel: Vessel) {
        if (!vessel.isLifeboat && vessel.mmsi.isBlank()) return
        try {
            val db = writableDatabase
            db.compileStatement(
                "INSERT OR REPLACE INTO $TABLE (mmsi, name, call_sign, ship_type, is_lifeboat, last_seen) " +
                    "VALUES (?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.bindString(1, vessel.mmsi)
                stmt.bindString(2, vessel.name)
                stmt.bindString(3, vessel.callSign)
                stmt.bindLong(4, vessel.shipType.toLong())
                stmt.bindLong(5, if (vessel.isLifeboat) 1L else 0L)
                stmt.bindLong(6, vessel.lastSeen)
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.compileStatement(
                "INSERT OR REPLACE INTO $TABLE (mmsi, name, call_sign, ship_type, is_lifeboat, last_seen) " +
                    "VALUES (?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                for (vessel in lifeboats) {
                    stmt.bindString(1, vessel.mmsi)
                    stmt.bindString(2, vessel.name)
                    stmt.bindString(3, vessel.callSign)
                    stmt.bindLong(4, vessel.shipType.toLong())
                    stmt.bindLong(5, if (vessel.isLifeboat) 1L else 0L)
                    stmt.bindLong(6, vessel.lastSeen)
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch save lifeboats: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }
}
