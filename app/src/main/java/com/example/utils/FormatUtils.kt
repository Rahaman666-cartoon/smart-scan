package com.example.utils

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024f
    val m = k * k
    return when {
        bytes >= m -> String.format(java.util.Locale.US, "%.2f MB", bytes / m)
        bytes >= k -> String.format(java.util.Locale.US, "%.1f KB", bytes / k)
        else -> "$bytes B"
    }
}

fun getBarcodeFormatName(format: Int): String {
    return when (format) {
        256 -> "QR CODE"
        1 -> "CODE 128"
        2 -> "CODE 39"
        4 -> "CODE 93"
        8 -> "CODABAR"
        16 -> "DATA MATRIX"
        32 -> "EAN 13"
        64 -> "EAN 8"
        128 -> "ITF"
        512 -> "UPC A"
        1024 -> "UPC E"
        else -> "BARCODE / QR CODE"
    }
}
