package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DocumentEntity
import com.example.ui.MainViewModel
import com.example.utils.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMergeScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()

    // Store selected document IDs
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var showMergeNameDialog by remember { mutableStateOf(false) }
    var mergedNameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge Documents") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (documents.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedIds.size < 2) {
                                Toast.makeText(context, "Select at least 2 PDF documents", Toast.LENGTH_SHORT).show()
                            } else {
                                mergedNameInput = "Merged_${System.currentTimeMillis() / 1000}"
                                showMergeNameDialog = true
                            }
                        },
                        enabled = selectedIds.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("trigger_merge_fab"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Merge ${selectedIds.size} Documents")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (documents.size < 2) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Insufficient Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You must have at least 2 scanned documents in your library archive to perform a PDF merge operation.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Select PDF files to merge into a consolidated copy:",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(documents) { doc ->
                            val isSelected = selectedIds.contains(doc.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable {
                                        selectedIds = if (isSelected) {
                                            selectedIds - doc.id
                                        } else {
                                            selectedIds + doc.id
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedIds = if (isSelected) {
                                                selectedIds - doc.id
                                            } else {
                                                selectedIds + doc.id
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.name,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${doc.pageCount} Pages • " + formatSize(doc.pdfSize),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMergeNameDialog) {
        AlertDialog(
            onDismissRequest = { showMergeNameDialog = false },
            title = { Text("Merge Documents") },
            text = {
                Column {
                    Text("Provide filename for the merged PDF output:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mergedNameInput,
                        onValueChange = { mergedNameInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merged_name_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedList = documents.filter { selectedIds.contains(it.id) }
                        viewModel.mergePdfs(context, selectedList, mergedNameInput)
                        showMergeNameDialog = false
                        onClose()
                    },
                    modifier = Modifier.testTag("confirm_merge_button")
                ) {
                    Text("Merge & Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
