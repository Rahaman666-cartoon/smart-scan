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
    private val repository: DocumentRepository,
    val settingsManager: SettingsManager
) : ViewModel() {

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

    val documents: StateFlow<List<DocumentEntity>> = combine(
        repository.allDocuments,
        _searchQuery,
        _sortType,
        _onlyFavorites
    ) { allDocs, query, sort, favOnly ->
        var list = allDocs
        if (favOnly) {
            list = list.filter { it.isFavorite }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.ocrText.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            SortType.DATE_DESC -> list.sortedByDescending { it.createdTime }
            SortType.DATE_ASC -> list.sortedBy { it.createdTime }
            SortType.NAME_ASC -> list.sortedBy { it.name }
            SortType.NAME_DESC -> list.sortedByDescending { it.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active multi-page scan state
    private val _scannedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val scannedPages: StateFlow<List<Bitmap>> = _scannedPages

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

    fun clearFileMessage() {
        _fileOperationMessage.value = null
    }

    // --- Current Scan Session (Multiple Page Scanner) ---
    fun addScannedPage(bitmap: Bitmap) {
        val current = _scannedPages.value.toMutableList()
        current.add(bitmap)
        _scannedPages.value = current
    }

    fun removeScannedPage(index: Int) {
        val current = _scannedPages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _scannedPages.value = current
        }
    }

    fun updateScannedPage(index: Int, bitmap: Bitmap) {
        val current = _scannedPages.value.toMutableList()
        if (index in current.indices) {
            current[index] = bitmap
            _scannedPages.value = current
        }
    }

    fun clearActiveSession() {
        _scannedPages.value = emptyList()
        _selectedPageForEditIndex.value = -1
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

    fun deleteDocument(context: Context, document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(document.filePath)
            if (file.exists()) {
                file.delete()
            }
            repository.delete(document)
            _fileOperationMessage.value = "Document deleted successfully"
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

                val sanitizedName = documentName.trim().ifEmpty { "SmartScan_${System.currentTimeMillis()}" }
                val targetFile = File(outputDir, "$sanitizedName.pdf")

                // Compress Bitmaps based on quality settings
                val quality = settingsManager.pdfQuality.value.compression
                val compressedPages = pages.map { original ->
                    val stream = java.io.ByteArrayOutputStream()
                    original.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    val bytes = stream.toByteArray()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                // 1. Generate PDF
                PdfUtils.convertBitmapsToPdf(compressedPages, targetFile)

                // 2. Perform OCR on all pages in background to make document catalog searchable
                val ocrTextBuilder = StringBuilder()
                for ((idx, bitmap) in compressedPages.withIndex()) {
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

                // 3. Persist to Room
                val entity = DocumentEntity(
                    name = sanitizedName,
                    filePath = targetFile.absolutePath,
                    pageCount = compressedPages.size,
                    ocrText = finalOcrText,
                    pdfSize = targetFile.length()
                )
                repository.insert(entity)

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

    fun splitPdf(context: Context, doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(doc.filePath)
                val outputDir = context.getExternalFilesDir("ScannedDocuments") ?: context.filesDir
                val results = PdfUtils.splitPdfFile(file, outputDir, doc.name + "_split")

                if (results.isNotEmpty()) {
                    results.forEach { singlePageFile ->
                        repository.insert(
                            DocumentEntity(
                                name = singlePageFile.nameWithoutExtension,
                                filePath = singlePageFile.absolutePath,
                                pageCount = 1,
                                ocrText = "Split from ${doc.name}\n${doc.ocrText}",
                                pdfSize = singlePageFile.length()
                            )
                        )
                    }
                    _fileOperationMessage.value = "Split PDF into ${results.size} files"
                } else {
                    _fileOperationMessage.value = "Failed to split PDF"
                }
            } catch (e: Exception) {
                _fileOperationMessage.value = "Split failed: ${e.message}"
            }
        }
    }

    fun importGalleryImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    addScannedPage(bitmap)
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
