package com.gymmanagement.service;

import com.gymmanagement.exception.DocumentNotFoundException;
import com.gymmanagement.model.Document;
import com.gymmanagement.model.DocumentChunk;
import com.gymmanagement.model.ScoredChunk;
import com.gymmanagement.repository.FakeDocumentChunkRepository;
import com.gymmanagement.repository.FakeDocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DocumentQaService using hand-rolled fakes for everything —
 * repositories AND the Gemini clients, via the AiChatClient/
 * AiEmbeddingClient interfaces. No network call, no database, and no
 * GEMINI_API_KEY needed to run this test at all.
 *
 * The real ranking a live embedding call would produce isn't
 * reproduced here — FakeDocumentChunkRepository.setNearestResult()
 * controls exactly what "comes back from the vector search" per test.
 * That's deliberate: this test is about whether DocumentQaService makes
 * the right decision GIVEN a set of scored chunks, not about whether
 * pgvector's cosine math is correct (that's exercised for real against
 * a live database, not something to reproduce in a unit test).
 */
class DocumentQaServiceTest {

    private static final String DOC_ID = "DOC-TEST-1";

    private FakeDocumentChunkRepository chunkRepo;
    private FakeAiChatClient            geminiClient;
    private DocumentQaService           documentQaService;

    @BeforeEach
    void setUp() {
        FakeDocumentRepository documentRepo = new FakeDocumentRepository();
        chunkRepo = new FakeDocumentChunkRepository();
        FakeAiEmbeddingClient embeddingClient = new FakeAiEmbeddingClient();
        geminiClient = new FakeAiChatClient();

        DocumentService documentService = new DocumentService(
            documentRepo, chunkRepo, new ChunkingService(), embeddingClient);

        documentQaService = new DocumentQaService(chunkRepo, embeddingClient, geminiClient, documentService);

        documentRepo.save(new Document(
            DOC_ID, "test.txt", "Membership fees are fifty pounds a month.", LocalDate.now(), "MEMBER"));
    }

    private DocumentChunk chunk(int index, String content) {
        return new DocumentChunk("CHUNK-" + index, DOC_ID, index, content);
    }

    @Test
    void relevantChunkProducesGroundedAnswerWithCitation() {
        // Distance 0.2 is well under the 0.45 threshold — genuinely relevant.
        chunkRepo.setNearestResult(List.of(
            new ScoredChunk(chunk(0, "Membership fees are fifty pounds a month."), 0.2)
        ));
        geminiClient.setCannedResponse("Fifty pounds a month.");

        DocumentQaService.DocumentAnswer result =
            documentQaService.askAboutDocument(DOC_ID, "How much does membership cost?");

        assertEquals("Fifty pounds a month.", result.answer());
        assertEquals(1, result.citations().size());
        assertEquals(0, result.citations().get(0).chunkIndex());
    }

    /**
     * The real point of this test isn't the returned text — it's proving
     * Gemini was never actually CALLED. If confidence-based refusal ever
     * regressed to "call Gemini anyway and hope it says I don't know,"
     * this is the test that would catch it, by checking the fake's
     * lastPromptReceived stayed null.
     */
    @Test
    void noRelevantChunksReturnsRefusalWithoutCallingGemini() {
        // Distance 0.8 is well above the 0.45 threshold — this chunk was
        // "found" by the search, but isn't actually relevant.
        chunkRepo.setNearestResult(List.of(
            new ScoredChunk(chunk(0, "The gym is open Monday through Friday."), 0.8)
        ));

        DocumentQaService.DocumentAnswer result =
            documentQaService.askAboutDocument(DOC_ID, "What is the capital of France?");

        assertEquals("I couldn't find anything in this document relevant to your question.",
            result.answer());
        assertTrue(result.citations().isEmpty());
        assertNull(geminiClient.getLastPromptReceived(),
            "Gemini should never be called when no chunk passes the relevance threshold");
    }

    @Test
    void nonexistentDocumentThrowsNotFoundException() {
        assertThrows(DocumentNotFoundException.class, () ->
            documentQaService.askAboutDocument("DOC-DOES-NOT-EXIST", "Anything?"));
    }

    /**
     * Pins down the actual mechanism that makes this "document Q&A"
     * rather than just "ask Gemini anything" — the explicit grounding
     * instruction has to genuinely be in the prompt, not just described
     * in a comment.
     */
    @Test
    void promptSentToGeminiIncludesGroundingInstruction() {
        chunkRepo.setNearestResult(List.of(
            new ScoredChunk(chunk(0, "Membership fees are fifty pounds a month."), 0.1)
        ));

        documentQaService.askAboutDocument(DOC_ID, "How much does it cost?");

        String actualPrompt = geminiClient.getLastPromptReceived();
        assertNotNull(actualPrompt);
        assertTrue(actualPrompt.contains("using ONLY"),
            "Prompt must instruct Gemini to answer only from the provided excerpts");
        assertTrue(actualPrompt.contains("say you don't know"),
            "Prompt must instruct Gemini to refuse rather than guess when the answer isn't present");
    }
}