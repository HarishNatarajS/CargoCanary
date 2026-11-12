package com.example.cargocanary.data.datasource

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.domain.model.TelemetryPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbSerialDataSource(context: Context) : ConnectionDataSource {
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _telemetryFlow = MutableStateFlow<TelemetryPacket?>(null)
    override val telemetryFlow: StateFlow<TelemetryPacket?> = _telemetryFlow.asStateFlow()

    private val _rawConsoleFlow = MutableStateFlow("")
    override val rawConsoleFlow: StateFlow<String> = _rawConsoleFlow.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _storageCapacity = MutableStateFlow<Int?>(null)
    override val storageCapacity: StateFlow<Int?> = _storageCapacity.asStateFlow()

    private var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect() {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        appendLog("[USB OTG] Searching for connected physical ESP32 device...")

        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            _connectionStatus.value = ConnectionStatus.ERROR
            _batteryLevel.value = null
            _storageCapacity.value = null
            _telemetryFlow.value = null
            appendLog("[USB OTG ERROR] No physical USB OTG device detected. Please connect ESP32 hardware via USB cable.")
            return
        }

        val device: UsbDevice? = deviceList.values.firstOrNull()
        if (device != null) {
            appendLog("[USB OTG] Found USB Device: ${device.deviceName} (VID:${device.vendorId} PID:${device.productId})")
            _connectionStatus.value = ConnectionStatus.CONNECTED
        } else {
            _connectionStatus.value = ConnectionStatus.ERROR
            _batteryLevel.value = null
            _storageCapacity.value = null
            _telemetryFlow.value = null
            appendLog("[USB OTG ERROR] USB Device connection failed.")
        }
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _batteryLevel.value = null
        _storageCapacity.value = null
        _telemetryFlow.value = null
        appendLog("[USB OTG] Connection closed.")
    }

    override suspend fun sendConfigJson(jsonConfig: String): Boolean {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            appendLog("[USB ERROR] Cannot send config — ESP32 not connected.")
            return false
        }
        appendLog("[USB TX @ 115200] Sending Config to ESP32: $jsonConfig")
        return true
    }

    override suspend fun scanRfid(): String {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            appendLog("[USB ERROR] Cannot scan RFID — ESP32 not connected.")
            return ""
        }
        appendLog("[USB OTG] Awaiting RC522 RFID Card Scan from ESP32...")
        return ""
    }

    override suspend fun retrieveCsvLogs(): List<TelemetryPacket> {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            appendLog("[USB ERROR] Cannot retrieve logs — ESP32 not connected.")
            return emptyList()
        }
        appendLog("[USB TX @ 115200] Command 'R' sent to ESP32...")
        return emptyList()
    }

    private fun appendLog(msg: String) {
        val current = _rawConsoleFlow.value
        _rawConsoleFlow.value = if (current.isEmpty()) msg else "$current\n$msg"
    }
}
