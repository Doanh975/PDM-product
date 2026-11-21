import auth.AuthService;
import auth.UserSession;
import dao.EmployeeDao;
import dao.EmployeeForm;
import dao.LeaveDao;
import dao.QueryDao;
import db.DBConnection;
import db.Jdbc;
import db.SchemaInfo;
import db.SqlErrorHandler;
import db.TableData;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Headless end-to-end check of the JDBC layer against a freshly loaded database
 * (run 01_schema_and_data.sql + 01b_triggers.sql first; do NOT run 02 before this).
 *   java -Dconfig=config.properties -cp "bin:lib/*:test-bin" SmokeTest
 */
public class SmokeTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        DBConnection.load();

        // --- login
        UserSession admin = AuthService.login("admin.tri", "anything".toCharArray());
        check("login admin.tri role", "ADMIN", admin.role);
        expectError("login disabled account", () -> AuthService.login("thao.ta", "x".toCharArray()), "Disabled");
        expectError("login unknown user", () -> AuthService.login("nobody", "x".toCharArray()), "Wrong username");

        // --- schema metadata
        check("tables found", true, SchemaInfo.tables().size() >= 15);
        check("LeaveType enum", "[Annual, Sick, Unpaid, Maternity, Personal]",
                SchemaInfo.enumValues("LeaveRequest", "LeaveType").toString());

        // --- Topic 15 queries with the report's parameters
        check("Q1 rows", 5, QueryDao.q1(3, d("2026-11-23"), d("2026-11-29")).rowCount());
        check("Q2 rows", 2, QueryDao.q2(d("2026-10-01"), d("2026-10-31"), 3).rowCount());
        check("Q3 rows", 6, QueryDao.q3(d("2026-10-01"), d("2026-12-31")).rowCount());
        TableData q4 = QueryDao.q4(d("2026-11-19"), 5);
        check("Q4 rows", 4, q4.rowCount());
        check("Q5 rows", 2, QueryDao.q5(d("2026-11-01"), d("2026-11-19")).rowCount());
        check("Report view summary rows", 15, QueryDao.departmentSummary().rowCount());
        check("Report understaffed rows", 16, QueryDao.understaffed().rowCount());
        check("Report compensation rows", 30, QueryDao.compensation().rowCount());
        check("Browse Employee rows", 30, QueryDao.browse("Employee", SchemaInfo.tables().get("Employee")).rowCount());

        // --- search: apostrophe + LIKE wildcard are treated as plain text
        check("search O'Connor (no SQL error)", 0, EmployeeDao.search("O'Connor", null, null).rowCount());
        check("search '%' literal", 0, EmployeeDao.search("%", null, null).rowCount());
        check("search 'Tran'", true, EmployeeDao.search("Tran", null, null).rowCount() >= 3);

        // --- employee CRUD (ISA total via transaction)
        EmployeeForm f = new EmployeeForm();
        f.fullName = "Test O'Connor"; f.ssn = "999999991"; f.sex = "M"; f.phone = "0909999999";
        f.email = "test.oconnor@company.vn"; f.hireDate = d("2026-11-19"); f.employmentStatus = "Active";
        f.departmentId = 2; f.positionId = 5; f.managerId = 1;
        f.type = EmployeeForm.PART_TIME; f.pay = new BigDecimal("40000"); f.hours = 20;
        int newId = EmployeeDao.insert(f);
        check("new employee is PartTime", 1, count("SELECT COUNT(*) FROM PartTime WHERE EmployeeID = ?", newId));
        f.employeeId = newId; f.type = EmployeeForm.FULL_TIME; f.pay = new BigDecimal("20000000"); f.hours = 160;
        EmployeeDao.update(f);
        check("switched to FullTime", 1, count("SELECT COUNT(*) FROM FullTime WHERE EmployeeID = ?", newId));
        check("no PartTime left", 0, count("SELECT COUNT(*) FROM PartTime WHERE EmployeeID = ?", newId));
        f.employeeId = null; f.email = "dup@company.vn";
        expectError("duplicate SSN", () -> EmployeeDao.insert(f), "SSN");
        f.ssn = "999999992"; f.departmentId = 2; f.positionId = 7;
        expectError("position not offered by department", () -> EmployeeDao.insert(f), "not offered");
        check("failed insert rolled back (no orphan Employee)", 0, count("SELECT COUNT(*) FROM Employee WHERE Ssn = ?", "999999992"));
        EmployeeForm cyc = EmployeeDao.load(1); cyc.managerId = 6;
        expectError("supervisor cycle trigger", () -> EmployeeDao.update(cyc), "cycle");
        expectError("delete employee with shifts", () -> EmployeeDao.delete(6), "Cannot delete");
        check("delete new employee", 1, EmployeeDao.delete(newId));

        // --- leave request CRUD
        int lr = LeaveDao.insert(10, "Personal", d("2026-12-15"), d("2026-12-15"), "Family errand");
        LeaveDao.update(lr, 10, "Personal", d("2026-12-15"), d("2026-12-16"), "Updated reason");
        expectError("USER cannot edit someone else's request", () -> LeaveDao.update(lr, 11, "Sick", d("2026-12-15"), d("2026-12-15"), "x"), "only by the applicant");
        expectError("end before start (CHECK)", () -> LeaveDao.insert(10, "Sick", d("2026-12-15"), d("2026-12-14"), "x"), "End date");
        LeaveDao.delete(lr, null);

        // --- approval transaction
        BigDecimal used8 = bd("SELECT UsedDays FROM LeaveBalance WHERE EmployeeID = 8 AND LeaveYear = 2026 AND LeaveType = 'Personal'");
        LeaveDao.approve(3, admin.employeeId, null);
        check("#3 approved -> COMMIT", "Approved", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = 3"));
        check("#3 balance +1", used8.add(BigDecimal.ONE).compareTo(
                bd("SELECT UsedDays FROM LeaveBalance WHERE EmployeeID = 8 AND LeaveYear = 2026 AND LeaveType = 'Personal'")), 0);

        expectError("#14 no balance row -> ROLLBACK", () -> LeaveDao.approve(14, admin.employeeId, null), "no Annual leave balance");
        check("#14 still Pending", "Pending", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = 14"));

        int big = LeaveDao.insert(11, "Annual", d("2026-12-01"), d("2026-12-31"), "Too long");
        expectError("exceeds allocated days -> ROLLBACK", () -> LeaveDao.approve(big, admin.employeeId, null), "Not enough leave balance");
        check("big request still Pending", "Pending", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = " + big));
        LeaveDao.delete(big, null);

        int own = LeaveDao.insert(1, "Annual", d("2026-12-01"), d("2026-12-01"), "Own");
        expectError("cannot approve own request", () -> LeaveDao.approve(own, admin.employeeId, null), "own leave request");
        LeaveDao.delete(own, null);

        expectError("reviewer without MANAGER/ADMIN account (trigger)", () -> LeaveDao.reject(15, 6, null), "Reviewer must have");
        LeaveDao.reject(15, admin.employeeId, null);
        check("#15 rejected", "Rejected", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = 15"));

        // --- roles: manager sees and reviews only their team, employee only themselves
        UserSession ngan = AuthService.login("mgr.ngan", "x".toCharArray());
        check("manager team (Ngan)", "[4, 5, 12, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 30]",
                new java.util.TreeSet<>(ngan.team).toString());
        check("manager can review team member #20", true, ngan.canReview(20));
        check("manager cannot review outside team (#8)", false, ngan.canReview(8));
        check("manager cannot review self", false, ngan.canReview(2));
        check("manager cannot manage employees", false, ngan.canManageEmployees());
        check("manager search = team + self", 18, EmployeeDao.search(null, null, null, ngan.visibleEmployees()).rowCount());
        TableData nganLeave = LeaveDao.list(null, null, ngan.visibleEmployees());
        long outside = 0;
        for (int r = 0; r < nganLeave.rowCount(); r++) {
            int emp = ((Number) nganLeave.value(r, "EmployeeID")).intValue();
            if (emp != 2 && !ngan.team.contains(emp)) outside++;
        }
        check("manager leave list has no one outside the team", 0, outside);
        check("manager leave list not empty", true, nganLeave.rowCount() > 0);
        expectError("manager approving outside team (#4, emp 9)",
                () -> LeaveDao.approve(4, ngan.employeeId, ngan.reviewableEmployees()), "not from your team");
        check("#4 still Pending", "Pending", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = 4"));
        LeaveDao.approve(7, ngan.employeeId, ngan.reviewableEmployees());
        check("manager approved team request #7 (emp 12)", "Approved", str("SELECT RequestStatus FROM LeaveRequest WHERE LeaveRequestID = 7"));

        UserSession binh = AuthService.login("binh.dinh", "x".toCharArray());
        check("employee sees only self", "[6]", binh.visibleEmployees().toString());
        check("employee cannot review", false, binh.canReview(7));
        check("employee leave list = own only", 1, LeaveDao.list(null, null, binh.visibleEmployees()).rowCount());
        check("employee home: annual leave left", new BigDecimal("9.00"), dao.QueryDao.myNumbers(6, d("2026-11-19")).annualLeft());

        // --- password change stores a real bcrypt hash
        AuthService.setPassword(admin, "binh.dinh", "secret123".toCharArray());
        expectError("non-admin cannot set someone else's password",
                () -> AuthService.setPassword(AuthService.login("mgr.ngan", "x".toCharArray()), "binh.dinh", "secret999".toCharArray()), "Only an administrator");
        check("bcrypt stored", true, AuthService.isBcrypt(str("SELECT PasswordHash FROM UserAccount WHERE Username = 'binh.dinh'")));
        check("login with new password", "USER", AuthService.login("binh.dinh", "secret123".toCharArray()).role);
        expectError("wrong password rejected", () -> AuthService.login("binh.dinh", "nope".toCharArray()), "Wrong username");

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    interface Action { void run() throws Exception; }

    static void expectError(String name, Action a, String expectedText) {
        try {
            a.run();
            fail(name, "expected an error containing '" + expectedText + "' but none was thrown");
        } catch (Exception e) {
            String msg = SqlErrorHandler.message(e);
            if (msg.contains(expectedText)) System.out.println("OK   " + name + "  ->  " + msg);
            else fail(name, "message was: " + msg);
        }
    }

    static void check(String name, Object expected, Object actual) {
        if (String.valueOf(expected).equals(String.valueOf(actual))) System.out.println("OK   " + name + " = " + actual);
        else fail(name, "expected " + expected + " but got " + actual);
    }

    static void fail(String name, String why) {
        failures++;
        System.out.println("FAIL " + name + ": " + why);
    }

    static LocalDate d(String s) { return LocalDate.parse(s); }

    static int count(String sql, Object... p) throws Exception {
        return ((Number) Jdbc.query(sql, p).rows.get(0)[0]).intValue();
    }

    static String str(String sql) throws Exception { return String.valueOf(Jdbc.query(sql).rows.get(0)[0]); }

    static BigDecimal bd(String sql) throws Exception { return (BigDecimal) Jdbc.query(sql).rows.get(0)[0]; }
}
