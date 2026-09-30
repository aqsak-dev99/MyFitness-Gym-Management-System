package com.gymmanagement.repository;

import com.gymmanagement.model.BootcampClass;
import java.util.List;
import java.util.Optional;

/** Contract for all BootcampClass data-access implementations. */
public interface BootcampRepository {
    void               save(BootcampClass bc);
    Optional<BootcampClass> findById(String classId);
    List<BootcampClass> findAll();
    void               delete(String classId);

    /**
     * Lightweight existence check — a single row-existence query, not a
     * full findAll(). Added specifically to fix BootcampSeeder's real
     * N+1 problem: its old "already seeded?" check went through
     * findAll() → each class's participants → MemberRepository.findById()
     * → loadPaymentsForMember(), for every class, just to answer a yes/no
     * question. That chain is what actually caused an application
     * startup failure tonight, not just a slow page load.
     */
    boolean hasAnyClasses();
}