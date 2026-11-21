package dao;

import auth.Scope;
import db.BusinessException;
import db.DBConnection;
import db.Jdbc;
import db.SqlTrace;
import db.TableData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class EmployeeDao {

    private EmployeeDao() {}

    private static final String BASE_SELECT =
            "SELECT e.EmployeeID, e.FullName, e.Ssn, e.Sex, e.Email, e.Phone, e.HireDate, e.EmploymentStatus, "
          + "d.DepartmentName, p.PositionName, "
          + "CASE WHEN ft.EmployeeID IS NOT NULL THEN 'Full-Time' "
          + "     WHEN pt.EmployeeID IS NOT NULL THEN 'Part-Time' ELSE '(none)' END AS EmploymentType, "
          + "m.FullName AS Supervisor "
          + "FROM Employee e "
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID "
          + "JOIN `Position` p ON e.PositionID = p.PositionID "
          + "LEFT JOIN FullTime ft ON e.EmployeeID = ft.EmployeeID "
          + "LEFT JOIN PartTime pt ON e.EmployeeID = pt.EmployeeID "
          + "LEFT JOIN Employee m ON e.ManagerID = m.EmployeeID ";

    /** Search with optional filters. Any argument may be null/blank to skip that filter. */
    public static TableData search(String keyword, Integer departmentId, String status) throws SQLException {
        return search(keyword, departmentId, status, null);
    }

    /** Same, limited to the given employees (null = everyone), e.g. a manager's team. */
    public static TableData search(String keyword, Integer departmentId, String status, Set<Integer> scope)
            throws SQLException {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append("WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        Scope.appendIn(sql, "e.EmployeeID", scope, params);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + escapeLike(keyword.trim()) + "%";
            sql.append(" AND (e.FullName LIKE ? OR e.Email LIKE ? OR e.Ssn LIKE ?)");
            params.add(like); params.add(like); params.add(like);
        }
        if (departmentId != null) {
            sql.append(" AND e.DepartmentID = ?");
            params.add(departmentId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.EmploymentStatus = ?");
            params.add(status);
        }
        sql.append(" ORDER BY e.EmployeeID");
        return Jdbc.query(sql.toString(), params.toArray());
    }

    public static TableData one(int employeeId) throws SQLException {
        return Jdbc.query(BASE_SELECT + "WHERE e.EmployeeID = ?", employeeId);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static EmployeeForm load(int employeeId) throws SQLException, BusinessException {
        TableData t = Jdbc.query(
                "SELECT e.*, ft.MonthlyBaseSalary, ft.StandardMonthlyHours, pt.HourlyRate, pt.ExpectedWeeklyHours "
              + "FROM Employee e LEFT JOIN FullTime ft ON e.EmployeeID = ft.EmployeeID "
              + "LEFT JOIN PartTime pt ON e.EmployeeID = pt.EmployeeID WHERE e.EmployeeID = ?", employeeId);
        if (t.rowCount() == 0) throw new BusinessException("Employee #" + employeeId + " no longer exists.");
        EmployeeForm f = new EmployeeForm();
        f.employeeId = employeeId;
        f.fullName = str(t.value(0, "FullName"));
        f.ssn = str(t.value(0, "Ssn"));
        f.sex = str(t.value(0, "Sex"));
        f.phone = str(t.value(0, "Phone"));
        f.email = str(t.value(0, "Email"));
        Object hd = t.value(0, "HireDate");
        f.hireDate = hd instanceof Date d ? d.toLocalDate() : (hd instanceof LocalDate ld ? ld : null);
        f.employmentStatus = str(t.value(0, "EmploymentStatus"));
        f.departmentId = ((Number) t.value(0, "DepartmentID")).intValue();
        f.positionId = ((Number) t.value(0, "PositionID")).intValue();
        Object mgr = t.value(0, "ManagerID");
        f.managerId = mgr == null ? null : ((Number) mgr).intValue();
        if (t.value(0, "MonthlyBaseSalary") != null) {
            f.type = EmployeeForm.FULL_TIME;
            f.pay = (BigDecimal) t.value(0, "MonthlyBaseSalary");
            f.hours = ((Number) t.value(0, "StandardMonthlyHours")).intValue();
        } else if (t.value(0, "HourlyRate") != null) {
            f.type = EmployeeForm.PART_TIME;
            f.pay = (BigDecimal) t.value(0, "HourlyRate");
            f.hours = ((Number) t.value(0, "ExpectedWeeklyHours")).intValue();
        }
        return f;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    /**
     * Inserts Employee and its FullTime/PartTime row in one transaction. This is how the
     * total ISA specialization (every employee is FullTime or PartTime) is enforced.
     */
    public static int insert(EmployeeForm f) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            SqlTrace.note("START TRANSACTION;");
            try {
                int id = Jdbc.insertReturningKey(c,
                        "INSERT INTO Employee (FullName, Ssn, Sex, Phone, Email, HireDate, EmploymentStatus, "
                      + "DepartmentID, PositionID, ManagerID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        f.fullName, f.ssn, f.sex, f.phone, f.email, f.hireDate, f.employmentStatus,
                        f.departmentId, f.positionId, f.managerId);
                insertSubtype(c, id, f);
                c.commit();
                SqlTrace.note("COMMIT;");
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                SqlTrace.note("ROLLBACK;");
                throw e;
            }
        }
    }

    public static void update(EmployeeForm f) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            SqlTrace.note("START TRANSACTION;");
            try {
                int n = Jdbc.update(c,
                        "UPDATE Employee SET FullName = ?, Ssn = ?, Sex = ?, Phone = ?, Email = ?, HireDate = ?, "
                      + "EmploymentStatus = ?, DepartmentID = ?, PositionID = ?, ManagerID = ? WHERE EmployeeID = ?",
                        f.fullName, f.ssn, f.sex, f.phone, f.email, f.hireDate, f.employmentStatus,
                        f.departmentId, f.positionId, f.managerId, f.employeeId);
                if (n != 1) throw new BusinessException("Employee #" + f.employeeId + " no longer exists.");
                // Replace the subtype row: delete both, insert the chosen one (keeps ISA disjoint + total).
                Jdbc.update(c, "DELETE FROM FullTime WHERE EmployeeID = ?", f.employeeId);
                Jdbc.update(c, "DELETE FROM PartTime WHERE EmployeeID = ?", f.employeeId);
                insertSubtype(c, f.employeeId, f);
                c.commit();
                SqlTrace.note("COMMIT;");
            } catch (SQLException | BusinessException | RuntimeException e) {
                c.rollback();
                SqlTrace.note("ROLLBACK;");
                throw e;
            }
        }
    }

    private static void insertSubtype(Connection c, int id, EmployeeForm f) throws SQLException {
        if (EmployeeForm.FULL_TIME.equals(f.type)) {
            Jdbc.update(c, "INSERT INTO FullTime (EmployeeID, MonthlyBaseSalary, StandardMonthlyHours) VALUES (?, ?, ?)",
                    id, f.pay, f.hours);
        } else {
            Jdbc.update(c, "INSERT INTO PartTime (EmployeeID, HourlyRate, ExpectedWeeklyHours) VALUES (?, ?, ?)",
                    id, f.pay, f.hours);
        }
    }

    /** FullTime/PartTime/UserAccount/LeaveBalance rows cascade; ShiftAssignment/LeaveRequest RESTRICT (error 1451). */
    public static int delete(int employeeId) throws SQLException {
        return Jdbc.update("DELETE FROM Employee WHERE EmployeeID = ?", employeeId);
    }
}
