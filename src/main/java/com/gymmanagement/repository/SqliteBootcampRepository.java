package com.gymmanagement.repository;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.membership.BootcampType;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * SqliteBootcampRepository — JDBC-backed implementation of BootcampRepository.
 *
 * Responsibilities:
 *   - CRUD on bootcamp_classes table.
 *   - Manage bootcamp_enrolments junction table (enrol/remove participants).
 *   - Reconstruct BootcampClass objects with live Instructor and Member
 *     references resolved by ID lookup at load time.
 *
 * Cross-entity reference strategy (same as JsonBootcampRepository):
 *   bootcamp_classes.instructor_id stores the staffId TEXT.
 *   bootcamp_enrolments stores (class_id, member_id) pairs.
 *   On load, instructor is resolved via the instructorSupplier (TrainerService
 *   list) and participants via SqliteMemberRepository.findByIds().
 *
 * This class receives its cross-entity lookup dependencies via constructor
 * injection (Supplier lambdas and a reference to SqliteMemberRepository)
 * so it remains testable without a running service layer.
 *
 * Transaction strategy:
 *   save() wraps the class upsert + enrolment sync in one transaction.
 *   Enrolment sync: DELETE all existing rows for this class_id, then
 *   re-INSERT the current participants.  This is simpler and safer than
 *   diff-based upsert for a small dataset.
 *
 * @Repository marks this as the Spring-managed bean fulfilling the
 * BootcampRepository interface. Its constructor also needs a
 * SqliteMemberRepository (already @Repository-annotated) and a
 * Supplier<List<Instructor>> (provided by StaffConfig) — Spring resolves
 * both automatically since there's exactly one bean of each type.
 */
@Repository
public class SqliteBootcampRepository implements BootcampRepository {

    private final SqliteMemberRepository   memberRepo;
    private final Supplier<List<Instructor>> instructorSupplier;
    private final DataSource dataSource;

    public SqliteBootcampRepository(SqliteMemberRepository   memberRepo,
                                    Supplier<List<Instructor>> instructorSupplier,
                                    DataSource dataSource) {
        this.memberRepo         = memberRepo;
        this.instructorSupplier = instructorSupplier;
        this.dataSource         = dataSource;
    }

    private Connection conn() throws SQLException {
        return dataSource.getConnection();
    }

    // ══════════════════════════════════════════════════════
    //  BootcampRepository interface
    // ══════════════════════════════════════════════════════

    @Override
    public void save(BootcampClass bc) {
        try (Connection c = conn()) {
            try {
                c.setAutoCommit(false);
                upsertClass(c, bc);
                syncEnrolments(c, bc);
                c.commit();
            } catch (SQLException e) {
                rollback(c);
                throw new RuntimeException("Failed to save bootcamp class " + bc.getClassId()
                                           + ": " + e.getMessage(), e);
            } finally {
                restoreAutoCommit(c);
            }
        } catch (SQLException outer) {
            throw new RuntimeException("Failed to save bootcamp class " + bc.getClassId()
                                       + ": " + outer.getMessage(), outer);
        }
    }

