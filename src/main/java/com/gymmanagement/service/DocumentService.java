package com.gymmanagement.service;

import com.gymmanagement.exception.DocumentNotFoundException;
import com.gymmanagement.model.Document;
import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.repository.DocumentChunkRepository;
import com.gymmanagement.repository.DocumentRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DocumentService — Milestone 1 (upload/retrieve), Milestone 2
 * (automatic chunking), and Milestone 4 (automatic embedding) all
 * happen inside one uploadDocument() call. Retrieval logic itself lives
 * in DocumentQaService, not here — this class's job stops at "the
 * document is stored, chunked, and every chunk has a vector."
 *
 * Depends on AiEmbeddingClient (interface), not the concrete
 * GeminiEmbeddingClient — same reasoning as DocumentQaService and
 * BootcampRecommendationService: lets a test fake stand in without
 * ever needing GEMINI_API_KEY set.
 */
@Service
public class DocumentService {

    private final DocumentRepository      documentRepo;
    private final DocumentChunkRepository chunkRepo;
    private final ChunkingService         chunkingService;
    private final AiEmbeddingClient       embeddingClient;

    public DocumentService(DocumentRepository documentRepo,
                           DocumentChunkRepository chunkRepo,
                           ChunkingService chunkingService,
                           AiEmbeddingClient embeddingClient) {
        this.documentRepo    = documentRepo;
        this.chunkRepo       = chunkRepo;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
    }

    /**
     * Uploads a document, splits it into chunks, and embeds every chunk
     * — all three happen in one call, on the same "all-or-nothing"
     * principle Milestone 2 established for chunking: if this method
     * returns successfully, the document is stored, chunked, AND every
     * chunk has a vector ready for search. Nothing downstream has to
     * check whether embedding "happened to work."
     */
    public Document uploadDocument(String filename, String content, String audience) {
        String documentId = "DOC-" + UUID.randomUUID();
        Document document = new Document(documentId, filename, content, LocalDate.now(), audience);
        documentRepo.save(document);

        List<String> chunkTexts = chunkingService.chunk(content);
        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkId = "CHUNK-" + UUID.randomUUID();
            chunkRepo.save(new DocumentChunk(chunkId, documentId, i, chunkTexts.get(i)));
            chunkIds.add(chunkId);
        }

        // One Gemini call embeds every chunk regardless of how many
        // there are (see GeminiEmbeddingClient.embedChunks for why).
        // Skipped entirely for empty content — nothing to embed, and
        // calling Gemini with an empty list would be a wasted request.
        if (!chunkTexts.isEmpty()) {
            List<float[]> embeddings = embeddingClient.embedChunks(chunkTexts);
            for (int i = 0; i < chunkIds.size(); i++) {
                chunkRepo.saveEmbedding(chunkIds.get(i), embeddings.get(i));
            }
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