package com.gymmanagement.config;

import com.gymmanagement.controller.AdminAiController;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.Role;
import com.gymmanagement.service.FakeAdminAssistantClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Admin AI endpoint is ADMIN-only using the project's real
 * mechanism — @RequireRole read by RoleAuthorizationInterceptor — the
 * same way RoleAuthorizationInterceptorTest proves ownership rules: a
 * real HandlerMethod off the real controller, driven by Spring's
 * MockHttpServletRequest, with a fake behind the controller. The
 * interceptor never invokes the controller method, so no AI is involved.
 */
class AdminAiAuthorizationTest {

    private final RoleAuthorizationInterceptor interceptor = new RoleAuthorizationInterceptor();

    private FakeAdminAssistantClient fakeAssistant;
    private AdminAiController        controller;
    private HandlerMethod            askHandlerMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        fakeAssistant = new FakeAdminAssistantClient();
        controller    = new AdminAiController(fakeAssistant);

        Method askMethod = Objects.requireNonNull(
            AdminAiController.class.getMethod("ask", AdminAiController.AdminAskRequest.class));
        askHandlerMethod = new HandlerMethod(controller, askMethod);
    }

    private void invokePreHandle(MockHttpServletRequest request) {
        interceptor.preHandle(request, new MockHttpServletResponse(), Objects.requireNonNull(askHandlerMethod));
    }

    private MockHttpServletRequest requestWithRole(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (role != null) request.setAttribute("role", role);
        return request;
    }

    @Test
    void anAdminTokenIsAllowed() {
        assertDoesNotThrow(() -> invokePreHandle(requestWithRole("ADMIN")));
    }

    @Test
    void aMemberTokenIsRejected() {
        UnauthorizedException thrown = assertThrows(UnauthorizedException.class,
            () -> invokePreHandle(requestWithRole("MEMBER")));

        assertTrue(thrown.getMessage().contains("ADMIN"));
    }

    @Test
    void aRequestWithNoRoleAtAllIsRejected() {
        assertThrows(UnauthorizedException.class, () -> invokePreHandle(requestWithRole(null)));
    }

    @Test
    void everyEndpointOnTheAdminAiControllerRequiresAdmin() {
        List<Method> endpoints = Arrays.stream(AdminAiController.class.getDeclaredMethods())
            .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
            .toList();

        assertFalse(endpoints.isEmpty());
        for (Method endpoint : endpoints) {
            RequireRole requireRole = endpoint.getAnnotation(RequireRole.class);
            assertTrue(requireRole != null && requireRole.value() == Role.ADMIN,
                endpoint.getName() + " must be @RequireRole(Role.ADMIN) — this controller is admin-only by design");
        }
    }

    @Test
    void theControllerPassesTheQuestionToTheAssistantAndReturnsItsAnswer() {
        fakeAssistant.setCannedResponse("This month's revenue is £1,234.00.");

        var response = controller.ask(new AdminAiController.AdminAskRequest("How much revenue did we make this month?"));

        assertEquals("This month's revenue is £1,234.00.", response.answer());
        assertEquals("How much revenue did we make this month?", fakeAssistant.getLastQuestionReceived());
    }
}
