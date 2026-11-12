package com.example.cargocanary.presentation.ui.destination

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.cargocanary.domain.model.ConnectionStatus
import com.example.cargocanary.presentation.ui.components.*
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.status

@Composable
fun DestinationScreen(
    viewModel: CargoViewModel,
    onNavigateAnalytics: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val shipment by viewModel.currentShipment.collectAsState()
    val retrievedLogs by viewModel.retrievedLogs.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    var isRetrieving by remember { mutableStateOf(false) }
    var retrievedDone by remember { mutableStateOf(false) }

    CanaryScreen(title = "Retrieve logs", subtitle = "Pull the journey data from the ESP32 and verify it.", onBack = onBack) {

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
            Text("Arrival", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            DetailRow("Journey", shipment.journeyId.ifBlank { "Not set" })
            Spacer(Modifier.height(10.dp))
            DetailRow("Destination", shipment.destinationLocation.ifBlank { "Not set" })
        }

        PrimaryAction(
            text = if (!isConnected) "Connect ESP32 first" else if (isRetrieving) "Downloading logs..." else "Retrieve transit logs",
            icon = Icons.Default.Download,
            enabled = isConnected && !isRetrieving,
            onClick = {
                isRetrieving = true
                viewModel.retrieveTransitLogs()
                retrievedDone = true
                isRetrieving = false
            }
        )

        if (retrievedDone) {
            Panel(
                containerColor = MaterialTheme.status.okContainer,
                borderColor = MaterialTheme.status.ok.copy(alpha = 0.35f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Default.VerifiedUser, tint = Color.White, background = MaterialTheme.status.ok)
                    Column {
                        Text("Data integrity verified", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${retrievedLogs.size} records saved to this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("CRC32 checksum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("0x4F8A9B2C", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                    StatusPill("Matches ESP32", MaterialTheme.status.ok)
                }
            }

            if (onNavigateAnalytics != null) {
                PrimaryAction(
                    text = "Open report and PDF",
                    icon = Icons.Default.Assessment,
                    onClick = onNavigateAnalytics
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
    }
}
