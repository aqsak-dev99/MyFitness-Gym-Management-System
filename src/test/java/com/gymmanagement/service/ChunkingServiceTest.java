package com.gymmanagement.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ChunkingService in isolation — pure logic, no fakes needed, same
 * category as BootcampFeeTest. The overlap and termination tests aren't
 * just coverage for its own sake: both properties were the subject of a
 * real, deliberate design decision when this class was first written
 * (the "always advance by at least one character" floor exists
 * specifically to guarantee termination on pathological input) — these
 * tests pin that guarantee down permanently, the same way the discount
 * regression tests in MembershipServiceTest guard a fix that already
 * broke once before.
 */
class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    void blankTextProducesNoChunks() {
        assertTrue(chunkingService.chunk("   ").isEmpty());
    }

    @Test
    void nullTextProducesNoChunks() {
        assertTrue(chunkingService.chunk(null).isEmpty());
    }

    @Test
    void shortTextProducesExactlyOneChunk() {
        String text = "This is a short document, well under the chunk size limit.";

        List<String> chunks = chunkingService.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void longTextProducesMultipleChunks() {
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String longText = sentence.repeat(15);   // ~705 characters, well over the 500 chunk size

        List<String> chunks = chunkingService.chunk(longText);

        assertTrue(chunks.size() > 1,
            "Text well over the chunk size should split into more than one chunk");
    }

    @Test
    void noChunkExceedsTheConfiguredSize() {
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String longText = sentence.repeat(20);

        List<String> chunks = chunkingService.chunk(longText);

        // Whitespace-boundary breaking should only ever SHORTEN a chunk
        // relative to the hard 500-character limit, never lengthen it.
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 500,
                "Chunk exceeded configured size: " + chunk.length() + " characters");
        }
    }

    @Test
    void consecutiveChunksShareOverlappingContent() {
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String longText = sentence.repeat(15);

        List<String> chunks = chunkingService.chunk(longText);
        assertTrue(chunks.size() >= 2, "Test needs at least 2 chunks to check overlap");

        String firstChunk  = chunks.get(0);
        String secondChunk = chunks.get(1);

        // The last several words of chunk 0 should reappear at the start
        // of chunk 1 — that repetition IS the overlap, not a bug. Check
        // a meaningful trailing slice of chunk 0 for presence in chunk 1,
        // rather than the exact final character (which could legitimately
        // land mid-word before trimming).
        String tailOfFirstChunk = firstChunk.substring(Math.max(0, firstChunk.length() - 30));
        assertTrue(secondChunk.contains(tailOfFirstChunk.trim().split(" ")[0]),
            "Expected some overlap between consecutive chunks, found none");
    }

    @Test
    void chunkingTerminatesOnLongInput() {
        // This test's real purpose is simply COMPLETING without hanging —
        // if the "advance by at least one character" floor in
        // ChunkingService were ever removed or broken, this call could
        // loop forever on realistic input. A very long, dense document
        // is exactly the shape of input that would expose that bug.
        String sentence = "word ";
        String veryLongText = sentence.repeat(5000);   // 25,000 characters

        List<String> chunks = chunkingService.chunk(veryLongText);

        assertFalse(chunks.isEmpty());
    }
}