package com.gymmanagement.repository;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;

import java.util.List;

/**
 * Contract for staff/instructor persistence — mirrors MemberRepository's
 * shape exactly. One interface covering all three staff categories
 * (rather than three separate repositories) matches the single-table,
 * discriminator-column design in DatabaseSchema.CREATE_STAFF.
 */
public interface StaffRepository {
    void saveFullTimeStaff(FullTimeStaff staff);
    void savePartTimeStaff(PartTimeStaff staff);
    void saveInstructor(Instructor instructor);

    List<FullTimeStaff> findAllFullTimeStaff();
    List<PartTimeStaff> findAllPartTimeStaff();
    List<Instructor>    findAllInstructors();

    boolean existsByStaffId(String staffId);
}