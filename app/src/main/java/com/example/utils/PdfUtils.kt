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
     * Converts a list of image URIs to a single multi-page PDF document
     * Decodes and processes one page at a time to prevent OutOfMemory errors.
     */
    fun convertUrisToPdf(context: android.content.Context, uris: List<android.net.Uri>, quality: Int, outputFile: File) {
        val document = PDDocument()
        try {
            for (uri in uris) {
                // Decode one image at a time
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
                
                val a4Width = PDRectangle.A4.width
                val a4Height = PDRectangle.A4.height
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, bitmap, quality / 100f, 72)
                val contentStream = PDPageContentStream(document, page)
                
                // Scale proportionally to fit A4
                val scale = minOf(a4Width / bitmap.width.toFloat(), a4Height / bitmap.height.toFloat())
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                
                // Center the image
                val startX = (a4Width - drawWidth) / 2f
                val startY = (a4Height - drawHeight) / 2f
                
                contentStream.drawImage(pdImage, startX, startY, drawWidth, drawHeight)
                contentStream.close()
                bitmap.recycle() // Release memory for this page
            }
            document.save(outputFile)
        } finally {
            document.close()
        }
    }

    /**
     * Converts a list of Bitmaps to a single multi-page PDF document
     */
    fun convertBitmapsToPdf(bitmaps: List<Bitmap>, outputFile: File) {
        val document = PDDocument()
        try {
            for (bitmap in bitmaps) {
                val a4Width = PDRectangle.A4.width
                val a4Height = PDRectangle.A4.height
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.75f, 72)
                val contentStream = PDPageContentStream(document, page)
                
                // Scale proportionally to fit A4
                val scale = minOf(a4Width / bitmap.width.toFloat(), a4Height / bitmap.height.toFloat())
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                
                // Center the image
                val startX = (a4Width - drawWidth) / 2f
                val startY = (a4Height - drawHeight) / 2f
                
                contentStream.drawImage(pdImage, startX, startY, drawWidth, drawHeight)
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


}
