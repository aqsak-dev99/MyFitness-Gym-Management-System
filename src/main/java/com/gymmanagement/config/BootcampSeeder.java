package com.gymmanagement.config;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.repository.BootcampRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * BootcampSeeder — fixes a real bug found when this app was first deployed
 * to Render: bootcamp classes came back as an empty list, because
 * Main.java's seedBootcampClasses() only ever runs when Main.main() is the
 * entry point — the plain console app. Spring Boot uses a completely
 * separate entry point (MyFitnessApplication), so that seeding logic
 * never executed at all under `mvn spring-boot:run` or on Render.
 *
 * It didn't show up locally because gym.db already had seeded data in it
 * from earlier console-app runs that same day — Spring Boot was reusing
 * an already-seeded file, not actually seeding anything itself. A fresh
 * container on Render had no such file, so the gap became visible.
 *
 * CommandLineRunner is Spring Boot's hook for "run this once, right after
 * the application context is fully wired." Any bean implementing this
 * interface has its run() method called automatically on startup — no
 * manual wiring needed, and by the time it runs, every other bean
 * (including BootcampRepository) is guaranteed to already exist.
 *
 * Same idempotency guard as Main.java: only seeds if the table is empty,
 * so this is safe to run on every restart without creating duplicates.
 */
@Component
public class BootcampSeeder implements CommandLineRunner {

    private final BootcampRepository bootcampRepo;

    public BootcampSeeder(BootcampRepository bootcampRepo) {
        this.bootcampRepo = bootcampRepo;
    }

    @Override
    public void run(String... args) {
        if (!bootcampRepo.hasAnyClasses()) {
            bootcampRepo.save(new BootcampClass("BC001", BootcampType.FAT_BURN,
                    "Mon/Wed 07:00", 10));
            bootcampRepo.save(new BootcampClass("BC002", BootcampType.FITNESS_AND_ENDURANCE,
                    "Tue/Thu 08:00", 10));
            bootcampRepo.save(new BootcampClass("BC003", BootcampType.FULL_BODY,
                    "Sat 09:00", 10));
            System.out.println("[Seed] Bootcamp classes seeded.");
        } else {
            System.out.println("[Seed] Bootcamp classes already exist — skipping seed.");
        }
    }
}