package com.example.cargocanary.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.cargocanary.domain.model.EnvironmentLimits
import com.example.cargocanary.domain.model.ShipmentModel
import com.example.cargocanary.domain.model.TelemetryPacket
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CargoDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _shipmentsUpdated = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val shipmentsUpdated: SharedFlow<Unit> = _shipmentsUpdated.asSharedFlow()

    init {
        _shipmentsUpdated.tryEmit(Unit)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE shipments (
                journeyId TEXT PRIMARY KEY,
                rfidUid TEXT,
                cargoName TEXT,
                cargoCategory TEXT,
                sender TEXT,
                receiver TEXT,
                sourceLocation TEXT,
                destinationLocation TEXT,
                shipmentDate TEXT,
                expectedDeliveryDate TEXT,
                minTemp REAL,
                maxTemp REAL,
                minHum REAL,
                maxHum REAL,
                maxLight REAL,
                maxShock REAL,
                status TEXT
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE telemetry_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                journeyId TEXT,
                timestamp INTEGER,
                temperature REAL,
                humidity REAL,
                accelX REAL,
                accelY REAL,
                accelZ REAL,
                lightLux REAL,
                reedSwitchOpen INTEGER,
                event TEXT
            )
            """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS shipments")
        db.execSQL("DROP TABLE IF EXISTS telemetry_logs")
        onCreate(db)
    }

    fun saveShipment(shipment: ShipmentModel) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("journeyId", shipment.journeyId)
            put("rfidUid", shipment.rfidUid)
            put("cargoName", shipment.cargoName)
            put("cargoCategory", shipment.cargoCategory)
            put("sender", shipment.sender)
            put("receiver", shipment.receiver)
            put("sourceLocation", shipment.sourceLocation)
            put("destinationLocation", shipment.destinationLocation)
            put("shipmentDate", shipment.shipmentDate)
            put("expectedDeliveryDate", shipment.expectedDeliveryDate)
            put("minTemp", shipment.limits.minTemp)
            put("maxTemp", shipment.limits.maxTemp)
            put("minHum", shipment.limits.minHum)
            put("maxHum", shipment.limits.maxHum)
            put("maxLight", shipment.limits.maxLight)
            put("maxShock", shipment.limits.maxShock)
            put("status", shipment.status)
        }
        db.insertWithOnConflict("shipments", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        _shipmentsUpdated.tryEmit(Unit)
    }

    fun getAllShipments(): List<ShipmentModel> {
        val list = mutableListOf<ShipmentModel>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM shipments ORDER BY shipmentDate DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ShipmentModel(
                        journeyId = it.getString(it.getColumnIndexOrThrow("journeyId")),
                        rfidUid = it.getString(it.getColumnIndexOrThrow("rfidUid")),
                        cargoName = it.getString(it.getColumnIndexOrThrow("cargoName")),
                        cargoCategory = it.getString(it.getColumnIndexOrThrow("cargoCategory")),
                        sender = it.getString(it.getColumnIndexOrThrow("sender")),
                        receiver = it.getString(it.getColumnIndexOrThrow("receiver")),
                        sourceLocation = it.getString(it.getColumnIndexOrThrow("sourceLocation")),
                        destinationLocation = it.getString(it.getColumnIndexOrThrow("destinationLocation")),
                        shipmentDate = it.getString(it.getColumnIndexOrThrow("shipmentDate")),
                        expectedDeliveryDate = it.getString(it.getColumnIndexOrThrow("expectedDeliveryDate")),
                        limits = EnvironmentLimits(
                            minTemp = it.getFloat(it.getColumnIndexOrThrow("minTemp")),
                            maxTemp = it.getFloat(it.getColumnIndexOrThrow("maxTemp")),
                            minHum = it.getFloat(it.getColumnIndexOrThrow("minHum")),
                            maxHum = it.getFloat(it.getColumnIndexOrThrow("maxHum")),
                            maxLight = it.getFloat(it.getColumnIndexOrThrow("maxLight")),
                            maxShock = it.getFloat(it.getColumnIndexOrThrow("maxShock"))
                        ),
                        status = it.getString(it.getColumnIndexOrThrow("status"))
                    )
                )
            }
        }
        return list
    }

    fun saveTelemetryBatch(journeyId: String, packets: List<TelemetryPacket>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in packets) {
                val values = ContentValues().apply {
                    put("journeyId", journeyId)
                    put("timestamp", p.timestamp)
                    put("temperature", p.temperature)
                    put("humidity", p.humidity)
                    put("accelX", p.accelX)
                    put("accelY", p.accelY)
                    put("accelZ", p.accelZ)
                    put("lightLux", p.lightLux)
                    put("reedSwitchOpen", if (p.reedSwitchOpen) 1 else 0)
                    put("event", p.event)
                }
                db.insert("telemetry_logs", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getTelemetryForJourney(journeyId: String): List<TelemetryPacket> {
        val list = mutableListOf<TelemetryPacket>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM telemetry_logs WHERE journeyId = ? ORDER BY timestamp ASC", arrayOf(journeyId))
        cursor.use {
            while (it.moveToNext()) {
                val eventIdx = it.getColumnIndex("event")
                val eventStr = if (eventIdx != -1) it.getString(eventIdx) else "NORMAL"
                list.add(
                    TelemetryPacket(
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        temperature = it.getFloat(it.getColumnIndexOrThrow("temperature")),
                        humidity = it.getFloat(it.getColumnIndexOrThrow("humidity")),
                        accelX = it.getFloat(it.getColumnIndexOrThrow("accelX")),
                        accelY = it.getFloat(it.getColumnIndexOrThrow("accelY")),
                        accelZ = it.getFloat(it.getColumnIndexOrThrow("accelZ")),
                        lightLux = it.getFloat(it.getColumnIndexOrThrow("lightLux")),
                        reedSwitchOpen = it.getInt(it.getColumnIndexOrThrow("reedSwitchOpen")) == 1,
                        event = eventStr ?: "NORMAL"
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME = "cargo_canary.db"
        private const val DATABASE_VERSION = 2
    }
}
