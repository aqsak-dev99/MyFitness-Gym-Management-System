package com.gymmanagement.repository;

import com.gymmanagement.model.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hand-written fake implementation of MemberRepository, used ONLY in tests.
 *
 * This is possible because MemberService depends on the MemberRepository
 * INTERFACE, not on SqliteMemberRepository directly — that constructor-
 * injection decision is what makes this work. Swap this in and tests run
 * in milliseconds, never touch gym.db, and never leave test data behind.
 *
 * This is almost exactly your old InMemoryMemberRepository from before the
 * SQLite migration — same idea, just scoped to tests now. It's also exactly
 * what a library like Mockito generates for you automatically; writing it
 * by hand here makes it obvious there's no magic involved, just a plain
 * class implementing an interface with an ArrayList behind it.
 */
public class FakeMemberRepository implements MemberRepository {

    private final List<Member> members = new ArrayList<>();

    @Override
    public void save(Member member) {
        members.removeIf(m -> m.getMemberId().equals(member.getMemberId()));
        members.add(member);
    }

    @Override
    public Optional<Member> findById(String memberId) {
        return members.stream()
                      .filter(m -> m.getMemberId().equals(memberId))
                      .findFirst();
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return members.stream()
                      .filter(m -> m.getEmail().equalsIgnoreCase(email))
                      .findFirst();
    }

    @Override
    public List<Member> findAll() {
        return new ArrayList<>(members);
    }

    @Override
    public void delete(String memberId) {
        members.removeIf(m -> m.getMemberId().equals(memberId));
    }

    @Override
    public boolean existsById(String memberId) {
        return members.stream().anyMatch(m -> m.getMemberId().equals(memberId));
    }
}