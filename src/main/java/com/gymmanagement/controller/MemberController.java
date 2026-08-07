package com.gymmanagement.controller;

import com.gymmanagement.model.Member;
import com.gymmanagement.service.MemberService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MemberController — the HTTP-facing layer for members.
 *
 * Notice what this class does NOT contain: no business rules, no
 * validation logic, no direct database access. It only translates HTTP
 * requests into calls on MemberService — the exact same MemberService
 * that already has 4 passing JUnit tests and powers the console demo.
 * That's the whole point of the layered architecture: the service layer
 * doesn't care who's calling it.
 *
 * @RestController = @Controller + @ResponseBody combined. It tells Spring
 * "return values from these methods should be serialised straight to
 * JSON in the HTTP response body," not rendered as an HTML page.
 *
 * @RequestMapping("/api/members") means every endpoint in this class is
 * automatically prefixed with that path — GET /api/members, not just GET /.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    // Same constructor-injection pattern as every service in this project.
    // Spring sees MemberController is a bean (@RestController) and MemberService
    // is a bean (@Service), and wires this constructor automatically.
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * GET /api/members
     * Returns every member as a JSON array. Jackson (bundled inside
     * spring-boot-starter-web) converts the List<Member> to JSON
     * automatically — nothing here manually builds a JSON string.
     */
    @GetMapping
    public List<Member> getAllMembers() {
        return memberService.getAllMembers();
    }

    /**
     * GET /api/members/{memberId}
     * {memberId} is a "path variable" — whatever's in that URL segment
     * gets passed as the memberId parameter below. If MemberService
     * throws MemberNotFoundException, GlobalExceptionHandler (see that
     * class) converts it into a proper 404 response instead of a raw 500.
     */
    @GetMapping("/{memberId}")
    public Member getMemberById(@PathVariable String memberId) {
        return memberService.getMemberById(memberId);
    }

    /**
     * POST /api/members
     * The request body — sent as JSON — gets automatically deserialised
     * into a MemberRegistrationRequest by @RequestBody. Returns 201
     * Created rather than the default 200, since a POST that creates
     * something new should say so explicitly in its status code.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Member registerMember(@Valid @RequestBody MemberRegistrationRequest request) {
        return memberService.registerMember(
            request.personId(), request.memberId(),
            request.name(), request.email(), request.phone()
        );
    }

    /**
     * DELETE /api/members/{memberId}
     * Returns 204 No Content — the conventional response for a successful
     * delete, where there's nothing meaningful left to send back.
     */
    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMember(@PathVariable String memberId) {
        memberService.deleteMember(memberId);
    }

    /**
     * PATCH /api/members/{memberId}/goal
     * PATCH (not PUT) since this updates one field, not the whole
     * resource. Returns the updated Member so the client can confirm
     * the goal actually landed without a separate follow-up GET.
     */
    @PatchMapping("/{memberId}/goal")
    public Member updateFitnessGoal(@PathVariable String memberId,
                                    @Valid @RequestBody UpdateGoalRequest request) {
        return memberService.updateFitnessGoal(memberId, request.fitnessGoal());
    }

    /**
     * A small "record" — Java's built-in way to declare an immutable data
     * holder in one line. This exists so the POST endpoint's JSON body
     * has a shape independent of the Member class itself — Member has
     * fields (like registrationDate) a client should never be allowed to
     * set directly on registration.
     *
     * @NotBlank rejects null AND whitespace-only strings — stricter than
     * @NotNull, which a string of just spaces would still pass. @Email
     * checks basic format, catching an obviously malformed address before
     * it ever reaches the database, rather than only being caught the
     * first time someone tries to actually send that member an email.
     */
    public record MemberRegistrationRequest(
        @NotBlank String personId,
        @NotBlank String memberId,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String phone
    ) {}

    public record UpdateGoalRequest(@NotBlank String fitnessGoal) {}
}