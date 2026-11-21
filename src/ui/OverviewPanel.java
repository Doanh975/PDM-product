package ui;

import auth.UserSession;
import dao.LeaveDao;
import dao.QueryDao;
import db.DBConnection;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;

/** Home page for admins (whole company) and managers (their team): key numbers and open items. */
public class OverviewPanel extends JPanel implements Refreshable {

    /** Opens another page: name of the page and an optional sub-view index. */
    public interface Navigator { void open(String page, int view); }

    private final Navigator nav;
    private final UserSession session;
    private final JLabel[] values = new JLabel[4];
    private final JLabel subtitle = Theme.muted(" ");
    private final JTable waitingTable = UiUtil.newTable();
    private final JTable staffTable = UiUtil.newTable();
    private final JLabel waitingTitle = new JLabel();
    private final JLabel staffTitle = new JLabel();
    private final JTextArea sql = UiUtil.sqlArea();

    public OverviewPanel(UserSession session, Navigator nav) {
        super(new BorderLayout());
        this.nav = nav;
        this.session = session;
        boolean mgr = session.isManager();
        UiUtil.hideColumns(waitingTable, "DepartmentName", "StartDate", "EndDate");

        JPanel kpis = new JPanel(new GridLayout(1, 4, 12, 0));
        kpis.setOpaque(false);
        kpis.add(kpi(0, mgr ? "Active people in your team" : "Active employees", "Employees", 0));
        kpis.add(kpi(1, mgr ? "Team shifts today" : "Shifts today", "Schedules", 0));
        kpis.add(kpi(2, "Leave requests to review", "Leave", 0));
        kpis.add(kpi(3, mgr ? "Team late arrivals this month" : "Late arrivals this month", mgr ? "Schedules" : "Insights", 1));

        JPanel lists = new JPanel(new GridLayout(1, 2, 12, 0));
        lists.setOpaque(false);
        lists.add(listCard(waitingTitle, waitingTable, "Insights", 3));
        lists.add(listCard(staffTitle, staffTable, "Reports", 1));

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(subtitle, BorderLayout.NORTH);
        top.add(kpis, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        body.add(lists, BorderLayout.CENTER);
        add(Theme.page(mgr ? "Team Overview" : "Overview", null, null, body, sql));
    }

    private JPanel kpi(int i, String caption, String page, int view) {
        values[i] = new JLabel("–");
        values[i].setFont(values[i].getFont().deriveFont(Font.BOLD, values[i].getFont().getSize2D() + 16f));
        values[i].setForeground(Theme.TEXT);
        JLabel cap = Theme.muted(caption);
        JLabel open = new JLabel("View  ›");
        open.setForeground(Theme.ACCENT);
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.add(cap, BorderLayout.NORTH);
        p.add(values[i], BorderLayout.CENTER);
        p.add(open, BorderLayout.SOUTH);
        p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel card = Theme.card(p);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { nav.open(page, view); }
        });
        return card;
    }

    private JPanel listCard(JLabel title, JTable table, String page, int view) {
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
        JLabel open = new JLabel("Open  ›");
        open.setForeground(Theme.ACCENT);
        open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        open.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { nav.open(page, view); }
        });
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title, BorderLayout.WEST);
        head.add(open, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(head, BorderLayout.NORTH);
        p.add(Theme.tableScroll(table), BorderLayout.CENTER);
        return Theme.card(p);
    }

    @Override public void refresh() {
        SqlTrace.clear();
        LocalDate today = DBConnection.referenceDate();
        subtitle.setText((session.isManager() ? "Your team at a glance. " : "") + "Today is " + today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
                + ". Click a box to see the details.");
        try {
            int[] k = QueryDao.kpis(today, session.visibleEmployees());
            // "to review" = pending requests this user may decide on (never their own)
            Set<Integer> reviewable = session.reviewableEmployees();
            TableData pending = LeaveDao.list(null, "Pending", reviewable);
            Set<Object> pendingIds = new HashSet<>();
            for (int r = 0; r < pending.rowCount(); r++) {
                if (!Integer.valueOf(session.employeeId).equals(((Number) pending.value(r, "EmployeeID")).intValue())) {
                    pendingIds.add(((Number) pending.value(r, "LeaveRequestID")).intValue());
                }
            }
            k[2] = pendingIds.size();
            for (int i = 0; i < 4; i++) values[i].setText(String.valueOf(k[i]));
            values[2].setForeground(k[2] > 0 ? new Color(0xB45309) : Theme.TEXT);
            values[3].setForeground(k[3] > 0 ? new Color(0xB45309) : Theme.TEXT);

            TableData all = QueryDao.q4(today, 5);
            List<Object[]> mine = new ArrayList<>();
            for (Object[] row : all.rows) if (pendingIds.contains(((Number) row[0]).intValue())) mine.add(row);
            TableData waiting = new TableData(all.columns, mine);
            UiUtil.setData(waitingTable, waiting);
            waitingTitle.setText("Leave requests waiting more than 5 working days (" + waiting.rowCount() + ")");

            TableData staff = QueryDao.understaffedFrom(today);
            UiUtil.setData(staffTable, staff);
            staffTitle.setText("Upcoming shifts that need more staff (" + staff.rowCount() + ")");
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load overview", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }
}
