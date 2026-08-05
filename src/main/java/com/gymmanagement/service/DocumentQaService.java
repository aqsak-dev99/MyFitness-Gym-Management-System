package com.gymmanagement.service;

import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DocumentQaService — the actual "ask a question about this document"
 * feature. Ties together chunking (already built), naive retrieval
 * (RetrievalService), and Gemini (GeminiClient) into one working flow.
 *
 * This call automatically goes through GeminiClient's existing rate
 * limiter — no new wiring needed for that protection, since every path
 * to Gemini in this project runs through that one class.
 */
@Service
public class DocumentQaService {

    private final DocumentChunkRepository chunkRepo;
    private final RetrievalService        retrievalService;
    private final GeminiClient            geminiClient;
    private final DocumentService         documentService;

    public DocumentQaService(DocumentChunkRepository chunkRepo,
                             RetrievalService retrievalService,
                             GeminiClient geminiClient,
                             DocumentService documentService) {
        this.chunkRepo        = chunkRepo;
        this.retrievalService = retrievalService;
        this.geminiClient     = geminiClient;
        this.documentService  = documentService;
    }

    public String askAboutDocument(String documentId, String question) {
        documentService.getDocument(documentId);   // throws DocumentNotFoundException if missing

        List<DocumentChunk> allChunks = chunkRepo.findByDocumentId(documentId);
        List<DocumentChunk> relevantChunks = retrievalService.findRelevantChunks(question, allChunks);

        // A simple, honest version of "confidence-based refusal" from the
        // original feature checklist: if keyword matching found nothing
        // relevant at all, don't call Gemini — return directly. This is
        // both a better answer (no risk of a hallucinated response about
        // content that isn't there) and a real quota saving, which matters
        // given tonight's daily-limit discovery.
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
