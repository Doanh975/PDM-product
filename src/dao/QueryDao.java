package dao;

import auth.Scope;
import db.DBConnection;
import db.Jdbc;
import db.SqlTrace;
import db.TableData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The Topic 15 queries (Q1-Q5) and report queries from 02_queries_and_updates.sql,
 * with the hard-coded dates/names replaced by '?' parameters.
 */
public final class QueryDao {

    private QueryDao() {}

    // Q1 - shift roster of one department for a date range
    public static final String Q1 =
            "SELECT sa.WorkDate, d.DepartmentName, e.EmployeeID, e.FullName,\n"
          + "       p.PositionName, s.ShiftName, s.StartTime, s.EndTime, sa.AssignmentStatus\n"
          + "FROM ShiftAssignment sa\n"
          + "JOIN Employee e ON sa.EmployeeID = e.EmployeeID\n"
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "JOIN `Position` p ON e.PositionID = p.PositionID\n"
          + "JOIN Shift s ON sa.ShiftID = s.ShiftID\n"
          + "WHERE d.DepartmentID = ?\n"
          + "  AND sa.WorkDate BETWEEN ? AND ?\n"
          + "ORDER BY sa.WorkDate, s.StartTime, e.EmployeeID";

    public static TableData q1(int departmentId, LocalDate from, LocalDate to) throws SQLException {
        return Jdbc.query(Q1, departmentId, from, to);
    }

    // Q2 - employees late more than N times in a period
    public static final String Q2 =
            "SELECT e.EmployeeID, e.FullName, d.DepartmentName, COUNT(a.AttendanceID) AS LateCount\n"
          + "FROM Attendance a\n"
          + "JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID\n"
          + "JOIN Employee e ON sa.EmployeeID = e.EmployeeID\n"
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "WHERE a.AttendanceStatus = 'Late'\n"
          + "  AND sa.WorkDate BETWEEN ? AND ?\n"
          + "GROUP BY e.EmployeeID, e.FullName, d.DepartmentName\n"
          + "HAVING COUNT(a.AttendanceID) > ?\n"
          + "ORDER BY LateCount DESC";

    public static TableData q2(LocalDate from, LocalDate to, int moreThan) throws SQLException {
        return Jdbc.query(Q2, from, to, moreThan);
    }

    // Q3 - total overtime per department in a period
    public static final String Q3 =
            "SELECT d.DepartmentID, d.DepartmentName,\n"
          + "       COUNT(a.AttendanceID) AS RecordedShifts,\n"
          + "       COALESCE(SUM(a.OvertimeHours), 0.00) AS TotalOvertimeHours\n"
          + "FROM Department d\n"
          + "JOIN Employee e ON d.DepartmentID = e.DepartmentID\n"
          + "JOIN ShiftAssignment sa ON e.EmployeeID = sa.EmployeeID\n"
          + "JOIN Attendance a ON sa.ShiftAssignmentID = a.ShiftAssignmentID\n"
          + "WHERE sa.WorkDate BETWEEN ? AND ?\n"
          + "GROUP BY d.DepartmentID, d.DepartmentName\n"
          + "HAVING SUM(a.OvertimeHours) > 0\n"
          + "ORDER BY TotalOvertimeHours DESC";

    public static TableData q3(LocalDate from, LocalDate to) throws SQLException {
        return Jdbc.query(Q3, from, to);
    }

    // Q4 - pending leave requests waiting more than N working days as of a date
    // (Saturdays, Sundays and PublicHoliday dates are not working days, as in 02_queries_and_updates.sql).
    // The team's script counts with a fixed 0..20 day sequence; a calendar CTE starting at the oldest
    // pending request gives the same result without that limit.
    public static final String Q4 =
            "WITH RECURSIVE CalendarDates AS (\n"
          + "    SELECT CAST(COALESCE((SELECT MIN(DATE(SubmittedAt)) FROM LeaveRequest\n"
          + "                          WHERE RequestStatus = 'Pending'), ?) AS DATE) AS CalDate\n"
          + "    UNION ALL\n"
          + "    SELECT DATE_ADD(CalDate, INTERVAL 1 DAY) FROM CalendarDates WHERE CalDate < ?\n"
          + "),\n"
          + "WorkingDays AS (\n"
          + "    SELECT c.CalDate FROM CalendarDates c\n"
          + "    WHERE DAYOFWEEK(c.CalDate) NOT IN (1, 7)\n"
          + "      AND c.CalDate NOT IN (SELECT HolidayDate FROM PublicHoliday)\n"
          + ")\n"
          + "SELECT * FROM (\n"
          + "    SELECT lr.LeaveRequestID, e.EmployeeID, e.FullName, d.DepartmentName,\n"
          + "           lr.LeaveType, lr.SubmittedAt, lr.StartDate, lr.EndDate,\n"
          + "           DATEDIFF(?, DATE(lr.SubmittedAt)) AS CalendarDaysWaiting,\n"
          + "           (SELECT COUNT(*) FROM WorkingDays wd\n"
          + "             WHERE wd.CalDate > DATE(lr.SubmittedAt) AND wd.CalDate <= ?) AS WorkingDaysWaiting\n"
          + "    FROM LeaveRequest lr\n"
          + "    JOIN Employee e ON lr.EmployeeID = e.EmployeeID\n"
          + "    JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "    WHERE lr.RequestStatus = 'Pending'\n"
          + ") t\n"
          + "WHERE t.WorkingDaysWaiting > ?\n"
          + "ORDER BY t.SubmittedAt ASC";

