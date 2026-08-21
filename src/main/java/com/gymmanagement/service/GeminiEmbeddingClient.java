package com.gymmanagement.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.gymmanagement.exception.AiServiceException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GeminiEmbeddingClient — Milestone 4. Turns text into vectors using
 * Gemini's embedding model, the piece that makes real semantic search
 * possible instead of RetrievalService's keyword overlap.
 *
 * Two public methods, not one, and that split is deliberate rather than
 * just "documents vs questions" naming convenience: Gemini's embedding
 * model takes a taskType hint, and RETRIEVAL_DOCUMENT vs RETRIEVAL_QUERY
 * genuinely produce different vectors for the same text. A question
 * ("What are the membership fees?") and the sentence that answers it
 * ("Monthly membership costs $50") don't share much vocabulary, but the
 * model is trained so a document embedded as RETRIEVAL_DOCUMENT and a
 * question embedded as RETRIEVAL_QUERY land close together in vector
 * space anyway. Embedding both the same way throws that asymmetry away —
 * a genuinely worse result, not just a style difference.
 *
 * Every call goes through GeminiRateLimiter — the SAME shared Spring
 * bean GeminiClient already uses, so chat calls and embedding calls draw
 * down one combined 5/minute budget rather than each getting their own.
 */
@Service
public class GeminiEmbeddingClient implements AiEmbeddingClient {

    private static final String DEFAULT_MODEL = "gemini-embedding-001";

    // gemini-embedding-001 natively outputs 3072-dimensional vectors.
    // The plan was originally to request 768 via outputDimensionality
    // (Google's own Matryoshka-truncation feature, meant to cut storage
    // and comparison cost 4x with minimal quality loss) — but the
    // batchEmbedContents endpoint used here did NOT honour that field
    // in testing: it returned full 3072-dimensional vectors regardless
    // of what was requested, confirmed by a real Postgres
    // "expected 768 dimensions, not 3072" error. Rather than keep
    // fighting an unconfirmed API quirk, this now accepts the model's
    // native size and DatabaseSchema's embedding column matches it —
    // a working 3072-dimensional vector beats a broken, smaller one.

    private final String            model;
    private final String            apiKey;
    private final RestClient        restClient;
    private final GeminiRateLimiter rateLimiter;

    public GeminiEmbeddingClient(GeminiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY environment variable is not set. " +
                "Get a free key at aistudio.google.com and export it before running.");
        }
        // Same resilience pattern as GEMINI_MODEL for the chat client —
        // not because the embedding model has shown any instability,
        // but because "configurable, with a safe default" costs nothing
        // and matches how this project already treats Gemini model names.
        String configured = System.getenv("GEMINI_EMBEDDING_MODEL");
        this.model = (configured == null || configured.isBlank()) ? DEFAULT_MODEL : configured;
        this.restClient = RestClient.create();
    }

    /**
     * Embeds every chunk of a document in ONE HTTP call via
     * batchEmbedContents, rather than one call per chunk. That's not
     * just an efficiency nicety — GeminiRateLimiter caps this app at 5
     * Gemini requests per minute total, and a document that chunks into,
     * say, 12 pieces would blow straight through that limit partway
     * through an upload if each chunk were its own request. One document
     * upload now costs exactly 1 request here, regardless of chunk count.
     */
    public List<float[]> embedChunks(List<String> chunkTexts) {
        return callBatchEmbed(chunkTexts, "RETRIEVAL_DOCUMENT");
    }

    /** Embeds a single incoming question at ask-time. */
    public float[] embedQuestion(String question) {
        return callBatchEmbed(List.of(question), "RETRIEVAL_QUERY").get(0);
    }

    private List<float[]> callBatchEmbed(List<String> texts, String taskType) {
        rateLimiter.checkAllowed();   // throws BEFORE any network call if over limit

        List<EmbedContentRequestItem> requests = texts.stream()
            .map(text -> new EmbedContentRequestItem(
                "models/" + model,
                new EmbedContent(List.of(new EmbedContentPart(text))),
                new EmbedContentConfig(taskType)))
            .collect(Collectors.toList());

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":batchEmbedContents";

        try {
            BatchEmbedContentsResponse response = restClient.post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BatchEmbedContentsRequest(requests))
                .retrieve()
                .body(BatchEmbedContentsResponse.class);

            if (response == null || response.embeddings() == null
                    || response.embeddings().size() != texts.size()) {
                throw new AiServiceException("Gemini returned an unexpected embedding response.");
            }

            return response.embeddings().stream()
                .map(GeminiEmbeddingClient::toFloatArray)
                .collect(Collectors.toList());

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            // Same philosophy as GeminiClient's catch block: wrap
            // whatever went wrong (network failure, non-2xx response,
            // malformed JSON) into one project-specific exception type.
            // No automatic retry here either.
            throw new AiServiceException("Failed to get embeddings from Gemini: " + e.getMessage());
        }
    }

    private static float[] toFloatArray(ContentEmbedding embedding) {
        List<Double> values = embedding.values();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }
}

// ── Request/response shapes for Gemini's batchEmbedContents endpoint ──
// A separate, smaller record set from GeminiRequest/GeminiContent/
// GeminiPart in GeminiClient.java. Those model generateContent's shape
// (a list of conversational turns); embedding requests are shaped
// completely differently (a batch of independent texts, each with its
// own task type), so reusing those records would mean fighting their
// shape rather than modeling this one honestly.
//
// EmbedContentRequestItem's third field is now the taskType string
// directly, not a nested EmbedContentConfig object — the config object
// existed specifically to carry outputDimensionality, which is gone now
// that truncation isn't being requested.

record EmbedContentPart(String text) {}

record EmbedContent(List<EmbedContentPart> parts) {}

// outputDimensionality removed — it was here to request 768-dimension
// truncation, but batchEmbedContents ignored it in testing and returned
// the model's native 3072 dimensions regardless. Same field structure
// otherwise as the original working request, since only the dimension
// request itself was the problem, not the request's shape.
record EmbedContentConfig(String taskType) {}

record EmbedContentRequestItem(String model, EmbedContent content, EmbedContentConfig embedContentConfig) {}

record BatchEmbedContentsRequest(List<EmbedContentRequestItem> requests) {}

record ContentEmbedding(List<Double> values) {}

record BatchEmbedContentsResponse(List<ContentEmbedding> embeddings) {}