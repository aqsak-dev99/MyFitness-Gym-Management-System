package com.gymmanagement.config;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.repository.StaffRepository;
import com.gymmanagement.service.TrainerService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Supplier;

/**
 * StaffConfig — supplies TrainerService's constructor with its initial
 * in-memory lists, and supplies SqliteBootcampRepository with a way to
 * resolve instructor_id at load time.
 *
 * Previously this class held hardcoded @Bean List.of(...) data with no
 * database backing at all — the actual root cause of staff/instructor
 * creation never surviving a restart (see StaffSeeder for the full
 * history). Now these three @Bean methods query StaffRepository instead
 * — a real, persisted table — so TrainerService starts up with whatever
 * genuinely exists in the database, on every boot, including anything
 * created via the API in a previous run.
 *
 * These three lists are a one-time startup SNAPSHOT, used only to
 * construct TrainerService. They are not re-queried afterward — the
 * ongoing, authoritative source of truth for a running process is
 * TrainerService's own in-memory copy (kept in sync with the database
 * on every write via its add*() methods). That's why instructorSupplier
 * below points at trainerService::getAllInstructors, not at the plain
 * List<Instructor> bean directly — the raw bean is a fixed snapshot
 * that would silently go stale the moment any new instructor is added.
 */
@Configuration
public class StaffConfig {

    @Bean
    public List<FullTimeStaff> fullTimeStaff(StaffRepository staffRepo) {
        return staffRepo.findAllFullTimeStaff();
    }

    @Bean
    public List<PartTimeStaff> partTimeStaff(StaffRepository staffRepo) {
        return staffRepo.findAllPartTimeStaff();
    }

    @Bean
    public List<Instructor> instructors(StaffRepository staffRepo) {
        return staffRepo.findAllInstructors();
    }

    /**
     * SqliteBootcampRepository's constructor takes a Supplier<List<Instructor>>,
     * not a plain List — Spring doesn't generate that wrapper automatically,
     * so it's defined explicitly here. Points at TrainerService's own
     * live in-memory list (see class-level comment for why), not the
     * startup-snapshot instructors() bean above.
     */
    @Bean
    public Supplier<List<Instructor>> instructorSupplier(TrainerService trainerService) {
        return trainerService::getAllInstructors;
    }
}