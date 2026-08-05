package com.gymmanagement.service;

import com.gymmanagement.exception.DocumentNotFoundException;
import com.gymmanagement.model.Document;
import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.repository.DocumentChunkRepository;
import com.gymmanagement.repository.DocumentRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DocumentService — Milestone 1 (upload/retrieve) plus Milestone 2
 * (automatic chunking on upload). Embeddings and retrieval are still
 * separate, later services building on top of this one.
 */
@Service
public class DocumentService {

    private final DocumentRepository      documentRepo;
    private final DocumentChunkRepository chunkRepo;
    private final ChunkingService         chunkingService;

    public DocumentService(DocumentRepository documentRepo,
                           DocumentChunkRepository chunkRepo,
                           ChunkingService chunkingService) {
        this.documentRepo    = documentRepo;
        this.chunkRepo       = chunkRepo;
        this.chunkingService = chunkingService;
    }

    /**
     * Uploads a document and immediately splits it into chunks, saving
     * both in one call. Chunking failure isn't handled separately from
     * upload failure — if this method returns successfully, the document
     * is both stored AND chunked, never one without the other.
     */
    public Document uploadDocument(String filename, String content) {
        String documentId = "DOC-" + UUID.randomUUID();
        Document document = new Document(documentId, filename, content, LocalDate.now());
        documentRepo.save(document);

        List<String> chunkTexts = chunkingService.chunk(content);
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkId = "CHUNK-" + UUID.randomUUID();
            chunkRepo.save(new DocumentChunk(chunkId, documentId, i, chunkTexts.get(i)));
        }

        return document;
    }

    public Document getDocument(String documentId) {
        return documentRepo.findById(documentId)
                           .orElseThrow(() -> new DocumentNotFoundException(
                               "No document found with ID: " + documentId));
    }

    public List<Document> getAllDocuments() {
        return documentRepo.findAll();
    }

    public List<DocumentChunk> getChunks(String documentId) {
        // Confirm the document exists first, so asking for chunks of a
        // nonexistent document gives a clear 404, not a silently empty list.
        getDocument(documentId);
        return chunkRepo.findByDocumentId(documentId);
    }

    public void deleteDocument(String documentId) {
        if (documentRepo.findById(documentId).isEmpty())
            throw new DocumentNotFoundException(
                "Cannot delete — no document found with ID: " + documentId);
        // Chunks are NOT deleted here explicitly — ON DELETE CASCADE on
        // document_chunks.document_id handles that automatically at the
        // database level the moment this next line runs.
        documentRepo.delete(documentId);
    }
}