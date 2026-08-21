package com.gymmanagement.service;

import com.gymmanagement.model.ScoredChunk;
import com.gymmanagement.repository.DocumentChunkRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DocumentQaService — Milestone 5 update: real confidence-based refusal
 * and real citations, both genuinely new here, not just polish.
 *
 * Milestone 4's refusal only fired on an EMPTY result — zero embedded
 * chunks at all. That's a much weaker guarantee than it sounds: pgvector
 * always returns its "closest" matches, even when none of them are
 * actually relevant. A document about gym policies asked "what's the
 * capital of France" would still get 5 chunks back — just badly-matching
 * ones — and Milestone 4 would have fed them to Gemini as if they were
 * genuine context. This version filters on actual match QUALITY
 * (distance), not just presence.
 */
@Service
public class DocumentQaService {

    private static final int TOP_K = 5;

    // Cosine distance threshold (pgvector's <=> operator: 0 = identical
    // direction, 1 = unrelated, 2 = opposite). Chunks scoring above this
    // are treated as "not actually relevant," even if they were the
    // closest available match.
    //
    // Calibrated from real measured data, not guessed: a genuinely
    // relevant question ("How much does it cost to join?" against a
    // membership-fees document) scored 0.380. A genuinely irrelevant one
    // ("What is the capital of France?" against the same document)
    // scored 0.548. The original guess of 0.7 sat above BOTH numbers,
    // so neither question was ever actually filtered — Gemini's own
    // prompt instruction was doing all the refusal work, silently.
    // 0.45 sits between the two measured values, closer to the relevant
    // side — deliberately erring toward rejecting borderline matches,
    // since trustworthy refusal is the actual point of this feature.
    // Still only two data points — worth revisiting with more real
    // question/document pairs as this gets used more.
    private static final double MAX_RELEVANT_DISTANCE = 0.45;

    private final DocumentChunkRepository chunkRepo;
    private final AiEmbeddingClient embeddingClient;
    private final AiChatClient      geminiClient;
    private final DocumentService         documentService;

    public DocumentQaService(DocumentChunkRepository chunkRepo,
                             AiEmbeddingClient embeddingClient,
                             AiChatClient geminiClient,
                             DocumentService documentService) {
        this.chunkRepo       = chunkRepo;
        this.embeddingClient = embeddingClient;
        this.geminiClient    = geminiClient;
        this.documentService = documentService;
    }

    public DocumentAnswer askAboutDocument(String documentId, String question) {
        documentService.getDocument(documentId);   // throws DocumentNotFoundException if missing

        float[] questionEmbedding = embeddingClient.embedQuestion(question);
        List<ScoredChunk> nearest =
            chunkRepo.findNearestByEmbedding(documentId, questionEmbedding, TOP_K);

        // Temporary diagnostic — prints the real distance for every
        // retrieved chunk, so MAX_RELEVANT_DISTANCE can be calibrated
        // against actual numbers instead of guessed blind. Safe to
        // remove once the threshold's been validated against a few
        // real relevant/irrelevant question pairs.
        nearest.forEach(sc -> System.out.println(
            "[QA] chunk " + sc.chunk().getChunkIndex() + " distance=" + sc.distance()));

        // The actual confidence check: keep only chunks close enough to
        // be genuinely relevant, not just "closest of what existed."
        List<ScoredChunk> relevant = nearest.stream()
            .filter(sc -> sc.distance() <= MAX_RELEVANT_DISTANCE)
            .collect(Collectors.toList());

        if (relevant.isEmpty()) {
            return new DocumentAnswer(
                "I couldn't find anything in this document relevant to your question.",
                List.of()
            );
        }

        String context = relevant.stream()
            .map(sc -> sc.chunk().getContent())
            .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildPrompt(context, question);
        String answer = geminiClient.ask(prompt);

        // Citations: exactly the chunks that actually passed the
        // relevance filter and were sent to Gemini — not every chunk
        // that happened to be retrieved. What's cited is genuinely what
        // grounded the answer, not a superset of "things we looked at."
        List<Citation> citations = relevant.stream()
            .map(sc -> new Citation(sc.chunk().getChunkIndex(), sc.chunk().getContent()))
            .collect(Collectors.toList());

        return new DocumentAnswer(answer, citations);
    }

    private String buildPrompt(String context, String question) {
        return "You are answering a question using ONLY the following document excerpts. " +
               "If the answer is not contained in these excerpts, say you don't know — " +
               "do not use outside knowledge or make up information.\n\n" +
               "Document excerpts:\n" + context + "\n\n" +
               "Question: " + question;
    }

    /** One chunk that actually grounded the answer, exposed for transparency. */
    public record Citation(int chunkIndex, String excerpt) {}

    /** The full response: the answer text, plus exactly what it was grounded in. */
    public record DocumentAnswer(String answer, List<Citation> citations) {}
}