package com.gymmanagement.repository;

import com.gymmanagement.model.BootcampClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryBootcampRepository implements BootcampRepository {

    private final List<BootcampClass> classes = new ArrayList<>();

    @Override
    public void save(BootcampClass bc) {
        for (int i = 0; i < classes.size(); i++) {
            if (classes.get(i).getClassId().equals(bc.getClassId())) {
                classes.set(i, bc);
                return;
            }
        }
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

    @Override
    public boolean hasAnyClasses() {
        return !classes.isEmpty();
    }
}