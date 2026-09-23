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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
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
    onNavigateToDetail: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()

    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Library", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.setOnlyFavorites(!onlyFavorites) },
                        modifier = Modifier.testTag("filter_favs_button")
                    ) {
                        Icon(
                            imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorites Only",
                            tint = if (onlyFavorites) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by name or content...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    .testTag("search_field_input")
            )

            // Sorting Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${documents.size} Documents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Box {
                    var expandedSortMenu by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { expandedSortMenu = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            when (sortType) {
                                MainViewModel.SortType.DATE_DESC -> "Newest First"
                                MainViewModel.SortType.DATE_ASC -> "Oldest First"
                                MainViewModel.SortType.NAME_ASC -> "Name A-Z"
                                MainViewModel.SortType.NAME_DESC -> "Name Z-A"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = expandedSortMenu,
                        onDismissRequest = { expandedSortMenu = false }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                        Text(
                            if (searchQuery.isNotEmpty()) "No matches found" else "Your library is empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
                        Text(
                            if (searchQuery.isNotEmpty()) "Try a different search term" else "Scan your first document to see it here",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(documents) { doc ->
                        DocumentListCard(
                            doc = doc,
                            dateFormatter = dateFormatter,
                            onClicked = { onNavigateToDetail(doc) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onRename = {
                                docToRename = doc
                                renameInput = doc.name
                            },
                            onDelete = { docToDelete = doc },
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
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        viewModel.renameDocument(context, docToRename!!, renameInput)
                        docToRename = null
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to permanently delete '${docToDelete!!.name}.pdf'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(context, docToDelete!!)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
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
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall)
            .clickable { onClicked() }
            .testTag("doc_item_card_${doc.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = borderStroke(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${doc.pageCount} Pages • ${formatSize(doc.pdfSize)} • ${dateFormatter.format(Date(doc.createdTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (doc.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMoreDropdown = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DropdownMenu(
                    expanded = showMoreDropdown,
                    onDismissRequest = { showMoreDropdown = false }
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

    LaunchedEffect(doc.filePath) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val file = File(doc.filePath)
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
                title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val file = File(doc.filePath)
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
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("PDF View") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("OCR Reader") })
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
                        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        items(pdfBitmaps) { bitmap ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = borderStroke(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        .padding(MaterialTheme.spacing.medium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(doc.ocrText))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val dir = context.getExternalFilesDir("ScannedText") ?: context.filesDir
                                    if (!dir.exists()) dir.mkdirs()
                                    val file = File(dir, "${doc.name}.txt")
                                    file.writeText(doc.ocrText)
                                    Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
                            Text("Export .TXT")
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = borderStroke(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        LazyColumn(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                            item {
                                if (doc.ocrText.isBlank()) {
                                    Text("No text detected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(doc.ocrText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(Color.Black)
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
