package com.gymmanagement.service;

import com.gymmanagement.model.DocumentChunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RetrievalService in isolation. The empty-result test matters
 * more than its size suggests — DocumentQaService's confidence-based
 * refusal for the naive retrieval path depends entirely on this method
 * returning an empty list when nothing is relevant, not just a
 * low-scoring one.
 */
class RetrievalServiceTest {

    private final RetrievalService retrievalService = new RetrievalService();

    private DocumentChunk chunk(int index, String content) {
        return new DocumentChunk("CHUNK-" + index, "DOC-TEST", index, content);
    }

    @Test
    void chunkWithMatchingKeywordsIsReturned() {
        List<DocumentChunk> chunks = List.of(
            chunk(0, "Membership fees are fifty pounds per month.")
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "How much does membership cost?", chunks);

        assertEquals(1, result.size());
    }

    @Test
    void chunkWithNoMatchingKeywordsIsExcluded() {
        List<DocumentChunk> chunks = List.of(
            chunk(0, "The gym is open Monday through Friday.")
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "What is the capital of France?", chunks);

        assertTrue(result.isEmpty());
    }

    /**
     * The exact behaviour DocumentQaService's naive-retrieval refusal
     * path depends on: a question genuinely unrelated to every available
     * chunk must come back as an empty list, not a low-ranked one —
     * that's the signal the caller uses to skip calling Gemini entirely.
     */
    @Test
    void noRelevantChunksAcrossAnyDocumentReturnsEmptyList() {
        List<DocumentChunk> chunks = List.of(
            chunk(0, "Bootcamp classes run Tuesday and Thursday mornings."),
            chunk(1, "Personal training sessions cost forty pounds each.")
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "What is the airspeed velocity of an unladen swallow?", chunks);

        assertTrue(result.isEmpty());
    }

    @Test
    void stopWordsAloneDoNotCountAsAMatch() {
        // Question and chunk genuinely share several words before
        // filtering ("is", "the", "this", "for") — but every one of
        // them is a stop word, so after filtering there's zero real
        // overlap. A naive substring match would incorrectly call this
        // relevant; keyword-set overlap after stop-word removal should not.
        List<DocumentChunk> chunks = List.of(
            chunk(0, "This is a place for the general public.")
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "What is the address for this office?", chunks);

        assertTrue(result.isEmpty(),
            "A chunk sharing only stop words with the question should not count as relevant");
    }

    @Test
    void moreRelevantChunkRanksBeforeLessRelevantChunk() {
        // Both chunks share at least one keyword with the question (so
        // both pass the filter and appear in the result) — this test is
        // specifically about ORDER between two positive matches, not
        // about which chunks get excluded (that's covered separately).
        List<DocumentChunk> chunks = List.of(
            chunk(0, "Membership information is available at the front desk."), // 1 keyword match: "membership"
            chunk(1, "Membership fees vary based on membership type.")          // 2 keyword matches: "membership", "fees"
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "What are the membership fees?", chunks);

        assertEquals(2, result.size(), "Both chunks share at least one keyword and should both appear");
        assertEquals(1, result.get(0).getChunkIndex(),
            "The chunk with more keyword overlap (2 matches) should rank before the one with fewer (1 match)");
    }

    @Test
    void resultsAreLimitedToTopFive() {
        List<DocumentChunk> manyChunks = List.of(
            chunk(0, "Membership pricing information here."),
            chunk(1, "Membership pricing details available."),
            chunk(2, "Membership pricing plans listed."),
            chunk(3, "Membership pricing options shown."),
            chunk(4, "Membership pricing tiers explained."),
            chunk(5, "Membership pricing summary included."),
            chunk(6, "Membership pricing overview provided.")
        );

        List<DocumentChunk> result = retrievalService.findRelevantChunks(
            "What is the membership pricing?", manyChunks);

        assertEquals(5, result.size(),
            "Even with 7 matching chunks, only the top 5 should be returned");
    }
}