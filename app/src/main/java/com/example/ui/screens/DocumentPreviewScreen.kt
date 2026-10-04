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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentPreviewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onAddPage: () -> Unit,
    onRetakePage: (Int) -> Unit
) {
    val context = LocalContext.current
    val scannedPages by viewModel.scannedPages.collectAsState()
    val previewingExistingDoc by viewModel.previewingExistingDoc.collectAsState()
    val scope = rememberCoroutineScope()
    
    var isTextMode by remember { mutableStateOf(false) }
    var ocrTextMap by remember { mutableStateOf(mapOf<Int, String>()) }
    var isRunningOcr by remember { mutableStateOf(false) }
    
    val initialName = remember(previewingExistingDoc) {
        previewingExistingDoc?.name ?: "New Doc ${SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault()).format(Date())}"
    }
    var documentName by remember { mutableStateOf(initialName) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    
    var showShareSheet by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showSignatureMenu by remember { mutableStateOf(false) }

    var pendingSignature by remember { mutableStateOf<Bitmap?>(null) }
    var signatureOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var signatureScale by remember { mutableStateOf(1f) }
    var signaturePage by remember { mutableStateOf(-1) }

    val pagerState = rememberPagerState(pageCount = { scannedPages.size })
    
    fun burnSignature(uri: Uri, sigBitmap: Bitmap, boxWidth: Float, boxHeight: Float, offset: androidx.compose.ui.geometry.Offset, effectiveScale: Float, pageIndex: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val originalBitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
                
                val imgWidth = originalBitmap.width.toFloat()
                val imgHeight = originalBitmap.height.toFloat()
                
                val imageAspectRatio = imgWidth / imgHeight
                val boxAspectRatio = boxWidth / boxHeight
                
                var renderedWidth = boxWidth
                var renderedHeight = boxHeight
                
                if (imageAspectRatio > boxAspectRatio) {
                    renderedHeight = boxWidth / imageAspectRatio
                } else {
                    renderedWidth = boxHeight * imageAspectRatio
                }
                
                val scaleX = imgWidth / renderedWidth
                val scaleY = imgHeight / renderedHeight
                
                val imageCenterX = imgWidth / 2f
                val imageCenterY = imgHeight / 2f
                
                val imageOffsetX = offset.x * scaleX
                val imageOffsetY = offset.y * scaleY
                
                val sigCenterX = imageCenterX + imageOffsetX
                val sigCenterY = imageCenterY + imageOffsetY
                
                val targetSigWidth = sigBitmap.width * effectiveScale * scaleX
                val targetSigHeight = sigBitmap.height * effectiveScale * scaleY
                
                val left = sigCenterX - targetSigWidth / 2f
                val top = sigCenterY - targetSigHeight / 2f
                
                val finalBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(finalBitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                
                val destRect = RectF(left, top, left + targetSigWidth, top + targetSigHeight)
                canvas.drawBitmap(sigBitmap, null, destRect, Paint().apply { isFilterBitmap = true })
                
                val outDir = File(context.cacheDir, "Signatures")
                outDir.mkdirs()
                val outFile = File(outDir, "signed_page_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use {
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                
                val newUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", outFile)
                
                withContext(Dispatchers.Main) {
                    viewModel.updateScannedPage(pageIndex, newUri)
                    pendingSignature = null
                    Toast.makeText(context, "Signature applied!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error applying signature: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && scannedPages.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val cachePath = File(context.cacheDir, "temp_pdf")
                    cachePath.mkdirs()
                    val tempFile = File(cachePath, "export.pdf")
                    val quality = viewModel.settingsManager.pdfQuality.value.compression
                    com.example.utils.PdfUtils.convertUrisToPdf(context, scannedPages, quality, tempFile)
                    
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to Device", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    val importSignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                    withContext(Dispatchers.Main) {
                        pendingSignature = bitmap
                        signatureOffset = androidx.compose.ui.geometry.Offset.Zero
                        signatureScale = 1f
                        signaturePage = pagerState.currentPage
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    // Auto-run OCR when switching to text tab
    LaunchedEffect(isTextMode, pagerState.currentPage) {
        if (isTextMode && scannedPages.isNotEmpty()) {
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
    
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (renameInput.isNotBlank()) documentName = renameInput
                    showRenameDialog = false 
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            containerColor = Color.White
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                
                ListItem(
                    headlineContent = { Text("Share PDF Document") },
                    supportingContent = { Text("Share the entire document as a PDF") },
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFD32F2F)) },
                    modifier = Modifier.clickable {
                        showShareSheet = false
                        if (scannedPages.isNotEmpty()) {
                            scope.launch {
                                Toast.makeText(context, "Preparing PDF for Share...", Toast.LENGTH_SHORT).show()
                                try {
                                    val cachePath = File(context.cacheDir, "shared_docs")
                                    cachePath.mkdirs()
                                    val file = File(cachePath, "shared_document.pdf")
                                    val quality = viewModel.settingsManager.pdfQuality.value.compression
                                    com.example.utils.PdfUtils.convertUrisToPdf(context, scannedPages, quality, file)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", file)
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Save to Device (PDF)") },
                    supportingContent = { Text("Save the entire document to Downloads") },
                    leadingContent = { Icon(Icons.Default.Download, null, tint = Color(0xFF388E3C)) },
                    modifier = Modifier.clickable {
                        showShareSheet = false
                        createPdfLauncher.launch("$documentName.pdf")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text("Share JPG Image") },
                    supportingContent = { Text("Share the current page only") },
                    leadingContent = { Icon(Icons.Default.Image, null, tint = Color(0xFF1976D2)) },
                    modifier = Modifier.clickable {
                        showShareSheet = false
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
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", file)
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Save to Gallery (JPG)") },
                    supportingContent = { Text("Save current page to Photos") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFFFBC02D)) },
                    modifier = Modifier.clickable {
                        showShareSheet = false
                        if (scannedPages.isNotEmpty()) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val currentUri = scannedPages[pagerState.currentPage]
                                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, currentUri)
                                    val bitmap = android.graphics.ImageDecoder.decodeBitmap(source)
                                    
                                    val filename = "SmartScan_${System.currentTimeMillis()}.jpg"
                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SmartScan")
                                        }
                                    }
                                    
                                    val resolver = context.contentResolver
                                    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        resolver.openOutputStream(uri)?.use {
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                renameInput = documentName
                                showRenameDialog = true
                            }
                    ) {
                        Text(
                            text = documentName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(18.dp), tint = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isTextMode = !isTextMode }) {
                        Icon(
                            if (isTextMode) Icons.Default.Image else Icons.Default.TextFields,
                            contentDescription = "Toggle Text/Image View",
                            tint = if (isTextMode) MaterialTheme.colorScheme.primary else Color(0xFF1E1E1E)
                        )
                    }
                    Text(
                        text = "${if (scannedPages.isEmpty()) 0 else pagerState.currentPage + 1}/${scannedPages.size}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E1E)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E1E1E),
                    navigationIconContentColor = Color(0xFF1E1E1E),
                    actionIconContentColor = Color(0xFF1E1E1E)
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.White,
                contentColor = Color(0xFF1E1E1E),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    BottomBarButton(
                        icon = Icons.Default.AddBox, 
                        label = "Add", 
                        onClick = onAddPage
                    )
                    
                    BottomBarButton(
                        icon = Icons.Default.Replay, 
                        label = "Retake", 
                        onClick = {
                            if (scannedPages.isNotEmpty()) {
                                onRetakePage(pagerState.currentPage)
                            }
                        }
                    )
                    
                    BottomBarButton(
                        icon = Icons.Default.Create, 
                        label = "Sign", 
                        onClick = { showSignatureMenu = true }
                    )
                    
                    BottomBarButton(
                        icon = Icons.Default.DeleteOutline, 
                        label = "Delete", 
                        onClick = {
                            if (scannedPages.isNotEmpty()) {
                                viewModel.removeScannedPage(pagerState.currentPage)
                                if (viewModel.scannedPages.value.isEmpty()) {
                                    onNavigateBack()
                                }
                            }
                        }
                    )
                    
                    BottomBarButton(
                        icon = Icons.Default.IosShare, 
                        label = "Export", 
                        onClick = { showShareSheet = true }
                    )
                    
                    BottomBarButton(
                        icon = Icons.Default.Check, 
                        label = "Done", 
                        onClick = {
                            if (previewingExistingDoc != null) {
                                viewModel.compileActiveSession(context, documentName)
                                Toast.makeText(context, "Saved Successfully", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            } else {
                                viewModel.compileActiveSession(context, documentName)
                                Toast.makeText(context, "Saved Successfully", Toast.LENGTH_SHORT).show()
                                onNavigateToHome()
                            }
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF121212))) { 
            
            if (scannedPages.isEmpty()) {
                Text("No pages scanned", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (!isTextMode) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val boxWidth = constraints.maxWidth.toFloat()
                            val boxHeight = constraints.maxHeight.toFloat()
                            
                            coil.compose.AsyncImage(
                                model = scannedPages[page],
                                contentDescription = "Page $page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            
                            if (pendingSignature != null && signaturePage == page) {
                                val sigWidth = pendingSignature!!.width
                                val sigHeight = pendingSignature!!.height
                                val initialScale = (boxWidth * 0.4f) / sigWidth
                                val effectiveScale = initialScale * signatureScale
                                
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        bitmap = pendingSignature!!.asImageBitmap(),
                                        contentDescription = "Pending Signature",
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset { 
                                                androidx.compose.ui.unit.IntOffset(
                                                    signatureOffset.x.roundToInt(), 
                                                    signatureOffset.y.roundToInt()
                                                ) 
                                            }
                                            .androidx.compose.ui.graphics.graphicsLayer(
                                                scaleX = effectiveScale,
                                                scaleY = effectiveScale
                                            )
                                            .pointerInput(Unit) {
                                                androidx.compose.foundation.gestures.detectTransformGestures { _, pan, zoom, _ ->
                                                    signatureScale *= zoom
                                                    signatureOffset += pan
                                                }
                                            }
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 32.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        FloatingActionButton(
                                            onClick = { pendingSignature = null },
                                            containerColor = Color.White
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                                        }
                                        FloatingActionButton(
                                            onClick = { 
                                                burnSignature(
                                                    uri = scannedPages[page], 
                                                    sigBitmap = pendingSignature!!,
                                                    boxWidth = boxWidth,
                                                    boxHeight = boxHeight,
                                                    offset = signatureOffset,
                                                    effectiveScale = effectiveScale,
                                                    pageIndex = page
                                                )
                                            },
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Apply")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
                            if (isRunningOcr && !ocrTextMap.containsKey(page)) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF1E1E1E))
                            } else {
                                val scrollState = rememberScrollState()
                                Text(
                                    text = ocrTextMap[page] ?: "No text found.",
                                    color = Color(0xFF1E1E1E),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                )
                                
                                FloatingActionButton(
                                    onClick = {
                                        val textToCopy = ocrTextMap[page] ?: ""
                                        if (textToCopy.isNotBlank() && textToCopy != "OCR Failed.") {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Scanned Text", textToCopy)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showSignatureDialog) {
        DrawSignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureReady = { bitmap ->
                showSignatureDialog = false
                pendingSignature = bitmap
                signatureOffset = androidx.compose.ui.geometry.Offset.Zero
                signatureScale = 1f
                signaturePage = pagerState.currentPage
            }
        )
    }
    
    if (showSignatureMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSignatureMenu = false },
            containerColor = Color.White
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Signature function", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                
                ListItem(
                    headlineContent = { Text("Add signature") },
                    leadingContent = { Icon(Icons.Default.Create, null, tint = Color.Black) },
                    modifier = Modifier.clickable {
                        showSignatureMenu = false
                        showSignatureDialog = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text("Scan your signature") },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = Color.Black) },
                    modifier = Modifier.clickable {
                        showSignatureMenu = false
                        Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text("Import from Album") },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = Color.Black) },
                    modifier = Modifier.clickable {
                        showSignatureMenu = false
                        importSignatureLauncher.launch("image/*")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color(0xFF1E1E1E)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
