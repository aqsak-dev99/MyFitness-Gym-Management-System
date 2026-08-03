package com.gymmanagement.db;

/**
 * DatabaseSchema holds every CREATE TABLE statement as a named constant.
 *
 * Why a separate class?
 *   Keeping DDL here means table structure is changed in exactly one place.
 *   DatabaseManager calls these constants on startup — no SQL is scattered
 *   across repository classes.
 *
 * Schema design decisions:
 *
 *  members
 *    Stores core Person fields plus Member-specific fields.
 *    registration_date is stored as ISO-8601 TEXT ("YYYY-MM-DD") because
 *    SQLite has no native DATE type; TEXT sorts correctly for date comparisons.
 *
 *  memberships
 *    One row per membership.  membership_type discriminator column stores
 *    the simple class name ("StudentSaverMembership", "StandardMembership",
 *    "PayAsYouGoMembership") so the repository can instantiate the correct
 *    concrete subclass on load.
 *    member_id is a UNIQUE FK — one member holds at most one membership.
 *    extra_data stores type-specific fields (student ID number, freeze count,
 *    sessions used) as a plain TEXT value.  This avoids three separate tables
 *    for a small dataset and keeps the join count low.
 *
 *  payments
 *    One row per payment transaction.  member_id FK links to members.
 *
 *  bootcamp_classes
 *    One row per class.  instructor_id is nullable — a class may be
 *    created before an instructor is assigned.
 *
 *  bootcamp_enrolments
 *    Junction table.  Resolves the many-to-many relationship between
 *    members and bootcamp classes.  enrolled_at records the enrolment date.
 *    Composite PK (class_id, member_id) prevents double-enrolment at the
 *    database level, complementing the service-layer check.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {}  // utility class

    // ── members ───────────────────────────────────────────
    public static final String CREATE_MEMBERS =
        "CREATE TABLE IF NOT EXISTS members (" +
        "  member_id          TEXT PRIMARY KEY," +
        "  person_id          TEXT NOT NULL," +
        "  name               TEXT NOT NULL," +
        "  email              TEXT NOT NULL," +
        "  phone              TEXT," +
        "  registration_date  TEXT NOT NULL" +
        ")";

    // ── memberships ───────────────────────────────────────
    public static final String CREATE_MEMBERSHIPS =
        "CREATE TABLE IF NOT EXISTS memberships (" +
        "  membership_id    TEXT PRIMARY KEY," +
        "  member_id        TEXT NOT NULL UNIQUE," +
        "  membership_type  TEXT NOT NULL," +   // discriminator
        "  start_date       TEXT NOT NULL," +
        "  end_date         TEXT NOT NULL," +
        "  monthly_fee      REAL NOT NULL," +
        "  frozen           INTEGER NOT NULL DEFAULT 0," +  // 0=false, 1=true
        "  extra_data       TEXT," +    // type-specific: studentIdNo | freezeCount | sessionsUsed
        "  FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE" +
        ")";

    // ── payments ──────────────────────────────────────────
    public static final String CREATE_PAYMENTS =
        "CREATE TABLE IF NOT EXISTS payments (" +
        "  payment_id   TEXT PRIMARY KEY," +
        "  member_id    TEXT NOT NULL," +
        "  amount       REAL NOT NULL," +
        "  description  TEXT," +
        "  payment_date TEXT NOT NULL," +
        "  status       TEXT NOT NULL," +
        "  FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE" +
        ")";

    // ── bootcamp_classes ──────────────────────────────────
    public static final String CREATE_BOOTCAMP_CLASSES =
        "CREATE TABLE IF NOT EXISTS bootcamp_classes (" +
        "  class_id      TEXT PRIMARY KEY," +
        "  bootcamp_type TEXT NOT NULL," +  // BootcampType enum name
        "  schedule      TEXT NOT NULL," +
        "  max_capacity  INTEGER NOT NULL," +
        "  instructor_id TEXT" +            // nullable — maps to Staff.staffId
        ")";

    // ── bootcamp_enrolments ───────────────────────────────
    public static final String CREATE_BOOTCAMP_ENROLMENTS =
        "CREATE TABLE IF NOT EXISTS bootcamp_enrolments (" +
        "  class_id     TEXT NOT NULL," +
        "  member_id    TEXT NOT NULL," +
        "  enrolled_at  TEXT NOT NULL," +
        "  PRIMARY KEY (class_id, member_id)," +
        "  FOREIGN KEY (class_id)  REFERENCES bootcamp_classes(class_id) ON DELETE CASCADE," +
        "  FOREIGN KEY (member_id) REFERENCES members(member_id)         ON DELETE CASCADE" +
        ")";

    // ── users (authentication) ────────────────────────────
    // member_id uses ON DELETE SET NULL, not CASCADE. If a member is deleted,
    // we don't want their login account silently deleted with them — that's
    // the exact class of surprise cascading-delete bug found earlier in this
    // project (see SqliteMemberRepository history). SET NULL just unlinks the
    // account from the member record instead of destroying the account.
    public static final String CREATE_USERS =
        "CREATE TABLE IF NOT EXISTS users (" +
        "  user_id        TEXT PRIMARY KEY," +
        "  username       TEXT NOT NULL UNIQUE," +
        "  password_hash  TEXT NOT NULL," +
        "  role           TEXT NOT NULL," +   // Role enum name: ADMIN or MEMBER
        "  member_id      TEXT," +            // nullable — links a MEMBER-role account to their Member row
        "  FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE SET NULL" +
        ")";

    // No ENABLE_FOREIGN_KEYS constant anymore. SQLite needed
    // "PRAGMA foreign_keys = ON" because it ships with foreign key
    // enforcement OFF by default — an easy-to-miss footgun. Postgres has
    // no such switch: foreign keys (and every ON DELETE CASCADE / SET NULL
    // rule above) are enforced unconditionally, always. One less thing
    // to remember to turn on, and one less way to silently lose that
    // protection if a connection setup step gets skipped.
}