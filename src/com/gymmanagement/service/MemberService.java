package com.gymmanagement.service;

import com.gymmanagement.exception.DuplicateMemberException;
import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.repository.MemberRepository;

import java.util.List;

/**
 * MemberService owns every business rule that involves a Member.
 *
 * Rules enforced here:
 *  - A member ID must be unique before registration.
 *  - A member must exist before a membership can be assigned or removed.
 *  - Removing a membership sets it to null — the record stays.
 *
 * This class has NO System.out calls and NO ArrayList declarations.
 * It receives a MemberRepository via constructor injection so the
 * storage implementation can be swapped without touching this class.
 */
public class MemberService {

    private final MemberRepository memberRepo;

    public MemberService(MemberRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    // ── registration ──────────────────────────────────────

    /**
     * Register a new member.
     * Throws DuplicateMemberException if the ID is already taken.
     */
    public Member registerMember(String personId, String memberId,
                                 String name, String email, String phone) {
        if (memberRepo.existsById(memberId))
            throw new DuplicateMemberException(
                "Member ID already registered: " + memberId);

        Member member = new Member(personId, memberId, name, email, phone);
        memberRepo.save(member);
        return member;
    }

    // ── retrieval ─────────────────────────────────────────

    public Member getMemberById(String memberId) {
        return memberRepo.findById(memberId)
                         .orElseThrow(() -> new MemberNotFoundException(
                             "No member found with ID: " + memberId));
    }

    public List<Member> getAllMembers() {
        return memberRepo.findAll();
    }

    // ── membership assignment ─────────────────────────────

    /**
     * Assign a membership to an existing member.
     * Overwrites any previously held membership.
     */
    public void assignMembership(String memberId, Membership membership) {
        Member member = getMemberById(memberId);
        member.setMembership(membership);
        memberRepo.save(member);
    }

    /**
     * Remove (cancel) a member's current membership.
     * The member record is retained.
     */
    public void removeMembership(String memberId) {
        Member member = getMemberById(memberId);
        if (member.getMembership() == null)
            throw new MemberNotFoundException(
                "Member " + memberId + " has no active membership to remove.");
        member.removeMembership();
        memberRepo.save(member);
    }

    // ── removal ───────────────────────────────────────────

    /**
     * Permanently remove a member from the system.
     */
    public void deleteMember(String memberId) {
        if (!memberRepo.existsById(memberId))
            throw new MemberNotFoundException(
                "Cannot delete — no member found with ID: " + memberId);
        memberRepo.delete(memberId);
    }
}
