package com.gymmanagement.repository;

import com.gymmanagement.model.DocumentChunk;

import java.util.List;

/** Contract for all DocumentChunk data-access implementations. */
public interface DocumentChunkRepository {
    void               save(DocumentChunk chunk);
    List<DocumentChunk> findByDocumentId(String documentId);

    // ── Milestone 4: embeddings + pgvector ──────────────

    /** Stores (or overwrites) the embedding vector for one existing chunk. */
    void saveEmbedding(String chunkId, float[] embedding);

    /**
     * Returns the topK chunks of one document whose stored embedding is
     * closest to queryEmbedding, nearest first. Chunks with no embedding
     * yet (documents uploaded before Milestone 4) are excluded rather
     * than sorted arbitrarily.
     */
    List<DocumentChunk> findNearestByEmbedding(String documentId, float[] queryEmbedding, int topK);
}