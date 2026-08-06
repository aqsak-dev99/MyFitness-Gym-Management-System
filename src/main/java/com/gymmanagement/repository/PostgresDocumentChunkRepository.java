package com.gymmanagement.repository;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.DocumentChunk;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostgresDocumentChunkRepository implements DocumentChunkRepository {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    @Override
    public void save(DocumentChunk chunk) {
        String sql =
            "INSERT INTO document_chunks (chunk_id, document_id, chunk_index, content) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(chunk_id) DO UPDATE SET " +
            "  document_id = excluded.document_id, " +
            "  chunk_index = excluded.chunk_index, " +
            "  content = excluded.content";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, chunk.getChunkId());
            ps.setString(2, chunk.getDocumentId());
            ps.setInt(3, chunk.getChunkIndex());
            ps.setString(4, chunk.getContent());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chunk " + chunk.getChunkId()
                                       + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentChunk> findByDocumentId(String documentId) {
        String sql = "SELECT * FROM document_chunks WHERE document_id = ? ORDER BY chunk_index ASC";
        List<DocumentChunk> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DocumentChunk(
                        rs.getString("chunk_id"),
                        rs.getString("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByDocumentId failed: " + e.getMessage(), e);
        }
        return result;
    }

    // ── Milestone 4: embeddings + pgvector ──────────────

    @Override
    public void saveEmbedding(String chunkId, float[] embedding) {
        String sql = "UPDATE document_chunks SET embedding = ?::vector WHERE chunk_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, toVectorLiteral(embedding));
            ps.setString(2, chunkId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("saveEmbedding: no chunk found with ID " + chunkId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("saveEmbedding failed for chunk " + chunkId
                                       + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentChunk> findNearestByEmbedding(String documentId, float[] queryEmbedding, int topK) {
        // `<=>` is pgvector's cosine DISTANCE operator (0 = identical
        // direction, 2 = opposite) — smaller means more similar, which is
        // exactly why this ORDER BY needs ASC, not DESC. Chunks with a
        // NULL embedding (uploaded before this migration existed) are
        // excluded explicitly rather than relying on Postgres's default
        // NULLS-LAST ordering to push them out of the LIMIT — that
        // default behaviour is real, but leaving it implicit would make
        // this query's intent (only ever compare chunks that actually
        // have a vector) harder to read a year from now.
        String sql =
            "SELECT chunk_id, document_id, chunk_index, content " +
            "FROM document_chunks " +
            "WHERE document_id = ? AND embedding IS NOT NULL " +
            "ORDER BY embedding <=> ?::vector ASC " +
            "LIMIT ?";
        List<DocumentChunk> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, documentId);
            ps.setString(2, toVectorLiteral(queryEmbedding));
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DocumentChunk(
                        rs.getString("chunk_id"),
                        rs.getString("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findNearestByEmbedding failed: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Formats a float[] as the text form pgvector expects, e.g.
     * "[0.1,0.2,0.3]". Sent as a plain String parameter and cast with
     * ::vector directly in the SQL above — deliberately not pulling in
     * the separate pgvector-java driver library, which would need its
     * own connection-level type registration on top of the plain JDBC
     * DriverManager connection DatabaseManager already hands out. Every
     * other query in this project is hand-written JDBC; a two-line
     * string formatter is a smaller addition than a new dependency for
     * one data type.
     */
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}