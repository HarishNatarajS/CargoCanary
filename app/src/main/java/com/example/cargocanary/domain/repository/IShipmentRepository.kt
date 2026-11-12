package com.example.cargocanary.domain.repository

import com.example.cargocanary.domain.model.ShipmentModel
import com.example.cargocanary.domain.model.TelemetryPacket
import kotlinx.coroutines.flow.Flow

interface IShipmentRepository {
    fun getAllShipments(): Flow<List<ShipmentModel>>
    suspend fun getShipment(journeyId: String): ShipmentModel?
    suspend fun saveShipment(shipment: ShipmentModel)
    fun getTelemetryForJourney(journeyId: String): Flow<List<TelemetryPacket>>
    suspend fun saveTelemetryBatch(journeyId: String, packets: List<TelemetryPacket>)
}
