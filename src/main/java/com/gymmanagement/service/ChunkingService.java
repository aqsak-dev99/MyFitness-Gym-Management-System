package com.gymmanagement.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ChunkingService — splits a document's text into overlapping pieces.
 *
 * Deliberately a pure function with no database access and no other
 * dependencies: given text in, chunks out, nothing else. That makes it
 * trivially easy to reason about correctness (and to unit test later) in
 * complete isolation from anything to do with Postgres or Gemini.
 *
 * Strategy: fixed-size chunks (CHUNK_SIZE characters), with a small
 * overlap (OVERLAP characters) between consecutive chunks. Overlap exists
 * so a sentence that happens to fall right on a chunk boundary doesn't
 * lose all its surrounding context in either chunk — a real, standard
 * technique in RAG systems, not a shortcut. Chunks prefer to break on a
 * whitespace boundary rather than mid-word, when one is reasonably close
 * to the target size.
 *
 * This is "RAG v1" chunking — naive, character-count-based, not aware of
 * sentence or paragraph structure. The natural upgrade later (semantic or
 * structure-aware chunking, as the original feature checklist called it)
 * replaces only this class — nothing else in the pipeline needs to change
 * when that happens, since everything downstream just consumes a
 * List<String> regardless of how it was produced.
 */
@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP    = 50;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int position   = 0;
        int textLength = text.length();

        while (position < textLength) {
            int hardEnd   = Math.min(position + CHUNK_SIZE, textLength);
            int actualEnd = hardEnd;

            // Prefer breaking at a whitespace boundary rather than
            // mid-word — but only if that space is meaningfully past
            // where this chunk started, so we don't produce a near-empty
            // chunk just to avoid splitting one word.
            if (hardEnd < textLength) {
                int lastSpace = text.lastIndexOf(' ', hardEnd);
                if (lastSpace > position) {
                    actualEnd = lastSpace;
                }
            }

            String chunkText = text.substring(position, actualEnd).trim();
            if (!chunkText.isEmpty()) {
                chunks.add(chunkText);
            }

            if (actualEnd >= textLength) break;

            // Move forward, creating overlap with the next chunk — but
            // always advance past `position` by at least one character.
            // Without this floor, a very short chunk (actualEnd close to
            // position, e.g. from a nearby whitespace break) combined
            // with overlap could produce nextPosition <= position,
            // looping on the same spot in the text forever. This one
            // line is the entire guarantee that this method always
            // terminates.
            int nextPosition = actualEnd - OVERLAP;
            position = Math.max(nextPosition, position + 1);
        }

        return chunks;
    }
}