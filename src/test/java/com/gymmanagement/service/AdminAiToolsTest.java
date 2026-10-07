package com.gymmanagement.service;

import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.service.ToolChatModel.ToolSpec;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests AdminAiTools — the allow-list between the model and the system.
 * The properties that matter here are the security-shaped ones: only the
 * five named tools exist, a made-up tool name or a hostile-looking
 * argument can't reach anything, bad input comes back as a structured
 * error instead of an exception, and failures never leak internals.
 */
class AdminAiToolsTest {

    private FakeMemberRepository memberRepo;
    private MemberService        memberService;
    private MembershipService    membershipService;
    private AdminAiTools         tools;

    @BeforeEach
    void setUp() {
        memberRepo        = new FakeMemberRepository();
        memberService     = new MemberService(memberRepo);
        membershipService = new MembershipService(memberRepo, new FakeBootcampRepository());
        tools = new AdminAiTools(new AdminOperationsService(memberService, membershipService));
    }

    private Member addMemberWithCompletedPayment(String id, double amount, LocalDate date) {
        Member member = memberService.registerMember("P" + id, id, "Member " + id, id + "@email.com", "07800100001");
        Payment payment = new Payment("PAY-" + id, amount, "Test payment", member);
        payment.markCompleted();
        payment.setPaymentDateFromDb(date);
        member.addPayment(payment);
        memberRepo.save(member);
        return member;
    }

    // ── the allow-list ────────────────────────────────────

    @Test
    void exactlyTheFiveReadOnlyToolsAreDeclared() {
        Set<String> names = tools.specs().stream().map(ToolSpec::name).collect(Collectors.toSet());

        assertEquals(Set.of("getRevenueSummary", "getOverdueMembers", "getMembershipStats",
                            "getClassOccupancy", "getRecentPayments"), names);
    }

    @Test
    void everyToolHasADescriptionAndAnObjectSchema() {
        for (ToolSpec spec : tools.specs()) {
            assertFalse(spec.description().isBlank(), spec.name() + " needs a description for the model");
            assertEquals("object", spec.parameters().get("type"), spec.name());
        }
    }

    @Test
    void noDeclaredToolIsNamedLikeAWriteOperation() {
        for (ToolSpec spec : tools.specs()) {
            assertTrue(spec.name().startsWith("get"), spec.name() + " should be a read-only getter");
        }
    }

    @Test
    void everyDeclaredToolActuallyExecutes() {
        for (ToolSpec spec : tools.specs()) {
            Map<String, Object> result = tools.execute(spec.name(), Map.of());
            assertFalse(result.containsKey("error"), spec.name() + " returned: " + result);
        }
    }

    @Test
    void anUnknownToolIsRejectedWithoutThrowing() {
        Map<String, Object> result = tools.execute("deleteAllMembers", Map.of());

        assertTrue(result.containsKey("error"));
        assertTrue(result.get("error").toString().contains("Unknown tool"));
        assertTrue(result.get("error").toString().contains("getRevenueSummary"));   // tells the model what IS allowed
    }

    @Test
    void aToolNameThatLooksLikeSqlIsJustAnUnknownTool() {
        Map<String, Object> result = tools.execute("SELECT * FROM members; DROP TABLE payments;", Map.of());

        assertTrue(result.get("error").toString().startsWith("Unknown tool"));
    }

    @Test
    void aMissingToolNameIsAnErrorNotACrash() {
        assertTrue(tools.execute(null, Map.of()).containsKey("error"));
    }

