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
}
