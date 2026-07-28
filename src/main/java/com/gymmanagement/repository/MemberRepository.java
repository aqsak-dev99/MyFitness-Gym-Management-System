package com.gymmanagement.repository;

import com.gymmanagement.model.Member;
import java.util.List;
import java.util.Optional;

/**
 * Contract for all Member data-access implementations.
 * Swap InMemoryMemberRepository for a SQL implementation
 * without touching any service class.
 */
public interface MemberRepository {
    void           save(Member member);
    Optional<Member> findById(String memberId);
    Optional<Member> findByEmail(String email);
    List<Member>   findAll();
    void           delete(String memberId);
    boolean        existsById(String memberId);
}