    @Override
    public Optional<BootcampClass> findById(String classId) {
        String sql = "SELECT * FROM bootcamp_classes WHERE class_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildBootcampClass(rs, c));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById bootcamp failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<BootcampClass> findAll() {
        String sql = "SELECT * FROM bootcamp_classes ORDER BY class_id";
        List<BootcampClass> result = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(buildBootcampClass(rs, c));
        } catch (SQLException e) {
            throw new RuntimeException("findAll bootcamp failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public void delete(String classId) {
        // ON DELETE CASCADE removes bootcamp_enrolments rows automatically.
        String sql = "DELETE FROM bootcamp_classes WHERE class_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, classId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete bootcamp failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasAnyClasses() {
        String sql = "SELECT 1 FROM bootcamp_classes LIMIT 1";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("hasAnyClasses failed: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════
    //  Private — write helpers
    // ══════════════════════════════════════════════════════

    /** INSERT new class row, or UPDATE in place if class_id already exists.
     *  Uses ON CONFLICT DO UPDATE instead of INSERT OR REPLACE for the same
     *  reason as SqliteMemberRepository.upsertMember — REPLACE would cascade
     *  delete bootcamp_enrolments.class_id rows on every save. */
    private void upsertClass(Connection c, BootcampClass bc) throws SQLException {
        String sql =
            "INSERT INTO bootcamp_classes " +
            "(class_id, bootcamp_type, schedule, max_capacity, instructor_id, cancelled) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(class_id) DO UPDATE SET " +
            "  bootcamp_type = excluded.bootcamp_type, " +
            "  schedule = excluded.schedule, " +
            "  max_capacity = excluded.max_capacity, " +
            "  instructor_id = excluded.instructor_id, " +
            "  cancelled = excluded.cancelled";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bc.getClassId());
            ps.setString(2, bc.getType().name());
            ps.setString(3, bc.getSchedule());
            ps.setInt   (4, bc.getMaxCapacity());
            if (bc.getInstructor() != null) {
                ps.setString(5, bc.getInstructor().getStaffId());
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
            }
            ps.setInt(6, bc.isCancelled() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /**
     * Sync enrolments: delete all existing enrolment rows for this class,
     * then re-insert the current participants list.
     * Simple and correct for small participant counts (max 10 per class).
     */
    private void syncEnrolments(Connection c, BootcampClass bc) throws SQLException {
        // 1. Clear existing enrolments for this class
        String del = "DELETE FROM bootcamp_enrolments WHERE class_id = ?";
        try (PreparedStatement ps = c.prepareStatement(del)) {
            ps.setString(1, bc.getClassId());
            ps.executeUpdate();
        }

        // 2. Re-insert current participants
        if (bc.getParticipants().isEmpty()) return;

        String ins =
            "INSERT INTO bootcamp_enrolments (class_id, member_id, enrolled_at) " +
            "VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            String today = LocalDate.now().toString();
            for (Member m : bc.getParticipants()) {
                ps.setString(1, bc.getClassId());
                ps.setString(2, m.getMemberId());
                ps.setString(3, today);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ══════════════════════════════════════════════════════
    //  Private — read/reconstruct helpers
    // ══════════════════════════════════════════════════════

    /**
     * Reconstruct a BootcampClass from a result-set row.
     * Wires instructor and participants by ID lookup.
     */
    private BootcampClass buildBootcampClass(ResultSet rs, Connection c) throws SQLException {
        String       classId      = rs.getString("class_id");
        String       typeStr      = rs.getString("bootcamp_type");
        String       schedule     = rs.getString("schedule");
        int          maxCapacity  = rs.getInt("max_capacity");
        String       instructorId = rs.getString("instructor_id");  // may be NULL
        boolean      cancelled    = rs.getInt("cancelled") == 1;

        BootcampType type;
        try {
            type = BootcampType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Unknown BootcampType in DB: " + typeStr, e);
        }

        BootcampClass bc = new BootcampClass(classId, type, schedule, maxCapacity);

        // Wire instructor (nullable)
        if (instructorId != null && !instructorId.isBlank()) {
            instructorSupplier.get().stream()
                              .filter(i -> i.getStaffId().equals(instructorId))
                              .findFirst()
                              .ifPresent(bc::assignInstructor);
        }

        // Wire participants from enrolments table — done BEFORE restoring
        // cancelled status below, since enrolMember() now correctly
        // refuses to enrol into an already-cancelled class. A class that
        // was cancelled AFTER already having real enrolments must still
        // have those enrolments restored on every reload.
        List<String> participantIds = loadParticipantIds(classId, c);
        memberRepo.findByIds(participantIds).forEach(bc::enrolMember);

        if (cancelled) bc.cancel();

        return bc;
    }

    /** Return the ordered list of member IDs enrolled in a given class. */
    private List<String> loadParticipantIds(String classId, Connection c) throws SQLException {
        String sql =
            "SELECT member_id FROM bootcamp_enrolments " +
            "WHERE class_id = ? ORDER BY enrolled_at ASC";
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("member_id"));
            }
        }
        return ids;
    }

    // ══════════════════════════════════════════════════════
    //  Private — transaction helpers
    // ══════════════════════════════════════════════════════

    private void rollback(Connection c) {
        try { c.rollback(); }
        catch (SQLException ex) {
            System.err.println("[DB] Bootcamp rollback failed: " + ex.getMessage());
        }
    }

    private void restoreAutoCommit(Connection c) {
        try { c.setAutoCommit(true); }
        catch (SQLException ex) {
            System.err.println("[DB] Could not restore auto-commit: " + ex.getMessage());
        }
    }
}