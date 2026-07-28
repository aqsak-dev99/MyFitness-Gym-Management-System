package com.gymmanagement.ui;

import com.gymmanagement.exception.DuplicateUserException;
import com.gymmanagement.exception.InvalidCredentialsException;
import com.gymmanagement.exception.UnauthorizedException;
import com.gymmanagement.model.*;
import com.gymmanagement.model.membership.*;
import com.gymmanagement.service.AuthService;
import com.gymmanagement.service.MemberService;
import com.gymmanagement.service.MembershipService;
import com.gymmanagement.service.TrainerService;

import java.util.Arrays;
import java.util.List;

/**
 * GymConsoleApp is the ONLY class in this project that calls System.out.
 *
 * It receives the four service objects via constructor injection, calls
 * their methods, and formats the results for the console.
 * No business logic lives here — if you find yourself writing an if-statement
 * about a fee or a membership rule in this class, it belongs in a service instead.
 */
public class GymConsoleApp {

    private final MemberService     memberService;
    private final MembershipService membershipService;
    private final TrainerService    trainerService;
    private final AuthService       authService;

    public GymConsoleApp(MemberService     memberService,
                         MembershipService membershipService,
                         TrainerService    trainerService,
                         AuthService       authService) {
        this.memberService     = memberService;
        this.membershipService = membershipService;
        this.trainerService    = trainerService;
        this.authService       = authService;
    }

    /** Entry point — runs all demo scenarios in order. */
    public void run() {
        printBanner("MyFitness Gym Management System");
        demoMemberRegistration();
        demoAuthentication();
        demoStaffRoster();
        demoMembershipManagement();
        demoBootcampManagement();
        demoFeeCalculation();
        printBanner("All scenarios complete");
    }

    // ══════════════════════════════════════════════════════
    // Demo scenarios
    // ══════════════════════════════════════════════════════