    public static TableData q4(LocalDate asOf, int moreThanDays) throws SQLException {
        return Jdbc.query(Q4, asOf, asOf, asOf, asOf, moreThanDays);
    }

    // Q5 - relational division: employees Present on every working day of the period.
    // The EXISTS guard stops a period without any working day from returning every employee.
    public static final String Q5 =
            "SELECT e.EmployeeID, e.FullName, d.DepartmentName\n"
          + "FROM Employee e\n"
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "WHERE EXISTS (\n"
          + "    SELECT 1 FROM ShiftAssignment sa_any\n"
          + "    WHERE sa_any.WorkDate BETWEEN ? AND ?\n"
          + "      AND DAYOFWEEK(sa_any.WorkDate) NOT IN (1, 7)\n"
          + "      AND sa_any.WorkDate NOT IN (SELECT HolidayDate FROM PublicHoliday)\n"
          + ")\n"
          + "AND NOT EXISTS (\n"
          + "    SELECT DISTINCT sa_cal.WorkDate\n"
          + "    FROM ShiftAssignment sa_cal\n"
          + "    WHERE sa_cal.WorkDate BETWEEN ? AND ?\n"
          + "      AND DAYOFWEEK(sa_cal.WorkDate) NOT IN (1, 7)\n"
          + "      AND sa_cal.WorkDate NOT IN (SELECT HolidayDate FROM PublicHoliday)\n"
          + "      AND NOT EXISTS (\n"
          + "          SELECT 1\n"
          + "          FROM ShiftAssignment sa_emp\n"
          + "          JOIN Attendance a ON sa_emp.ShiftAssignmentID = a.ShiftAssignmentID\n"
          + "          WHERE sa_emp.EmployeeID = e.EmployeeID\n"
          + "            AND sa_emp.WorkDate = sa_cal.WorkDate\n"
          + "            AND a.AttendanceStatus = 'Present'\n"
          + "      )\n"
          + ")\n"
          + "ORDER BY e.EmployeeID";

    public static TableData q5(LocalDate from, LocalDate to) throws SQLException {
        return Jdbc.query(Q5, from, to, from, to);
    }

    public static final String EMPLOYEE_SUMMARY =
            "SELECT * FROM vw_EmployeeAttendanceSummary\n"
          + "WHERE TotalShiftsRecorded > 0\n"
          + "ORDER BY TotalOvertimeHours DESC, LateDays DESC";

    /** Body of vw_EmployeeAttendanceSummary, used when the view has not been created (02 script not run). */
    public static final String EMPLOYEE_SUMMARY_INLINE =
            "SELECT * FROM (\n"
          + "    SELECT e.EmployeeID, e.FullName, d.DepartmentName,\n"
          + "           COUNT(a.AttendanceID) AS TotalShiftsRecorded,\n"
          + "           SUM(CASE WHEN a.AttendanceStatus = 'Present' THEN 1 ELSE 0 END) AS PresentDays,\n"
          + "           SUM(CASE WHEN a.AttendanceStatus = 'Late' THEN 1 ELSE 0 END) AS LateDays,\n"
          + "           SUM(CASE WHEN a.AttendanceStatus = 'Absent' THEN 1 ELSE 0 END) AS AbsentDays,\n"
          + "           COALESCE(SUM(a.OvertimeHours), 0.00) AS TotalOvertimeHours\n"
          + "    FROM Employee e\n"
          + "    JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "    LEFT JOIN ShiftAssignment sa ON e.EmployeeID = sa.EmployeeID\n"
          + "    LEFT JOIN Attendance a ON sa.ShiftAssignmentID = a.ShiftAssignmentID\n"
          + "    GROUP BY e.EmployeeID, e.FullName, d.DepartmentName\n"
          + ") v\n"
          + "WHERE TotalShiftsRecorded > 0\n"
          + "ORDER BY TotalOvertimeHours DESC, LateDays DESC";

