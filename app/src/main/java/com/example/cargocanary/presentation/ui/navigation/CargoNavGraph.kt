package com.example.cargocanary.presentation.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Hub", Icons.Default.Dashboard)
    object Telemetry : Screen("telemetry", "Live", Icons.Default.Sensors)
    object Config : Screen("config", "Setup", Icons.Default.Settings)
    object Rfid : Screen("rfid", "Arm", Icons.Default.Nfc)
    object Destination : Screen("destination", "Retrieve", Icons.Default.Download)
    object Analytics : Screen("analytics", "Report", Icons.Default.Assessment)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Telemetry,
    Screen.Config,
    Screen.Rfid,
    Screen.Destination,
    Screen.Analytics
)
