package ui;

import auth.UserSession;
import dao.LeaveDao;
import dao.QueryDao;
import db.DBConnection;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;

/** Home page for an employee (USER role): next shift, own numbers, upcoming shifts and recent requests. */
public class EmployeeHomePanel extends JPanel implements Refreshable {

    private final UserSession session;
    private final OverviewPanel.Navigator nav;
    private final JLabel greeting = new JLabel();
    private final JLabel subtitle = Theme.muted(" ");
    private final JLabel[] values = new JLabel[4];
    private final JLabel[] notes = new JLabel[4];
    private final JTable shifts = UiUtil.newTable();
    private final JTable requests = UiUtil.newTable();
    private final JTextArea sql = UiUtil.sqlArea();

    public EmployeeHomePanel(UserSession session, OverviewPanel.Navigator nav) {
        super(new BorderLayout());
        this.session = session;
        this.nav = nav;
        UiUtil.hideColumns(requests, "EmployeeID", "Applicant", "Reason", "ReviewedAt", "SubmittedAt");

        JPanel kpis = new JPanel(new GridLayout(1, 4, 12, 0));
        kpis.setOpaque(false);
        kpis.add(kpi(0, "Next shift", "Schedules"));
        kpis.add(kpi(1, "Annual leave left", "Leave"));
        kpis.add(kpi(2, "Requests waiting", "Leave"));
        kpis.add(kpi(3, "Late this month", "Schedules"));

        JPanel lists = new JPanel(new GridLayout(1, 2, 12, 0));
        lists.setOpaque(false);
        lists.add(Theme.card("My upcoming shifts", Theme.tableScroll(shifts)));
        lists.add(Theme.card("My recent leave requests", Theme.tableScroll(requests)));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(subtitle, BorderLayout.NORTH);
        top.add(kpis, BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(lists, BorderLayout.CENTER);

        String first = session.fullName.contains(" ")
                ? session.fullName.substring(session.fullName.lastIndexOf(' ') + 1) : session.fullName;
        add(Theme.page("Hi, " + first, null, null, body, sql));
    }

    private JPanel kpi(int i, String caption, String page) {
        values[i] = new JLabel("–");
        values[i].setFont(values[i].getFont().deriveFont(Font.BOLD, values[i].getFont().getSize2D() + 12f));
        notes[i] = Theme.muted(" ");
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.add(Theme.muted(caption), BorderLayout.NORTH);
        p.add(values[i], BorderLayout.CENTER);
        p.add(notes[i], BorderLayout.SOUTH);
        p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel card = Theme.card(p);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { nav.open(page, 0); }
        });
        return card;
    }

    @Override public void refresh() {
        SqlTrace.clear();
        LocalDate today = DBConnection.referenceDate();
        subtitle.setText("Today is " + today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)) + ".");
        try {
            TableData up = QueryDao.upcomingShifts(session.employeeId, today);
            UiUtil.setData(shifts, up);
            if (up.rowCount() > 0) {
                Object d = up.value(0, "WorkDate");
                LocalDate next = LocalDate.parse(String.valueOf(d));
                values[0].setText(next.equals(today) ? "Today" : next.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)));
                notes[0].setText(up.value(0, "ShiftName") + "  " + hhmm(up.value(0, "StartTime")) + "–" + hhmm(up.value(0, "EndTime")));
            } else {
                values[0].setText("None");
                notes[0].setText("No shifts planned yet");
            }

            QueryDao.MyNumbers n = QueryDao.myNumbers(session.employeeId, today);
            values[1].setText(n.annualLeft() == null ? "–" : Theme.text(n.annualLeft()) + " days");
            notes[1].setText(n.annualLeft() == null ? "No annual leave balance" : "in " + today.getYear());
            values[2].setText(String.valueOf(n.pendingRequests()));
            notes[2].setText("leave requests pending");
            values[3].setText(String.valueOf(n.lateThisMonth()));
            notes[3].setText("overtime this month: " + Theme.text(n.overtimeThisMonth()) + " h");

            TableData req = LeaveDao.list(session.employeeId, null, null);
            UiUtil.setData(requests, req);
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load home page", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }

    private static String hhmm(Object time) {
        String s = String.valueOf(time);
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }
}
