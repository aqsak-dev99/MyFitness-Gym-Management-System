package com.gymmanagement.controller;

import com.gymmanagement.config.RequireRole;
import com.gymmanagement.exception.DocumentNotFoundException;
import com.gymmanagement.model.Document;
import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.model.Role;
import com.gymmanagement.service.DocumentQaService;
import com.gymmanagement.service.DocumentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DocumentController — document upload/retrieval, chunk viewing, asking
 * questions, and (added for the Admin/Member RAG split) real audience
 * enforcement.
 *
 * This is genuine server-side security, not just a hidden UI option:
 * getAllDocuments() filters by the CALLER's real role (read from the
 * same request attribute MemberController.getMyProfile() already uses
 * for linkedMemberId — set by JwtAuthenticationFilter, not trusted
 * client input), and askAboutDocument()/getDocument() reject a
 * mismatched role outright, even if someone crafts a request with a
 * document ID they were never shown in their own list.
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
    @RequireRole(Role.ADMIN)
    public Document upload(@Valid @RequestBody UploadDocumentRequest request) {
        return documentService.uploadDocument(request.filename(), request.content(), request.audience());
    }

    /**
     * Filtered by the caller's real role — a MEMBER genuinely never
     * receives ADMIN-audience documents in this list, and vice versa.
     * This is the real fix for the gap found by inspection: before
     * this, any authenticated user saw every document regardless of
     * role.
     */
    @GetMapping
    public List<Document> getAllDocuments(HttpServletRequest request) {
        String callerRole = (String) request.getAttribute("role");
        return documentService.getAllDocuments().stream()
            .filter(d -> d.getAudience().equals(callerRole))
            .toList();
    }

    @GetMapping("/{documentId}")
    public Document getDocument(@PathVariable String documentId, HttpServletRequest request) {
        Document document = documentService.getDocument(documentId);
        rejectIfWrongAudience(document, request);
        return document;
    }

    @GetMapping("/{documentId}/chunks")
    public List<DocumentChunk> getChunks(@PathVariable String documentId, HttpServletRequest request) {
        rejectIfWrongAudience(documentService.getDocument(documentId), request);
        return documentService.getChunks(documentId);
    }

    @PostMapping("/{documentId}/ask")
    public DocumentQaService.DocumentAnswer askAboutDocument(@PathVariable String documentId,
                                                              @Valid @RequestBody AskAboutDocumentRequest request,
                                                              HttpServletRequest httpRequest) {
        rejectIfWrongAudience(documentService.getDocument(documentId), httpRequest);
        return documentQaService.askAboutDocument(documentId, request.question());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireRole(Role.ADMIN)
    public void deleteDocument(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);
    }

    /**
     * The real enforcement point shared by every read/ask endpoint
     * above — a MEMBER directly guessing or reusing an ADMIN document's
     * ID (bypassing the already-filtered list) still gets rejected here,
     * not just hidden from view.
     */
    private void rejectIfWrongAudience(Document document, HttpServletRequest request) {
        String callerRole = (String) request.getAttribute("role");
        if (!document.getAudience().equals(callerRole)) {
            throw new DocumentNotFoundException(
                "No document found with ID: " + document.getDocumentId());
        }
    }

    public record UploadDocumentRequest(@NotBlank String filename, @NotBlank String content, @NotBlank String audience) {}
    public record AskAboutDocumentRequest(@NotBlank String question) {}
}