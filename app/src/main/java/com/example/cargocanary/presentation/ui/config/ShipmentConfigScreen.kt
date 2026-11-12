package com.example.cargocanary.presentation.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.domain.model.EnvironmentLimits
import com.example.cargocanary.domain.model.ShipmentModel
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.status

@Composable
fun ShipmentConfigScreen(
    viewModel: CargoViewModel,
    onBack: (() -> Unit)? = null
) {
    val currentShipment by viewModel.currentShipment.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    var journeyId by remember { mutableStateOf(currentShipment.journeyId) }
    var cargoName by remember { mutableStateOf(currentShipment.cargoName) }
    var cargoCategory by remember { mutableStateOf(currentShipment.cargoCategory) }
    var sender by remember { mutableStateOf(currentShipment.sender) }
    var receiver by remember { mutableStateOf(currentShipment.receiver) }
    var source by remember { mutableStateOf(currentShipment.sourceLocation) }
    var destination by remember { mutableStateOf(currentShipment.destinationLocation) }

    var minTemp by remember { mutableStateOf(currentShipment.limits.minTemp.toString()) }
    var maxTemp by remember { mutableStateOf(currentShipment.limits.maxTemp.toString()) }
    var minHum by remember { mutableStateOf(currentShipment.limits.minHum.toString()) }
    var maxHum by remember { mutableStateOf(currentShipment.limits.maxHum.toString()) }
    var maxLight by remember { mutableStateOf(currentShipment.limits.maxLight.toString()) }
    var maxShock by remember { mutableStateOf(currentShipment.limits.maxShock.toString()) }

    var configuredNotice by remember { mutableStateOf(false) }

    // Validation: every limit must be a number, each min must sit below its max, and ESP32 must be connected.
    fun bad(s: String) = s.toFloatOrNull() == null
    fun inverted(lo: String, hi: String): Boolean {
        val a = lo.toFloatOrNull(); val b = hi.toFloatOrNull()
        return a != null && b != null && a >= b
    }
    val tempRangeBad = inverted(minTemp, maxTemp)
    val humRangeBad = inverted(minHum, maxHum)
    val canSave = isConnected && journeyId.isNotBlank() &&
        listOf(minTemp, maxTemp, minHum, maxHum, maxLight, maxShock).none { bad(it) } &&
        !tempRangeBad && !humRangeBad

    CanaryScreen(title = "Create transit", subtitle = "Describe the shipment and set the limits the ESP32 will watch.", onBack = onBack) {

        if (!isConnected) {
            Panel(
                containerColor = MaterialTheme.status.warnContainer,
                borderColor = MaterialTheme.status.warn.copy(alpha = 0.35f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Default.WifiOff, tint = Color.White, background = MaterialTheme.status.warn)
                    Text("ESP32 disconnected. Connect via USB or Bluetooth on the Hub screen first.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Panel {
            Text("Shipment", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(journeyId, { journeyId = it; configuredNotice = false }, "Journey ID", isError = journeyId.isBlank())
                Field(cargoName, { cargoName = it; configuredNotice = false }, "Cargo name")
                Field(cargoCategory, { cargoCategory = it; configuredNotice = false }, "Cargo category")
                Field(sender, { sender = it; configuredNotice = false }, "Sender")
                Field(receiver, { receiver = it; configuredNotice = false }, "Receiver")
            }
        }

        Panel {
            Text("Route", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(source, { source = it; configuredNotice = false }, "Source")
                Field(destination, { destination = it; configuredNotice = false }, "Destination")
            }
        }

        Panel {
            Text("Limits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text("An alert is logged whenever a reading goes outside these values.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(minTemp, { minTemp = it; configuredNotice = false }, "Min temp", Modifier.weight(1f), keyboard = KeyboardType.Text, suffix = "°C", isError = bad(minTemp) || tempRangeBad)
                    Field(maxTemp, { maxTemp = it; configuredNotice = false }, "Max temp", Modifier.weight(1f), keyboard = KeyboardType.Text, suffix = "°C", isError = bad(maxTemp) || tempRangeBad)
                }
                if (tempRangeBad) ErrorNote("Min temperature must be below max.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(minHum, { minHum = it; configuredNotice = false }, "Min humidity", Modifier.weight(1f), suffix = "%", isError = bad(minHum) || humRangeBad)
                    Field(maxHum, { maxHum = it; configuredNotice = false }, "Max humidity", Modifier.weight(1f), suffix = "%", isError = bad(maxHum) || humRangeBad)
                }
                if (humRangeBad) ErrorNote("Min humidity must be below max.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(maxLight, { maxLight = it; configuredNotice = false }, "Max light", Modifier.weight(1f), suffix = "lux", isError = bad(maxLight))
                    Field(maxShock, { maxShock = it; configuredNotice = false }, "Max shock", Modifier.weight(1f), suffix = "g", isError = bad(maxShock))
                }
            }
        }

        PrimaryAction(
            text = if (isConnected) "Save and send to ESP32" else "Connect ESP32 first",
            icon = Icons.AutoMirrored.Filled.Send,
            enabled = canSave,
            onClick = {
                val updated = ShipmentModel(
                    journeyId = journeyId,
                    rfidUid = currentShipment.rfidUid,
                    cargoName = cargoName,
                    cargoCategory = cargoCategory,
                    sender = sender,
                    receiver = receiver,
                    sourceLocation = source,
                    destinationLocation = destination,
                    shipmentDate = currentShipment.shipmentDate,
                    expectedDeliveryDate = currentShipment.expectedDeliveryDate,
                    limits = EnvironmentLimits(
                        minTemp = minTemp.toFloatOrNull() ?: -20f,
                        maxTemp = maxTemp.toFloatOrNull() ?: 60f,
                        minHum = minHum.toFloatOrNull() ?: 0f,
                        maxHum = maxHum.toFloatOrNull() ?: 100f,
                        maxLight = maxLight.toFloatOrNull() ?: 1000f,
                        maxShock = maxShock.toFloatOrNull() ?: 3.5f
                    ),
                    status = "CONFIGURED"
                )
                viewModel.updateShipmentConfig(updated)
                viewModel.configureEsp32()
                configuredNotice = true
            }
        )

        if (configuredNotice) {
            Panel(
                containerColor = MaterialTheme.status.okContainer,
                borderColor = MaterialTheme.status.ok.copy(alpha = 0.35f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Default.Check, tint = Color.White, background = MaterialTheme.status.ok)
                    Text("Configuration saved and sent to the ESP32.", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

@Composable
private fun ErrorNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
