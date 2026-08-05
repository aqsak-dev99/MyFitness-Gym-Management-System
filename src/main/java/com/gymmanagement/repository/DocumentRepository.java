package com.gymmanagement.repository;

import com.gymmanagement.model.Document;

import java.util.List;
import java.util.Optional;

/** Contract for all Document data-access implementations. */
public interface DocumentRepository {
    void             save(Document document);
    Optional<Document> findById(String documentId);
    List<Document>   findAll();
    void             delete(String documentId);
}
