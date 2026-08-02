package com.gymmanagement.repository;

import com.gymmanagement.db.DatabaseManager;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.Payment;
import com.gymmanagement.model.membership.Membership;
import com.gymmanagement.model.membership.PayAsYouGoMembership;
import com.gymmanagement.model.membership.StandardMembership;
import com.gymmanagement.model.membership.StudentSaverMembership;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SqliteMemberRepository — JDBC-backed implementation of MemberRepository.
 *
 * Implements the exact same interface as InMemoryMemberRepository.
 * The service layer calls this through the MemberRepository interface
 * and is completely unaware of the SQL underneath.
 *
 * Responsibilities:
 *   - CRUD operations on the members table.
 *   - UPSERT (INSERT OR REPLACE) on the memberships table whenever a
 *     member with a non-null membership is saved.
 *   - NULL membership on save → DELETE from memberships (removeMembership).
 *   - Load membership back when reading a member (eager load, one extra query).
 *   - Save and load payment history for each member.
 *
 * Membership polymorphism:
 *   The "membership_type" column stores the simple class name.
 *   The "extra_data" column stores one type-specific value as TEXT:
 *     StudentSaverMembership → studentIdNumber
 *     StandardMembership     → freezeCount (INTEGER stored as TEXT)
 *     PayAsYouGoMembership   → sessionsUsed (INTEGER stored as TEXT)
 *   This avoids three extra tables while keeping the schema readable.
 *
 * Transaction strategy:
 *   save() uses a manual transaction (setAutoCommit false) so that the
 *   member row, membership row, and payment rows are written atomically.
 *   If any step fails the whole save is rolled back.
 *
 * @Repository marks this as the Spring-managed bean fulfilling the
 * MemberRepository interface. Since this is the only implementation
 * registered, Spring injects it automatically wherever a MemberRepository
 * is asked for — like MemberService's constructor — no extra config needed.
 */
@Repository
public class SqliteMemberRepository implements MemberRepository {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    // ══════════════════════════════════════════════════════
    //  MemberRepository interface
    // ══════════════════════════════════════════════════════

    @Override
    public void save(Member member) {
        Connection c = conn();
        try {
            c.setAutoCommit(false);
            upsertMember(c, member);
            upsertMembership(c, member);
            upsertPayments(c, member);
            c.commit();
        } catch (SQLException e) {
            rollback(c);
            throw new RuntimeException("Failed to save member " + member.getMemberId()
                                       + ": " + e.getMessage(), e);
        } finally {
            restoreAutoCommit(c);
        }
    }