    public static TableData employeeSummary() throws SQLException {
        try {
            return Jdbc.query(EMPLOYEE_SUMMARY);
        } catch (SQLException e) {
            if (e.getErrorCode() != 1146) throw e;
            SqlTrace.note("-- vw_EmployeeAttendanceSummary not found, running its definition directly:");
            return Jdbc.query(EMPLOYEE_SUMMARY_INLINE);
        }
    }

    public static final String UNDERSTAFFED =
            "SELECT sr.WorkDate, s.ShiftName, sr.RequiredEmployees,\n"
          + "       COUNT(sa.ShiftAssignmentID) AS AssignedCount,\n"
          + "       (sr.RequiredEmployees - COUNT(sa.ShiftAssignmentID)) AS StaffingShortage\n"
          + "FROM ShiftRequirement sr\n"
          + "JOIN Shift s ON sr.ShiftID = s.ShiftID\n"
          + "LEFT JOIN ShiftAssignment sa\n"
          + "       ON sr.ShiftID = sa.ShiftID\n"
          + "      AND sr.WorkDate = sa.WorkDate\n"
          + "      AND sa.AssignmentStatus <> 'Cancelled'\n"
          + "GROUP BY sr.WorkDate, s.ShiftName, sr.RequiredEmployees\n"
          + "HAVING COUNT(sa.ShiftAssignmentID) < sr.RequiredEmployees\n"
          + "ORDER BY sr.WorkDate, StaffingShortage DESC";

    public static TableData understaffed() throws SQLException {
        return Jdbc.query(UNDERSTAFFED);
    }

    public static final String COMPENSATION =
            "SELECT e.EmployeeID, e.FullName, d.DepartmentName, 'Full-Time' AS ContractType,\n"
          + "       ft.MonthlyBaseSalary AS EstimatedMonthlyPay,\n"
          + "       ft.StandardMonthlyHours AS ExpectedMonthlyHours\n"
          + "FROM Employee e\n"
          + "JOIN FullTime ft ON e.EmployeeID = ft.EmployeeID\n"
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "UNION ALL\n"
          + "SELECT e.EmployeeID, e.FullName, d.DepartmentName, 'Part-Time' AS ContractType,\n"
          + "       (pt.HourlyRate * pt.ExpectedWeeklyHours * 4) AS EstimatedMonthlyPay,\n"
          + "       (pt.ExpectedWeeklyHours * 4) AS ExpectedMonthlyHours\n"
          + "FROM Employee e\n"
          + "JOIN PartTime pt ON e.EmployeeID = pt.EmployeeID\n"
          + "JOIN Department d ON e.DepartmentID = d.DepartmentID\n"
          + "ORDER BY EstimatedMonthlyPay DESC";

    public static TableData compensation() throws SQLException {
        return Jdbc.query(COMPENSATION);
    }