    @Test
    void unexpectedArgumentsAreIgnoredAndCannotInjectAnything() {
        Member member = addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 1));
        Map<String, Object> hostile = new HashMap<>();
        hostile.put("sql", "DROP TABLE members");
        hostile.put("memberId", "M001");

        Map<String, Object> result = tools.execute("getOverdueMembers", hostile);

        assertFalse(result.containsKey("error"));
        assertEquals(1, memberService.getAllMembers().size());   // nothing was touched
        assertNotNull(memberService.getMemberById(member.getMemberId()));
    }

    // ── revenue tool: arguments and results ───────────────

    @Test
    void revenueToolReturnsStructuredDataForADateRange() {
        addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 10));
        addMemberWithCompletedPayment("M002", 60.00, LocalDate.of(2026, 8, 10));

        Map<String, Object> result = tools.execute("getRevenueSummary",
            Map.of("startDate", "2026-09-01", "endDate", "2026-09-30"));

        assertEquals("GBP", result.get("currency"));
        assertEquals(40.0, ((Number) result.get("totalRevenue")).doubleValue());
        assertEquals(1, ((Number) result.get("paymentCount")).intValue());
        assertEquals("2026-09-01", result.get("startDate"));   // dates arrive as plain ISO strings
        assertEquals("2026-09-30", result.get("endDate"));
    }

    @Test
    void revenueToolWithNoDatesMeansAllTime() {
        addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 10));
        addMemberWithCompletedPayment("M002", 60.00, LocalDate.of(2026, 8, 10));

        Map<String, Object> result = tools.execute("getRevenueSummary", Map.of());

        assertEquals(100.0, ((Number) result.get("totalRevenue")).doubleValue());
    }

    @Test
    void revenueToolWithNullArgumentsBehavesLikeNoArguments() {
        addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 10));

        Map<String, Object> result = tools.execute("getRevenueSummary", null);

        assertEquals(40.0, ((Number) result.get("totalRevenue")).doubleValue());
    }

    @Test
    void emptyRevenueCarriesTheExplicitNoDataNoteAndOmitsTheAverage() {
        Map<String, Object> result = tools.execute("getRevenueSummary", Map.of("startDate", "2030-01-01"));

        assertTrue(result.get("note").toString().contains("No completed payments"));
        assertFalse(result.containsKey("averagePayment"));   // null fields are dropped, not sent as null
    }

    @Test
    void anInvalidDateComesBackAsAFixableError() {
        Map<String, Object> result = tools.execute("getRevenueSummary", Map.of("startDate", "September 1st"));

        assertTrue(result.get("error").toString().contains("startDate"));
        assertTrue(result.get("error").toString().contains("YYYY-MM-DD"));
    }

    @Test
    void aNonStringDateIsAnError() {
        Map<String, Object> result = tools.execute("getRevenueSummary", Map.of("endDate", 20260930));

        assertTrue(result.get("error").toString().contains("endDate"));
    }

    @Test
    void aReversedDateRangeIsAnError() {
        Map<String, Object> result = tools.execute("getRevenueSummary",
            Map.of("startDate", "2026-10-01", "endDate", "2026-09-01"));

        assertTrue(result.get("error").toString().contains("must not be after"));
    }

    // ── recent-payments tool: the integer argument ────────

    @Test
    void limitAcceptsAWholeNumberADecimalAndANumericString() {
        for (int i = 1; i <= 5; i++) addMemberWithCompletedPayment("M00" + i, 10.00 + i, LocalDate.of(2026, 9, i));

        assertEquals(2, ((Number) tools.execute("getRecentPayments", Map.of("limit", 2)).get("returned")).intValue());
        assertEquals(3, ((Number) tools.execute("getRecentPayments", Map.of("limit", 3.0)).get("returned")).intValue());
        assertEquals(4, ((Number) tools.execute("getRecentPayments", Map.of("limit", "4")).get("returned")).intValue());
    }

    @Test
    void aNonNumericLimitIsAnError() {
        Map<String, Object> result = tools.execute("getRecentPayments", Map.of("limit", "lots"));

        assertTrue(result.get("error").toString().contains("limit"));
    }

    // ── failure containment ───────────────────────────────

    @Test
    void aDataLayerFailureIsContainedAndNeverLeaksItsDetails() {
        AdminOperationsService failing = new AdminOperationsService(memberService, membershipService) {
            @Override
            public OverdueMembers getOverdueMembers() {
                throw new RuntimeException("jdbc:postgresql://secret-host/db password=hunter2");
            }
        };
        AdminAiTools failingTools = new AdminAiTools(failing);

        Map<String, Object> result = assertDoesNotThrow(() -> failingTools.execute("getOverdueMembers", Map.of()));

        assertTrue(result.containsKey("error"));
        assertFalse(result.get("error").toString().contains("secret-host"));
        assertFalse(result.get("error").toString().contains("hunter2"));
    }

    // ── what actually goes over the wire ──────────────────

    @Test
    void everyToolResultIsPlainJsonThatSerialisesCleanly() throws Exception {
        Member member = addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 10));
        memberService.assignMembership(member.getMemberId(), new StandardMembership("MEM-M001", 12));
        member.getMembership().setNextPaymentDueDate(LocalDate.now().minusDays(9));

        ObjectMapper plain = new ObjectMapper();   // deliberately NOT configured for java.time

        for (ToolSpec spec : tools.specs()) {
            Map<String, Object> result = tools.execute(spec.name(), Map.of());
            String json = assertDoesNotThrow(() -> plain.writeValueAsString(result), spec.name());
            assertFalse(json.isBlank());
        }
    }

    @Test
    void overdueToolReturnsTheMembersWithPlainStringDates() {
        Member member = addMemberWithCompletedPayment("M001", 40.00, LocalDate.of(2026, 9, 10));
        memberService.assignMembership(member.getMemberId(), new StandardMembership("MEM-M001", 12));
        LocalDate due = LocalDate.now().minusDays(9);
        member.getMembership().setNextPaymentDueDate(due);

        Map<String, Object> result = tools.execute("getOverdueMembers", Map.of());

        assertEquals(1, ((Number) result.get("totalOverdue")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) result.get("members");
        assertEquals("M001", members.get(0).get("memberId"));
        assertEquals(due.toString(), members.get(0).get("nextPaymentDueDate"));
        assertEquals(9, ((Number) members.get(0).get("daysOverdue")).intValue());
    }
}
