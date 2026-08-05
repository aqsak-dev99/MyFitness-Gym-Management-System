package com.gymmanagement.repository;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.Document;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgresDocumentRepository — named for the actual current database,
 * unlike the older SqliteMemberRepository/SqliteBootcampRepository/
 * SqliteUserRepository classes, which kept their pre-migration names.
 * Since this is brand new code with no legacy naming to carry forward,
 * it's named correctly from the start rather than repeating that
 * inconsistency.
 *
 * Same safe-upsert pattern as every other repository in this project:
 * ON CONFLICT DO UPDATE, never INSERT OR REPLACE — same reasoning as
 * SqliteMemberRepository.upsertMember() from earlier in the project.
 */
@Repository
public class PostgresDocumentRepository implements DocumentRepository {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    @Override
    public void save(Document document) {
        String sql =
            "INSERT INTO documents (document_id, filename, content, uploaded_at) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(document_id) DO UPDATE SET " +
            "  filename = excluded.filename, " +
            "  content = excluded.content, " +
            "  uploaded_at = excluded.uploaded_at";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, document.getDocumentId());
            ps.setString(2, document.getFilename());
            ps.setString(3, document.getContent());
            ps.setString(4, document.getUploadedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save document " + document.getDocumentId()
                                       + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Document> findById(String documentId) {
        String sql = "SELECT * FROM documents WHERE document_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildDocument(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Document> findAll() {
        String sql = "SELECT * FROM documents ORDER BY uploaded_at DESC";
        List<Document> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(buildDocument(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll documents failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public void delete(String documentId) {
        String sql = "DELETE FROM documents WHERE document_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, documentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete document failed: " + e.getMessage(), e);
        }
    }

    private Document buildDocument(ResultSet rs) throws SQLException {
        return new Document(
            rs.getString("document_id"),
            rs.getString("filename"),
            rs.getString("content"),
            LocalDate.parse(rs.getString("uploaded_at"))
        );
    }
}
