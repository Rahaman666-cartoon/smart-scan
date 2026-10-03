package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.MainViewModel
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
                        val uri = scannedPages[currentIndex]
                        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        com.example.utils.MlKitHelper.recognizeTextAndSuggestName(bitmap).first
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
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { 
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Image", fontWeight = FontWeight.Bold) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.Gray
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Text", fontWeight = FontWeight.Bold) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.Gray
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
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
            ) {
                // Divider
                Divider(color = Color.DarkGray, thickness = 1.dp)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    // Share PDF Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (scannedPages.isNotEmpty()) {
                                scope.launch {
                                    Toast.makeText(context, "Preparing PDF for Share...", Toast.LENGTH_SHORT).show()
                                    try {
                                        val cachePath = File(context.cacheDir, "shared_docs")
                                        cachePath.mkdirs()
                                        val file = File(cachePath, "shared_document.pdf")
                                        com.example.utils.PdfUtils.convertUrisToPdf(context, scannedPages, 100, file)
                                        
                                        val uri = FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", file)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error sharing PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF", tint = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share PDF", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    // Share JPG Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (scannedPages.isNotEmpty()) {
                                try {
                                    val currentUri = scannedPages[pagerState.currentPage]
                                    val cachePath = File(context.cacheDir, "images")
                                    cachePath.mkdirs()
                                    val file = File(cachePath, "shared_image.jpg")
                                    val inputStream = context.contentResolver.openInputStream(currentUri)
                                    val fileOutputStream = FileOutputStream(file)
                                    inputStream?.copyTo(fileOutputStream)
                                    inputStream?.close()
                                    fileOutputStream.close()
                                    
                                    val uri = FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share JPG"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sharing JPG", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Share JPG", tint = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share JPG", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    // Extract Text or Copy Text Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (selectedTab == 0) {
                                selectedTab = 1
                            } else {
                                val textToCopy = ocrTextMap[pagerState.currentPage] ?: ""
                                if (textToCopy.isNotBlank() && textToCopy != "OCR Failed.") {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Scanned Text", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (selectedTab == 0) Icons.Default.TextFields else Icons.Default.ContentCopy,
                            contentDescription = if (selectedTab == 0) "Extract Text" else "Copy Text",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (selectedTab == 0) "Extract Text" else "Copy Text",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Main Save Button
                    Button(
                        onClick = { 
                            var name = "Scan_${System.currentTimeMillis()}"
                            viewModel.compileActiveSession(context, name)
                            Toast.makeText(context, "Saved Successfully", Toast.LENGTH_SHORT).show()
                            onNavigateToHome()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.Black)) {
            
            if (scannedPages.isEmpty()) {
                Text("No pages scanned", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (selectedTab == 0) {
                        // Immersive Image View
                        // NO extra white padding or gray containers! Just pure black and the scaled image.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            coil.compose.AsyncImage(
                                model = scannedPages[page],
                                contentDescription = "Page $page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        // Text View
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(16.dp)
                        ) {
                            if (isRunningOcr && !ocrTextMap.containsKey(page)) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                            } else {
                                val scrollState = rememberScrollState()
                                Text(
                                    text = ocrTextMap[page] ?: "No text found.",
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
