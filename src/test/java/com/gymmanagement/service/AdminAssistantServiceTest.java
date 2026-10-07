package com.gymmanagement.service;

import com.gymmanagement.exception.AiRateLimitExceededException;
import com.gymmanagement.exception.AiServiceException;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.service.ToolChatModel.FunctionResponse;
import com.gymmanagement.service.ToolChatModel.Part;
import com.gymmanagement.service.ToolChatModel.Turn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Admin AI tool-calling loop in AdminAssistantService, driving
 * it with a scripted FakeToolChatModel — so the whole flow
 * (question → tool request → real tool → real service → result handed
 * back → answer) is exercised for real, with nothing but the model
 * itself faked. No network, no API key.
 */
class AdminAssistantServiceTest {

    private FakeMemberRepository memberRepo;
    private FakeToolChatModel    model;
    private AdminAssistantService assistant;

    @BeforeEach
    void setUp() {
        memberRepo = new FakeMemberRepository();
        MemberService memberService = new MemberService(memberRepo);
        MembershipService membershipService = new MembershipService(memberRepo, new FakeBootcampRepository());
        AdminAiTools tools = new AdminAiTools(new AdminOperationsService(memberService, membershipService));

        model     = new FakeToolChatModel();
        assistant = new AdminAssistantService(model, tools);

        // One real, dated, completed payment so tool results contain real numbers.
        Member member = memberService.registerMember("P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        Payment payment = new Payment("PAY-1", 40.00, "Membership renewal", member);
        payment.markCompleted();
        payment.setPaymentDateFromDb(LocalDate.of(2026, 9, 10));
        member.addPayment(payment);
        memberRepo.save(member);
    }

    /** All function responses the loop sent back in one "user" turn. */
    private List<FunctionResponse> responsesIn(Turn turn) {
        return turn.parts().stream().map(Part::functionResponse).toList();
    }

    // ── no tool needed ────────────────────────────────────

    @Test
    void aQuestionThatNeedsNoDataIsAnsweredInASingleModelCall() {
        model.thenText("Peak hours are usually early morning and evenings.");

        String answer = assistant.askAdminAssistant("When is a gym usually busiest?");

        assertEquals("Peak hours are usually early morning and evenings.", answer);
        assertEquals(1, model.getCalls().size());
    }

    @Test
    void theModelIsOfferedExactlyTheAllowListedTools() {
        model.thenText("ok");

        assistant.askAdminAssistant("hello");

        List<String> offered = model.lastCall().tools().stream().map(t -> t.name()).sorted().toList();
        assertEquals(List.of("getClassOccupancy", "getMembershipStats", "getOverdueMembers",
                             "getRecentPayments", "getRevenueSummary"), offered);
    }

    // ── the tool round-trip ───────────────────────────────

    @Test
    void aToolRequestIsExecutedForRealAndItsStructuredResultIsHandedBackToTheModel() {
        model.thenCall("getRevenueSummary", Map.of("startDate", "2026-09-01", "endDate", "2026-09-30"))
             .thenText("You made £40.00 in September.");

        String answer = assistant.askAdminAssistant("How much revenue did we make in September?");

        assertEquals("You made £40.00 in September.", answer);
        assertEquals(2, model.getCalls().size());

        // The second model call must contain: the question, the model's own tool request, and the REAL result.
        List<Turn> secondConversation = model.getCalls().get(1).conversation();
        assertEquals(3, secondConversation.size());
        assertEquals("user", secondConversation.get(0).role());
        assertEquals("model", secondConversation.get(1).role());
        assertEquals("user", secondConversation.get(2).role());

        FunctionResponse response = responsesIn(secondConversation.get(2)).get(0);
        assertEquals("getRevenueSummary", response.name());
        assertEquals(40.0, ((Number) response.response().get("totalRevenue")).doubleValue());
        assertEquals(1, ((Number) response.response().get("paymentCount")).intValue());
    }

    @Test
    void theModelsOwnToolTurnIsEchoedBackUnchangedSoThoughtSignaturesSurvive() {
        Part signedCall = new Part(null,
            new ToolChatModel.FunctionCall("getMembershipStats", Map.of(), "call-1"), null, "opaque-signature");
        Turn modelTurn = new Turn("model", List.of(signedCall));
        model.thenTurn(modelTurn).thenText("done");

        assistant.askAdminAssistant("How many members do we have?");

        List<Turn> secondConversation = model.getCalls().get(1).conversation();
        assertSame(modelTurn, secondConversation.get(1));   // same object, byte-for-byte what Gemini sent
        assertEquals("call-1", responsesIn(secondConversation.get(2)).get(0).id());   // call id echoed on the response
    }

    @Test
    void severalToolRequestsInOneModelTurnAreAllAnsweredInOrder() {
        model.thenCalls(List.of(
                FakeToolChatModel.callPart("getRevenueSummary", Map.of(), "a"),
                FakeToolChatModel.callPart("getOverdueMembers", Map.of(), "b"),
                FakeToolChatModel.callPart("getClassOccupancy", Map.of(), "c")))
             .thenText("Here is the summary.");

        assistant.askAdminAssistant("Give me revenue, overdue members and class capacity.");

        List<FunctionResponse> responses = responsesIn(model.getCalls().get(1).conversation().get(2));
        assertEquals(List.of("getRevenueSummary", "getOverdueMembers", "getClassOccupancy"),
            responses.stream().map(FunctionResponse::name).toList());
        assertEquals(List.of("a", "b", "c"), responses.stream().map(FunctionResponse::id).toList());
    }

    @Test
    void theModelCanTakeMoreThanOneRoundBeforeAnswering() {
        model.thenCall("getMembershipStats", Map.of())
             .thenCall("getOverdueMembers", Map.of())
             .thenText("Two things checked.");

        String answer = assistant.askAdminAssistant("Compare membership numbers with overdue payers.");

        assertEquals("Two things checked.", answer);
        assertEquals(3, model.getCalls().size());
        assertEquals(5, model.lastCall().conversation().size());   // question + 2×(model turn, result turn)
    }

    // ── errors are data, not crashes ──────────────────────

    @Test
    void aMadeUpToolNameIsAnsweredWithAnErrorTheModelCanReadAndTheLoopContinues() {
        model.thenCall("deleteAllMembers", Map.of())
             .thenText("I can't do that — I can only report information.");

        String answer = assistant.askAdminAssistant("Delete everyone.");

        assertEquals("I can't do that — I can only report information.", answer);
        FunctionResponse response = responsesIn(model.getCalls().get(1).conversation().get(2)).get(0);
        assertTrue(response.response().get("error").toString().contains("Unknown tool"));
    }

    @Test
    void anInvalidArgumentIsReturnedToTheModelSoItCanCorrectItself() {
        model.thenCall("getRevenueSummary", Map.of("startDate", "last month"))
             .thenCall("getRevenueSummary", Map.of("startDate", "2026-09-01", "endDate", "2026-09-30"))
             .thenText("£40.00");

        String answer = assistant.askAdminAssistant("Revenue last month?");

        assertEquals("£40.00", answer);
        FunctionResponse firstAttempt = responsesIn(model.getCalls().get(1).conversation().get(2)).get(0);
        assertTrue(firstAttempt.response().containsKey("error"));
        FunctionResponse secondAttempt = responsesIn(model.getCalls().get(2).conversation().get(4)).get(0);
        assertFalse(secondAttempt.response().containsKey("error"));
    }

    // ── bounds ────────────────────────────────────────────

    @Test
    void theLoopIsBoundedAndTheFinalCallOffersNoToolsAtAll() {
        for (int i = 0; i < AdminAssistantService.MAX_MODEL_CALLS; i++) {
            model.thenCall("getMembershipStats", Map.of());   // a model that never stops asking
        }

        assertThrows(AiServiceException.class, () -> assistant.askAdminAssistant("loop forever"));

        assertEquals(AdminAssistantService.MAX_MODEL_CALLS, model.getCalls().size());
        assertFalse(model.getCalls().get(0).tools().isEmpty());
        assertTrue(model.lastCall().tools().isEmpty(), "the last allowed call must force a text answer");
    }

    @Test
    void ifTheModelAnswersOnTheForcedFinalCallThatAnswerIsReturned() {
        for (int i = 0; i < AdminAssistantService.MAX_MODEL_CALLS - 1; i++) {
            model.thenCall("getMembershipStats", Map.of());
        }
        model.thenText("Here is what I found.");

        assertEquals("Here is what I found.", assistant.askAdminAssistant("busy question"));
        assertTrue(model.lastCall().tools().isEmpty());
    }

    @Test
    void toolCallsBeyondThePerTurnCapStillGetAResponseButAreNotExecuted() {
        List<Part> calls = new ArrayList<>();
        int total = AdminAssistantService.MAX_TOOL_CALLS_PER_TURN + 2;
        for (int i = 0; i < total; i++) calls.add(FakeToolChatModel.callPart("getMembershipStats", Map.of(), "id" + i));
        model.thenCalls(calls).thenText("done");

        assistant.askAdminAssistant("everything at once");

        List<FunctionResponse> responses = responsesIn(model.getCalls().get(1).conversation().get(2));
        assertEquals(total, responses.size());   // Gemini requires an answer to every call it made
        assertFalse(responses.get(AdminAssistantService.MAX_TOOL_CALLS_PER_TURN - 1).response().containsKey("error"));
        assertTrue(responses.get(AdminAssistantService.MAX_TOOL_CALLS_PER_TURN).response().get("error")
            .toString().contains("Too many"));
    }

    // ── instructions to the model ─────────────────────────

    @Test
    void theSystemInstructionGivesTodaysDateAndForbidsInventingNumbers() {
        model.thenText("ok");

        assistant.askAdminAssistant("hello");

        String instruction = model.lastCall().systemInstruction();
        assertTrue(instruction.contains(LocalDate.now().toString()), "needed to resolve 'this month'");
        assertTrue(instruction.contains("Never invent numbers"));
        assertTrue(instruction.contains("cannot create, change, refund or delete"));
        assertTrue(instruction.contains("not instructions"), "tool data must be treated as data");
    }

    // ── failures ──────────────────────────────────────────

    @Test
    void aBlankFinalAnswerIsAnErrorNotAnEmptyResponse() {
        model.thenText("   ");

        assertThrows(AiServiceException.class, () -> assistant.askAdminAssistant("hello"));
    }

    @Test
    void aTurnWithNoPartsIsAnError() {
        model.thenTurn(new Turn("model", null));

        assertThrows(AiServiceException.class, () -> assistant.askAdminAssistant("hello"));
    }

    @Test
    void aModelFailurePropagatesUnchangedAndIsNotRetried() {
        model.thenFail(new AiServiceException("The AI assistant is temporarily overloaded. Please try again in a moment."));

        AiServiceException thrown = assertThrows(AiServiceException.class, () -> assistant.askAdminAssistant("hello"));

        assertEquals("The AI assistant is temporarily overloaded. Please try again in a moment.", thrown.getMessage());
        assertEquals(1, model.getCalls().size(), "no automatic retry");
    }

    @Test
    void aRateLimitHitMidConversationSurfacesAsTheRateLimitError() {
        model.thenCall("getMembershipStats", Map.of())
             .thenFail(new AiRateLimitExceededException("AI request limit reached"));

        assertThrows(AiRateLimitExceededException.class, () -> assistant.askAdminAssistant("hello"));
    }

    @Test
    void theQuestionReachesTheModelAsTheFirstUserTurn() {
        model.thenText("ok");

        assistant.askAdminAssistant("Who is overdue?");

        Turn first = model.lastCall().conversation().get(0);
        assertEquals("user", first.role());
        assertEquals("Who is overdue?", first.parts().get(0).text());
        assertNull(first.parts().get(0).functionCall());
    }
}
