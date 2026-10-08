package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("documentId"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["documentId"])]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true) val pageId: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val canonicalFilePath: String
)

