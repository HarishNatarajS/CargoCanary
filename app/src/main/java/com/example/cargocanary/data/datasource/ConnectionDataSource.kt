package com.example.cargocanary.data.datasource

import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.domain.model.TelemetryPacket
import kotlinx.coroutines.flow.StateFlow

interface ConnectionDataSource {
    val connectionStatus: StateFlow<ConnectionStatus>
    val telemetryFlow: StateFlow<TelemetryPacket?>
    val rawConsoleFlow: StateFlow<String>
    val batteryLevel: StateFlow<Int?>
    val storageCapacity: StateFlow<Int?>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendConfigJson(jsonConfig: String): Boolean
    suspend fun scanRfid(): String
    suspend fun retrieveCsvLogs(): List<TelemetryPacket>
}
