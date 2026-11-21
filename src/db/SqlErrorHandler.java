package db;

import java.awt.Component;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Turns MySQL / JDBC errors into plain-language messages. Users never see a stack trace.
 */
public final class SqlErrorHandler {

    private SqlErrorHandler() {}

    /** Friendly names for constraints declared in 01_schema_and_data.sql. Unknown names fall back to a generic text. */
    private static final Map<String, String> CONSTRAINT_TEXT = Map.ofEntries(
            Map.entry("uq_employee_ssn", "Another employee already has this SSN."),
            Map.entry("uq_employee_email", "Another employee already uses this email address."),
            Map.entry("uq_department_name", "A department with this name already exists."),
            Map.entry("uq_position_name", "A position with this name already exists."),
            Map.entry("uq_useraccount_username", "This username is already taken."),
            Map.entry("uq_useraccount_emp", "This employee already has a user account."),
            Map.entry("uq_emp_shift_date", "This employee is already assigned to that shift on that date."),
            Map.entry("uq_attendance_assignment", "This shift assignment already has an attendance record."),
            Map.entry("uq_shift_name", "A shift with this name already exists."),
            Map.entry("fk_employee_dept_pos", "The selected position is not offered by the selected department."),
            Map.entry("fk_employee_manager", "The selected supervisor does not exist."),
            Map.entry("fk_shiftassign_employee", "This employee still has shift assignments."),
            Map.entry("fk_leavereq_employee", "This employee still has leave requests."),
            Map.entry("fk_leavereq_reviewer", "The reviewer does not exist."),
            Map.entry("chk_leave_dates", "End date must be on or after the start date."),
            Map.entry("chk_leave_balance_days", "Not enough leave balance: used days would exceed allocated days."),
            Map.entry("chk_attendance_overtime", "Overtime hours cannot be negative."),
            Map.entry("chk_attendance_times", "Check-out time cannot be earlier than check-in time."),
            Map.entry("chk_fulltime_salary", "Monthly base salary must be greater than 0."),
            Map.entry("chk_fulltime_hours", "Standard monthly hours must be greater than 0."),
            Map.entry("chk_parttime_rate", "Hourly rate must be greater than 0."),
            Map.entry("chk_parttime_hours", "Expected weekly hours must be between 1 and 40."),
            Map.entry("chk_shift_times", "Shift times are invalid for the overnight setting."),
            Map.entry("chk_shift_req_count", "Required employees must be greater than 0."),
            Map.entry("chk_deptpos_headcount", "Max headcount must be greater than 0."),
            Map.entry("chk_leave_balance_year", "Leave year must be between 2000 and 2100.")
    );

    private static final Pattern DUP_KEY = Pattern.compile("for key '(?:[^'.]*\\.)?([^']+)'");
    private static final Pattern FK_NAME = Pattern.compile("CONSTRAINT `([^`]+)`");
    private static final Pattern FK_CHILD = Pattern.compile("fails \\(`[^`]*`\\.`([^`]+)`");
    private static final Pattern CHECK_NAME = Pattern.compile("[Cc]heck constraint '([^']+)'");

    public static String message(Throwable t) {
        if (t instanceof BusinessException) return t.getMessage();
        if (!(t instanceof SQLException e)) {
            return "Unexpected error: " + firstLine(t.getMessage());
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String state = e.getSQLState() == null ? "" : e.getSQLState();

        switch (e.getErrorCode()) {
            case 1062: {
                String k = find(DUP_KEY, msg);
                return CONSTRAINT_TEXT.getOrDefault(k, "This value already exists (duplicate " + (k == null ? "key" : k) + ").");
            }
            case 1451: {
                String fk = find(FK_NAME, msg);
                String child = find(FK_CHILD, msg);
                String detail = CONSTRAINT_TEXT.getOrDefault(fk, "Other records in " + (child == null ? "another table" : child) + " still refer to it.");
                return "Cannot delete or change this record. " + detail + " Remove or reassign those records first.";
            }
            case 1452: {
                String fk = find(FK_NAME, msg);
                return CONSTRAINT_TEXT.getOrDefault(fk, "A referenced record does not exist (" + (fk == null ? "foreign key" : fk) + ").");
            }
            case 3819: {
                String ck = find(CHECK_NAME, msg);
                return CONSTRAINT_TEXT.getOrDefault(ck, "A data rule was violated (" + (ck == null ? "check constraint" : ck) + ").");
            }
            case 99001: // ScriptRunner: missing privilege to create triggers (message already written for users)
                return msg;
            case 1644: // SIGNAL SQLSTATE '45000' from a trigger: its text is already written for users
                return msg;
            case 1045: return "MySQL rejected the user/password. Fix them in Database settings.";
            case 1049: return "The database is not installed yet. Use Database settings > Install database.";
            case 1146: return "A table or view is missing (" + firstLine(msg) + "). The database may be an older or newer version than the app expects.";
            case 1054: return "Unknown column (" + firstLine(msg) + "). The database structure may have changed.";
            case 1048: return "A required field is empty.";
            case 1406: return "A value is too long for its field.";
            case 1265:
            case 1366:
            case 1292: return "A value has the wrong format for its field (" + firstLine(msg) + ").";
            case 1205: return "The record is locked by another user. Please try again.";
            default: break;
        }
        if (state.startsWith("08") || e.getErrorCode() == 0 && msg.toLowerCase().contains("communications link")) {
            return "Cannot reach MySQL. Make sure MySQL is running, then check Database settings.";
        }
        return "Database error (" + e.getErrorCode() + "): " + firstLine(msg);
    }

    public static void show(Component parent, String action, Throwable t) {
        String text = message(t);
        System.err.println("[" + action + "] " + t.getClass().getSimpleName() + ": " + firstLine(t.getMessage()));
        // Deferred so the calling panel can first update its SQL box / tables behind the dialog.
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, text, action + " failed", JOptionPane.ERROR_MESSAGE));
    }

    private static String find(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
