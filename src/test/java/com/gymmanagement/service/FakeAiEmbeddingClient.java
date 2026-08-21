package com.gymmanagement.service;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written fake implementation of AiEmbeddingClient, used ONLY in
 * tests. The actual vectors returned don't matter for testing
 * DocumentQaService/BootcampRecommendationService — the ranking a real
 * embedding would produce is controlled directly via
 * FakeDocumentChunkRepository.setNearestResult() instead. This fake only
 * needs to return *something* of the right shape without ever calling
 * Gemini.
 */
public class FakeAiEmbeddingClient implements AiEmbeddingClient {

    @Override
    public List<float[]> embedChunks(List<String> chunkTexts) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            result.add(new float[]{1.0f, 0.0f, 0.0f});
        }
        return result;
    }

    @Override
    public float[] embedQuestion(String question) {
        return new float[]{1.0f, 0.0f, 0.0f};
    }
}