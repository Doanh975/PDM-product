package ui;

import auth.UserSession;
import dao.EmployeeDao;
import dao.Item;
import dao.LookupDao;
import db.SchemaInfo;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Search employees by keyword, department and status. */
public class SearchPanel extends JPanel implements Refreshable {

    private final JTextField keyword = new JTextField(22);
    private final JComboBox<Item> dept = new JComboBox<>();
    private final JComboBox<String> status = new JComboBox<>();
    private final JLabel countLabel = Theme.muted("");
    private final JTable table = UiUtil.newTable();
    private final JTextArea sql = UiUtil.sqlArea();
    private boolean filling;

    private final Set<Integer> scope;

    public SearchPanel(UserSession session) {
        super(new BorderLayout());
        this.scope = session.visibleEmployees();
        Theme.placeholder(keyword, "Search by name, email or SSN");
        keyword.putClientProperty("JTextField.showClearButton", true);
        JButton search = Theme.primary("Search");
        JButton clear = Theme.subtle("Clear filters");
        JPanel toolbar = Theme.row(keyword, dept, status, search, clear, countLabel);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(toolbar, BorderLayout.NORTH);
        body.add(Theme.card(Theme.tableScroll(table)), BorderLayout.CENTER);
        add(Theme.page(session.isManager() ? "My Team" : "Employees",
                session.isManager() ? "You and everyone who reports to you, directly or through another manager."
                                    : "Find people by name, email or SSN, and filter by department or status.", null, body, sql));

        search.addActionListener(e -> search());
        keyword.addActionListener(e -> search());
        dept.addActionListener(e -> { if (!filling) search(); });
        status.addActionListener(e -> { if (!filling) search(); });
        clear.addActionListener(e -> {
            filling = true;
            keyword.setText("");
            dept.setSelectedIndex(0);
            status.setSelectedIndex(0);
            filling = false;
            search();
        });
    }

    @Override public void refresh() {
        filling = true;
        try {
            Item keepDept = (Item) dept.getSelectedItem();
            Object keepStatus = status.getSelectedItem();
            dept.removeAllItems();
            dept.addItem(new Item(null, "All departments"));
            for (Item i : LookupDao.departments()) dept.addItem(i);
            if (keepDept != null) dept.setSelectedItem(keepDept);
            status.removeAllItems();
            status.addItem("All statuses");
            for (String s : SchemaInfo.enumValues("Employee", "EmploymentStatus")) status.addItem(s);
            if (keepStatus != null) status.setSelectedItem(keepStatus);
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load filters", ex);
        } finally {
            filling = false;
        }
        search();
    }

    private void search() {
        SqlTrace.clear();
        try {
            Item d = (Item) dept.getSelectedItem();
            String s = (String) status.getSelectedItem();
            TableData data = EmployeeDao.search(keyword.getText(), d == null ? null : d.id,
                    s == null || s.startsWith("All") ? null : s, scope);
            UiUtil.setData(table, data);
            countLabel.setText(data.rowCount() == 0 ? "No employees match these filters" : UiUtil.plural(data.rowCount(), "employee"));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Search", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }
}
