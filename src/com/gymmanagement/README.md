# MyFitness

A gym management backend, built from scratch to survive the kind of question that starts with "walk me through why you did it this way." Not a tutorial clone, not a class assignment — a real system that's been broken, debugged, and fixed enough times to actually mean something.

It manages members, three different membership pricing models, bootcamp classes with capacity and discount logic, payments, and login — all backed by a real database, all covered by a real test suite.

---

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

This wasn't the first shape it took. It started as one flat `Main.java` — creating objects, running demo scenarios, printing output, and standing in as the database, all in the same file. Splitting that apart was the single change that mattered most here. `Main.java` is about 10 lines now. It wires four services together and gets out of the way. Everything else has exactly one job.

---

## Decisions I'd actually defend

Every one of these exists because something broke first, not because a guide said to do it this way.

**`ON CONFLICT(member_id) DO UPDATE` instead of `INSERT OR REPLACE`.**
`INSERT OR REPLACE` doesn't update a row — it deletes it and inserts a new one. With `ON DELETE CASCADE` on `bootcamp_enrolments.member_id`, that meant every routine member update silently wiped that member's bootcamp enrolments. Nothing errored. It just quietly lost data. Switching to a real `UPDATE` fixed it without touching the cascade rule anywhere else.

**`users.member_id` uses `ON DELETE SET NULL`, not `CASCADE`.**
Applied on purpose, right after finding the bug above. Deleting a member shouldn't be able to silently delete their login too.

**`Member.equals()` and `hashCode()` are based on `memberId`, not object identity.**
Every SQLite fetch builds a brand-new `Member` object. Without this override, two fetches of "the same" member were never equal to each other, which quietly broke duplicate-enrolment checks and — less obviously — made the bootcamp discount logic never fire, since it depends on counting a member's existing enrolments correctly.

**Idempotent `registerIfAbsent()`-style helpers throughout.**
Re-running setup against an existing `gym.db` shouldn't throw a duplicate-key exception. It should just recognize the data's already there and move on.

**A hand-written `FakeMemberRepository` instead of Mockito.**
The build is manual `javac` and jars right now, no Maven. Mockito pulls in three more transitive dependencies for a project already juggling classpath jars by hand. A five-minute fake implementing the same interface tests the service layer just as well, without the extra dependency risk.

**`Arrays.asList()` instead of `List.of()` for nullable payment lists.**
`List.of()` throws on any `null` element. In this codebase, `null` in that list is a meaningful, legitimate value — not a bug to guard against.

---

## Tech stack

| | |
|---|---|
| Language | Java |
| Persistence | SQLite via JDBC |
| Auth | BCrypt |
| Testing | JUnit 5, hand-rolled fakes (no mocking framework yet) |
| Build | manual `javac` + jars — Maven is next |

---

## Running it locally

```bash
find src test -name "*.java" > sources.txt
javac -cp "lib/sqlite-jdbc-3.45.1.0.jar:lib/slf4j-api-1.7.36.jar:lib/jbcrypt-0.4.jar:lib/junit-platform-console-standalone-1.10.2.jar" -d out @sources.txt
java -cp "out:lib/sqlite-jdbc-3.45.1.0.jar:lib/slf4j-api-1.7.36.jar:lib/jbcrypt-0.4.jar" com.gymmanagement.Main
```

Running the test suite:

```bash
java -jar lib/junit-platform-console-standalone-1.10.2.jar execute \
  --classpath "out:lib/sqlite-jdbc-3.45.1.0.jar:lib/slf4j-api-1.7.36.jar:lib/jbcrypt-0.4.jar" \
  --scan-classpath --details tree
```

`gym.db` is created automatically on first run. Re-running is safe — nothing gets seeded twice.

---

## Roadmap

1. ~~Layered architecture~~ — done
2. ~~SQLite persistence~~ — done
3. ~~Authentication~~ — done
4. ~~JUnit coverage across all four services~~ — done, 31 tests passing
5. Migrate off manual `javac` onto Maven — the test suite exists partly to catch anything this migration breaks
6. Convert to a Spring Boot REST API
7. Docker
8. Deploy — Azure App Service (free tier) + Azure SQL, Render as backup so the live link doesn't go dark mid-application-cycle
9. Write up the debugging process as a blog post — the cascading-delete bug alone is worth its own writeup