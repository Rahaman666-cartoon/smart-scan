package com.example.utils

import android.graphics.Bitmap
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    /**
     * Converts a list of Bitmaps to a single multi-page PDF document
     */
    fun convertBitmapsToPdf(bitmaps: List<Bitmap>, outputFile: File) {
        val document = PDDocument()
        try {
            for (bitmap in bitmaps) {
                // Ensure correct aspect ratio / dimensions
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.75f, 72)
                val contentStream = PDPageContentStream(document, page)
                
                contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                contentStream.close()
            }
            document.save(outputFile)
        } finally {
            document.close()
        }
    }

    /**
     * Merges multiple PDF files on disk into one consolidated PDF file without rasterizing
     */
    fun mergePdfFiles(pdfFiles: List<File>, outputFile: File): Boolean {
        if (pdfFiles.isEmpty()) return false
        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            for (file in pdfFiles) {
                if (file.exists()) {
                    merger.addSource(file)
                }
            }
            // Use null for MemoryUsageSetting to use main memory (default)
            merger.mergeDocuments(null)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Splits a multi-page PDF file into standard separate pages without rasterizing
     */
    fun splitPdfFile(inputFile: File, outputDirectory: File, baseName: String): List<File> {
        val resultFiles = mutableListOf<File>()
        if (!inputFile.exists()) return resultFiles
        
        var document: PDDocument? = null
        try {
            document = PDDocument.load(inputFile)
            val splitter = Splitter()
            @Suppress("UNCHECKED_CAST")
            val splitDocuments = splitter.split(document) as List<PDDocument>
            
            var index = 0
            for (splitDoc in splitDocuments) {
                val outputFile = File(outputDirectory, "${baseName}_page_${index + 1}.pdf")
                splitDoc.save(outputFile)
                splitDoc.close()
                resultFiles.add(outputFile)
                index++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document?.close()
        }
        return resultFiles
    }
}
