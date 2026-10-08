package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val createdTime: Long = System.currentTimeMillis(),
    val pageCount: Int,
    val isFavorite: Boolean = false,
    val ocrText: String = "",
    val pdfSize: Long = 0
)

