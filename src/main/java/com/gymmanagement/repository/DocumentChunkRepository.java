package com.gymmanagement.repository;

import com.gymmanagement.model.DocumentChunk;

import java.util.List;

/** Contract for all DocumentChunk data-access implementations. */
public interface DocumentChunkRepository {
    void               save(DocumentChunk chunk);
    List<DocumentChunk> findByDocumentId(String documentId);
}