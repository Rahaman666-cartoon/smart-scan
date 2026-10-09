package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.data.DocumentEntity
import com.example.data.DocumentPageEntity
import com.example.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SavedDocumentScreen(
    document: DocumentEntity,
    pages: List<DocumentPageEntity>,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onEditSession: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { if (pages.isEmpty()) 1 else pages.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (pages.isNotEmpty()) {
                        Button(onClick = {
                            viewModel.loadExistingDocumentForPreview(com.example.data.DocumentWithPages(document, pages))
                            onEditSession()
                        }) {
                            Text("Edit")
                        }
                    }
                    Button(onClick = {
                        try {
                            val file = File(document.filePath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.applicationContext.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open PDF", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("View PDF")
                    }
                    Button(onClick = {
                        try {
                            val file = File(document.filePath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.applicationContext.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error sharing", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Share")
                    }
                    Button(onClick = {
                        viewModel.deleteDocument(context, document)
                        onNavigateBack()
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFE0E0E0))) {
            if (pages.isEmpty()) {
                // Legacy PDF-only mode
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Legacy PDF Document")
                    Text("No canonical JPEGs available.")
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        val modelPath = pages[page].canonicalFilePath
                        val model = if (modelPath.startsWith("file://") || modelPath.startsWith("content://")) {
                            Uri.parse(modelPath)
                        } else {
                            File(modelPath)
                        }
                        coil.compose.AsyncImage(
                            model = model,
                            contentDescription = "Page $page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

