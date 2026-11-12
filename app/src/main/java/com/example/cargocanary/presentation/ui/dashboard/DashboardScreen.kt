package com.example.cargocanary.presentation.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cargocanary.domain.model.ConnectionMode
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.ui.navigation.Screen
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.*
import androidx.compose.runtime.collectAsState

@Composable
fun DashboardScreen(
    viewModel: CargoViewModel,
    onNavigate: (String) -> Unit
) {
    val mode by viewModel.currentMode.collectAsState()
    val status by viewModel.connectionStatus.collectAsState()
    val battery by viewModel.batteryLevel.collectAsState()
    val storage by viewModel.storageCapacity.collectAsState()
    val shipment by viewModel.currentShipment.collectAsState()
    val telemetry by viewModel.telemetryFlow.collectAsState()
    val isArmed by viewModel.isArmed.collectAsState()

    CanaryScreen(title = "Cargo Canary", subtitle = "Shipment integrity and hardware hub") {

        // ---- Hero: the shipment and its route ----
        val (statusColor, statusLabel) = when (status) {
            ConnectionStatus.CONNECTED -> HeroOk to "Connected"
            ConnectionStatus.CONNECTING -> HeroWarn to "Connecting"
            else -> HeroDanger to status.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        Panel(
            shape = RoundedCornerShape(28.dp),
            containerColor = HeroInk,
            contentColor = OnHeroInk,
            borderColor = HeroInkLine,
            contentPadding = PaddingValues(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    shipment.journeyId.ifBlank { "No active journey" },
                    style = MaterialTheme.typography.labelLarge,
                    color = OnHeroInkMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(statusLabel, statusColor)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (isArmed) "Armed and logging" else "Waiting to arm",
                style = MaterialTheme.typography.displaySmall,
                color = if (isArmed) HeroOk else Canary
            )
            if (shipment.cargoName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${shipment.cargoName}, ${shipment.cargoCategory}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnHeroInkMuted
                )
            }
            Spacer(Modifier.height(26.dp))
            RouteLine(shipment.sourceLocation, shipment.destinationLocation)
        }

        // ---- Connection ----
        Panel {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text("Choose how this phone talks to the ESP32.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            ModeSelector(selected = mode, onSelect = { viewModel.setConnectionMode(it) })
            Spacer(Modifier.height(14.dp))
            if (status == ConnectionStatus.CONNECTED) {
                OutlinedButton(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            } else {
                Button(
                    onClick = { viewModel.connect() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Connect to ESP32") }
            }
        }

        // ---- Device health ----
        val isConnected = status == ConnectionStatus.CONNECTED
        val batteryText = if (isConnected && battery != null) "$battery%" else "--"
        val storageText = if (isConnected && storage != null) "$storage%" else "--"
        val batteryFraction = if (isConnected && battery != null) battery.asPercentFraction() else 0f
        val storageFraction = if (isConnected && storage != null) storage.asPercentFraction() else 0f

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceMeter(
                label = "Battery",
                value = batteryText,
                icon = Icons.Default.BatteryFull,
                fraction = batteryFraction,
                color = when {
                    batteryFraction < 0.2f -> MaterialTheme.status.danger
                    batteryFraction < 0.4f -> MaterialTheme.status.warn
                    else -> MaterialTheme.status.ok
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            DeviceMeter(
                label = "Flash used",
                value = storageText,
                icon = Icons.Default.Storage,
                fraction = storageFraction,
                color = when {
                    storageFraction > 0.85f -> MaterialTheme.status.danger
                    storageFraction > 0.7f -> MaterialTheme.status.warn
                    else -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // ---- Live readings ----
        Panel {
            Text("Live readings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Readout("Temperature", telemetry?.let { "%.1f".format(it.temperature) } ?: "--", "°C", Modifier.weight(1f))
                Readout("Humidity", telemetry?.let { "%.1f".format(it.humidity) } ?: "--", "%", Modifier.weight(1f))
                Readout("Shock", telemetry?.let { "%.2f".format(it.accelZ) } ?: "--", "g", Modifier.weight(1f))
            }
        }

        // ---- Workflow shortcuts ----
        SectionTitle("Shipment workflow")
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Screen.Config.icon, "Create transit", "Set route and limits", { onNavigate(Screen.Config.route) }, Modifier.weight(1f).fillMaxHeight())
            ActionTile(Screen.Rfid.icon, "Arm tag", "Bind an RFID tag", { onNavigate(Screen.Rfid.route) }, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Screen.Destination.icon, "Retrieve logs", "Download at destination", { onNavigate(Screen.Destination.route) }, Modifier.weight(1f).fillMaxHeight())
            ActionTile(Screen.Analytics.icon, "View report", "Charts and PDF", { onNavigate(Screen.Analytics.route) }, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Origin, dashed line, destination. The line is the visual identity of the hero. */
@Composable
private fun RouteLine(source: String, destination: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            source.ifBlank { "Origin" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = OnHeroInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Canvas(Modifier.weight(0.7f).height(14.dp).padding(horizontal = 8.dp)) {
            val cy = size.height / 2f
            val r = 5.dp.toPx()
            val strokeW = 2.dp.toPx()
            drawCircle(Canary, radius = r - strokeW / 2f, center = Offset(r, cy), style = Stroke(strokeW))
            drawLine(
                color = OnHeroInkMuted.copy(alpha = 0.7f),
                start = Offset(r * 2 + 6.dp.toPx(), cy),
                end = Offset(size.width - r * 2 - 6.dp.toPx(), cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
            )
            drawCircle(Canary, radius = r, center = Offset(size.width - r, cy))
        }
        Text(
            destination.ifBlank { "Destination" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = OnHeroInk,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModeSelector(selected: ConnectionMode, onSelect: (ConnectionMode) -> Unit) {
    val options = listOf(
        Triple(ConnectionMode.USB_SERIAL, "USB Serial", Icons.Default.Usb),
        Triple(ConnectionMode.BLE, "Bluetooth BLE", Icons.Default.Bluetooth)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = mode == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "modeBackground"
            )
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DeviceMeter(label: String, value: String, icon: ImageVector, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(icon)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.tabular())
        Spacer(Modifier.height(10.dp))
        MeterBar(fraction, color)
    }
}

@Composable
private fun Readout(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.tabular())
            Text(unit, modifier = Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, title: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        IconBadge(icon)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
