package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateMemberException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.model.Member;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.MemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests MemberService in isolation, using FakeMemberRepository instead of
 * SqliteMemberRepository. MemberService has no idea it's talking to a fake —
 * it only knows the MemberRepository interface. That's the whole point.
 */
class MemberServiceTest {

    private MemberService memberService;

    /**
     * Runs before EVERY @Test method below, giving each test a completely
     * fresh, empty fake repository. Without this, one test's leftover data
     * could leak into the next test and cause confusing, order-dependent
     * failures — tests should never depend on each other.
     */
    @BeforeEach
    void setUp() {
        MemberRepository fakeRepo = new FakeMemberRepository();
        memberService = new MemberService(fakeRepo);
    }

    @Test
    void registeringNewMemberSucceeds() {
        Member member = memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertEquals("M001", member.getMemberId());
        assertEquals("Alice Smith", member.getName());
    }

    @Test
    void registeringDuplicateMemberIdThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        // assertThrows takes the exception TYPE and a block of code (a
        // lambda) — it runs that code and checks the exception was thrown.
        // If registerMember DIDN'T throw, this test would fail, not pass.
        assertThrows(DuplicateMemberException.class, () ->
            memberService.registerMember("P002", "M001", "Someone Else", "x@x.com", "0000")
        );
    }

    @Test
    void gettingUnknownMemberThrowsNotFound() {
        assertThrows(MemberNotFoundException.class, () ->
            memberService.getMemberById("DOES_NOT_EXIST")
        );
    }

    @Test
    void removingMembershipFromMemberWithNoneThrows() {
        memberService.registerMember(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");

        assertThrows(MemberNotFoundException.class, () ->
            memberService.removeMembership("M001")
        );
    }
}