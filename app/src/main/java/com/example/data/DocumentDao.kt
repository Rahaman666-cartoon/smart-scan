package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM scanned_documents ORDER BY createdTime DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: Long): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPageEntity>)
    
    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: Long)
}
