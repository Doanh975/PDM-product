package dao;

import auth.Scope;
import db.BusinessException;
import db.DBConnection;
import db.Jdbc;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LeaveDao {

    private LeaveDao() {}

    /** Requests, optionally filtered by applicant and status, limited to scope (null = everyone). */
    public static TableData list(Integer employeeId, String status, Set<Integer> scope) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT lr.LeaveRequestID, e.FullName AS Applicant, lr.RequestStatus, lr.LeaveType, lr.StartDate, lr.EndDate, "
              + "lr.Reason, lr.SubmittedAt, r.FullName AS ReviewedBy, lr.ReviewedAt, lr.EmployeeID "
              + "FROM LeaveRequest lr JOIN Employee e ON lr.EmployeeID = e.EmployeeID "
              + "LEFT JOIN Employee r ON lr.ReviewedBy = r.EmployeeID WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        Scope.appendIn(sql, "lr.EmployeeID", scope, params);
        if (employeeId != null) { sql.append(" AND lr.EmployeeID = ?"); params.add(employeeId); }
        if (status != null && !status.isBlank()) { sql.append(" AND lr.RequestStatus = ?"); params.add(status); }
        sql.append(" ORDER BY lr.SubmittedAt DESC, lr.LeaveRequestID DESC");
        return Jdbc.query(sql.toString(), params.toArray());
    }

    public static TableData balances(int employeeId) throws SQLException {
        return Jdbc.query(
                "SELECT LeaveYear, LeaveType, AllocatedDays, UsedDays, (AllocatedDays - UsedDays) AS RemainingDays "
              + "FROM LeaveBalance WHERE EmployeeID = ? ORDER BY LeaveYear DESC, LeaveType", employeeId);
    }

    public static int insert(int employeeId, String type, LocalDate start, LocalDate end, String reason) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            return Jdbc.insertReturningKey(c,
                    "INSERT INTO LeaveRequest (EmployeeID, LeaveType, StartDate, EndDate, Reason) VALUES (?, ?, ?, ?, ?)",
                    employeeId, type, start, end, reason);
        }
    }

    /** Only Pending requests can be edited. ownerId != null limits it to that applicant's own requests. */
    public static void update(int requestId, Integer ownerId, String type, LocalDate start, LocalDate end, String reason)
            throws SQLException, BusinessException {
        String sql = "UPDATE LeaveRequest SET LeaveType = ?, StartDate = ?, EndDate = ?, Reason = ? "
                + "WHERE LeaveRequestID = ? AND RequestStatus = 'Pending'" + (ownerId != null ? " AND EmployeeID = ?" : "");
        int n = ownerId != null
                ? Jdbc.update(sql, type, start, end, reason, requestId, ownerId)
                : Jdbc.update(sql, type, start, end, reason, requestId);
        if (n != 1) throw new BusinessException("Only Pending requests can be edited, and only by the applicant or an admin.");
    }

    public static void delete(int requestId, Integer ownerId) throws SQLException, BusinessException {
        String sql = "DELETE FROM LeaveRequest WHERE LeaveRequestID = ? AND RequestStatus = 'Pending'"
                + (ownerId != null ? " AND EmployeeID = ?" : "");
        int n = ownerId != null ? Jdbc.update(sql, requestId, ownerId) : Jdbc.update(sql, requestId);
        if (n != 1) throw new BusinessException("Only Pending requests can be deleted (approved/rejected ones are kept for history).");
    }

    /** reviewable = applicants this reviewer may decide on (null = anyone except the reviewer). */
    public static void reject(int requestId, int reviewerId, Set<Integer> reviewable) throws SQLException, BusinessException {
        TableData t = Jdbc.query("SELECT EmployeeID FROM LeaveRequest WHERE LeaveRequestID = ?", requestId);
        if (t.rowCount() == 0) throw new BusinessException("Request #" + requestId + " no longer exists.");
        checkReviewer(((Number) t.value(0, "EmployeeID")).intValue(), reviewerId, reviewable);
        int n = Jdbc.update(
                "UPDATE LeaveRequest SET RequestStatus = 'Rejected', ReviewedBy = ?, ReviewedAt = NOW() "
              + "WHERE LeaveRequestID = ? AND RequestStatus = 'Pending'", reviewerId, requestId);
        if (n != 1) throw new BusinessException("Request #" + requestId + " is no longer Pending.");
    }

    /**
     * Multi-table transaction (BR25): mark the request Approved AND add the days to LeaveBalance.UsedDays.
     * Rolls back when:
     *   - the request is not Pending / is the reviewer's own request,
     *   - there is no LeaveBalance row for (employee, year, type)  -> UPDATE matches 0 rows, checked here,
     *   - UsedDays would exceed AllocatedDays                       -> CHECK chk_leave_balance_days (error 3819),
     *   - the reviewer trigger rejects the reviewer                  -> SIGNAL 45000 (error 1644).
     *   - the applicant is not in reviewable (null = anyone except the reviewer).
     * Returns the number of days deducted.
     */
    public static BigDecimal approve(int requestId, int reviewerId, Set<Integer> reviewable) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            SqlTrace.note("START TRANSACTION;");
            try {
                TableData t = Jdbc.query(c,
                        "SELECT EmployeeID, LeaveType, StartDate, EndDate, RequestStatus FROM LeaveRequest "
                      + "WHERE LeaveRequestID = ? FOR UPDATE", requestId);
                if (t.rowCount() == 0) throw new BusinessException("Request #" + requestId + " no longer exists.");
                int employeeId = ((Number) t.value(0, "EmployeeID")).intValue();
                String type = (String) t.value(0, "LeaveType");
                LocalDate start = toLocalDate(t.value(0, "StartDate"));
                LocalDate end = toLocalDate(t.value(0, "EndDate"));
                if (!"Pending".equals(t.value(0, "RequestStatus"))) {
                    throw new BusinessException("Request #" + requestId + " is already " + t.value(0, "RequestStatus") + ".");
                }
                checkReviewer(employeeId, reviewerId, reviewable);

                BigDecimal days = countLeaveDays(c, start, end);

                int n1 = Jdbc.update(c,
                        "UPDATE LeaveRequest SET RequestStatus = 'Approved', ReviewedBy = ?, ReviewedAt = NOW() "
                      + "WHERE LeaveRequestID = ? AND RequestStatus = 'Pending'", reviewerId, requestId);
                if (n1 != 1) throw new BusinessException("Request #" + requestId + " is no longer Pending.");

                int n2 = Jdbc.update(c,
                        "UPDATE LeaveBalance SET UsedDays = UsedDays + ? "
                      + "WHERE EmployeeID = ? AND LeaveYear = ? AND LeaveType = ?",
                        days, employeeId, start.getYear(), type);
                if (n2 != 1) {
                    throw new BusinessException("Employee #" + employeeId + " has no " + type + " leave balance for "
                            + start.getYear() + ". The approval was rolled back.");
                }
                c.commit();
                SqlTrace.note("COMMIT;  -- " + days + " day(s) deducted");
                return days;
            } catch (SQLException | BusinessException | RuntimeException e) {
                c.rollback();
                SqlTrace.note("ROLLBACK;  -- " + SqlErrorHandler.message(e));
                throw e;
            }
        }
    }

    private static void checkReviewer(int applicantId, int reviewerId, Set<Integer> reviewable) throws BusinessException {
        if (applicantId == reviewerId) throw new BusinessException("You cannot review your own leave request.");
        if (reviewable != null && !reviewable.contains(applicantId)) {
            throw new BusinessException("This request is not from your team, so you cannot review it.");
        }
    }


    /** Days deducted for a leave, per leave.dayCountMode in config.properties (CALENDAR or WORKING). */
    static BigDecimal countLeaveDays(Connection c, LocalDate start, LocalDate end) throws SQLException {
        String mode = DBConnection.get("leave.dayCountMode", "CALENDAR").toUpperCase();
        if (!"WORKING".equals(mode)) {
            return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
        }
        Set<LocalDate> holidays = new HashSet<>();
        TableData h = Jdbc.query(c,
                "SELECT HolidayDate FROM PublicHoliday WHERE HolidayDate BETWEEN ? AND ?", start, end);
        for (Object[] r : h.rows) holidays.add(toLocalDate(r[0]));
        long n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) n++;
        }
        return BigDecimal.valueOf(n);
    }

    static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(String.valueOf(o));
    }
}
