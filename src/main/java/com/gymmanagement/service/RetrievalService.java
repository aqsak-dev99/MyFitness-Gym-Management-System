 package com.gymmanagement.service;

import com.gymmanagement.model.DocumentChunk;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RetrievalService — finds which chunks are most relevant to a question,
 * using keyword overlap. This is "RAG v1" retrieval: no embeddings, no
 * vector math, no external calls — just counting how many meaningful
 * words a question and a chunk share. This is a genuine simplified form
 * of real information retrieval (a rough ancestor of TF-IDF/BM25, the
 * techniques search engines used before embeddings became common), not
 * a fake shortcut standing in for the real thing.
 *
 * Pure logic, no database or API dependencies — takes chunks in, returns
 * ranked chunks out. This is exactly what makes the future upgrade to
 * pgvector clean: only THIS class gets replaced, nothing else in the
 * pipeline (chunking, prompt building, the Gemini call) needs to change,
 * since everything downstream just consumes "the top few relevant chunks"
 * regardless of how they were selected.
 */
@Service
public class RetrievalService {

    private static final int TOP_K = 5;

    // Common words filtered out before scoring — without this, almost
    // every chunk would score similarly just from sharing "the", "is",
    // "a" with the question, drowning out genuinely relevant matches.
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "should",
        "could", "may", "might", "must", "can", "of", "in", "on", "at", "to",
        "for", "with", "by", "from", "about", "as", "into", "like", "through",
        "and", "or", "but", "if", "then", "so", "what", "how", "why", "when",
        "where", "who", "which", "this", "that", "these", "those", "i", "you",
        "it", "we", "they", "my", "your", "its", "our", "their"
    );

    /**
     * Returns the top-scoring chunks for a question, best match first.
     * Chunks with zero keyword overlap are excluded entirely — an empty
     * result means "nothing in this document looked relevant," which the
     * caller uses to skip calling Gemini at all (see DocumentQaService).
     */
    public List<DocumentChunk> findRelevantChunks(String question, List<DocumentChunk> allChunks) {
        Set<String> questionKeywords = tokenize(question);

        return allChunks.stream()
            .map(chunk -> Map.entry(chunk, scoreChunk(chunk, questionKeywords)))
            .filter(entry -> entry.getValue() > 0)
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(TOP_K)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /** Score = how many distinct question-keywords this chunk contains. */
    private int scoreChunk(DocumentChunk chunk, Set<String> questionKeywords) {
        Set<String> chunkKeywords = tokenize(chunk.getContent());
        int score = 0;
        for (String keyword : questionKeywords) {
            if (chunkKeywords.contains(keyword)) score++;
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
            .filter(word -> !word.isBlank())
            .filter(word -> !STOP_WORDS.contains(word))
            .collect(Collectors.toSet());
    }
}
