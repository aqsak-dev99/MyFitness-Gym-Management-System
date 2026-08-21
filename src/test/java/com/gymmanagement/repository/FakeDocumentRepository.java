package com.gymmanagement.repository;

import com.gymmanagement.model.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A hand-written fake implementation of DocumentRepository, used ONLY in tests. Same pattern as FakeMemberRepository. */
public class FakeDocumentRepository implements DocumentRepository {

    private final List<Document> documents = new ArrayList<>();

    @Override
    public void save(Document document) {
        documents.removeIf(d -> d.getDocumentId().equals(document.getDocumentId()));
        documents.add(document);
    }

    @Override
    public Optional<Document> findById(String documentId) {
        return documents.stream()
                        .filter(d -> d.getDocumentId().equals(documentId))
                        .findFirst();
    }

    @Override
    public List<Document> findAll() {
        return new ArrayList<>(documents);
    }

    @Override
    public void delete(String documentId) {
        documents.removeIf(d -> d.getDocumentId().equals(documentId));
    }
}