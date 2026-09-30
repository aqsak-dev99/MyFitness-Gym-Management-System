package com.gymmanagement;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.*;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.SqliteBootcampRepository;
import com.gymmanagement.repository.SqliteMemberRepository;
import com.gymmanagement.repository.SqliteStaffRepository;
import com.gymmanagement.repository.SqliteUserRepository;
import com.gymmanagement.service.AuthService;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;
import com.gymmanagement.ui.GymConsoleApp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.List;

/**
 * Main.java — application entry point for the plain console app (a
 * completely separate entry point from MyFitnessApplication/Spring Boot
 * — see the class-level notes on BootcampSeeder for why that distinction
 * matters).
 *
 * Updated for the HikariCP migration: this runs entirely outside
 * Spring's context, so it can't have a DataSource autowired the way
 * every @Repository bean now does. It builds its own small
 * HikariDataSource directly instead — same pool library, same
 * DATABASE_URL parsing (DatabaseManager.resolveConnectionParts(),
 * unchanged), just constructed by hand here since there's no Spring
 * container to do it for this entry point.
 *
 * Changes from the JSON version further back:
 *   1. Repositories are SQLite/Postgres-backed — no JSON files, no
 *      shutdown flush.
 *   2. Schema creation now happens once, right after the DataSource is
 *      built below — same statements DataSourceConfig runs for the
 *      real Spring app, just invoked directly here instead.
 *   3. SqliteBootcampRepository receives SqliteMemberRepository directly
 *      (not a generic Supplier<List<Member>>) because it needs findByIds()
 *      which is not on the MemberRepository interface.
 *   4. Seed guard uses bootcampRepo.hasAnyClasses() — the same lightweight
 *      check the real seeder now uses, rather than a full findAll().
 *   5. Shutdown hook closes the pool cleanly, not a single Connection.
 *
 * Everything else (services, UI, business logic) is unchanged.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        // ── 1. Build a small HikariCP pool for this standalone entry point ──
        String[] parts = DatabaseManager.resolveConnectionParts();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parts[0]);
        config.setUsername(parts[1]);
        config.setPassword(parts[2]);
        config.setMaximumPoolSize(3);   // this console app is single-user by nature
        config.setMinimumIdle(1);
        HikariDataSource dataSource = new HikariDataSource(config);
        com.gymmanagement.config.DataSourceConfig.runSchemaCreation(dataSource);

        // ── 2. Build staff lists ───────────────────────────────────────────────
        List<FullTimeStaff> fullTimeStaff = buildFullTimeStaff();
        List<PartTimeStaff> partTimeStaff = buildPartTimeStaff();
        List<Instructor>    instructors   = buildInstructors();

        // ── 3. Repositories ────────────────────────────────────────────────────
        SqliteMemberRepository memberRepo = new SqliteMemberRepository(dataSource);
        SqliteUserRepository   userRepo   = new SqliteUserRepository(dataSource);

        // SqliteBootcampRepository needs:
        //   memberRepo   — to resolve participant IDs → Member objects
        //   instructors  — to resolve instructor_id → Instructor objects
        SqliteBootcampRepository bootcampRepo =
                new SqliteBootcampRepository(memberRepo, () -> instructors, dataSource);
        SqliteStaffRepository  staffRepo   = new SqliteStaffRepository(dataSource);

        // ── 4. Wire services ───────────────────────────────────────────────────
        MemberService     memberService     = new MemberService(memberRepo);
        TrainerService    trainerService    = new TrainerService(fullTimeStaff,
                                                                  partTimeStaff,
                                                                  instructors,
                                                                  staffRepo);
        MembershipService membershipService = new MembershipService(memberRepo, bootcampRepo);
        AuthService        authService      = new AuthService(userRepo);

        // ── 5. Seed bootcamp classes only on first run ─────────────────────────
        // hasAnyClasses() is a single lightweight existence query — the same
        // fix applied to the real Spring seeder, not a full findAll().
        if (!bootcampRepo.hasAnyClasses()) {
            seedBootcampClasses(bootcampRepo);
        }

        // ── 6. Shutdown hook — close the pool on exit ──────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dataSource.close();
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