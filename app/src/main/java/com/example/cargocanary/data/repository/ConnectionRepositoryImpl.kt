package com.example.cargocanary.data.repository

import android.content.Context
import com.example.cargocanary.data.datasource.BleConnectionDataSource
import com.example.cargocanary.data.datasource.ConnectionDataSource
import com.example.cargocanary.data.datasource.UsbSerialDataSource
import com.example.cargocanary.domain.model.ConnectionMode
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.domain.model.TelemetryPacket
import com.example.cargocanary.domain.repository.IConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionRepositoryImpl(context: Context) : IConnectionRepository {
    private val usbSource = UsbSerialDataSource(context)
    private val bleSource = BleConnectionDataSource(context)

    private val _currentMode = MutableStateFlow(ConnectionMode.USB_SERIAL)
    override val currentMode: StateFlow<ConnectionMode> = _currentMode.asStateFlow()

    private var activeSource: ConnectionDataSource = usbSource

    override val connectionStatus: StateFlow<ConnectionStatus>
        get() = activeSource.connectionStatus
    override val telemetryFlow: StateFlow<TelemetryPacket?>
        get() = activeSource.telemetryFlow
    override val rawConsoleFlow: StateFlow<String>
        get() = activeSource.rawConsoleFlow
    override val batteryLevel: StateFlow<Int?>
        get() = activeSource.batteryLevel
    override val storageCapacity: StateFlow<Int?>
        get() = activeSource.storageCapacity

    override fun setMode(mode: ConnectionMode) {
        if (_currentMode.value == mode) return
        activeSource = when (mode) {
            ConnectionMode.USB_SERIAL -> usbSource
            ConnectionMode.BLE -> bleSource
        }
        _currentMode.value = mode
    }

    override suspend fun connect() {
        activeSource.connect()
    }

    override suspend fun disconnect() {
        activeSource.disconnect()
    }

    override suspend fun sendConfigJson(jsonConfig: String): Boolean {
        return activeSource.sendConfigJson(jsonConfig)
    }

    override suspend fun scanRfid(): String {
        return activeSource.scanRfid()
    }

    override suspend fun retrieveCsvLogs(): List<TelemetryPacket> {
        return activeSource.retrieveCsvLogs()
    }
}
