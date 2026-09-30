package com.gymmanagement.repository;

import com.gymmanagement.model.FullTimeStaff;
import com.gymmanagement.model.Instructor;
import com.gymmanagement.model.PartTimeStaff;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * SqliteStaffRepository — JDBC-backed implementation of StaffRepository.
 *
 * Same "single table, discriminator column, nullable type-specific
 * columns" strategy already used for memberships (see
 * SqliteMemberRepository), and the same ON CONFLICT DO UPDATE upsert
 * pattern throughout — deliberately not INSERT OR REPLACE, for the exact
 * reason documented on SqliteMemberRepository.upsertMember(): REPLACE
 * deletes-then-reinserts, which would trigger any ON DELETE CASCADE FKs
 * pointing at staff_id in the future. None exist yet, but the same
 * discipline applies regardless.
 *
 * Deliberately does NOT reconstruct Instructor.assignedClasses on load
 * (every findAllInstructors() call returns instructors with an empty
 * assigned-classes list). This is intentional, not an oversight:
 * TrainerService calls findAllInstructors() exactly once, at its own
 * construction (application startup), then keeps that list in memory
 * for the process's lifetime, mutating assignedClasses directly via
 * Instructor.assignToClass()/removeFromClass() exactly as it always
 * has. Reconstructing assignedClasses here by querying bootcamp_classes
 * would create a genuine mutual-recursion risk with
 * SqliteBootcampRepository, which already resolves instructor_id via a
 * lazy Supplier<List<Instructor>> pointed at TrainerService's own
 * in-memory list — this repository loading instructors that themselves
 * trigger loading all bootcamp classes, which trigger re-resolving
 * instructors again, and so on. Keeping load-time assignedClasses empty
 * and letting TrainerService's existing in-memory mutation be the only
 * source of truth for that specific relationship avoids the cycle
 * entirely, while still making the staff row itself durable.
 */
@Repository
public class SqliteStaffRepository implements StaffRepository {

    private final DataSource dataSource;

    public SqliteStaffRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection conn() throws SQLException {
        return dataSource.getConnection();
    }

    // ══════════════════════════════════════════════════════
    //  Save
    // ══════════════════════════════════════════════════════

