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
}