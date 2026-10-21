package com.example.docscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.docscan.ui.components.DocScanBottomNavigation
import com.example.docscan.ui.components.FloatingScanButton
import com.example.docscan.ui.screens.CameraScanScreen
import com.example.docscan.ui.screens.MainHomeScreen
import com.example.docscan.ui.screens.PdfDetailScreen
import com.example.docscan.ui.screens.RewardsScreen
import com.example.docscan.ui.screens.SettingsScreen
import com.example.docscan.ui.theme.DocScanTheme
import com.example.docscan.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocScanApp()
                }
            }
        }
    }
}

@Composable
fun DocScanApp(
    viewModel: MainViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isCameraOpen by viewModel.isCameraOpen.collectAsState()
    val isDetailOpen by viewModel.isDetailOpen.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    when {
        isDetailOpen -> {
            BackHandler {
                viewModel.closeDetailScreen()
            }
            PdfDetailScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeDetailScreen() }
            )
        }
        isCameraOpen -> {
            BackHandler {
                viewModel.closeCamera()
            }
            CameraScanScreen(
                viewModel = viewModel,
                onClose = { viewModel.closeCamera() }
            )
        }
        else -> {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    DocScanBottomNavigation(
                        currentTab = currentTab,
                        onTabSelected = { tab -> viewModel.setCurrentTab(tab) },
                        onScanClick = { viewModel.startScan("BATCH") }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        "DOCUMENTOS" -> {
                            MainHomeScreen(
                                viewModel = viewModel,
                                onNavigateToRewards = { viewModel.setCurrentTab("RECOMPENSAS") }
                            )
                        }
                        "RECOMPENSAS" -> {
                            RewardsScreen(viewModel = viewModel)
                        }
                        "AJUSTES" -> {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }

                    // Floating Scan Pill Button docked at bottom right above bottom nav (as in mockups)
                    if (currentTab != "ESCANEAR") {
                        FloatingScanButton(
                            onClick = { viewModel.startScan("BATCH") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
