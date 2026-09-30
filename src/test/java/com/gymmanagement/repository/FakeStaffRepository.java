package com.gymmanagement.repository;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written fake implementation of StaffRepository, used ONLY in
 * tests — same pattern as FakeMemberRepository. Backed by three plain
 * ArrayLists rather than one combined list, since StaffRepository's
 * three save/findAll method pairs are already type-segregated.
 */
public class FakeStaffRepository implements StaffRepository {

    private final List<FullTimeStaff> fullTimeStaff = new ArrayList<>();
    private final List<PartTimeStaff> partTimeStaff = new ArrayList<>();
    private final List<Instructor>    instructors    = new ArrayList<>();

    @Override
    public void saveFullTimeStaff(FullTimeStaff staff) {
        fullTimeStaff.removeIf(s -> s.getStaffId().equals(staff.getStaffId()));
        fullTimeStaff.add(staff);
    }

    @Override
    public void savePartTimeStaff(PartTimeStaff staff) {
        partTimeStaff.removeIf(s -> s.getStaffId().equals(staff.getStaffId()));
        partTimeStaff.add(staff);
    }

    @Override
    public void saveInstructor(Instructor instructor) {
        instructors.removeIf(i -> i.getStaffId().equals(instructor.getStaffId()));
        instructors.add(instructor);
    }

    @Override
    public List<FullTimeStaff> findAllFullTimeStaff() { return new ArrayList<>(fullTimeStaff); }

    @Override
    public List<PartTimeStaff> findAllPartTimeStaff() { return new ArrayList<>(partTimeStaff); }

    @Override
    public List<Instructor> findAllInstructors() { return new ArrayList<>(instructors); }

    @Override
    public boolean existsByStaffId(String staffId) {
        return fullTimeStaff.stream().anyMatch(s -> s.getStaffId().equals(staffId))
            || partTimeStaff.stream().anyMatch(s -> s.getStaffId().equals(staffId))
            || instructors.stream().anyMatch(i -> i.getStaffId().equals(staffId));
    }
}