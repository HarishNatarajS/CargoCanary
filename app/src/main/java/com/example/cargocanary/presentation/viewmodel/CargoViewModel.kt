package com.example.cargocanary.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cargocanary.data.local.CargoDatabaseHelper
import com.example.cargocanary.data.repository.ConnectionRepositoryImpl
import com.example.cargocanary.data.repository.ShipmentRepositoryImpl
import com.example.cargocanary.domain.model.ConnectionMode
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.domain.model.ShipmentModel
import com.example.cargocanary.domain.model.TelemetryPacket
import com.example.cargocanary.domain.repository.IConnectionRepository
import com.example.cargocanary.domain.repository.IShipmentRepository
import com.example.cargocanary.presentation.analytics.PdfReportGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CargoViewModel(
    private val connectionRepository: IConnectionRepository,
    private val shipmentRepository: IShipmentRepository,
    private val appContext: Context
) : ViewModel() {

    val currentMode: StateFlow<ConnectionMode> = connectionRepository.currentMode
    val connectionStatus: StateFlow<ConnectionStatus> = connectionRepository.connectionStatus
    val telemetryFlow: StateFlow<TelemetryPacket?> = connectionRepository.telemetryFlow
    val rawConsoleFlow: StateFlow<String> = connectionRepository.rawConsoleFlow
    val batteryLevel: StateFlow<Int?> = connectionRepository.batteryLevel
    val storageCapacity: StateFlow<Int?> = connectionRepository.storageCapacity

    private val _currentShipment = MutableStateFlow(ShipmentModel())
    val currentShipment: StateFlow<ShipmentModel> = _currentShipment.asStateFlow()

    private val _retrievedLogs = MutableStateFlow<List<TelemetryPacket>>(emptyList())
    val retrievedLogs: StateFlow<List<TelemetryPacket>> = _retrievedLogs.asStateFlow()

    private val _rfidScanStatus = MutableStateFlow("Waiting for RFID Package Scan...")
    val rfidScanStatus: StateFlow<String> = _rfidScanStatus.asStateFlow()

    private val _pendingRfidTag = MutableStateFlow<String?>(null)
    val pendingRfidTag: StateFlow<String?> = _pendingRfidTag.asStateFlow()

    private val _uiPromptMessage = MutableStateFlow<String?>(null)
    val uiPromptMessage: StateFlow<String?> = _uiPromptMessage.asStateFlow()

    private val _isArmed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

    val allShipments: StateFlow<List<ShipmentModel>> = shipmentRepository.getAllShipments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setConnectionMode(mode: ConnectionMode) {
        connectionRepository.setMode(mode)
    }

    fun connect() {
        viewModelScope.launch {
            connectionRepository.connect()
            if (connectionStatus.value == ConnectionStatus.ERROR) {
                val modeName = if (currentMode.value == ConnectionMode.USB_SERIAL) "USB OTG Cable" else "Bluetooth BLE"
                _uiPromptMessage.value = "No ESP32 Device Found via $modeName!\n\nPlease make sure your ESP32 board is powered on and connected."
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
        }
    }

    fun clearUiPromptMessage() {
        _uiPromptMessage.value = null
    }

    fun updateShipmentConfig(newShipment: ShipmentModel) {
        _currentShipment.value = newShipment
    }

    fun configureEsp32() {
        viewModelScope.launch {
            if (connectionStatus.value != ConnectionStatus.CONNECTED) {
                _uiPromptMessage.value = "ESP32 Disconnected!\n\nPlease connect your ESP32 hardware before sending configuration."
                return@launch
            }
            val shipment = _currentShipment.value.copy(status = "ARMED & LOGGING ACTIVE")
            val jsonConfig = """{"journeyId":"${shipment.journeyId}","minTemp":${shipment.limits.minTemp},"maxTemp":${shipment.limits.maxTemp},"maxLight":${shipment.limits.maxLight},"maxShock":${shipment.limits.maxShock}}"""
            val success = connectionRepository.sendConfigJson(jsonConfig)
            if (success) {
                shipmentRepository.saveShipment(shipment)
                _currentShipment.value = shipment
                _isArmed.value = true
                _uiPromptMessage.value = "Configuration successfully written to ESP32! Shipment ARMED."
            } else {
                _uiPromptMessage.value = "Failed to send configuration to ESP32 hardware."
            }
        }
    }

    fun scanRfidTag() {
        viewModelScope.launch {
            if (connectionStatus.value != ConnectionStatus.CONNECTED) {
                _uiPromptMessage.value = "ESP32 Disconnected!\n\nPlease connect your ESP32 hardware before scanning RFID cards."
                return@launch
            }

            _rfidScanStatus.value = "Scanning RC522 RFID Tag..."
            val uid = connectionRepository.scanRfid()
            if (uid.isBlank()) {
                _rfidScanStatus.value = "RFID Scan Failed or No Tag Detected"
                _uiPromptMessage.value = "No RFID Tag Detected!\n\nPlease place an RFID card on the RC522 reader."
                return@launch
            }

            // Check if tag already exists in local SQLite DB
            val existingList = allShipments.value
            val existingShipment = existingList.find { it.rfidUid.equals(uid, ignoreCase = true) }

            if (existingShipment != null) {
                if (existingShipment.status.contains("ARMED") || existingShipment.status.contains("ACTIVE")) {
                    // Scenario 2: Armed tag re-scanned at warehouse
                    _uiPromptMessage.value = "Shipment ${existingShipment.journeyId} is currently ARMED & LOGGING in transit.\n\nDo you want to mark it as ARRIVED AT DESTINATION and retrieve transit logs?"
                    _currentShipment.value = existingShipment
                } else {
                    // EXISTING DESTINATION TAG: Load shipment & automatically retrieve LittleFS CSV logs
                    _currentShipment.value = existingShipment
                    _rfidScanStatus.value = "Existing Tag Recognized (UID: $uid) — Downloading Transit Logs..."
                    _isArmed.value = true
                    retrieveTransitLogs()
                }
            } else {
                // NEW TAG: Prompt user for confirmation dialog
                _pendingRfidTag.value = uid
                _rfidScanStatus.value = "New Tag Detected (UID: $uid) — Awaiting User Confirmation"
            }
        }
    }

    fun confirmRfidTag(uid: String) {
        val autoJourneyId = if (_currentShipment.value.journeyId.isNotBlank()) {
            _currentShipment.value.journeyId
        } else {
            "CC-" + uid.replace(" ", "").takeLast(6).uppercase()
        }
        val updated = _currentShipment.value.copy(
            journeyId = autoJourneyId,
            rfidUid = uid,
            status = "TAG_CONFIRMED_PENDING_CONFIG"
        )
        _currentShipment.value = updated
        _pendingRfidTag.value = null
        _rfidScanStatus.value = "Tag $uid Confirmed! Please set transit limits."
    }

    fun dismissRfidTagConfirmation() {
        _pendingRfidTag.value = null
        _rfidScanStatus.value = "Tag scan discarded. Ready to scan a different tag."
    }

    fun markArrivedAndRetrieve() {
        viewModelScope.launch {
            val updated = _currentShipment.value.copy(status = "ARRIVED")
            _currentShipment.value = updated
            shipmentRepository.saveShipment(updated)
            _uiPromptMessage.value = "Marked as Arrived at Destination. Downloading transit logs..."
            retrieveTransitLogs()
        }
    }

    fun retrieveTransitLogs() {
        viewModelScope.launch {
            if (connectionStatus.value != ConnectionStatus.CONNECTED) {
                _uiPromptMessage.value = "ESP32 Disconnected!\n\nConnection interrupted mid-download. Please reconnect and retry."
                return@launch
            }
            try {
                val logs = connectionRepository.retrieveCsvLogs()
                _retrievedLogs.value = logs
                if (logs.isEmpty()) {
                    _uiPromptMessage.value = "Warning: No stored LittleFS logs retrieved from ESP32."
                } else {
                    val journeyId = _currentShipment.value.journeyId
                    if (journeyId.isNotBlank()) {
                        shipmentRepository.saveTelemetryBatch(journeyId, logs)
                    }
                    _uiPromptMessage.value = "Successfully downloaded ${logs.size} records. CRC32 Verified."
                }
            } catch (e: Exception) {
                _uiPromptMessage.value = "Download Interrupted! Error: ${e.message}. Please retry."
            }
        }
    }

    fun generatePdfReport() {
        val file = PdfReportGenerator.generateReport(appContext, _currentShipment.value, _retrievedLogs.value)
        if (file != null) {
            PdfReportGenerator.sharePdfReport(appContext, file)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dbHelper = CargoDatabaseHelper(context)
            val connRepo = ConnectionRepositoryImpl(context)
            val shipRepo = ShipmentRepositoryImpl(dbHelper)
            return CargoViewModel(connRepo, shipRepo, context) as T
        }
    }
}
