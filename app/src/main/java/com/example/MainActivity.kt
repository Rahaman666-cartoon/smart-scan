package com.example

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.SmartScanTheme
import com.example.utils.OpenCvImageProcessor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize OpenCV image processing runtime
        OpenCvImageProcessor.init(applicationContext)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.settingsManager.themeMode.collectAsState()
            val themeAccent by viewModel.settingsManager.themeAccent.collectAsState()

            SmartScanTheme(themeMode = themeMode, themeAccent = themeAccent) {
                
                // Handle background system logs or snackbar notifications
                val operationMessage by viewModel.fileOperationMessage.collectAsState()
                LaunchedEffect(operationMessage) {
                    operationMessage?.let { msg ->
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                        viewModel.clearFileMessage()
                    }
                }

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) + fadeIn() },
                    exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left) + fadeOut() },
                    popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) + fadeIn() },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) + fadeOut() }
                ) {
                    composable(
                        "dashboard",
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() }
                    ) {
                        var currentDashboardTab by remember { mutableStateOf(0) }
                        
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                Column {
                                    HorizontalDivider(color = Color(0xFFE1E2EC), thickness = 1.dp)
                                    NavigationBar(
                                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                                        containerColor = Color(0xFFF0F4F9),
                                        tonalElevation = 0.dp
                                    ) {
                                        NavigationBarItem(
                                            selected = currentDashboardTab == 0,
                                            onClick = { currentDashboardTab = 0 },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                            label = { Text("Home") },
                                            colors = NavigationBarItemDefaults.colors()
                                        )
                                        NavigationBarItem(
                                            selected = currentDashboardTab == 1,
                                            onClick = { currentDashboardTab = 1 },
                                            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Library") },
                                            label = { Text("Library") },
                                            colors = NavigationBarItemDefaults.colors()
                                        )
                                        NavigationBarItem(
                                            selected = currentDashboardTab == 2,
                                            onClick = { currentDashboardTab = 2 },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                            label = { Text("Settings") },
                                            colors = NavigationBarItemDefaults.colors()
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                when (currentDashboardTab) {
                                    0 -> HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToCamera = { navController.navigate("camera") },
                                        onNavigateToBarcode = { navController.navigate("camera") },
                                        onNavigateToDocument = { /* No primitive route for this yet, handle later */ },
                                        onNavigateToMerge = { navController.navigate("pdf_merge") },
                                        onNavigateToCrop = { /* Not used from Home */ }
                                    )
                                    1 -> DocumentListScreen(
                                        viewModel = viewModel,
                                        onNavigateToDetail = { /* No primitive route for this yet */ }
                                    )
                                    2 -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    composable("camera") {
                        CameraScanScreen(
                            viewModel = viewModel,
                            onCaptured = { bitmap ->
                                // Cache bitmap in viewModel or use a shared state holder for simplicity
                                viewModel.addScannedPage(bitmap)
                                navController.navigate("crop")
                            },
                            onBarcodeScanned = { barcode ->
                                navController.popBackStack("dashboard", false)
                            },
                            onClose = { navController.popBackStack() }
                        )
                    }

                    composable("crop") {
                        // Assuming the bitmap is the last scanned page in viewModel
                        val pages by viewModel.scannedPages.collectAsState()
                        val lastBitmap = pages.lastOrNull()
                        if (lastBitmap != null) {
                            PerspectiveCropScreen(
                                bitmap = lastBitmap,
                                onCropped = { finalWarpedBitmap ->
                                    viewModel.updateScannedPage(pages.size - 1, finalWarpedBitmap)
                                    navController.popBackStack("dashboard", false)
                                },
                                onCancel = {
                                    viewModel.removeScannedPage(pages.size - 1)
                                    navController.popBackStack()
                                }
                            )
                        } else {
                            navController.popBackStack()
                        }
                    }

                    composable("pdf_merge") {
                        PdfMergeScreen(
                            viewModel = viewModel,
                            onClose = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
