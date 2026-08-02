# MyFitness

A gym management backend, built from scratch to survive the kind of question that starts with "walk me through why you did it this way." Not a tutorial clone, not a class assignment — a real system that's been broken, debugged, and fixed enough times to actually mean something.

It manages members, three different membership pricing models, bootcamp classes with capacity and discount logic, payments, and login — all backed by a real database, all covered by a real test suite, all built through a real dependency-management tool instead of hand-downloaded jars.

## What it does

**Members** — register, look up, update, deactivate. Backed by SQLite, so nothing disappears on restart.

**Three membership types**, each with its own pricing logic — Standard, Student Saver, Pay-As-You-Go — modeled through an abstract `Membership` class rather than a `type` field and a growing `if/else` chain.

**Bootcamp classes** — enrolment respects capacity (`ClassFullException` when a class is full), and a 7% discount kicks in automatically once a member is enrolled in more than one class. This discount logic broke silently once already — more on that below — so it's now guarded by a dedicated regression test.

**Payments** — processed through the service layer, with a hard business rule (no payment over a set ceiling) enforced *before* anything touches the database. Status moves through explicit methods (`markCompleted()`, `markFailed()`), never a field overwritten from outside.

**Authentication** — BCrypt-hashed passwords, `ADMIN` and `MEMBER` roles, login gating what each role can actually do. A wrong password and a username that doesn't exist return the *identical* error — deliberately, so a login attempt can't be used to figure out which usernames are real.

**31 passing JUnit 5 tests**, one for every service in the system — not padding, not getter/setter tests. Two of them exist specifically because a bug made it past manual testing once and won't get the chance to do it silently again.

---

## Architecture

```
com.gymmanagement
├── model/                  Person, Member, Staff hierarchy, GymClass, User
│   └── membership/          Standard / StudentSaver / PayAsYouGo, IBootcampFee
├── repository/              MemberRepository, BootcampRepository, UserRepository
│                            (interfaces — Sqlite implementations underneath)
├── service/                 MemberService, MembershipService,
│                            TrainerService, AuthService — the business rules live here
├── exception/                8 custom types: MemberNotFoundException,
│                            DuplicateMemberException, ClassFullException,
│                            PaymentFailedException, InvalidInputException,
│                            InvalidCredentialsException, DuplicateUserException,
│                            UnauthorizedException
├── db/                      DatabaseManager (connection), DatabaseSchema (DDL)
└── ui/                      GymConsoleApp — the only class allowed to touch System.out
```

Source lives under Maven's standard layout — `src/main/java/...` for the application, `src/test/java/...` for the 31-test suite — rather than a flat `src/` folder.

This wasn't the first shape it took, in either sense. It started as one flat `Main.java` — creating objects, running demo scenarios, printing output, and standing in as the database, all in the same file. Splitting that apart was the single change that mattered most here. `Main.java` is about 10 lines now. It wires four services together and gets out of the way. Everything else has exactly one job. The build system went through its own equivalent shift later — from manually downloaded jars and hand-built classpath strings to Maven managing every dependency by declaration. Same principle, different layer: stop doing by hand what a tool exists to do correctly.

---

## Decisions I'd actually defend

Every one of these exists because something broke first, not because a guide said to do it this way.

**`ON CONFLICT(member_id) DO UPDATE` instead of `INSERT OR REPLACE`.**
`INSERT OR REPLACE` doesn't update a row — it deletes it and inserts a new one. With `ON DELETE CASCADE` on `bootcamp_enrolments.member_id`, that meant every routine member update silently wiped that member's bootcamp enrolments. Nothing errored. It just quietly lost data. Switching to a real `UPDATE` fixed it without touching the cascade rule anywhere else.

**`users.member_id` uses `ON DELETE SET NULL`, not `CASCADE`.**
Applied on purpose, right after finding the bug above, and before it had the chance to repeat itself in the newly added auth tables. Deleting a member shouldn't be able to silently delete their login too.

**`Member.equals()` and `hashCode()` are based on `memberId`, not object identity.**
Every SQLite fetch builds a brand-new `Member` object. Without this override, two fetches of "the same" member were never equal to each other, which quietly broke duplicate-enrolment checks and — less obviously — made the bootcamp discount logic never fire, since it depends on counting a member's existing enrolments correctly. Two of the 31 tests exist purely to make sure this specific fix can never silently regress.

**Idempotent `registerIfAbsent()`-style helpers throughout.**
Re-running setup against an existing `gym.db` shouldn't throw a duplicate-key exception. It should just recognize the data's already there and move on.

**A hand-written `FakeMemberRepository` instead of Mockito.**
Mockito pulls in several more transitive dependencies. Given how much of this project involved untangling dependency and classpath issues by hand, adding a heavier mocking framework for a marginal convenience felt like the wrong tradeoff. A five-minute fake implementing the same interface tests the service layer just as well, with nothing extra to break.

**`Arrays.asList()` instead of `List.of()` for nullable payment lists.**
`List.of()` throws on any `null` element. In this codebase, `null` in that list is a meaningful, legitimate value — not a bug to guard against.

**Manual `javac` and jars first, then a deliberate Maven migration — not Maven from day one.**
The project started on manual `javac` because that's what surfaced *why* a build tool matters in the first place: chasing individual jar files, discovering `sqlite-jdbc` needed `slf4j-api` as an undeclared transitive dependency, and hand-writing classpath strings for every compile and every test run. Migrating to Maven afterward meant that value was actually understood, not just assumed.

---

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Persistence | SQLite via JDBC |
| Auth | BCrypt |
| Testing | JUnit 5, hand-rolled fakes (no mocking framework) |
| Build | Maven |

---

## Running it locally

```bash
mvn compile
```

Run the app:

```bash
mvn compile exec:java
```

Run the full test suite:

```bash
mvn test
```

That's it. Maven resolves `sqlite-jdbc`, `slf4j-api`, `jbcrypt`, and `junit-jupiter` on its own — no manually downloaded jars, no classpath strings to assemble by hand.

`gym.db` is created automatically on first run. Re-running is safe — nothing gets seeded twice.

---

## Roadmap

1. ~~Layered architecture~~ — done
2. ~~SQLite persistence~~ — done
3. ~~Authentication~~ — done
4. ~~JUnit coverage across all four services~~ — done, 31 tests passing
5. ~~Migrate off manual `javac` onto Maven~~ — done. The test suite existed partly to catch anything this migration broke — it caught nothing, all 31 passed straight through the new build layout.
6. Convert to a Spring Boot REST API
7. Docker
8. Deploy — Azure App Service (free tier) + Azure SQL, Render as backup so the live link doesn't go dark mid-application-cycle
9. Write up the debugging process as a blog post — the cascading-delete bug alone is worth its own writeup