    private void demoMemberRegistration() {
        printSection("Member Registration & Welcome");

        Member alice = registerIfAbsent(
                "P001", "M001", "Alice Smith", "alice@email.com", "07800100001");
        registerIfAbsent(
                "P002", "M002", "Ben Okoro",   "ben@email.com",   "07800100002");
        registerIfAbsent(
                "P003", "M003", "Claire Duval","claire@email.com","07800100003");
        registerIfAbsent(
                "P004", "M004", "David Park",  "david@email.com", "07800100004");

        printWelcome(alice);
        System.out.println(alice.getDetails());

        // Duplicate detection
        printSubSection("Duplicate member guard");
        try {
            memberService.registerMember("P999", "M001", "Duplicate", "x@x.com", "0000");
        } catch (com.gymmanagement.exception.DuplicateMemberException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Invalid input guard
        printSubSection("Invalid input guard");
        try {
            memberService.registerMember("", "M099", "Bad Actor", "x@x.com", "0000");
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }

    /**
     * Registers a member, or returns the existing record if this ID was
     * already registered in a previous run. SQLite persists across runs,
     * so re-running this demo against an existing gym.db would otherwise
     * throw DuplicateMemberException on every restart. This keeps the demo
     * safely re-runnable without needing to delete gym.db each time.
     */
    private Member registerIfAbsent(String personId, String memberId,
                                    String name, String email, String phone) {
        try {
            return memberService.registerMember(personId, memberId, name, email, phone);
        } catch (com.gymmanagement.exception.DuplicateMemberException e) {
            return memberService.getMemberById(memberId);
        }
    }

    private void demoAuthentication() {
        printSection("Authentication");

        // Register an admin account (no linked member — admins aren't gym members)
        printSubSection("Register accounts");
        User admin  = registerUserIfAbsent("admin", "Admin@123", Role.ADMIN, null);
        // Register a member-role account linked to Alice's member record (M001)
        User aliceLogin = registerUserIfAbsent("alice", "Alice@123", Role.MEMBER, "M001");

        // Successful login
        printSubSection("Successful login");
        User loggedInAdmin = authService.login("admin", "Admin@123");
        System.out.println(loggedInAdmin.getDetails());

        // Failed login — wrong password
        printSubSection("Failed login — wrong password");
        try {
            authService.login("admin", "WrongPassword");
        } catch (InvalidCredentialsException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Duplicate username guard
        printSubSection("Duplicate username guard");
        try {
            authService.register("admin", "AnotherPassword", Role.ADMIN, null);
        } catch (DuplicateUserException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Role-based authorization — admin passes, member fails
        printSubSection("Role-based authorization");
        authService.requireRole(admin, Role.ADMIN);
        System.out.println(admin.getUsername() + " is authorized for ADMIN actions.");
        try {
            authService.requireRole(aliceLogin, Role.ADMIN);
        } catch (UnauthorizedException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }

    /**
     * Registers a login account, or skips if the username already exists —
     * same idempotency pattern as registerIfAbsent(), for the same reason:
     * SQLite persists this account across runs, so re-running the demo
     * would otherwise crash on DuplicateUserException every restart.
     */
    private User registerUserIfAbsent(String username, String plainPassword,
                                      Role role, String linkedMemberId) {
        try {
            return authService.register(username, plainPassword, role, linkedMemberId);
        } catch (DuplicateUserException e) {
            return authService.login(username, plainPassword);
        }
    }

    private void demoStaffRoster() {
        printSection("Staff Roster — Polymorphism Demo");

        List<Staff> all = trainerService.getAllStaff();
        System.out.printf("%-5s  %-20s  %-20s  %-10s%n",
                          "ID", "Name", "Type", "Available");
        System.out.println("-".repeat(62));
        for (Staff s : all) {
            System.out.printf("%-5s  %-20s  %-20s  %-10b%n",
                              s.getStaffId(), s.getName(),
                              s.getClass().getSimpleName(), s.isAvailable());
        }
        System.out.println();
        System.out.println("Full-time count  : " + trainerService.getAllFullTimeStaff().size());
        System.out.println("Part-time count  : " + trainerService.getAllPartTimeStaff().size());
        System.out.println("Instructor count : " + trainerService.getAllInstructors().size());
    }

    private void demoMembershipManagement() {
        printSection("Membership Management");

        // Assign memberships
        memberService.assignMembership("M001", new StudentSaverMembership("MEM101", "S-A12345"));
        memberService.assignMembership("M002", new StandardMembership("MEM102", 12));
        memberService.assignMembership("M003", new PayAsYouGoMembership("MEM103"));
        memberService.assignMembership("M004", new StandardMembership("MEM104", 6));

        // Record PAYG sessions for Claire (M003) via service
        membershipService.recordPayAsYouGoSession("M003");
        membershipService.recordPayAsYouGoSession("M003");

        // Display all members with days/months remaining
        printSubSection("Active membership status");
        for (Member m : memberService.getAllMembers()) {
            System.out.println(m.getDetails());
            Membership ms = m.getMembership();
            if (ms != null) {
                System.out.println("  -> Days remaining  : " + ms.getDaysRemaining());
                System.out.println("  -> Months remaining: " + ms.getMonthsRemaining());
            }
            System.out.println();
        }

        // Freeze / unfreeze Ben's Standard membership
        printSubSection("Freeze and unfreeze (Ben — M002)");
        membershipService.freezeMembership("M002");
        System.out.println("Ben active after freeze : "
                           + memberService.getMemberById("M002").getMembership().isActive());
        membershipService.unfreezeMembership("M002");
        System.out.println("Ben active after unfreeze: "
                           + memberService.getMemberById("M002").getMembership().isActive());

        // Remove Alice's membership
        printSubSection("Remove membership (Alice — M001)");
        memberService.removeMembership("M001");
        System.out.println(memberService.getMemberById("M001").getDetails());
    }

    private void demoBootcampManagement() {
        printSection("Bootcamp Class Management");

        // Classes already seeded in Main — retrieve them
        List<BootcampClass> bootcamps = membershipService.getAllBootcampClasses();

        printSubSection("Instructor assignment");
        List<Instructor> instructors = trainerService.getAllInstructors();
        trainerService.assignInstructorToClass(instructors.get(0).getStaffId(), bootcamps.get(0));
        trainerService.assignInstructorToClass(instructors.get(1).getStaffId(), bootcamps.get(1));
        trainerService.assignInstructorToClass(instructors.get(2).getStaffId(), bootcamps.get(2));
        // assignInstructorToClass only mutates the in-memory BootcampClass object —
        // it must be explicitly saved, or the next fetch from the repository
        // will show "Instructor: Not assigned" since the DB was never updated.
        bootcamps.forEach(membershipService::addBootcampClass);

        printSubSection("Member enrolments");
        Payment p1 = enrolIfAbsent("M001", "BC001");
        Payment p2 = enrolIfAbsent("M002", "BC001");
        Payment p3 = enrolIfAbsent("M002", "BC002"); // Ben's 2nd → discount
        Payment p4 = enrolIfAbsent("M003", "BC002");
        Payment p5 = enrolIfAbsent("M004", "BC003");

        // Process payments — skip any enrolment that was already in place
        // from a previous run (enrolIfAbsent returns null for those).
        printSubSection("Payment processing");
        for (Payment p : Arrays.asList(p1, p2, p3, p4, p5)) {
            if (p == null) continue;
            membershipService.processPayment(p);
            System.out.println(p.getDetails());
            System.out.println();
        }

        // Print class details
        printSubSection("Class details after enrolments");
        for (BootcampClass bc : membershipService.getAllBootcampClasses()) {
            System.out.println(bc.getDetails());
            System.out.println();
        }

        // Remove Alice from Fat Burn
        printSubSection("Remove Alice (M001) from Fat Burn (BC001)");
        membershipService.removeFromBootcamp("M001", "BC001");
        System.out.println(membershipService.getBootcampById("BC001").getDetails());
    }

    /**
     * Enrols a member in a bootcamp class, or skips silently if they are
     * already enrolled — which happens on a second run against a gym.db
     * that already has this enrolment persisted from a previous session.
     * Returns null when skipped so the caller omits payment processing
     * for an enrolment that already happened previously.
     */
    private Payment enrolIfAbsent(String memberId, String classId) {
        try {
            return membershipService.enrolInBootcamp(memberId, classId);
        } catch (IllegalStateException e) {
            System.out.println("Skipped — " + e.getMessage());
            return null;
        }
    }

    private void demoFeeCalculation() {
        printSection("Bootcamp Fee Calculation — IBootcampFee Interface");

        BootcampClass fatBurn = membershipService.getBootcampById("BC001");

        System.out.println("Fee for 1 class  : £" + String.format("%.2f", fatBurn.calcBootcampFee(1)));
        System.out.println("Fee for 2 classes: £" + String.format("%.2f", fatBurn.calcBootcampFee(2))
                           + " per class  (7% discount applied)");
        System.out.printf("Total for 2      : £%.2f%n",  fatBurn.calcBootcampFee(2) * 2);

        printSubSection("Interface polymorphism — IBootcampFee reference");
        IBootcampFee[] feeClasses = membershipService.getAllBootcampClasses()
                                                     .toArray(new IBootcampFee[0]);
        System.out.printf("%-38s | 1-class   | 2-class%n", "Class");
        System.out.println("-".repeat(62));
        for (IBootcampFee f : feeClasses) {
            System.out.printf("%-38s | £%-8.2f | £%.2f%n",
                              ((BootcampClass) f).getClassName(),
                              f.calcBootcampFee(1),
                              f.calcBootcampFee(2));
        }

        printSubSection("Instructor summary");
        for (Instructor ins : trainerService.getAllInstructors()) {
            System.out.println(ins.getDetails());
            System.out.println();
        }
    }

    // ══════════════════════════════════════════════════════
    // Console formatting helpers
    // ══════════════════════════════════════════════════════

    private void printWelcome(Member member) {
        System.out.println("============================================");
        System.out.println("  Welcome to MyFitness, " + member.getName() + "!");
        System.out.println("============================================");
        System.out.println("Gym hours  : Mon–Sun  06:00–22:00");
        System.out.println("Contact    : info@myfitness.com | 0123 456 789");
        System.out.println("Upcoming   : Bootcamp classes available!");
        System.out.println("Facilities : Equipment, showers, free Wi-Fi,");
        System.out.println("             changing rooms, sports water.");
        System.out.println("============================================\n");
    }

    private void printBanner(String text) {
        System.out.println("\n" + "=".repeat(62));
        System.out.println("  " + text);
        System.out.println("=".repeat(62));
    }

    private void printSection(String text) {
        System.out.println("\n" + "─".repeat(62));
        System.out.println("  " + text);
        System.out.println("─".repeat(62));
    }

    private void printSubSection(String text) {
        System.out.println("\n>> " + text);
        System.out.println("   " + "─".repeat(text.length()));
    }
}