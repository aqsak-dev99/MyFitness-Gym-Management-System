package com.gymmanagement;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.*;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.SqliteBootcampRepository;
import com.gymmanagement.repository.SqliteMemberRepository;
import com.gymmanagement.repository.SqliteUserRepository;
import com.gymmanagement.service.AuthService;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;
import com.gymmanagement.ui.GymConsoleApp;

import java.util.List;

/**
 * Main.java — application entry point.
 *
 * Changes from JSON version:
 *   1. Repositories are now SQLite-backed (SqliteMemberRepository,
 *      SqliteBootcampRepository) — no JSON files, no shutdown flush.
 *   2. DatabaseManager.getInstance() opens gym.db and creates the schema
 *      before any repository is constructed.
 *   3. SqliteBootcampRepository receives SqliteMemberRepository directly
 *      (not a generic Supplier<List<Member>>) because it needs findByIds()
 *      which is not on the MemberRepository interface.
 *   4. Seed guard uses bootcampRepo.findAll().isEmpty() — when gym.db
 *      already has rows from a previous run, no seed data is inserted.
 *   5. Shutdown hook closes the DB connection cleanly (flushes WAL).
 *
 * Everything else (services, UI, business logic) is unchanged.
 */
public class Main {

    public static void main(String[] args) {

        // ── 1. Initialise the database ─────────────────────────────────────────
        // Opens gym.db (creates it on first run), enables FK enforcement,
        // and runs CREATE TABLE IF NOT EXISTS for all five tables.
        DatabaseManager db = DatabaseManager.getInstance();

        // ── 2. Build staff lists ───────────────────────────────────────────────
        List<FullTimeStaff> fullTimeStaff = buildFullTimeStaff();
        List<PartTimeStaff> partTimeStaff = buildPartTimeStaff();
        List<Instructor>    instructors   = buildInstructors();

        // ── 3. Repositories ────────────────────────────────────────────────────
        SqliteMemberRepository memberRepo = new SqliteMemberRepository();
        SqliteUserRepository   userRepo   = new SqliteUserRepository();

        // SqliteBootcampRepository needs:
        //   memberRepo   — to resolve participant IDs → Member objects
        //   instructors  — to resolve instructor_id → Instructor objects
        SqliteBootcampRepository bootcampRepo =
                new SqliteBootcampRepository(memberRepo, () -> instructors);

        // ── 4. Wire services ───────────────────────────────────────────────────
        MemberService     memberService     = new MemberService(memberRepo);
        TrainerService    trainerService    = new TrainerService(fullTimeStaff,
                                                                  partTimeStaff,
                                                                  instructors);
        MembershipService membershipService = new MembershipService(memberRepo, bootcampRepo);
        AuthService        authService      = new AuthService(userRepo);

        // ── 5. Seed bootcamp classes only on first run ─────────────────────────
        // bootcampRepo.findAll() queries the DB — returns empty list when the
        // table is empty (first run).  On subsequent runs the rows exist and
        // seeding is skipped, preventing duplicates.
        if (bootcampRepo.findAll().isEmpty()) {
            seedBootcampClasses(bootcampRepo);
        }

        // ── 6. Shutdown hook — close DB connection on exit ─────────────────────
        // SQLite WAL mode may buffer writes; closing the connection flushes them.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            db.close();
            System.out.println("[App] Shutdown complete.");
        }));

        // ── 7. Launch ──────────────────────────────────────────────────────────
        new GymConsoleApp(memberService, membershipService, trainerService, authService).run();
    }

    // ── seed helpers ──────────────────────────────────────────────────────────

    private static List<FullTimeStaff> buildFullTimeStaff() {
        return List.of(
            new FullTimeStaff("P011","FT001","Emma Patel",
                "emma@myfitness.com","07700200001","Senior Trainer",    2600.00,"Mon-Fri 06:00-14:00"),
            new FullTimeStaff("P012","FT002","Liam Chen",
                "liam@myfitness.com","07700200002","Gym Supervisor",    2400.00,"Mon-Fri 14:00-22:00"),
            new FullTimeStaff("P013","FT003","Sara Hassan",
                "sara@myfitness.com","07700200003","Fitness Coach",     2300.00,"Mon-Fri 08:00-16:00"),
            new FullTimeStaff("P014","FT004","Jake Turner",
                "jake@myfitness.com","07700200004","Equipment Manager", 2200.00,"Tue-Sat 09:00-17:00"),
            new FullTimeStaff("P015","FT005","Nina Brown",
                "nina@myfitness.com","07700200005","Front Desk Manager",2100.00,"Mon-Fri 07:00-15:00")
        );
    }

    private static List<PartTimeStaff> buildPartTimeStaff() {
        return List.of(
            new PartTimeStaff("P021","PT001","Mike Lee",
                "mike@myfitness.com","07700300001","Cleaner",        12.50,20,"Weekdays 06:00-10:00"),
            new PartTimeStaff("P022","PT002","Anna White",
                "anna@myfitness.com","07700300002","Receptionist",   13.00,24,"Weekends"),
            new PartTimeStaff("P023","PT003","Tom Grey",
                "tom@myfitness.com", "07700300003","Security",       14.00,16,"Fri-Sat evenings"),
            new PartTimeStaff("P024","PT004","Yemi Adio",
                "yemi@myfitness.com","07700300004","Towel Assistant", 11.50,12,"Mon/Wed/Fri")
        );
    }

    private static List<Instructor> buildInstructors() {
        return List.of(
            new Instructor("P031","INS001","Carlos Ruiz",
                "carlos@myfitness.com","07700400001",2800.00,"Mon-Fri 07:00-15:00","Full Body"),
            new Instructor("P032","INS002","Priya Sharma",
                "priya@myfitness.com", "07700400002",2750.00,"Mon-Fri 09:00-17:00","Fitness & Endurance"),
            new Instructor("P033","INS003","Derek Flynn",
                "derek@myfitness.com", "07700400003",2700.00,"Tue-Sat 10:00-18:00","Fat Burn")
        );
    }

    private static void seedBootcampClasses(BootcampRepository repo) {
        repo.save(new BootcampClass("BC001", BootcampType.FAT_BURN,              "Mon/Wed 07:00", 10));
        repo.save(new BootcampClass("BC002", BootcampType.FITNESS_AND_ENDURANCE, "Tue/Thu 08:00", 10));
        repo.save(new BootcampClass("BC003", BootcampType.FULL_BODY,             "Sat 09:00",     10));
    }
}