package com.gymmanagement.config;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;
import com.gymmanagement.repository.StaffRepository;
import com.gymmanagement.service.TrainerService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * StaffSeeder — replaces StaffConfig's hardcoded @Bean lists with real,
 * persisted rows. This is the actual fix for staff/instructor data never
 * surviving a restart: StaffConfig's own comments already documented
 * that gap honestly ("staff were never persisted — Main.java always
 * rebuilt this same list on every startup").
 *
 * Exact same 12 people, same IDs (INS001-003, FT001-005, PT001-004),
 * same details — this seeds identical data, just into a real table
 * instead of rebuilding it in Java on every boot. Same idempotency
 * guard as BootcampSeeder: only seeds if the data is already there.
 *
 * Deliberately calls trainerService.add*() rather than
 * staffRepository.save*() directly. Spring fully constructs every bean
 * (TrainerService included — its constructor already loads whatever the
 * repository currently has) BEFORE any CommandLineRunner executes. On a
 * completely fresh database, writing straight to the repository here
 * would correctly persist the rows but leave TrainerService's own
 * in-memory list — the actual source of truth for conflict-checking
 * during this running process — empty until the next restart. Going
 * through TrainerService's own add methods keeps both in sync
 * immediately, the same way a real POST request would.
 */
@Component
@Order(1)
public class StaffSeeder implements CommandLineRunner {

    private final TrainerService trainerService;
    private final StaffRepository staffRepo;

    public StaffSeeder(TrainerService trainerService, StaffRepository staffRepo) {
        this.trainerService = trainerService;
        this.staffRepo = staffRepo;
    }

    @Override
    public void run(String... args) {
        if (staffRepo.existsByStaffId("INS001")) {
            System.out.println("[Seed] Staff already exist — skipping seed.");
            return;
        }

        trainerService.addFullTimeStaff(new FullTimeStaff("P011", "FT001", "Emma Patel",
            "emma@myfitness.com", "07700200001", "Senior Trainer", 2600.00, "Mon-Fri 06:00-14:00"));
        trainerService.addFullTimeStaff(new FullTimeStaff("P012", "FT002", "Liam Chen",
            "liam@myfitness.com", "07700200002", "Gym Supervisor", 2400.00, "Mon-Fri 14:00-22:00"));
        trainerService.addFullTimeStaff(new FullTimeStaff("P013", "FT003", "Sara Hassan",
            "sara@myfitness.com", "07700200003", "Fitness Coach", 2300.00, "Mon-Fri 08:00-16:00"));
        trainerService.addFullTimeStaff(new FullTimeStaff("P014", "FT004", "Jake Turner",
            "jake@myfitness.com", "07700200004", "Equipment Manager", 2200.00, "Tue-Sat 09:00-17:00"));
        trainerService.addFullTimeStaff(new FullTimeStaff("P015", "FT005", "Nina Brown",
            "nina@myfitness.com", "07700200005", "Front Desk Manager", 2100.00, "Mon-Fri 07:00-15:00"));

        trainerService.addPartTimeStaff(new PartTimeStaff("P021", "PT001", "Mike Lee",
            "mike@myfitness.com", "07700300001", "Cleaner", 12.50, 20, "Weekdays 06:00-10:00"));
        trainerService.addPartTimeStaff(new PartTimeStaff("P022", "PT002", "Anna White",
            "anna@myfitness.com", "07700300002", "Receptionist", 13.00, 24, "Weekends"));
        trainerService.addPartTimeStaff(new PartTimeStaff("P023", "PT003", "Tom Grey",
            "tom@myfitness.com", "07700300003", "Security", 14.00, 16, "Fri-Sat evenings"));
        trainerService.addPartTimeStaff(new PartTimeStaff("P024", "PT004", "Yemi Adio",
            "yemi@myfitness.com", "07700300004", "Towel Assistant", 11.50, 12, "Mon/Wed/Fri"));

        trainerService.addInstructor(new Instructor("P031", "INS001", "Carlos Ruiz",
            "carlos@myfitness.com", "07700400001", 2800.00, "Mon-Fri 07:00-15:00", "Full Body"));
        trainerService.addInstructor(new Instructor("P032", "INS002", "Priya Sharma",
            "priya@myfitness.com", "07700400002", 2750.00, "Mon-Fri 09:00-17:00", "Fitness & Endurance"));
        trainerService.addInstructor(new Instructor("P033", "INS003", "Derek Flynn",
            "derek@myfitness.com", "07700400003", 2700.00, "Tue-Sat 10:00-18:00", "Fat Burn"));

        System.out.println("[Seed] Staff seeded (5 full-time, 4 part-time, 3 instructors).");
    }
}