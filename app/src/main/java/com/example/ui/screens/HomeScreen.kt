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
    onNavigateToDocument: (DocumentEntity) -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToCrop: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()
    val barcodeResult by viewModel.barcodeResult.collectAsState()
    val scope = rememberCoroutineScope()

    var showSaveDialog by remember { mutableStateOf(false) }
    var documentNameInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

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

    val barcodeGalleryLauncher = rememberLauncherForActivityResult(
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
                        val barcodes = com.example.utils.MlKitHelper.scanBarcodes(bitmap)
                        withContext(Dispatchers.Main) {
                            if (barcodes.isNotEmpty()) {
                                Toast.makeText(context, "Barcode: ${barcodes.first().displayValue}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No barcode found in image", Toast.LENGTH_SHORT).show()
                            }
                        }
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
            .background(Color(0xFFF8F9FA))
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Good Morning,",
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
                        // Premium Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { /* profile */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
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
                        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                color = Color(0xFF1E1E1E)
                            )
                            Text(
                                "Auto-detect edges",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    // Secondary Tools Stack
                    Column(
                        modifier = Modifier.weight(1f).height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Import Image
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.05f))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { galleryLauncher.launch("image/*") },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFE8F5E9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF43A047))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Import Image", fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E))
                            }
                        }

                        // Merge PDF
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.05f))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onNavigateToMerge() },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFFFF3E0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFF57C00))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Merge PDFs", fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // QR Code Standalone
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.05f))
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { barcodeGalleryLauncher.launch("image/*") },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFF3E5F5), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF8E24AA))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("QR / Barcode", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E1E1E))
                                Text("Scan any code instantly", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                    }
                }
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
                    color = Color(0xFF1E1E1E)
                )
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* navigate to library */ }
                )
            }
        }

        val filteredDocs = if (searchQuery.isBlank()) documents else documents.filter { it.name.contains(searchQuery, ignoreCase = true) }

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
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // PDF Icon/Thumbnail
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E1E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dateFormatter.format(Date(doc.createdTime))}  •  ${formatSize(doc.pdfSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(doc) }) {
                            Icon(
                                imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (doc.isFavorite) Color(0xFFFFC107) else Color.LightGray
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
        AlertDialog(
            onDismissRequest = { viewModel.clearBarcodeResult() },
            title = { Text("Barcode Scanned") },
            text = {
                Column {
                    Text("Format: ${getBarcodeFormatName(barcodeResult!!.format)}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = barcodeResult!!.rawValue,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearBarcodeResult() }) { Text("Done") }
            }
        )
    }
}
