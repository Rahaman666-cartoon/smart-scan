package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocumentEntity
import com.example.ui.MainViewModel
import com.example.utils.formatSize
import com.example.utils.getBarcodeFormatName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToBarcode: () -> Unit,
    onNavigateToDocument: (com.example.data.DocumentWithPages) -> Unit,
    onNavigateToCrop: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.recentDocuments.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()
    val barcodeResult by viewModel.barcodeResult.collectAsState()
    val scope = rememberCoroutineScope()

    var showSaveDialog by remember { mutableStateOf(false) }
    var documentNameInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var showImportGuideDialog by remember { mutableStateOf(false) }

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 4..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }
                    }
                    if (bitmap != null) {
                        onNavigateToCrop(bitmap)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    val qrGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }
                    }
                    if (bitmap != null) {
                        viewModel.scanQrOrBarcode(bitmap)
                    }
                } catch (e: Exception) {}
            }
        }
    }



    // A beautiful gradient brush for the header
    val headerGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. Stunning Header ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerGradient)
                    .padding(horizontal = 24.dp, vertical = 40.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Ready to scan?",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Glassmorphism Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your documents...", color = Color.White.copy(alpha=0.8f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- 2. Floating Action Grid ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Massive Scan Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.primary)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onNavigateToCamera() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = "Scan Document",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Smart Scan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Auto-detect edges",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    // Import Image Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f))
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { showImportGuideDialog = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFE8F5E9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Import Image",
                                    tint = Color(0xFF43A047),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Import Image",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "From gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // QR Code Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Scan QR
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.05f))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onNavigateToBarcode() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFF3E5F5), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF8E24AA))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scan QR", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Import QR
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.05f))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { qrGalleryLauncher.launch("image/*") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFFFF3E0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFE65100))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Import QR", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }



        // --- 3. Recent Documents (Sleek List) ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Scans",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* navigate to library */ }
                )
            }
        }

        val filteredDocs = if (searchQuery.isBlank()) documents else documents.filter { it.document.name.contains(searchQuery, ignoreCase = true) }

        if (filteredDocs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isBlank()) "No scans yet" else "No results found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }
        } else {
            val showList = if (searchQuery.isBlank()) filteredDocs.take(5) else filteredDocs
            items(showList) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToDocument(doc) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val thumbnailPath = doc.pages.firstOrNull()?.canonicalFilePath
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailPath != null) {
                                coil.compose.AsyncImage(
                                    model = thumbnailPath,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.document.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dateFormatter.format(Date(doc.document.createdTime))}  •  ${formatSize(doc.document.pdfSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(doc.document) }) {
                            Icon(
                                imageVector = if (doc.document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (doc.document.isFavorite) Color(0xFFFFC107) else Color.LightGray
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) } // padding for bottom nav
        }
    }

    // Dialogs...
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Document", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = documentNameInput,
                    onValueChange = { documentNameInput = it },
                    label = { Text("Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.compileActiveSession(context, documentNameInput)
                    showSaveDialog = false
                }) { Text("Save Document") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (barcodeResult != null) {
        val result = barcodeResult!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearBarcodeResult() },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Determine icon and title based on valueType
                val (icon, title, actionText) = when (result.valueType) {
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_URL -> Triple(Icons.Default.Link, "Website Link", "Open in Browser")
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_WIFI -> Triple(Icons.Default.Wifi, "WiFi Network", "Copy Password")
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_CONTACT_INFO -> Triple(Icons.Default.Contacts, "Contact Card", "Copy Contact")
                    else -> Triple(Icons.Default.QrCode, "Scanned Code", "Copy Text")
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = result.displayValue.takeIf { it.isNotBlank() } ?: result.rawValue,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Scanned Content", result.rawValue)
                        
                        when (result.valueType) {
                            com.google.mlkit.vision.barcode.common.Barcode.TYPE_URL -> {
                                try {
                                    val url = result.url ?: result.rawValue
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Could not open link. Copied to clipboard.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            com.google.mlkit.vision.barcode.common.Barcode.TYPE_WIFI -> {
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Details copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                        viewModel.clearBarcodeResult()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(actionText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, result.rawValue)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showImportGuideDialog) {
        AlertDialog(
            onDismissRequest = { showImportGuideDialog = false },
            title = {
                Text(
                    text = "Import via Scanner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = "To import an image and use our smart auto-crop AI, tap 'Open Scanner', then tap the Gallery icon at the bottom left of the scanner screen.",
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportGuideDialog = false
                        onNavigateToCamera()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Open Scanner", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportGuideDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}



