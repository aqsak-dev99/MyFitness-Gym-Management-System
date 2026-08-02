package com.gymmanagement.config;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Supplier;

/**
 * StaffConfig — provides the hand-seeded staff data as Spring beans.
 *
 * This data has no database table (staff were never persisted — Main.java
 * always rebuilt this same list on every startup). There's no @Component
 * or @Repository to annotate for data like this; a @Configuration class
 * with @Bean factory methods is the standard Spring way to hand-construct
 * something that doesn't come from component scanning or a database.
 *
 * The data below is an exact copy of Main.java's buildFullTimeStaff(),
 * buildPartTimeStaff(), and buildInstructors() — same people, same IDs,
 * same schedules — so the console app and the REST API show identical
 * seed data, not two different fake datasets.
 *
 * Only instructors() and instructorSupplier() are actually required for
 * today's MembershipController (SqliteBootcampRepository needs the
 * Supplier to resolve instructor_id → Instructor at load time). The other
 * two lists are included for completeness and so TrainerService's own
 * Spring wiring needs zero extra configuration when that's built next.
 */
@Configuration
public class StaffConfig {

    @Bean
    public List<FullTimeStaff> fullTimeStaff() {
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

    @Bean
    public List<PartTimeStaff> partTimeStaff() {
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

    @Bean
    public List<Instructor> instructors() {
        return List.of(
            new Instructor("P031","INS001","Carlos Ruiz",
                "carlos@myfitness.com","07700400001",2800.00,"Mon-Fri 07:00-15:00","Full Body"),
            new Instructor("P032","INS002","Priya Sharma",
                "priya@myfitness.com", "07700400002",2750.00,"Mon-Fri 09:00-17:00","Fitness & Endurance"),
            new Instructor("P033","INS003","Derek Flynn",
                "derek@myfitness.com", "07700400003",2700.00,"Tue-Sat 10:00-18:00","Fat Burn")
        );
    }

    /**
     * SqliteBootcampRepository's constructor takes a Supplier<List<Instructor>>,
     * not a plain List — Spring doesn't generate that wrapper automatically,
     * so it's defined explicitly here, just wrapping the instructors() bean
     * above in the shape that constructor expects.
     */
    @Bean
    public Supplier<List<Instructor>> instructorSupplier(List<Instructor> instructors) {
        return () -> instructors;
    }
}