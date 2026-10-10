package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.ImageUtils
import com.example.utils.MlKitUtils
import com.example.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltViewModel
class MainViewModel @Inject constructor(
    val repository: DocumentRepository,
    val settingsManager: SettingsManager
) : ViewModel() {

    var cropBitmap: android.graphics.Bitmap? = null
    var cropPoints: List<android.graphics.PointF>? = null

    // Document listing, filtration and sorting
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    enum class SortType {
        DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
    }
    private val _sortType = MutableStateFlow(SortType.DATE_DESC)
    val sortType: StateFlow<SortType> = _sortType

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

    val documents: StateFlow<List<com.example.data.DocumentWithPages>> = combine(
        repository.allDocumentsWithPages,
        _searchQuery,
        _sortType,
        _onlyFavorites
    ) { allDocs, query, sort, favOnly ->
        var list = allDocs
        if (favOnly) {
            list = list.filter { it.document.isFavorite }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.document.name.contains(query, ignoreCase = true) ||
                        it.document.ocrText.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            SortType.DATE_DESC -> list.sortedByDescending { it.document.createdTime }
            SortType.DATE_ASC -> list.sortedBy { it.document.createdTime }
            SortType.NAME_ASC -> list.sortedBy { it.document.name }
            SortType.NAME_DESC -> list.sortedByDescending { it.document.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val recentDocuments: StateFlow<List<com.example.data.DocumentWithPages>> = repository.allDocumentsWithPages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active multi-page scan state
    private val _scannedPages = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val scannedPages: StateFlow<List<android.net.Uri>> = _scannedPages

    private val _previewingExistingDoc = MutableStateFlow<com.example.data.DocumentEntity?>(null)
    val previewingExistingDoc: StateFlow<com.example.data.DocumentEntity?> = _previewingExistingDoc

    fun loadExistingDocumentForPreview(doc: com.example.data.DocumentWithPages) {
        _previewingExistingDoc.value = doc.document
        _scannedPages.value = doc.pages.sortedBy { it.pageIndex }.map {
            if (it.canonicalFilePath.startsWith("file://") || it.canonicalFilePath.startsWith("content://")) {
                android.net.Uri.parse(it.canonicalFilePath)
            } else {
                android.net.Uri.fromFile(java.io.File(it.canonicalFilePath))
            }
        }
    }

    // Current page being edited/cropped
    private val _selectedPageForEditIndex = MutableStateFlow<Int?>(-1)
    val selectedPageForEditIndex: StateFlow<Int?> = _selectedPageForEditIndex

    // OCR operation state
    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState

    // QR/Barcode scanning results
    private val _barcodeResult = MutableStateFlow<MlKitUtils.BarcodeResult?>(null)
    val barcodeResult: StateFlow<MlKitUtils.BarcodeResult?> = _barcodeResult

    private val _fileOperationMessage = MutableStateFlow<String?>(null)
    val fileOperationMessage: StateFlow<String?> = _fileOperationMessage

    sealed interface OcrState {
        object Idle : OcrState
        object Processing : OcrState
        data class Success(val text: String) : OcrState
        data class Error(val errorMsg: String) : OcrState
    }

    // --- Search & Settings ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun setOnlyFavorites(fav: Boolean) {
        _onlyFavorites.value = fav
    }

    fun clearBarcodeResult() {
        _barcodeResult.value = null
    }
    
    fun setBarcodeResult(result: MlKitUtils.BarcodeResult) {
        _barcodeResult.value = result
    }

    fun clearFileMessage() {
        _fileOperationMessage.value = null
    }

    // --- Current Scan Session (Multiple Page Scanner) ---
    fun addScannedPage(uri: android.net.Uri) {
        val current = _scannedPages.value.toMutableList()
        current.add(uri)
        _scannedPages.value = current
    }

    fun removeScannedPage(index: Int) {
        val current = _scannedPages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _scannedPages.value = current
        }
    }


    fun updateScannedPage(index: Int, uri: android.net.Uri) {
        val current = _scannedPages.value.toMutableList()
        if (index in current.indices) {
            current[index] = uri
            _scannedPages.value = current
        }
    }

    fun clearActiveSession() {
        _scannedPages.value = emptyList()
        _selectedPageForEditIndex.value = -1
        _previewingExistingDoc.value = null
        _ocrState.value = OcrState.Idle
    }

    fun selectPageForEdit(index: Int?) {
        _selectedPageForEditIndex.value = index
    }

    // --- Database CRUD and File System Actions ---
    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.update(document.copy(isFavorite = !document.isFavorite))
        }
    }

    fun deleteDocument(context: Context, document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete the PDF file
                val pdfFile = File(document.filePath)
                if (pdfFile.exists()) {
                    pdfFile.delete()
                }

                // Delete canonical JPEG pages
                val pages = repository.getPagesForDocument(document.id)
                pages.forEach { page ->
                    val pageFile = File(page.canonicalFilePath)
                    if (pageFile.exists()) {
                        pageFile.delete()
                    }
                }

                repository.delete(document)
                _fileOperationMessage.value = "Document deleted"
            } catch (e: Exception) {
                _fileOperationMessage.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun renameDocument(context: Context, document: DocumentEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(document.filePath)
            if (!oldFile.exists()) {
                _fileOperationMessage.value = "Original file not found"
                return@launch
            }

            // Create target filename
            val sanitized = newName.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            val finalName = if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
            val newFile = File(oldFile.parentFile, finalName)

            if (oldFile.renameTo(newFile)) {
                repository.update(document.copy(name = finalName.removeSuffix(".pdf"), filePath = newFile.absolutePath))
                _fileOperationMessage.value = "Renamed successfully"
            } else {
                _fileOperationMessage.value = "Rename failed"
            }
        }
    }



    // --- PDF Compiling, OCR, QR Actions ---
    fun compileActiveSession(context: Context, documentName: String) {
        val pages = _scannedPages.value
        if (pages.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _ocrState.value = OcrState.Processing
            try {
                // Determine output destination
                val outputDir = context.getExternalFilesDir("ScannedDocuments") ?: context.filesDir
                if (!outputDir.exists()) outputDir.mkdirs()

                val sanitizedName = documentName.trim().ifEmpty { "ScanToPDF_${System.currentTimeMillis()}" }
                val targetFile = File(outputDir, "$sanitizedName.pdf")

                // 1. Generate PDF
                val quality = settingsManager.pdfQuality.value.compression
                PdfUtils.convertUrisToPdf(context, pages, quality, targetFile)

                // 2. Perform OCR on all pages in background to make document catalog searchable
                val ocrTextBuilder = StringBuilder()
                for ((idx, uri) in pages.withIndex()) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    val text = kotlin.coroutines.suspendCoroutine<String> { continuation ->
                        MlKitUtils.performOcr(bitmap,
                            onSuccess = { result ->
                                continuation.resumeWith(Result.success(result))
                            },
                            onFailure = {
                                continuation.resumeWith(Result.success(""))
                            }
                        )
                    }
                    if (text.isNotBlank()) {
                        ocrTextBuilder.append("\nPage ${idx + 1}:\n").append(text)
                    }
                }

                val finalOcrText = ocrTextBuilder.toString()

                val existingDoc = _previewingExistingDoc.value

                if (existingDoc != null) {
                    // Update existing document
                    val updatedEntity = existingDoc.copy(
                        name = sanitizedName,
                        filePath = targetFile.absolutePath,
                        pageCount = pages.size,
                        ocrText = finalOcrText,
                        pdfSize = targetFile.length()
                    )
                    repository.update(updatedEntity)
                    repository.deletePagesForDocument(existingDoc.id)
                    val pageEntities = pages.mapIndexed { index, uri ->
                        com.example.data.DocumentPageEntity(
                            documentId = existingDoc.id,
                            pageIndex = index,
                            canonicalFilePath = uri.path ?: uri.toString()
                        )
                    }
                    repository.insertPages(pageEntities)
                } else {
                    // Create new document
                    val entity = DocumentEntity(
                        name = sanitizedName,
                        filePath = targetFile.absolutePath,
                        pageCount = pages.size,
                        ocrText = finalOcrText,
                        pdfSize = targetFile.length()
                    )
                    val documentId = repository.insert(entity)

                    // Save canonical pages
                    val pageEntities = pages.mapIndexed { index, uri ->
                        com.example.data.DocumentPageEntity(
                            documentId = documentId,
                            pageIndex = index,
                            canonicalFilePath = uri.path ?: uri.toString()
                        )
                    }
                    repository.insertPages(pageEntities)
                }

                withContext(Dispatchers.Main) {
                    clearActiveSession()
                    _ocrState.value = OcrState.Success(finalOcrText)
                    _fileOperationMessage.value = "Document saved as $sanitizedName.pdf"
                }
            } catch (e: Exception) {
                _ocrState.value = OcrState.Error(e.message ?: "PDF creation failure")
            }
        }
    }

    fun ocrSingleBitmap(bitmap: Bitmap) {
        _ocrState.value = OcrState.Processing
        MlKitUtils.performOcr(bitmap,
            onSuccess = { text ->
                _ocrState.value = OcrState.Success(text)
            },
            onFailure = { e ->
                _ocrState.value = OcrState.Error(e.message ?: "OCR execution failed")
            }
        )
    }

    fun scanQrOrBarcode(bitmap: Bitmap) {
        MlKitUtils.scanBarcode(bitmap,
            onSuccess = { list ->
                if (list.isNotEmpty()) {
                    _barcodeResult.value = list.first()
                } else {
                    _fileOperationMessage.value = "No barcode or QR code detected"
                }
            },
            onFailure = { e ->
                _fileOperationMessage.value = "Barcode scan error: ${e.message}"
            }
        )
    }

    fun mergePdfs(context: Context, docList: List<DocumentEntity>, mergedName: String) {
        if (docList.size < 2) {
            _fileOperationMessage.value = "Select 2 or more files to merge"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputDir = context.getExternalFilesDir("ScannedDocuments") ?: context.filesDir
                val file = File(outputDir, "${mergedName.trim().ifEmpty { "Merged_${System.currentTimeMillis()}" }}.pdf")

                val filesToMerge = docList.map { File(it.filePath) }
                val success = PdfUtils.mergePdfFiles(filesToMerge, file)

                if (success) {
                    val combinedOcr = docList.joinToString("\n\n") { "--- ${it.name} ---\n${it.ocrText}" }
                    val totalPages = docList.sumOf { it.pageCount }

                    repository.insert(
                        DocumentEntity(
                            name = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            pageCount = totalPages,
                            ocrText = combinedOcr,
                            pdfSize = file.length()
                        )
                    )
                    _fileOperationMessage.value = "PDFs merged successfully"
                } else {
                    _fileOperationMessage.value = "Error merging PDFs"
                }
            } catch (e: Exception) {
                _fileOperationMessage.value = "Merge failure: ${e.message}"
            }
        }
    }


    fun importGalleryImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val outputDir = java.io.File(context.filesDir, "CanonicalPages")
                    if (!outputDir.exists()) outputDir.mkdirs()
                    val outputFile = java.io.File(outputDir, "page_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(5)}.jpg")
                    val outputStream = java.io.FileOutputStream(outputFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    addScannedPage(android.net.Uri.fromFile(outputFile))
                    _fileOperationMessage.value = "Image imported to editor session"
                } else {
                    _fileOperationMessage.value = "Failed to load image from gallery"
                }
            } catch (e: Exception) {
                _fileOperationMessage.value = "Error: ${e.message}"
            }
        }
    }
}

