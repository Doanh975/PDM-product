package ui;

import dao.QueryDao;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

/** Aggregate reports: department attendance summary (view), understaffed shifts, pay overview. */
public class ReportPanel extends JPanel implements Refreshable {

    private static final String[][] REPORTS = {
            {"Attendance by employee", "Shifts recorded, present / late / absent days and overtime per employee (view vw_EmployeeAttendanceSummary)."},
            {"Shifts that need more staff", "Planned shifts where fewer people are assigned than required."},
            {"Pay overview", "Estimated monthly pay and hours for full-time and part-time staff."}
    };

    private final JToggleButton[] buttons = new JToggleButton[REPORTS.length];
    private final JLabel description = Theme.muted(" ");
    private final JLabel countLabel = Theme.muted("");
    private final JTable table = UiUtil.newTable();
    private final JTextArea sql = UiUtil.sqlArea();
    private int current = 0;

    public ReportPanel() {
        super(new BorderLayout());
        ButtonGroup group = new ButtonGroup();
        JPanel chooser = Theme.row();
        for (int i = 0; i < REPORTS.length; i++) {
            int idx = i;
            buttons[i] = new JToggleButton(REPORTS[i][0], i == 0);
            buttons[i].putClientProperty("JButton.buttonType", "roundRect");
            group.add(buttons[i]);
            chooser.add(buttons[i]);
            buttons[i].addActionListener(e -> { current = idx; run(); });
        }
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(chooser, BorderLayout.NORTH);
        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);
        info.add(description, BorderLayout.WEST);
        info.add(countLabel, BorderLayout.EAST);
        top.add(info, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(Theme.card(Theme.tableScroll(table)), BorderLayout.CENTER);
        add(Theme.page("Reports", "Summaries for managers. Pick a report.", null, body, sql));
    }

    @Override public void refresh() { run(); }

    public void show(int index) {
        current = index;
        buttons[index].setSelected(true);
        run();
    }

    private void run() {
        SqlTrace.clear();
        description.setText(REPORTS[current][1]);
        try {
            TableData d = switch (current) {
                case 1 -> QueryDao.understaffed();
                case 2 -> QueryDao.compensation();
                default -> QueryDao.employeeSummary();
            };
            UiUtil.setData(table, d);
            countLabel.setText(UiUtil.plural(d.rowCount(), "row"));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Run report", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }
}
