package com.gymmanagement.service;

import java.util.List;

/** AiEmbeddingClient — the contract GeminiEmbeddingClient fulfills. Same reasoning as AiChatClient. */
public interface AiEmbeddingClient {
    List<float[]> embedChunks(List<String> chunkTexts);
    float[] embedQuestion(String question);
}