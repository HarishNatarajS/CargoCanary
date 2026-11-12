package com.example.cargocanary.data.repository

import com.example.cargocanary.data.local.CargoDatabaseHelper
import com.example.cargocanary.domain.model.ShipmentModel
import com.example.cargocanary.domain.model.TelemetryPacket
import com.example.cargocanary.domain.repository.IShipmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ShipmentRepositoryImpl(private val dbHelper: CargoDatabaseHelper) : IShipmentRepository {
    override fun getAllShipments(): Flow<List<ShipmentModel>> = flow {
        dbHelper.shipmentsUpdated.collect {
            emit(dbHelper.getAllShipments())
        }
    }

    override suspend fun getShipment(journeyId: String): ShipmentModel? {
        return dbHelper.getAllShipments().find { it.journeyId == journeyId }
    }

    override suspend fun saveShipment(shipment: ShipmentModel) {
        dbHelper.saveShipment(shipment)
    }

    override fun getTelemetryForJourney(journeyId: String): Flow<List<TelemetryPacket>> = flow {
        emit(dbHelper.getTelemetryForJourney(journeyId))
    }

    override suspend fun saveTelemetryBatch(journeyId: String, packets: List<TelemetryPacket>) {
        dbHelper.saveTelemetryBatch(journeyId, packets)
    }
}
