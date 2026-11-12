package com.example.cargocanary.presentation.ui.rfid

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.status

@Composable
fun RfidArmingScreen(
    viewModel: CargoViewModel,
    onBack: (() -> Unit)? = null
) {
    val rfidStatus by viewModel.rfidScanStatus.collectAsState()
    val isArmed by viewModel.isArmed.collectAsState()
    val shipment by viewModel.currentShipment.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val pendingTag by viewModel.pendingRfidTag.collectAsState()
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    // Confirmation Alert Dialog when a new RFID card is scanned
    if (pendingTag != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRfidTagConfirmation() },
            title = { Text("Confirm Scanned RFID Tag") },
            text = {
                Text("Scanned RFID Card UID: $pendingTag\n\nDo you want to confirm and use this RFID Tag for this shipment?")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRfidTag(pendingTag!!) }) {
                    Text("Confirm & Use Tag")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissRfidTagConfirmation() }) {
                    Text("Cancel / Scan Different Tag")
                }
            }
        )
    }

    CanaryScreen(title = "Arm shipment", subtitle = "Hold the RFID tag to the RC522 reader to start logging.", onBack = onBack) {

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

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            ScanBadge(armed = isArmed)
            Spacer(Modifier.height(8.dp))

            Text(
                if (isArmed) "Shipment armed" else "Ready to arm",
                style = MaterialTheme.typography.headlineMedium,
                color = if (isArmed) MaterialTheme.status.ok else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                rfidStatus,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            StatusPill(
                text = shipment.journeyId.ifBlank { "No active journey" },
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (shipment.rfidUid.isNotBlank()) {
            Panel {
                Text("Bound tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    shipment.rfidUid,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        PrimaryAction(
            text = if (!isConnected) "Connect ESP32 first" else if (isArmed) "Scan a different tag" else "Scan RFID tag",
            icon = Icons.Default.Nfc,
            enabled = isConnected,
            onClick = { viewModel.scanRfidTag() }
        )
    }
}

/** Pulsing rings invite a scan; once armed the motion stops and a check confirms it. */
@Composable
private fun ScanBadge(armed: Boolean) {
    val accent = if (armed) MaterialTheme.status.ok else MaterialTheme.colorScheme.primary
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        if (!armed) PulseRings(accent)
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (armed) Icons.Default.Check else Icons.Default.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

@Composable
private fun PulseRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse"
    )
    Canvas(Modifier.fillMaxSize()) {
        val maxR = size.minDimension / 2f
        listOf(0f, 0.5f).forEach { offset ->
            val p = (pulse + offset) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.4f),
                radius = maxR * (0.5f + 0.5f * p),
                style = Stroke(2.dp.toPx())
            )
        }
    }
}
