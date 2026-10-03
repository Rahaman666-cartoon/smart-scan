package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.DocumentEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.spacing
import com.example.utils.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (com.example.data.DocumentWithPages) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()

    var docToRename by remember { mutableStateOf<com.example.data.DocumentEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var docToDelete by remember { mutableStateOf<com.example.data.DocumentEntity?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Library", fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E)) },
                actions = {
                    IconButton(onClick = { viewModel.setOnlyFavorites(!onlyFavorites) }) {
                        Icon(
                            imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorites Only",
                            tint = if (onlyFavorites) Color(0xFFFFC107) else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8F9FA)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            // Modern Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by name or content...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = Color(0xFF1E1E1E),
                    unfocusedTextColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .shadow(4.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f))
            )

            // Sorting Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${documents.size} Documents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    var expandedSortMenu by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier.clickable { expandedSortMenu = true },
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (sortType) {
                                    MainViewModel.SortType.DATE_DESC -> "Newest First"
                                    MainViewModel.SortType.DATE_ASC -> "Oldest First"
                                    MainViewModel.SortType.NAME_ASC -> "Name A-Z"
                                    MainViewModel.SortType.NAME_DESC -> "Name Z-A"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = expandedSortMenu,
                        onDismissRequest = { expandedSortMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Date (Newest first)") },
                            onClick = { viewModel.setSortType(MainViewModel.SortType.DATE_DESC); expandedSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Oldest first)") },
                            onClick = { viewModel.setSortType(MainViewModel.SortType.DATE_ASC); expandedSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name A-Z") },
                            onClick = { viewModel.setSortType(MainViewModel.SortType.NAME_ASC); expandedSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name Z-A") },
                            onClick = { viewModel.setSortType(MainViewModel.SortType.NAME_DESC); expandedSortMenu = false }
                        )
                    }
                }
            }

            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No matches found" else "Your library is empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E1E1E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "Try a different search term" else "Scan your first document to see it here",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents) { doc ->
                        DocumentListCard(
                            doc = doc,
                            dateFormatter = dateFormatter,
                            onClicked = { onNavigateToDetail(doc) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc.document) },
                            onRename = {
                                docToRename = doc.document
                                renameInput = doc.document.name
                            },
                            onDelete = { docToDelete = doc.document },
                            onSplit = { viewModel.splitPdf(context, doc) }
                        )
                    }
                }
            }
        }
    }

    if (docToRename != null) {
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename File", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        viewModel.renameDocument(context, docToRename!!, renameInput)
                        docToRename = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete Document", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${docToDelete!!.name}.pdf'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(context, docToDelete!!)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DocumentListCard(
    doc: DocumentEntity,
    dateFormatter: SimpleDateFormat,
    onClicked: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit
) {
    var showMoreDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClicked() },
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                    .size(56.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.document.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1E1E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${doc.document.pageCount} Pages • ${formatSize(doc.document.pdfSize)} • ${dateFormatter.format(Date(doc.document.createdTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (doc.document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (doc.document.isFavorite) Color(0xFFFFC107) else Color.LightGray
                )
            }

            Box {
                IconButton(onClick = { showMoreDropdown = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
                }

                DropdownMenu(
                    expanded = showMoreDropdown,
                    onDismissRequest = { showMoreDropdown = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Rename") } },
                        onClick = { onRename(); showMoreDropdown = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CallSplit, null); Spacer(Modifier.width(8.dp)); Text("Split Pages") } },
                        onClick = { onSplit(); showMoreDropdown = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Delete", color = MaterialTheme.colorScheme.error) } },
                        onClick = { onDelete(); showMoreDropdown = false }
                    )
                }
            }
        }
    }
}

// ... DocumentDetailScreen and ZoomableImage remain identical ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    doc: DocumentEntity,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var activeTab by remember { mutableStateOf(0) }

    var pdfBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var renderingError by remember { mutableStateOf(false) }

    LaunchedEffect(doc.document.filePath) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val file = File(doc.document.filePath)
            if (file.exists()) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
            } else {
                renderingError = true
            }
        } catch (e: Exception) {
            renderingError = true
        }
        pdfBitmaps = bitmaps
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E1E1E)) }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val file = File(doc.document.filePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Document"))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF1E1E1E))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            TabRow(selectedTabIndex = activeTab, containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("PDF View", fontWeight = FontWeight.Bold) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("OCR Reader", fontWeight = FontWeight.Bold) })
            }

            if (activeTab == 0) {
                if (renderingError) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("PDF file not found.", color = MaterialTheme.colorScheme.error)
                    }
                } else if (pdfBitmaps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pdfBitmaps) { bitmap ->
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                ZoomableImage(bitmap)
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(doc.document.ocrText))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Text")
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val dir = context.getExternalFilesDir("ScannedText") ?: context.filesDir
                                    if (!dir.exists()) dir.mkdirs()
                                    val file = File(dir, "${doc.document.name}.txt")
                                    file.writeText(doc.document.ocrText)
                                    Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export .TXT")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            item {
                                if (doc.document.ocrText.isBlank()) {
                                    Text("No text detected.", color = Color.Gray)
                                } else {
                                    Text(doc.document.ocrText, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1E1E1E), lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
