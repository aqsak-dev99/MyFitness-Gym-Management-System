package com.gymmanagement.config;

import com.gymmanagement.controller.MembershipController;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.FakeStaffRepository;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves who may use the two payment endpoints, with the project's real
 * mechanism (RoleAuthorizationInterceptor reading @RequireOwnership /
 * @RequireRole) — a real HandlerMethod off the real controller, driven
 * by Spring's MockHttpServletRequest, same approach as
 * RoleAuthorizationInterceptorTest.
 *
 *   POST /api/members/{memberId}/membership/pay      member pays THEIR OWN
 *   POST /api/members/{memberId}/membership/payment  ADMIN only
 *
 * The second matters as much as the first: the admin endpoint skips the
 * "only when something is due" rule, so a member must never be able to
 * reach it.
 */
class MemberPaymentAuthorizationTest {

    private final RoleAuthorizationInterceptor interceptor = new RoleAuthorizationInterceptor();

    private MemberService         memberService;
    private MembershipController  controller;
    private HandlerMethod         payHandlerMethod;
    private HandlerMethod         adminRecordHandlerMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        FakeMemberRepository memberRepo = new FakeMemberRepository();
        memberService = new MemberService(memberRepo);
        MembershipService membershipService =
            new MembershipService(memberRepo, new FakeBootcampRepository());
        TrainerService trainerService = new TrainerService(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new FakeStaffRepository());
        controller = new MembershipController(membershipService, trainerService, memberService);

        Method payMethod = Objects.requireNonNull(
            MembershipController.class.getMethod("payMembershipOnline", String.class));
        payHandlerMethod = new HandlerMethod(controller, payMethod);

        Method adminMethod = Objects.requireNonNull(
            MembershipController.class.getMethod("recordMembershipPayment", String.class));
        adminRecordHandlerMethod = new HandlerMethod(controller, adminMethod);
    }

    private MockHttpServletRequest request(String pathMemberId, String role, String linkedMemberId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            Objects.requireNonNull(Map.of("memberId", pathMemberId)));
        if (role != null)           request.setAttribute("role", role);
        if (linkedMemberId != null) request.setAttribute("linkedMemberId", linkedMemberId);
        return request;
    }

    private void invokePreHandle(MockHttpServletRequest request, HandlerMethod handlerMethod) {
        interceptor.preHandle(request, new MockHttpServletResponse(),
            Objects.requireNonNull(handlerMethod));
    }

    // ── member "Pay now" ───────────────────────────────────

    @Test
    void aMemberCanPayTheirOwnMembership() {
        assertDoesNotThrow(() ->
            invokePreHandle(request("M100", "MEMBER", "M100"), payHandlerMethod));
    }

    /** The case the ownership rule exists to prevent. */
    @Test
    void aMemberCannotPayAnotherMembersMembership() {
        assertThrows(UnauthorizedException.class, () ->
            invokePreHandle(request("M200", "MEMBER", "M100"), payHandlerMethod));
    }

    @Test
    void aMemberTokenWithNoLinkedMemberCannotPayAnything() {
        assertThrows(UnauthorizedException.class, () ->
            invokePreHandle(request("M100", "MEMBER", null), payHandlerMethod));
    }

    @Test
    void anAdminCanPayOnBehalfOfAnyMember() {
        assertDoesNotThrow(() ->
            invokePreHandle(request("M999", "ADMIN", null), payHandlerMethod));
    }

    @Test
    void thePayEndpointIsProtectedByOwnershipNotByAdminOnlyRole() throws NoSuchMethodException {
        Method pay = MembershipController.class.getMethod("payMembershipOnline", String.class);

        RequireOwnership ownership = pay.getAnnotation(RequireOwnership.class);
        assertTrue(ownership != null && "memberId".equals(ownership.value()),
            "payMembershipOnline must be @RequireOwnership(\"memberId\")");
        assertEquals(null, pay.getAnnotation(RequireRole.class),
            "payMembershipOnline must not be ADMIN-only — members use it");
    }

    // ── admin "Record payment" ─────────────────────────────

    @Test
    void anAdminCanRecordAPayment() {
        assertDoesNotThrow(() ->
            invokePreHandle(request("M100", "ADMIN", null), adminRecordHandlerMethod));
    }

    @Test
    void aMemberCannotUseTheAdminRecordPaymentEndpointEvenForThemselves() {
        assertThrows(UnauthorizedException.class, () ->
            invokePreHandle(request("M100", "MEMBER", "M100"), adminRecordHandlerMethod));
    }

    // ── controller → service wiring ────────────────────────

    @Test
    void thePayEndpointRecordsAPaymentAndReturnsTheUpdatedMembership() {
        memberService.registerMember("P001", "M100", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M100", new StandardMembership("MEM001", 12));
        Member member = memberService.getMemberById("M100");
        LocalDate due = LocalDate.now().minusDays(10);
        member.getMembership().setNextPaymentDueDate(due);
        int paymentsBefore = member.getPaymentHistory().size();

        Membership returned = controller.payMembershipOnline("M100");

        assertEquals(due.plusMonths(1), returned.getNextPaymentDueDate());
        assertEquals(paymentsBefore + 1, memberService.getMemberById("M100").getPaymentHistory().size());
    }

    @Test
    void thePayEndpointRefusesAMemberWhoIsPaidUp() {
        memberService.registerMember("P001", "M100", "Alice Smith", "alice@email.com", "07800100001");
        memberService.assignMembership("M100", new StandardMembership("MEM001", 12));
        memberService.getMemberById("M100").getMembership()
            .setNextPaymentDueDate(LocalDate.now().plusMonths(2));

        assertThrows(IllegalStateException.class, () -> controller.payMembershipOnline("M100"));
    }
}
