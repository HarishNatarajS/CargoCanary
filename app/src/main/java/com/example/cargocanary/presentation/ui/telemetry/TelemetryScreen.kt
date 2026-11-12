package com.example.cargocanary.presentation.ui.telemetry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.*

@Composable
fun TelemetryScreen(
    viewModel: CargoViewModel,
    onBack: (() -> Unit)? = null
) {
    val telemetry by viewModel.telemetryFlow.collectAsState()
    val rawConsole by viewModel.rawConsoleFlow.collectAsState()
    val shipment by viewModel.currentShipment.collectAsState()
    val limits = shipment.limits

    CanaryScreen(title = "Live sensors", subtitle = "Readings from the ESP32. Values outside your limits turn red.", onBack = onBack) {

        // ---- Seal state first: it is the most important signal ----
        SealBanner(reedOpen = telemetry?.reedSwitchOpen)

        // ---- Environment ----
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Temperature",
                value = telemetry?.let { "%.1f".format(it.temperature) } ?: "--",
                unit = "°C",
                icon = Icons.Default.Thermostat,
                alert = telemetry?.let { it.temperature !in limits.minTemp..limits.maxTemp } ?: false,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricTile(
                label = "Humidity",
                value = telemetry?.let { "%.1f".format(it.humidity) } ?: "--",
                unit = "%",
                icon = Icons.Default.WaterDrop,
                alert = telemetry?.let { it.humidity !in limits.minHum..limits.maxHum } ?: false,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Light",
                value = telemetry?.let { "%.0f".format(it.lightLux) } ?: "--",
                unit = "lux",
                icon = Icons.Default.LightMode,
                alert = telemetry?.let { it.lightLux > limits.maxLight } ?: false,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricTile(
                label = "Shock (Z)",
                value = telemetry?.let { "%.2f".format(it.accelZ) } ?: "--",
                unit = "g",
                icon = Icons.Default.Vibration,
                alert = telemetry?.let { it.accelZ > limits.maxShock } ?: false,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // ---- Full 3-axis acceleration ----
        Panel {
            Text("Acceleration, all axes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisValue("X", telemetry?.let { "%.2f".format(it.accelX) } ?: "--", Modifier.weight(1f))
                AxisValue("Y", telemetry?.let { "%.2f".format(it.accelY) } ?: "--", Modifier.weight(1f))
                AxisValue("Z", telemetry?.let { "%.2f".format(it.accelZ) } ?: "--", Modifier.weight(1f))
            }
        }

        // ---- Hardware console ----
        SectionTitle("Hardware console")
        val consoleScroll = rememberScrollState()
        LaunchedEffect(rawConsole) { consoleScroll.animateScrollTo(consoleScroll.maxValue) }
        Panel(
            containerColor = Color(0xFF0B1215),
            contentColor = Color(0xFF4ADE80),
            borderColor = HeroInkLine,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text("115200 baud, or BLE GATT", style = MaterialTheme.typography.labelSmall, color = OnHeroInkMuted)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                Text(
                    text = rawConsole.ifEmpty { "Waiting for serial or BLE output from the ESP32..." },
                    color = if (rawConsole.isEmpty()) OnHeroInkMuted else Color(0xFF4ADE80),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.verticalScroll(consoleScroll)
                )
            }
        }
    }
}

@Composable
private fun SealBanner(reedOpen: Boolean?) {
    val status = MaterialTheme.status
    val (container, accent, icon, title, detail) = when (reedOpen) {
        null -> BannerStyle(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Sensors, "Waiting for sensor data", "Connect to the ESP32 to see the package seal state.")
        true -> BannerStyle(status.dangerContainer, status.danger, Icons.Default.LockOpen, "Package opened", "Reed switch is open. Treat this shipment as tampered.")
        false -> BannerStyle(status.okContainer, status.ok, Icons.Default.Lock, "Package sealed", "Reed switch is closed. No tampering detected.")
    }
    Panel(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface, borderColor = accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, tint = Color.White, background = accent)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class BannerStyle(val container: Color, val accent: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val detail: String)

@Composable
private fun AxisValue(axis: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .then(Modifier)
            .padding(vertical = 2.dp)
    ) {
        Text(axis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.tabular())
            Text("g", modifier = Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