    @Override
    public void saveFullTimeStaff(FullTimeStaff staff) {
        String sql = baseUpsertSql();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindBaseFields(ps, staff.getStaffId(), staff.getPersonId(), "FULL_TIME",
                staff.getName(), staff.getEmail(), staff.getPhone(),
                staff.getRole(), staff.isAvailable());
            ps.setDouble(9, staff.getSalary());
            ps.setString(10, staff.getWorkSchedule());
            ps.setNull(11, Types.VARCHAR);   // specialisation — not applicable
            ps.setNull(12, Types.DOUBLE);    // hourly_rate
            ps.setNull(13, Types.INTEGER);   // hours_per_week
            ps.setNull(14, Types.VARCHAR);   // shift_pattern
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("saveFullTimeStaff failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void savePartTimeStaff(PartTimeStaff staff) {
        String sql = baseUpsertSql();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindBaseFields(ps, staff.getStaffId(), staff.getPersonId(), "PART_TIME",
                staff.getName(), staff.getEmail(), staff.getPhone(),
                staff.getRole(), staff.isAvailable());
            ps.setNull(9, Types.DOUBLE);     // salary
            ps.setNull(10, Types.VARCHAR);   // work_schedule
            ps.setNull(11, Types.VARCHAR);   // specialisation
            ps.setDouble(12, staff.getHourlyRate());
            ps.setInt(13, staff.getHoursPerWeek());
            ps.setString(14, staff.getShiftPattern());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("savePartTimeStaff failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveInstructor(Instructor instructor) {
        String sql = baseUpsertSql();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindBaseFields(ps, instructor.getStaffId(), instructor.getPersonId(), "INSTRUCTOR",
                instructor.getName(), instructor.getEmail(), instructor.getPhone(),
                instructor.getRole(), instructor.isAvailable());
            ps.setDouble(9, instructor.getSalary());
            ps.setString(10, instructor.getWorkSchedule());
            ps.setString(11, instructor.getSpecialisation());
            ps.setNull(12, Types.DOUBLE);    // hourly_rate
            ps.setNull(13, Types.INTEGER);   // hours_per_week
            ps.setNull(14, Types.VARCHAR);   // shift_pattern
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("saveInstructor failed: " + e.getMessage(), e);
        }
    }

    private String baseUpsertSql() {
        return
            "INSERT INTO staff " +
            "(staff_id, person_id, staff_type, name, email, phone, role, available, " +
            " salary, work_schedule, specialisation, hourly_rate, hours_per_week, shift_pattern) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (staff_id) DO UPDATE SET " +
            "  person_id = EXCLUDED.person_id, " +
            "  staff_type = EXCLUDED.staff_type, " +
            "  name = EXCLUDED.name, " +
            "  email = EXCLUDED.email, " +
            "  phone = EXCLUDED.phone, " +
            "  role = EXCLUDED.role, " +
            "  available = EXCLUDED.available, " +
            "  salary = EXCLUDED.salary, " +
            "  work_schedule = EXCLUDED.work_schedule, " +
            "  specialisation = EXCLUDED.specialisation, " +
            "  hourly_rate = EXCLUDED.hourly_rate, " +
            "  hours_per_week = EXCLUDED.hours_per_week, " +
            "  shift_pattern = EXCLUDED.shift_pattern";
    }

    private void bindBaseFields(PreparedStatement ps, String staffId, String personId,
                                 String staffType, String name, String email, String phone,
                                 String role, boolean available) throws SQLException {
        ps.setString(1, staffId);
        ps.setString(2, personId);
        ps.setString(3, staffType);
        ps.setString(4, name);
        ps.setString(5, email);
        ps.setString(6, phone);
        ps.setString(7, role);
        ps.setInt(8, available ? 1 : 0);
    }

    // ══════════════════════════════════════════════════════
    //  Find
    // ══════════════════════════════════════════════════════

    @Override
    public List<FullTimeStaff> findAllFullTimeStaff() {
        List<FullTimeStaff> result = new ArrayList<>();
        String sql = "SELECT * FROM staff WHERE staff_type = 'FULL_TIME' ORDER BY name";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new FullTimeStaff(
                    rs.getString("person_id"), rs.getString("staff_id"), rs.getString("name"),
                    rs.getString("email"), rs.getString("phone"), rs.getString("role"),
                    rs.getDouble("salary"), rs.getString("work_schedule")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllFullTimeStaff failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<PartTimeStaff> findAllPartTimeStaff() {
        List<PartTimeStaff> result = new ArrayList<>();
        String sql = "SELECT * FROM staff WHERE staff_type = 'PART_TIME' ORDER BY name";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new PartTimeStaff(
                    rs.getString("person_id"), rs.getString("staff_id"), rs.getString("name"),
                    rs.getString("email"), rs.getString("phone"), rs.getString("role"),
                    rs.getDouble("hourly_rate"), rs.getInt("hours_per_week"), rs.getString("shift_pattern")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllPartTimeStaff failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<Instructor> findAllInstructors() {
        List<Instructor> result = new ArrayList<>();
        String sql = "SELECT * FROM staff WHERE staff_type = 'INSTRUCTOR' ORDER BY name";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // assignedClasses intentionally starts empty — see class-level note above.
                result.add(new Instructor(
                    rs.getString("person_id"), rs.getString("staff_id"), rs.getString("name"),
                    rs.getString("email"), rs.getString("phone"), rs.getDouble("salary"),
                    rs.getString("work_schedule"), rs.getString("specialisation")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllInstructors failed: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public boolean existsByStaffId(String staffId) {
        String sql = "SELECT 1 FROM staff WHERE staff_id = ? LIMIT 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsByStaffId failed: " + e.getMessage(), e);
        }
    }
}