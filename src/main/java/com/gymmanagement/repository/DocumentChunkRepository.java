package com.gymmanagement.repository;

import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.model.ScoredChunk;

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
     * closest to queryEmbedding, nearest first, each paired with its
     * actual distance score (Milestone 5 needs this — Milestone 4 only
     * needed the chunks themselves, not how close they really were).
     * Chunks with no embedding yet (documents uploaded before Milestone 4)
     * are excluded rather than sorted arbitrarily.
     */
    List<ScoredChunk> findNearestByEmbedding(String documentId, float[] queryEmbedding, int topK);
}