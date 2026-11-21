package ui;

import dao.QueryDao;
import db.SchemaInfo;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;

/** Data browser: view any table. The table list is an allowlist read from database metadata. */
public class BrowsePanel extends JPanel implements Refreshable {

    private final JComboBox<String> tableBox = new JComboBox<>();
    private final JLabel countLabel = Theme.muted("");
    private final JTable table = UiUtil.newTable();
    private final JTextArea sql = UiUtil.sqlArea();
    private Map<String, List<String>> allowlist = new LinkedHashMap<>();
    private boolean loading;

    public BrowsePanel() {
        super(new BorderLayout());
        JButton reload = new JButton("Reload");
        JPanel toolbar = Theme.row(new JLabel("Table"), tableBox, reload, countLabel);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(toolbar, BorderLayout.NORTH);
        body.add(Theme.card(Theme.tableScroll(table)), BorderLayout.CENTER);
        add(Theme.page("Data Browser", "Raw view of every table in the database (read-only).", null, body, sql));

        tableBox.addActionListener(e -> { if (!loading) loadTable(); });
        reload.addActionListener(e -> loadTable());
    }

    @Override public void refresh() {
        try {
            allowlist = new LinkedHashMap<>(SchemaInfo.tables());
            String keep = (String) tableBox.getSelectedItem();
            loading = true;
            tableBox.removeAllItems();
            for (String t : allowlist.keySet()) tableBox.addItem(t);
            if (keep != null && allowlist.containsKey(keep)) tableBox.setSelectedItem(keep);
            loading = false;
            loadTable();
        } catch (Exception ex) {
            loading = false;
            SqlErrorHandler.show(this, "Load table list", ex);
        }
    }

    private void loadTable() {
        String t = (String) tableBox.getSelectedItem();
        if (t == null || !allowlist.containsKey(t)) return;
        SqlTrace.clear();
        try {
            TableData d = QueryDao.browse(t, allowlist.get(t));
            UiUtil.setData(table, d);
            countLabel.setText(UiUtil.plural(d.rowCount(), "row"));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load " + t, ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }
}
