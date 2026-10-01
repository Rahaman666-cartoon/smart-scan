package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.utils.MlKitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentPreviewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val scannedPages by viewModel.scannedPages.collectAsState()
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) } // 0 = Image, 1 = Text
    var ocrTextMap by remember { mutableStateOf(mapOf<Int, String>()) }
    var isRunningOcr by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { scannedPages.size })

    // Auto-run OCR when switching to text tab
    LaunchedEffect(selectedTab, pagerState.currentPage) {
        if (selectedTab == 1 && scannedPages.isNotEmpty()) {
            val currentIndex = pagerState.currentPage
            if (!ocrTextMap.containsKey(currentIndex)) {
                isRunningOcr = true
                try {
                    val text = withContext<String>(Dispatchers.IO) {
                        com.example.utils.MlKitHelper.recognizeTextAndSuggestName(scannedPages[currentIndex]).first
                    }
                    val displayText = text.trim().ifEmpty { "No text found in this document." }
                    ocrTextMap = ocrTextMap.toMutableMap().apply { put(currentIndex, displayText) }
                } catch (e: Exception) {
                    Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    ocrTextMap = ocrTextMap.toMutableMap().apply { put(currentIndex, "OCR Failed.") }
                }
                isRunningOcr = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Image", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Text", fontWeight = FontWeight.Bold) }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Text(
                        text = "${if (scannedPages.isEmpty()) 0 else pagerState.currentPage + 1}/${scannedPages.size}",
                        modifier = Modifier.padding(end = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (selectedTab == 0) Color.Black else MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = if (selectedTab == 0) Color.Black else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { 
                        var name = "Scan_${System.currentTimeMillis()}"
                        viewModel.compileActiveSession(context, name)
                        Toast.makeText(context, "Saved as PDF", Toast.LENGTH_SHORT).show()
                        onNavigateToHome()
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("PDF")
                    }
                    Button(onClick = {
                        if (scannedPages.isNotEmpty()) {
                            try {
                                val currentBitmap = scannedPages[pagerState.currentPage]
                                val dir = File(context.getExternalFilesDir(null), "ScannedImages")
                                if (!dir.exists()) dir.mkdirs()
                                val file = File(dir, "Scan_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(file).use { out ->
                                    currentBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                }
                                Toast.makeText(context, "Saved JPG", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save JPG", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("JPG")
                    }
                    Button(onClick = {
                        if (scannedPages.isNotEmpty()) {
                            try {
                                val currentBitmap = scannedPages[pagerState.currentPage]
                                val cachePath = File(context.cacheDir, "images")
                                cachePath.mkdirs()
                                val file = File(cachePath, "shared_image.png")
                                val fileOutputStream = FileOutputStream(file)
                                currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                                fileOutputStream.close()
                                
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.applicationContext.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Image"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sharing", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Text("Share")
                    }
                    Button(onClick = onNavigateToHome, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                        Text("Exit")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFE0E0E0))) {
            if (scannedPages.isEmpty()) {
                Text("No pages scanned", modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    // Immersive Image View
                    if (selectedTab == 0) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            Image(
                                bitmap = scannedPages[page].asImageBitmap(),
                                contentDescription = "Page $page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        // Text View
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp).background(Color.White).padding(16.dp)) {
                            if (isRunningOcr && !ocrTextMap.containsKey(page)) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else {
                                Text(
                                    text = ocrTextMap[page] ?: "No text found.",
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
