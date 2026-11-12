package com.example.cargocanary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cargocanary.presentation.ui.analytics.AnalyticsScreen
import com.example.cargocanary.presentation.ui.config.ShipmentConfigScreen
import com.example.cargocanary.presentation.ui.dashboard.DashboardScreen
import com.example.cargocanary.presentation.ui.destination.DestinationScreen
import com.example.cargocanary.presentation.ui.navigation.Screen
import com.example.cargocanary.presentation.ui.navigation.bottomNavItems
import com.example.cargocanary.presentation.ui.rfid.RfidArmingScreen
import com.example.cargocanary.presentation.ui.telemetry.TelemetryScreen
import com.example.cargocanary.presentation.viewmodel.CargoViewModel
import com.example.cargocanary.ui.theme.CargocanaryTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CargoViewModel by viewModels {
        CargoViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CargocanaryTheme {
                CargoMainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CargoMainAppScreen(viewModel: CargoViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val promptMessage by viewModel.uiPromptMessage.collectAsState()

    // Hardware & Transit State Alert Dialog Pop-Up
    if (promptMessage != null) {
        val isArrivedPrompt = promptMessage!!.contains("ARRIVED AT DESTINATION")
        AlertDialog(
            onDismissRequest = { viewModel.clearUiPromptMessage() },
            title = { Text(if (isArrivedPrompt) "Transit State Warning" else "Hardware Alert", fontWeight = FontWeight.Bold) },
            text = { Text(promptMessage!!) },
            confirmButton = {
                Button(onClick = {
                    if (isArrivedPrompt) {
                        viewModel.markArrivedAndRetrieve()
                    } else {
                        viewModel.clearUiPromptMessage()
                    }
                }) {
                    Text(if (isArrivedPrompt) "Mark Arrived & Download" else "OK")
                }
            },
            dismissButton = if (isArrivedPrompt) {
                {
                    OutlinedButton(onClick = { viewModel.clearUiPromptMessage() }) {
                        Text("Keep Logging in Transit")
                    }
                }
            } else null
        )
    }

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(screen.route) },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            alwaysShowLabel = false,
                            label = { Text(screen.title, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel = viewModel, onNavigate = navigateTo)
            }
            composable(Screen.Telemetry.route) {
                TelemetryScreen(viewModel = viewModel)
            }
            composable(Screen.Config.route) {
                ShipmentConfigScreen(viewModel = viewModel)
            }
            composable(Screen.Rfid.route) {
                RfidArmingScreen(viewModel = viewModel)
            }
            composable(Screen.Destination.route) {
                DestinationScreen(
                    viewModel = viewModel,
                    onNavigateAnalytics = { navigateTo(Screen.Analytics.route) }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(viewModel = viewModel)
            }
        }
    }
}
