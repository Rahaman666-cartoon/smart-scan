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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

                val scannerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                        scanResult?.pages?.forEach { page ->
                            try {
                                val inputStream = contentResolver.openInputStream(page.imageUri)
                                if (inputStream != null) {
                                    val outputDir = java.io.File(filesDir, "CanonicalPages")
                                    if (!outputDir.exists()) outputDir.mkdirs()
                                    val outputFile = java.io.File(outputDir, "page_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(5)}.jpg")
                                    val outputStream = java.io.FileOutputStream(outputFile)
                                    inputStream.copyTo(outputStream)
                                    inputStream.close()
                                    outputStream.close()
                                    viewModel.addScannedPage(android.net.Uri.fromFile(outputFile))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (scanResult?.pages?.isNotEmpty() == true) {
                            navController.navigate("document_preview")
                        }
                    }
                }

                val activity = this@MainActivity
                val launchScanner: () -> Unit = {
                    val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setPageLimit(30)
                        .setResultFormats(
                            com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                            com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                        )
                        .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()

                    com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options)
                        .getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            scannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                        }
                        .addOnFailureListener { e ->
                            android.widget.Toast.makeText(activity, "Scanner failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                }

                fun openPdfDocument(context: android.content.Context, doc: com.example.data.DocumentEntity) {
                    try {
                        val file = java.io.File(doc.filePath)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.applicationContext.packageName}.fileprovider",
                            file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not open document: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

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
                                        onNavigateToCamera = { launchScanner() },
                                        onNavigateToBarcode = { launchScanner() },
                                        onNavigateToDocument = { doc -> navController.navigate("saved_document/${doc.id}") },
                                        onNavigateToMerge = { navController.navigate("pdf_merge") },
                                        onNavigateToCrop = { /* Not used from Home */ }
                                    )
                                    1 -> DocumentListScreen(
                                        viewModel = viewModel,
                                        onNavigateToDetail = { doc -> navController.navigate("saved_document/${doc.id}") }
                                    )
                                    2 -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    composable(
                        "document_preview",
                        enterTransition = { slideIntoContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Up) + androidx.compose.animation.fadeIn() },
                        exitTransition = { slideOutOfContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Down) + androidx.compose.animation.fadeOut() }
                    ) {
                        com.example.ui.screens.DocumentPreviewScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToHome = {
                                navController.navigate("dashboard") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        "saved_document/{documentId}",
                        arguments = listOf(androidx.navigation.navArgument("documentId") { type = androidx.navigation.NavType.LongType })
                    ) { backStackEntry ->
                        val documentId = backStackEntry.arguments?.getLong("documentId") ?: 0L
                        var document by remember { mutableStateOf<com.example.data.DocumentEntity?>(null) }
                        var pages by remember { mutableStateOf<List<com.example.data.DocumentPageEntity>>(emptyList()) }
                        
                        LaunchedEffect(documentId) {
                            document = viewModel.repository.getDocumentById(documentId)
                            pages = viewModel.repository.getPagesForDocument(documentId)
                        }

                        if (document != null) {
                            com.example.ui.screens.SavedDocumentScreen(
                                document = document!!,
                                pages = pages,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onEditSession = {
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
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
