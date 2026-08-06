package com.gymmanagement.service;

import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DocumentQaService — Milestone 4 update: retrieval is now real vector
 * search through pgvector instead of RetrievalService's keyword overlap.
 * RetrievalService itself is left in place, unused by this class — it's
 * a legitimate "v1" worth pointing to when explaining how the feature
 * evolved, not dead code to feel bad about.
 */
@Service
public class DocumentQaService {

    private static final int TOP_K = 5;

    private final DocumentChunkRepository chunkRepo;
    private final GeminiEmbeddingClient   embeddingClient;
    private final GeminiClient            geminiClient;
    private final DocumentService         documentService;

    public DocumentQaService(DocumentChunkRepository chunkRepo,
                             GeminiEmbeddingClient embeddingClient,
                             GeminiClient geminiClient,
                             DocumentService documentService) {
        this.chunkRepo       = chunkRepo;
        this.embeddingClient = embeddingClient;
        this.geminiClient    = geminiClient;
        this.documentService = documentService;
    }

    public String askAboutDocument(String documentId, String question) {
        documentService.getDocument(documentId);   // throws DocumentNotFoundException if missing

        float[] questionEmbedding = embeddingClient.embedQuestion(question);
        List<DocumentChunk> relevantChunks =
            chunkRepo.findNearestByEmbedding(documentId, questionEmbedding, TOP_K);

        // Still a real, if simpler, form of Milestone 3's refusal idea:
        // a document with zero embedded chunks (nothing uploaded since
        // Milestone 4 shipped, or an upload that predates it) has
        // nothing to compare against, so skip the Gemini chat call
        // entirely rather than answer from nothing. Confidence-based
        // refusal on MATCH QUALITY itself — not just presence/absence —
        // is Milestone 5's job, deliberately not tackled here.
        if (relevantChunks.isEmpty()) {
            return "I couldn't find anything in this document relevant to your question.";
        }

        String context = relevantChunks.stream()
            .map(DocumentChunk::getContent)
            .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildPrompt(context, question);
        return geminiClient.ask(prompt);
    }

    /**
     * The actual grounding mechanism that makes this "document Q&A"
     * rather than just "ask Gemini anything" — explicitly instructing
     * the model to answer only from the provided excerpts, and to say so
     * if the answer isn't there, rather than guessing.
     */
    private String buildPrompt(String context, String question) {
        return "You are answering a question using ONLY the following document excerpts. " +
               "If the answer is not contained in these excerpts, say you don't know — " +
               "do not use outside knowledge or make up information.\n\n" +
               "Document excerpts:\n" + context + "\n\n" +
               "Question: " + question;
    }
}