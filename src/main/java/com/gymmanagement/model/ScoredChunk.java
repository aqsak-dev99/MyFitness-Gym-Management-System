package com.gymmanagement.model;

/**
 * ScoredChunk — a DocumentChunk paired with its cosine distance from a
 * query, as reported by pgvector's `<=>` operator: 0 means identical
 * direction (as relevant as it gets), 1 means unrelated, 2 means opposite.
 *
 * Exists specifically so DocumentQaService can make a real confidence
 * decision — "was this chunk actually relevant, or just the least-bad
 * option available" — instead of only knowing whether a chunk was found
 * at all.
 */
public record ScoredChunk(DocumentChunk chunk, double distance) {}