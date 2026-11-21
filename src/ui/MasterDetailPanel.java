package ui;

import auth.UserSession;
import dao.EmployeeDao;
import dao.LeaveDao;
import dao.QueryDao;
import db.SqlErrorHandler;
import db.SqlTrace;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;

/**
 * Master-detail: pick an employee (top) to see their shifts with attendance, clock events and
 * leave balance (bottom). USER accounts only see themselves.
 */
public class MasterDetailPanel extends JPanel implements Refreshable {

    private final UserSession session;
    private final JTable master = UiUtil.newTable();
    private final JTable assignments = UiUtil.newTable();
    private final JTable events = UiUtil.newTable();
    private final JTable balances = UiUtil.newTable();
    private final JLabel detailTitle = new JLabel("Select an employee above");
    private final JTextArea sql = UiUtil.sqlArea();

    public MasterDetailPanel(UserSession session) {
        super(new BorderLayout());
        this.session = session;
        UiUtil.hideColumns(master, "Ssn", "Sex", "Email", "Phone", "HireDate");

        detailTitle.setFont(detailTitle.getFont().deriveFont(Font.BOLD, detailTitle.getFont().getSize2D() + 1f));
        JTabbedPane details = new JTabbedPane();
        details.putClientProperty("JTabbedPane.tabType", "underlined");
        details.addTab("Shifts & attendance", Theme.tableScroll(assignments));
        details.addTab("Clock-in / clock-out log", Theme.tableScroll(events));
        details.addTab("Leave balance", Theme.tableScroll(balances));
        JPanel lower = new JPanel(new BorderLayout(0, 6));
        lower.setOpaque(false);
        lower.add(detailTitle, BorderLayout.NORTH);
        lower.add(details, BorderLayout.CENTER);

        JPanel body;
        if (!session.isEmployee()) {
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    Theme.card("Employees", Theme.tableScroll(master)), Theme.card(lower));
            split.setResizeWeight(0.42);
            split.setBorder(null);
            split.setOpaque(false);
            body = new JPanel(new BorderLayout());
            body.setOpaque(false);
            body.add(split);
        } else {
            body = Theme.card(lower);
        }
        String title = session.isEmployee() ? "My Shifts & Attendance"
                : session.isManager() ? "Team Schedules & Attendance" : "Schedules & Attendance";
        String sub = !session.isEmployee()
                ? (session.isManager() ? "You and the people who report to you. " : "")
                  + "Pick an employee to see their shifts, attendance, clock events and leave balance."
                : "Your shifts, attendance, clock events and leave balance.";
        add(Theme.page(title, sub, null, body, sql));

        master.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadDetail();
        });
    }

    @Override public void refresh() {
        SqlTrace.clear();
        try {
            UiUtil.setData(master, !session.isEmployee()
                    ? EmployeeDao.search(null, null, null, session.visibleEmployees())
                    : EmployeeDao.one(session.employeeId));
            if (master.getRowCount() > 0) master.setRowSelectionInterval(0, 0);
            if (!!session.isEmployee()) loadDetail();
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load employees", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }


    private void loadDetail() {
        Integer id = !session.isEmployee() ? UiUtil.selectedInt(master, "EmployeeID") : Integer.valueOf(session.employeeId);
        if (id == null) return;
        SqlTrace.clear();
        try {
            Object name = !session.isEmployee() ? UiUtil.selected(master, "FullName") : session.fullName;
            detailTitle.setText(name + "  (#" + id + ")");
            UiUtil.setData(assignments, QueryDao.assignmentsOf(id));
            UiUtil.setData(events, QueryDao.clockEventsOf(id));
            UiUtil.setData(balances, LeaveDao.balances(id));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load details", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }
}
