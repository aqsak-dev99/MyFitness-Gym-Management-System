package com.gymmanagement.model;

import java.time.LocalDate;

/**
 * Document — a stored piece of text (nutrition guide, workout manual, etc.)
 * that members will eventually be able to ask questions about.
 *
 * Milestone 1 scope: raw storage only. No chunking, no embeddings — this
 * class just proves a document can be uploaded and retrieved correctly.
 * Chunking and retrieval logic build on top of this once storage is proven,
 * same discipline as everything else in this project.
 */
public class Document {

    private final String    documentId;
    private final String    filename;
    private final String    content;
    private final LocalDate uploadedAt;
    private final String    audience;   // "MEMBER" or "ADMIN" — see DatabaseSchema.ADD_DOCUMENT_AUDIENCE_COLUMN

    public Document(String documentId, String filename, String content, LocalDate uploadedAt, String audience) {
        if (documentId == null || documentId.isBlank())
            throw new IllegalArgumentException("Document ID cannot be empty.");
        if (filename == null || filename.isBlank())
            throw new IllegalArgumentException("Filename cannot be empty.");
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Document content cannot be empty.");

        this.documentId = documentId;
        this.filename   = filename;
        this.content    = content;
        this.uploadedAt = uploadedAt != null ? uploadedAt : LocalDate.now();
        this.audience   = (audience == null || audience.isBlank()) ? "MEMBER" : audience;
    }

    public String    getDocumentId() { return documentId; }
    public String    getFilename()   { return filename;   }
    public String    getContent()    { return content;    }
    public LocalDate getUploadedAt() { return uploadedAt;  }
    public String    getAudience()   { return audience;    }
}