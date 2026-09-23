package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocumentsFlow()

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insert(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun update(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun delete(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteById(id: Long) {
        documentDao.deleteDocumentById(id)
    }
}
