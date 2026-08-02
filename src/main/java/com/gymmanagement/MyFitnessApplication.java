package com.gymmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring Boot entry point — a separate, parallel entry point from
 * Main.java, not a replacement for it.
 *
 * @SpringBootApplication does three things at once:
 *  1. Marks this as the application's configuration root.
 *  2. Enables auto-configuration (Spring Boot sets up the embedded web
 *     server, Jackson JSON conversion, etc. based on what's on the
 *     classpath — you didn't have to configure any of that by hand).
 *  3. Enables component scanning — Spring walks every class in this
 *     package and its sub-packages looking for @Service, @Repository,
 *     @RestController, etc. and registers each one as a managed "bean."
 *     This is WHY MemberService (now @Service) and SqliteMemberRepository
 *     (now @Repository) get found and wired together automatically —
 *     nobody had to write `new MemberService(new SqliteMemberRepository())`
 *     anywhere. Spring does that wiring for you, using the exact same
 *     constructor you already wrote.
 *
 * Run with: mvn spring-boot:run
 * Your original console demo still runs separately with: mvn compile exec:java
 */
@SpringBootApplication
public class MyFitnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyFitnessApplication.class, args);
    }
}