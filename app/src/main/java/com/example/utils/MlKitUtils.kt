package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object MlKitUtils {

    /**
     * Extracts text from a Bitmap using Google ML Kit Text Recognition
     */
    fun performOcr(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    onSuccess(visionText.text)
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    /**
     * Scans a Bitmap for barcodes and QR codes
     */
    fun scanBarcode(
        bitmap: Bitmap,
        onSuccess: (List<BarcodeResult>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val results = barcodes.map { barcode ->
                        BarcodeResult(
                            rawValue = barcode.rawValue ?: "",
                            displayValue = barcode.displayValue ?: "",
                            format = barcode.format,
                            valueType = barcode.valueType,
                            url = barcode.url?.url
                        )
                    }
                    onSuccess(results)
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    data class BarcodeResult(
        val rawValue: String,
        val displayValue: String,
        val format: Int,
        val valueType: Int,
        val url: String? = null
    )
}