    @Override
    public Optional<Member> findById(String memberId) {
        String sql = "SELECT * FROM members WHERE member_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildMember(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        String sql = "SELECT * FROM members WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(buildMember(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByEmail failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT * FROM members ORDER BY name";
        List<Member> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(buildMember(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public void delete(String memberId) {
        // ON DELETE CASCADE in the schema removes memberships, payments,
        // and bootcamp_enrolments rows automatically.
        String sql = "DELETE FROM members WHERE member_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsById(String memberId) {
        String sql = "SELECT 1 FROM members WHERE member_id = ? LIMIT 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsById failed: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════
    //  Private — write helpers
    // ══════════════════════════════════════════════════════

    /** INSERT OR REPLACE the member row. */
    /** INSERT new member row, or UPDATE in place if member_id already exists.
     *  Uses ON CONFLICT DO UPDATE (a real SQL UPDATE) instead of INSERT OR
     *  REPLACE. REPLACE deletes the existing row first, which triggers
     *  ON DELETE CASCADE on bootcamp_enrolments.member_id and silently wipes
     *  that member's bootcamp enrolments every time they are saved. */
    private void upsertMember(Connection c, Member member) throws SQLException {
        String sql =
            "INSERT INTO members " +
            "(member_id, person_id, name, email, phone, registration_date) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(member_id) DO UPDATE SET " +
            "  person_id = excluded.person_id, " +
            "  name = excluded.name, " +
            "  email = excluded.email, " +
            "  phone = excluded.phone, " +
            "  registration_date = excluded.registration_date";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, member.getMemberId());
            ps.setString(2, member.getPersonId());
            ps.setString(3, member.getName());
            ps.setString(4, member.getEmail());
            ps.setString(5, member.getPhone());
            ps.setString(6, member.getRegistrationDate().toString());
            ps.executeUpdate();
        }
    }

    /**
     * Save or remove the membership row for this member.
     * If membership is null  → DELETE the row (membership was cancelled).
     * If membership is set   → INSERT OR REPLACE with full fields.
     */
    private void upsertMembership(Connection c, Member member) throws SQLException {
        if (member.getMembership() == null) {
            // Membership removed — delete any existing row
            String del = "DELETE FROM memberships WHERE member_id = ?";
            try (PreparedStatement ps = c.prepareStatement(del)) {
                ps.setString(1, member.getMemberId());
                ps.executeUpdate();
            }
            return;
        }

        Membership m = member.getMembership();
        String     type      = m.getClass().getSimpleName();
        String     extraData = buildExtraData(m);

        String sql =
            "INSERT OR REPLACE INTO memberships " +
            "(membership_id, member_id, membership_type, start_date, end_date, " +
            " monthly_fee, frozen, extra_data) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getMembershipId());
            ps.setString(2, member.getMemberId());
            ps.setString(3, type);
            ps.setString(4, m.getStartDate().toString());
            ps.setString(5, m.getEndDate().toString());
            ps.setDouble(6, m.getMonthlyFee());
            ps.setInt   (7, m.isFrozen() ? 1 : 0);
            ps.setString(8, extraData);
            ps.executeUpdate();
        }
    }

    /**
     * Persist payments that are not yet in the database.
     * Uses INSERT OR IGNORE so existing rows are not overwritten.
     */
    private void upsertPayments(Connection c, Member member) throws SQLException {
        if (member.getPaymentHistory().isEmpty()) return;

        String sql =
            "INSERT OR IGNORE INTO payments " +
            "(payment_id, member_id, amount, description, payment_date, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Payment p : member.getPaymentHistory()) {
                ps.setString(1, p.getPaymentId());
                ps.setString(2, member.getMemberId());
                ps.setDouble(3, p.getAmount());
                ps.setString(4, p.getDescription());
                ps.setString(5, p.getPaymentDate().toString());
                ps.setString(6, p.getStatus());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ══════════════════════════════════════════════════════
    //  Private — read/reconstruct helpers
    // ══════════════════════════════════════════════════════

    /**
     * Reconstruct a Member from a ResultSet row.
     * Eagerly loads the membership and payment history via additional queries.
     */
    private Member buildMember(ResultSet rs) throws SQLException {
        String    memberId         = rs.getString("member_id");
        String    personId         = rs.getString("person_id");
        String    name             = rs.getString("name");
        String    email            = rs.getString("email");
        String    phone            = rs.getString("phone");
        LocalDate registrationDate = LocalDate.parse(rs.getString("registration_date"));

        Member member = new Member(personId, memberId, name, email, phone);
        // Override the auto-set registration date with the stored one
        setRegistrationDate(member, registrationDate);

        // Eagerly load membership
        Membership membership = loadMembership(memberId);
        if (membership != null) member.setMembership(membership);

        // Eagerly load payment history
        loadPaymentsForMember(memberId, member).forEach(member::addPayment);

        return member;
    }

    /** Load the membership row for a given member (returns null if none). */
    private Membership loadMembership(String memberId) throws SQLException {
        String sql = "SELECT * FROM memberships WHERE member_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String    membershipId = rs.getString("membership_id");
                String    type         = rs.getString("membership_type");
                LocalDate startDate    = LocalDate.parse(rs.getString("start_date"));
                LocalDate endDate      = LocalDate.parse(rs.getString("end_date"));
                double    monthlyFee   = rs.getDouble("monthly_fee");
                boolean   frozen       = rs.getInt("frozen") == 1;
                String    extraData    = rs.getString("extra_data");

                return reconstructMembership(membershipId, type, startDate,
                                             endDate, monthlyFee, frozen, extraData);
            }
        }
    }

    /**
     * Reconstruct the correct Membership subclass from stored fields.
     * The "type" discriminator drives which constructor to call.
     * Stored dates override the constructors' LocalDate.now() defaults via
     * reflection-free field setters added to Membership for persistence use.
     */
    private Membership reconstructMembership(String    membershipId,
                                              String    type,
                                              LocalDate startDate,
                                              LocalDate endDate,
                                              double    monthlyFee,
                                              boolean   frozen,
                                              String    extraData) {
        Membership m;
        switch (type) {
            case "StudentSaverMembership": {
                // extraData = studentIdNumber
                String studentId = extraData != null ? extraData : "UNKNOWN";
                m = new StudentSaverMembership(membershipId, studentId);
                break;
            }
            case "StandardMembership": {
                // Standard constructor sets duration from months — we override
                // dates after construction to restore exact stored values.
                // extraData = freezeCount (we restore frozen state via freeze())
                m = new StandardMembership(membershipId, 12); // placeholder months
                int freezeCount = extraData != null ? parseInt(extraData) : 0;
                for (int i = 0; i < freezeCount; i++) m.freeze();
                if (!frozen) m.unfreeze();   // restore actual frozen state
                break;
            }
            case "PayAsYouGoMembership": {
                // extraData = sessionsUsed
                PayAsYouGoMembership payg = new PayAsYouGoMembership(membershipId);
                int sessions = extraData != null ? parseInt(extraData) : 0;
                for (int i = 0; i < sessions; i++) payg.addSession();
                m = payg;
                break;
            }
            default:
                throw new RuntimeException("Unknown membership type in DB: " + type);
        }

        // Override dates with exact stored values
        m.setEndDate(endDate);

        // Restore frozen state for types other than Standard
        if (frozen && !(m instanceof StandardMembership)) m.freeze();

        return m;
    }

    /**
     * Load all payment history rows for a member, ordered by date.
     * Requires the already-reconstructed Member object because Payment's
     * constructor requires a Member reference. Called from buildMember()
     * after the Member is fully built.
     */
    private List<Payment> loadPaymentsForMember(String memberId, Member member)
            throws SQLException {
        String sql =
            "SELECT * FROM payments WHERE member_id = ? ORDER BY payment_date ASC";
        List<Payment> payments = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String    paymentId   = rs.getString("payment_id");
                    double    amount      = rs.getDouble("amount");
                    String    description = rs.getString("description");
                    String    status      = rs.getString("status");

                    Payment p = new Payment(paymentId, amount, description, member);
                    if (Payment.STATUS_COMPLETED.equals(status)) p.markCompleted();
                    else if (Payment.STATUS_FAILED.equals(status)) p.markFailed();
                    payments.add(p);
                }
            }
        }
        return payments;
    }

    // ══════════════════════════════════════════════════════
    //  Private — mapping helpers
    // ══════════════════════════════════════════════════════

    /**
     * Returns the type-specific extra_data string for each Membership subclass.
     *   StudentSaverMembership → studentIdNumber
     *   StandardMembership     → freezeCount
     *   PayAsYouGoMembership   → sessionsUsed
     */
    private String buildExtraData(Membership m) {
        if (m instanceof StudentSaverMembership) {
            return ((StudentSaverMembership) m).getStudentIdNumber();
        } else if (m instanceof StandardMembership) {
            return String.valueOf(((StandardMembership) m).getFreezeCount());
        } else if (m instanceof PayAsYouGoMembership) {
            return String.valueOf(((PayAsYouGoMembership) m).getSessionsUsed());
        }
        return null;
    }

    /**
     * Reflection-free way to restore the registration date.
     * Member stores registrationDate as LocalDate.now() in its constructor.
     * We need to reset it to the value stored in the DB without adding a
     * public setter (which would allow arbitrary date manipulation).
     * Solution: add a package-private setter on Member used only by this repo.
     */
    private void setRegistrationDate(Member member, LocalDate date) {
        member.setRegistrationDateFromDb(date);
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // ══════════════════════════════════════════════════════
    //  Private — transaction helpers
    // ══════════════════════════════════════════════════════

    private void rollback(Connection c) {
        try { c.rollback(); }
        catch (SQLException ex) {
            System.err.println("[DB] Rollback failed: " + ex.getMessage());
        }
    }

    private void restoreAutoCommit(Connection c) {
        try { c.setAutoCommit(true); }
        catch (SQLException ex) {
            System.err.println("[DB] Could not restore auto-commit: " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    //  Package-private helper called by SqliteBootcampRepository
    // ══════════════════════════════════════════════════════

    /**
     * Load members by ID list.  Used by SqliteBootcampRepository to
     * reconstruct participant lists without a full findAll().
     */
    List<Member> findByIds(List<String> memberIds) {
        List<Member> result = new ArrayList<>();
        for (String id : memberIds) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }
}