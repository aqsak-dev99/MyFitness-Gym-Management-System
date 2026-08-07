package com.gymmanagement.controller;

import com.gymmanagement.model.Document;
import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.service.DocumentQaService;
import com.gymmanagement.service.DocumentService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DocumentController — document upload/retrieval, chunk viewing, and now
 * (Milestone 3) actually asking questions about a document's content.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService   documentService;
    private final DocumentQaService documentQaService;

    public DocumentController(DocumentService documentService, DocumentQaService documentQaService) {
        this.documentService   = documentService;
        this.documentQaService = documentQaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document upload(@RequestBody UploadDocumentRequest request) {
        return documentService.uploadDocument(request.filename(), request.content());
    }

    @GetMapping
    public List<Document> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/{documentId}")
    public Document getDocument(@PathVariable String documentId) {
        return documentService.getDocument(documentId);
    }

    @GetMapping("/{documentId}/chunks")
    public List<DocumentChunk> getChunks(@PathVariable String documentId) {
        return documentService.getChunks(documentId);
    }

    @PostMapping("/{documentId}/ask")
    public DocumentQaService.DocumentAnswer askAboutDocument(@PathVariable String documentId,
                                                              @RequestBody AskAboutDocumentRequest request) {
        return documentQaService.askAboutDocument(documentId, request.question());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);
    }

    public record UploadDocumentRequest(String filename, String content) {}
    public record AskAboutDocumentRequest(String question) {}
}