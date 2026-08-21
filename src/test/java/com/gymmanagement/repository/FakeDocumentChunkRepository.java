package com.gymmanagement.repository;

import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.model.ScoredChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written fake implementation of DocumentChunkRepository.
 *
 * findNearestByEmbedding() doesn't do any real vector math — it just
 * returns whatever the test configured via setNearestResult(). That's
 * deliberate: real cosine-distance ranking is PostgresDocumentChunkRepository's
 * job, already exercised for real against a live database. A unit test
 * of DocumentQaService only needs to control "given these scored chunks,
 * does the service filter/refuse/build a prompt correctly" — it has no
 * reason to reimplement pgvector's actual math.
 */
public class FakeDocumentChunkRepository implements DocumentChunkRepository {

    private final List<DocumentChunk> chunks = new ArrayList<>();
    private List<ScoredChunk> nearestResult = new ArrayList<>();

    @Override
    public void save(DocumentChunk chunk) {
        chunks.add(chunk);
    }

    @Override
    public List<DocumentChunk> findByDocumentId(String documentId) {
        return chunks.stream()
                     .filter(c -> c.getDocumentId().equals(documentId))
                     .toList();
    }

    @Override
    public void saveEmbedding(String chunkId, float[] embedding) {
        // No-op — this fake never needs to actually store a vector,
        // since findNearestByEmbedding() below doesn't read from `chunks`.
    }

    @Override
    public List<ScoredChunk> findNearestByEmbedding(String documentId, float[] queryEmbedding, int topK) {
        return new ArrayList<>(nearestResult);
    }

    /** Test setup hook — controls exactly what findNearestByEmbedding() returns next. */
    public void setNearestResult(List<ScoredChunk> result) {
        this.nearestResult = result;
    }
}
