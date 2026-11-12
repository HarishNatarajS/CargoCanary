package com.example.cargocanary.domain.model

enum class ConnectionMode {
    USB_SERIAL, BLE
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class EnvironmentLimits(
    val minTemp: Float = -10f,
    val maxTemp: Float = 40f,
    val minHum: Float = 0f,
    val maxHum: Float = 80f,
    val maxLight: Float = 500f,
    val maxShock: Float = 2.0f
)

data class ShipmentModel(
    val journeyId: String = "",
    val rfidUid: String = "",
    val cargoName: String = "",
    val cargoCategory: String = "",
    val sender: String = "",
    val receiver: String = "",
    val sourceLocation: String = "",
    val destinationLocation: String = "",
    val shipmentDate: String = "",
    val expectedDeliveryDate: String = "",
    val limits: EnvironmentLimits = EnvironmentLimits(),
    val status: String = "NOT_CONFIGURED"
)

data class TelemetryPacket(
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 1f,
    val lightLux: Float = 0f,
    val reedSwitchOpen: Boolean = false,
    val event: String = "NORMAL"
)