    /** {active employees, shifts on the day, pending leave requests, late arrivals this month}, limited to scope (null = everyone). */
    public static int[] kpis(LocalDate today, Set<Integer> scope) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            return new int[]{
                    scoped(c, "SELECT COUNT(*) FROM Employee e WHERE e.EmploymentStatus = 'Active'", "e.EmployeeID", scope),
                    scoped(c, "SELECT COUNT(*) FROM ShiftAssignment sa WHERE sa.WorkDate = ? AND sa.AssignmentStatus <> 'Cancelled'",
                            "sa.EmployeeID", scope, today),
                    scoped(c, "SELECT COUNT(*) FROM LeaveRequest lr WHERE lr.RequestStatus = 'Pending'", "lr.EmployeeID", scope),
                    scoped(c, "SELECT COUNT(*) FROM Attendance a JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID\n"
                            + "WHERE a.AttendanceStatus = 'Late' AND sa.WorkDate BETWEEN ? AND ?", "sa.EmployeeID", scope,
                            today.withDayOfMonth(1), today)
            };
        }
    }

    private static int scoped(Connection c, String sql, String column, Set<Integer> scope, Object... p)
            throws SQLException {
        StringBuilder sb = new StringBuilder(sql);
        List<Object> params = new ArrayList<>(Arrays.asList(p));
        Scope.appendIn(sb, column, scope, params);
        return count(c, sb.toString(), params.toArray());
    }

    /** Shifts of one employee from a date on (not cancelled). */
    public static TableData upcomingShifts(int employeeId, LocalDate from) throws SQLException {
        return Jdbc.query(
                "SELECT sa.WorkDate, DAYNAME(sa.WorkDate) AS Day, s.ShiftName, s.StartTime, s.EndTime, sa.AssignmentStatus\n"
              + "FROM ShiftAssignment sa JOIN Shift s ON sa.ShiftID = s.ShiftID\n"
              + "WHERE sa.EmployeeID = ? AND sa.WorkDate >= ? AND sa.AssignmentStatus <> 'Cancelled'\n"
              + "ORDER BY sa.WorkDate, s.StartTime", employeeId, from);
    }

    /** Figures for an employee's home page. annualLeft is null when there is no Annual balance for the year. */
    public record MyNumbers(int lateThisMonth, BigDecimal overtimeThisMonth, int pendingRequests, BigDecimal annualLeft) {}

    public static MyNumbers myNumbers(int employeeId, LocalDate today) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            TableData a = Jdbc.query(c,
                    "SELECT COALESCE(SUM(a.AttendanceStatus = 'Late'), 0) AS LateCount,\n"
                  + "       COALESCE(SUM(a.OvertimeHours), 0) AS Overtime\n"
                  + "FROM Attendance a JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID\n"
                  + "WHERE sa.EmployeeID = ? AND sa.WorkDate BETWEEN ? AND ?", employeeId, today.withDayOfMonth(1), today);
            TableData p = Jdbc.query(c,
                    "SELECT COUNT(*) FROM LeaveRequest WHERE EmployeeID = ? AND RequestStatus = 'Pending'", employeeId);
            TableData b = Jdbc.query(c,
                    "SELECT AllocatedDays - UsedDays FROM LeaveBalance\n"
                  + "WHERE EmployeeID = ? AND LeaveYear = ? AND LeaveType = 'Annual'", employeeId, today.getYear());
            return new MyNumbers(
                    ((Number) a.rows.get(0)[0]).intValue(),
                    new BigDecimal(a.rows.get(0)[1].toString()),
                    ((Number) p.rows.get(0)[0]).intValue(),
                    b.rowCount() == 0 ? null : (BigDecimal) b.rows.get(0)[0]);
        }
    }

    private static int count(Connection c, String sql, Object... p) throws SQLException {
        return ((Number) Jdbc.query(c, sql, p).rows.get(0)[0]).intValue();
    }

    public static TableData understaffedFrom(LocalDate from) throws SQLException {
        return Jdbc.query(
                "SELECT sr.WorkDate, s.ShiftName, sr.RequiredEmployees AS Needed,\n"
              + "       COUNT(sa.ShiftAssignmentID) AS Assigned,\n"
              + "       (sr.RequiredEmployees - COUNT(sa.ShiftAssignmentID)) AS Missing\n"
              + "FROM ShiftRequirement sr\n"
              + "JOIN Shift s ON sr.ShiftID = s.ShiftID\n"
              + "LEFT JOIN ShiftAssignment sa ON sr.ShiftID = sa.ShiftID AND sr.WorkDate = sa.WorkDate\n"
              + "      AND sa.AssignmentStatus <> 'Cancelled'\n"
              + "WHERE sr.WorkDate >= ?\n"
              + "GROUP BY sr.WorkDate, s.ShiftName, sr.RequiredEmployees\n"
              + "HAVING COUNT(sa.ShiftAssignmentID) < sr.RequiredEmployees\n"
              + "ORDER BY sr.WorkDate, s.ShiftName", from);
    }

    public static TableData assignmentsOf(int employeeId) throws SQLException {
        return Jdbc.query(
                "SELECT sa.ShiftAssignmentID, sa.WorkDate, s.ShiftName, s.StartTime, s.EndTime, sa.AssignmentStatus,\n"
              + "       a.AttendanceStatus, a.ActualCheckIn, a.ActualCheckOut, a.OvertimeHours, a.Notes\n"
              + "FROM ShiftAssignment sa\n"
              + "JOIN Shift s ON sa.ShiftID = s.ShiftID\n"
              + "LEFT JOIN Attendance a ON sa.ShiftAssignmentID = a.ShiftAssignmentID\n"
              + "WHERE sa.EmployeeID = ?\n"
              + "ORDER BY sa.WorkDate DESC, s.StartTime", employeeId);
    }

    public static TableData clockEventsOf(int employeeId) throws SQLException {
        return Jdbc.query(
                "SELECT sa.WorkDate, ae.AttendanceID, ae.EventNo, ae.EventType, ae.EventTimestamp, ae.DeviceLocation\n"
              + "FROM AttendanceEvent ae\n"
              + "JOIN Attendance a ON ae.AttendanceID = a.AttendanceID\n"
              + "JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID\n"
              + "WHERE sa.EmployeeID = ?\n"
              + "ORDER BY sa.WorkDate DESC, ae.AttendanceID, ae.EventNo", employeeId);
    }

    /** Browse tab: table name must come from the SchemaInfo allowlist; it is never typed by the user. */
    public static TableData browse(String table, List<String> orderBy) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM `").append(table.replace("`", "``")).append('`');
        if (orderBy != null && !orderBy.isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < orderBy.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append('`').append(orderBy.get(i).replace("`", "``")).append('`');
            }
        }
        return Jdbc.query(sql.toString());
    }
}
