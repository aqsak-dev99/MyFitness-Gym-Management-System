package com.gymmanagement.model;

/**
 * DocumentChunk — one split piece of a Document's content.
 *
 * chunkIndex preserves the original order chunks appeared in the source
 * document — needed later for retrieval to reconstruct sensible context,
 * not just an arbitrary bag of text fragments.
 */
public class DocumentChunk {

    private final String chunkId;
    private final String documentId;
    private final int    chunkIndex;
    private final String content;

    public DocumentChunk(String chunkId, String documentId, int chunkIndex, String content) {
        if (chunkId == null || chunkId.isBlank())
            throw new IllegalArgumentException("Chunk ID cannot be empty.");
        if (documentId == null || documentId.isBlank())
            throw new IllegalArgumentException("Document ID cannot be empty.");
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Chunk content cannot be empty.");

        this.chunkId    = chunkId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content    = content;
    }

    public String getChunkId()    { return chunkId;    }
    public String getDocumentId() { return documentId; }
    public int    getChunkIndex() { return chunkIndex; }
    public String getContent()    { return content;    }
}