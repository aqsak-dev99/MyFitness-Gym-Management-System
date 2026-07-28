package com.gymmanagement.repository;

import com.gymmanagement.model.BootcampClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hand-written fake implementation of BootcampRepository, used ONLY in
 * tests — same idea as FakeMemberRepository. MembershipService depends on
 * the BootcampRepository INTERFACE, so this fake slots in without
 * MembershipService ever knowing it's not talking to SQLite.
 */
public class FakeBootcampRepository implements BootcampRepository {

    private final List<BootcampClass> classes = new ArrayList<>();

    @Override
    public void save(BootcampClass bc) {
        classes.removeIf(c -> c.getClassId().equals(bc.getClassId()));
        classes.add(bc);
    }

    @Override
    public Optional<BootcampClass> findById(String classId) {
        return classes.stream()
                      .filter(c -> c.getClassId().equals(classId))
                      .findFirst();
    }

    @Override
    public List<BootcampClass> findAll() {
        return new ArrayList<>(classes);
    }

    @Override
    public void delete(String classId) {
        classes.removeIf(c -> c.getClassId().equals(classId));
    }
}