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

    // ── documents (RAG feature, Milestone 1) ──────────────
    // Raw storage only — the whole document's text content in one row.
    // No chunking, no embeddings yet; those are separate, later tables
    // (document_chunks) built on top of this once storage itself is
    // proven working. Deliberately not a file upload today — content is
    // plain text pasted into the request body. Real file/PDF upload is a
    // separate, later enhancement, not needed to prove this pipeline.
    public static final String CREATE_DOCUMENTS =
        "CREATE TABLE IF NOT EXISTS documents (" +
        "  document_id  TEXT PRIMARY KEY," +
        "  filename     TEXT NOT NULL," +
        "  content      TEXT NOT NULL," +
        "  uploaded_at  TEXT NOT NULL" +
        ")";

    // ── document_chunks (RAG feature, Milestone 2, extended in 4) ──
    // Split pieces of a document's content, generated automatically on
    // upload. Uses ON DELETE CASCADE — deliberately the opposite choice
    // from users.member_id earlier in this schema. That distinction was
    // never "always avoid CASCADE" — it was "avoid it when the child row
    // has independent value" (a login account matters on its own, even
    // if the linked member is deleted). A chunk has no such independent
    // value: it's purely derived from its parent document's content, and
    // has no reason to exist once that document is gone.
    //
    // The embedding column (Milestone 4) is nullable on purpose: chunks
    // created before this migration have no embedding yet, and retrieval
    // must correctly skip them rather than error.
    //
    // VECTOR(3072), not 768: gemini-embedding-001's batchEmbedContents
    // endpoint did not honour the requested outputDimensionality=768
    // truncation — it returned full 3072-dimensional vectors regardless,
    // confirmed by a real "expected 768 dimensions, not 3072" Postgres
    // error during testing. Rather than keep fighting an API quirk,
    // this column now matches what Gemini actually sends. Less compact
    // than the originally-planned 768, but correct — a working, larger
    // vector beats a broken, smaller one.
    public static final String CREATE_DOCUMENT_CHUNKS =
        "CREATE TABLE IF NOT EXISTS document_chunks (" +
        "  chunk_id     TEXT PRIMARY KEY," +
        "  document_id  TEXT NOT NULL," +
        "  chunk_index  INTEGER NOT NULL," +
        "  content      TEXT NOT NULL," +
        "  embedding    VECTOR(3072)," +
        "  FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE" +
        ")";

    // ── pgvector extension (RAG feature, Milestone 4) ──────
    // Switches on the `vector` type Postgres needs to understand the
    // embedding column above. Neon pre-installs the pgvector binary;
    // this just enables it for this specific database. IF NOT EXISTS
    // makes it safe to run on every app startup, same as every
    // CREATE TABLE statement in this file.
    public static final String CREATE_VECTOR_EXTENSION =
        "CREATE EXTENSION IF NOT EXISTS vector";

    // document_chunks may already exist from before this migration —
    // the embedding column in CREATE_DOCUMENT_CHUNKS above only takes
    // effect when that CREATE TABLE actually creates a new table. This
    // ALTER is what adds the column to a table that already exists.
    // IF NOT EXISTS makes it safe to run again on every future startup.
    public static final String ADD_EMBEDDING_COLUMN =
        "ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS embedding VECTOR(3072)";

    // ── fitness_goal (Bootcamp Recommendations feature) ────
    // Free text, nullable — existing members won't have one set until
    // they explicitly provide it via PATCH /api/members/{id}/goal.
    // Same ALTER-with-IF-NOT-EXISTS pattern as the embedding column
    // above: the members table already exists in every environment
    // this app runs in, so a plain CREATE TABLE can't add this column
    // — only an explicit ALTER can.
    public static final String ADD_FITNESS_GOAL_COLUMN =
        "ALTER TABLE members ADD COLUMN IF NOT EXISTS fitness_goal TEXT";

    /**
     * Soft-deactivation for members — explicitly NOT a hard delete.
     * Existing DELETE /api/members/{id} remains available separately,
     * but the Admin member-management UI uses this instead, so
     * historical membership/enrolment data referencing a deactivated
     * member's ID stays intact. Defaults every existing row to active
     * (1) — Postgres backfills NOT NULL DEFAULT on ADD COLUMN as a
     * single fast metadata operation, no manual UPDATE needed.
     */
    public static final String ADD_ACTIVE_COLUMN =
        "ALTER TABLE members ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1";

    /**
     * Soft-cancellation for bootcamp classes — same reasoning as
     * ADD_ACTIVE_COLUMN above: a hard DELETE would cascade-remove
     * bootcamp_enrolments rows (or be blocked by the FK entirely),
     * either way losing real enrolment history. This preserves it.
     */
    public static final String ADD_CANCELLED_COLUMN =
        "ALTER TABLE bootcamp_classes ADD COLUMN IF NOT EXISTS cancelled INTEGER NOT NULL DEFAULT 0";

    /**
     * Real membership billing — the due date genuinely advances on
     * payment (see Membership.advancePaymentDueDate()), rather than
     * requiring a separate lookup against payment history to know if
     * the current period was paid. Nullable: PayAsYouGoMembership has
     * no recurring due date at all (pay-per-session), so its rows
     * legitimately have NULL here, not a fabricated date.
     */
    public static final String ADD_NEXT_PAYMENT_DUE_DATE_COLUMN =
        "ALTER TABLE memberships ADD COLUMN IF NOT EXISTS next_payment_due_date DATE";

    /**
     * Admin/Member RAG separation. Every existing document defaults to
     * 'MEMBER' (preserving current behavior for anything already
     * uploaded) — nothing becomes invisible to Members that was visible
     * before this column existed. New Admin-only documents are
     * uploaded with audience='ADMIN' explicitly.
     */
    public static final String ADD_DOCUMENT_AUDIENCE_COLUMN =
        "ALTER TABLE documents ADD COLUMN IF NOT EXISTS audience TEXT NOT NULL DEFAULT 'MEMBER'";

    // ── staff ──────────────────────────────────────────────
    // One table for all three staff categories (FullTimeStaff,
    // PartTimeStaff, Instructor), using the same "discriminator column +
    // nullable type-specific columns" pattern already proven for
    // memberships above — avoids three near-identical tables for what's
    // a small dataset in practice.
    //
    // staff_type: 'FULL_TIME' | 'PART_TIME' | 'INSTRUCTOR'
    // salary / work_schedule: used by FULL_TIME and INSTRUCTOR rows
    //   (Instructor extends FullTimeStaff in the domain model — same
    //   two fields apply), null for PART_TIME rows.
    // specialisation: INSTRUCTOR only, null otherwise.
    // hourly_rate / hours_per_week / shift_pattern: PART_TIME only,
    //   null otherwise.
    //
    // This table was missing entirely until now — staff/instructor data
    // was previously supplied only as hardcoded Spring @Bean values in
    // StaffConfig (see that class's own comments), meaning nothing
    // created via the API ever survived a restart. This table, plus
    // SqliteStaffRepository and StaffSeeder, is the actual fix.
    public static final String CREATE_STAFF =
        "CREATE TABLE IF NOT EXISTS staff (" +
        "  staff_id        TEXT PRIMARY KEY," +
        "  person_id       TEXT NOT NULL," +
        "  staff_type      TEXT NOT NULL," +
        "  name            TEXT NOT NULL," +
        "  email           TEXT NOT NULL," +
        "  phone           TEXT," +
        "  role            TEXT NOT NULL," +
        "  available       INTEGER NOT NULL DEFAULT 1," +
        "  salary          REAL," +
        "  work_schedule   TEXT," +
        "  specialisation  TEXT," +
        "  hourly_rate     REAL," +
        "  hours_per_week  INTEGER," +
        "  shift_pattern   TEXT" +
        ")";
}