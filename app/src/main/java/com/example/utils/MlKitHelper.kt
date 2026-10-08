package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object MlKitHelper {

    // 1. Text Recognition (OCR) & Smart Auto-Naming
    suspend fun recognizeTextAndSuggestName(bitmap: Bitmap): Pair<String, String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        try {
            val result = recognizer.process(image).await()
            val text = result.text
            
            // Smart Auto-Naming Logic based on detected text
            val lowerText = text.lowercase()
            val suggestedName = when {
                lowerText.contains("aadhar") || lowerText.contains("government of india") -> "Aadhar_Card"
                lowerText.contains("invoice") || lowerText.contains("total due") -> "Invoice"
                lowerText.contains("receipt") -> "Receipt"
                lowerText.contains("tax") || lowerText.contains("pan") -> "PAN_Card"
                lowerText.contains("driving licence") -> "Driving_Licence"
                lowerText.contains("passport") -> "Passport"
                else -> "Scanned_Document"
            }
            
            return Pair(text, suggestedName)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair("", "Scanned_Document")
        }
    }

    // 2. Barcode & QR Code Scanning
    suspend fun scanBarcodes(bitmap: Bitmap): List<Barcode> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        
        try {
            return scanner.process(image).await()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // 3. Subject Segmentation (Background Removal)
    suspend fun removeBackground(bitmap: Bitmap): Bitmap? {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        // Use multiple subjects and foreground confidence mask
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().build())
            .build()
            
        val segmenter = SubjectSegmentation.getClient(options)
        
        try {
            val result = segmenter.process(image).await()
            val mask = result.foregroundConfidenceMask ?: return bitmap
            
            // Create a new bitmap with transparency
            val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            mask.rewind()
            
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val index = y * bitmap.width + x
                    val confidence = mask.get()
                    
                    if (confidence < 0.5f) { // Threshold for background
                        pixels[index] = Color.TRANSPARENT
                    }
                }
            }
            
            resultBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return resultBitmap
            
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap
        }
    }
}

