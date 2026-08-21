package com.gymmanagement.config;

import com.gymmanagement.controller.AiController;
import com.gymmanagement.controller.MemberController;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.MemberRepository;
import com.gymmanagement.service.BootcampRecommendationService;
import com.gymmanagement.service.FakeAiChatClient;
import com.gymmanagement.service.FakeBootcampToolCallingClient;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RoleAuthorizationInterceptor's ownership enforcement on the two
 * endpoints it was actually applied to. Uses REAL HandlerMethod objects
 * built via reflection off the REAL controller classes — exactly what
 * Spring does internally — plus Spring's own MockHttpServletRequest
 * (bundled in spring-boot-starter-test; a genuine hand-implemented
 * class, not a mocking/proxy framework, consistent with this project's
 * no-Mockito approach). Fakes stand in for every repository/AI
 * dependency, same pattern as every other test in this project.
 *
 * preHandle() never actually invokes the underlying controller method —
 * interceptors run before Spring dispatches to it — so the controller
 * instances here only need to exist and satisfy their own constructors;
 * their methods are never called.
 *
 * Every call to interceptor.preHandle() goes through the small
 * invokePreHandle() helper below rather than being called directly at
 * each of the 5 call sites — that's not new scope, it's the same
 * Objects.requireNonNull() null-safety wrapping already used for the
 * Method values below, just applied once instead of five times.
 * Eclipse's null-checker doesn't treat @NonNull on a field as a proven
 * guarantee when the field is only ever assigned inside @BeforeEach
 * (a JUnit lifecycle convention, not something the Java compiler itself
 * verifies), so the field-level annotation alone didn't resolve these —
 * this does, by proving non-null at the actual point of use instead.
 */
class RoleAuthorizationInterceptorTest {

    private final RoleAuthorizationInterceptor interceptor = new RoleAuthorizationInterceptor();

    private HandlerMethod updateGoalHandlerMethod;
    private HandlerMethod bootcampRecommendationHandlerMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        MemberRepository memberRepo = new FakeMemberRepository();
        MemberService memberService = new MemberService(memberRepo);
        MemberController memberController = new MemberController(memberService);

        Method updateGoalMethod = Objects.requireNonNull(MemberController.class.getMethod(
            "updateFitnessGoal", String.class, MemberController.UpdateGoalRequest.class));
        updateGoalHandlerMethod = new HandlerMethod(memberController, updateGoalMethod);

        BootcampRepository bootcampRepo = new FakeBootcampRepository();
        MembershipService membershipService = new MembershipService(memberRepo, bootcampRepo);
        BootcampRecommendationService recommendationService =
            new BootcampRecommendationService(memberService, membershipService, new FakeAiChatClient());
        AiController aiController = new AiController(
            new FakeAiChatClient(), recommendationService, new FakeBootcampToolCallingClient());

        Method recommendationMethod = Objects.requireNonNull(AiController.class.getMethod(
            "getBootcampRecommendation", String.class));
        bootcampRecommendationHandlerMethod = new HandlerMethod(aiController, recommendationMethod);
    }

    private MockHttpServletRequest requestWithPathMemberId(String memberId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            Objects.requireNonNull(Map.of("memberId", memberId)));
        return request;
    }

    /**
     * The one place that actually calls interceptor.preHandle() — every
     * test below goes through here instead of calling it directly, so
     * the null-safety proof for the handlerMethod argument only needs
     * to exist once, not at all 5 call sites.
     */
    private void invokePreHandle(MockHttpServletRequest request, HandlerMethod handlerMethod) {
        interceptor.preHandle(request, new MockHttpServletResponse(),
            Objects.requireNonNull(handlerMethod));
    }

    // ── PATCH /api/members/{memberId}/goal ─────────────────

    @Test
    void adminCanUpdateAnyMembersGoal() {
        MockHttpServletRequest request = requestWithPathMemberId("M999");
        request.setAttribute("role", "ADMIN");
        request.setAttribute("linkedMemberId", null);   // ADMIN accounts have no linked member

        assertDoesNotThrow(() -> invokePreHandle(request, updateGoalHandlerMethod));
    }

    @Test
    void memberCanUpdateTheirOwnGoal() {
        MockHttpServletRequest request = requestWithPathMemberId("M100");
        request.setAttribute("role", "MEMBER");
        request.setAttribute("linkedMemberId", "M100");   // matches the path

        assertDoesNotThrow(() -> invokePreHandle(request, updateGoalHandlerMethod));
    }

    /** The core case the whole feature exists to prevent. */
    @Test
    void memberCannotUpdateAnotherMembersGoal() {
        MockHttpServletRequest request = requestWithPathMemberId("M200");   // someone else's ID
        request.setAttribute("role", "MEMBER");
        request.setAttribute("linkedMemberId", "M100");   // this token belongs to M100, not M200

        assertThrows(UnauthorizedException.class, () -> invokePreHandle(request, updateGoalHandlerMethod));
    }

    // ── GET /api/ai/members/{memberId}/bootcamp-recommendation ──

    /**
     * Confirms the SECOND endpoint is genuinely protected too, not just
     * assumed to be because it uses the same annotation name — catches
     * exactly the class of bug where the annotation gets forgotten on
     * one of two intended endpoints.
     */
    @Test
    void memberCannotGetAnotherMembersBootcampRecommendation() {
        MockHttpServletRequest request = requestWithPathMemberId("M200");
        request.setAttribute("role", "MEMBER");
        request.setAttribute("linkedMemberId", "M100");

        assertThrows(UnauthorizedException.class,
            () -> invokePreHandle(request, bootcampRecommendationHandlerMethod));
    }

    @Test
    void adminCanGetAnyMembersBootcampRecommendation() {
        MockHttpServletRequest request = requestWithPathMemberId("M999");
        request.setAttribute("role", "ADMIN");
        request.setAttribute("linkedMemberId", null);

        assertDoesNotThrow(() -> invokePreHandle(request, bootcampRecommendationHandlerMethod));
    }
}