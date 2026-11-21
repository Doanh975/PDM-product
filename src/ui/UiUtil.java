package ui;

import db.BusinessException;
import db.TableData;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.text.DefaultCaret;

/** Shared Swing helpers (tables, validation, dialogs). */
public final class UiUtil {

    private UiUtil() {}

    public static JTable newTable() {
        JTable t = new JTable(new ResultTableModel(TableData.empty()));
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
        Theme.CellRenderer r = new Theme.CellRenderer();
        t.setDefaultRenderer(Object.class, r);
        t.setDefaultRenderer(Number.class, r);
        t.setDefaultRenderer(Date.class, r);
        t.setDefaultRenderer(Boolean.class, r);
        t.getTableHeader().setReorderingAllowed(false);
        Font hf = t.getTableHeader().getFont();
        t.getTableHeader().setFont(hf.deriveFont(Font.BOLD));
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return t;
    }

    public static void setData(JTable table, TableData data) {
        table.setModel(new ResultTableModel(data));
        table.setAutoCreateRowSorter(true);
        packColumns(table);
    }

    /** Columns (raw DB names) kept in the data but not shown, e.g. IDs needed only for selection. */
    public static void hideColumns(JTable table, String... rawNames) {
        table.putClientProperty("hiddenColumns", Set.of(rawNames));
        packColumns(table);
    }

    /**
     * Sizes each column to fit its header and (up to 200 rows of) content, capped at 280px, and
     * removes hidden columns. Tables that almost fit are squeezed to the width instead of scrolling.
     */
    static void packColumns(JTable table) {
        FontMetrics fm = table.getFontMetrics(table.getFont().deriveFont(Font.BOLD));
        FontMetrics hfm = table.getFontMetrics(table.getTableHeader().getFont());
        TableData d = data(table);
        @SuppressWarnings("unchecked")
        Set<String> hidden = (Set<String>) table.getClientProperty("hiddenColumns");
        if (table.getColumnCount() != d.columns.size()) table.createDefaultColumnsFromModel();
        int total = 0;
        for (int c = 0; c < d.columns.size(); c++) {
            int w = hfm.stringWidth(Theme.humanize(d.columns.get(c))) + 30;
            for (int r = 0; r < Math.min(200, d.rows.size()); r++) {
                w = Math.max(w, fm.stringWidth(Theme.text(d.rows.get(r)[c])) + 34);
            }
            w = Math.min(w, 280);
            table.getColumnModel().getColumn(c).setPreferredWidth(w);
            if (hidden == null || !hidden.contains(d.columns.get(c))) total += w;
        }
        if (hidden != null) {
            for (int c = table.getColumnModel().getColumnCount() - 1; c >= 0; c--) {
                TableColumn col = table.getColumnModel().getColumn(c);
                if (hidden.contains(d.columns.get(col.getModelIndex()))) table.removeColumn(col);
            }
        }
        Container parent = table.getParent();
        int available = parent != null && parent.getWidth() > 0 ? parent.getWidth() : 900;
        table.setAutoResizeMode(total < available * 1.3 ? JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS : JTable.AUTO_RESIZE_OFF);
    }

    public static String plural(int n, String word) { return n + " " + word + (n == 1 ? "" : "s"); }

    public static TableData data(JTable table) {
        return ((ResultTableModel) table.getModel()).data;
    }

    /** Value of a column (raw DB name) in the selected row, or null when nothing is selected. */
    public static Object selected(JTable table, String column) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return data(table).value(modelRow, column);
    }

    public static Integer selectedInt(JTable table, String column) {
        Object v = selected(table, column);
        return v == null ? null : ((Number) v).intValue();
    }

    /** Selects the row whose raw column equals id; returns true if found. */
    public static boolean selectById(JTable table, String column, int id) {
        TableData d = data(table);
        for (int r = 0; r < table.getRowCount(); r++) {
            Object v = d.value(table.convertRowIndexToModel(r), column);
            if (v instanceof Number n && n.intValue() == id) {
                table.setRowSelectionInterval(r, r);
                table.scrollRectToVisible(table.getCellRect(r, 0, true));
                return true;
            }
        }
        return false;
    }

    public static JTextArea sqlArea() {
        JTextArea a = new JTextArea(6, 80);
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setLineWrap(false);
        ((DefaultCaret) a.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return a;
    }

    public static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.insets = new Insets(4, 0, 4, 10);
        g.anchor = GridBagConstraints.WEST;
        g.fill = x == 0 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        g.weightx = x == 0 ? 0 : 1;
        return g;
    }

    public static JSpinner intSpinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(4);
        return s;
    }

    public static String required(JTextField f, String label) throws BusinessException {
        String v = f.getText().trim();
        if (v.isEmpty()) throw new BusinessException(label + " is required.");
        return v;
    }

    public static int integer(JTextField f, String label, int min, int max) throws BusinessException {
        String v = required(f, label);
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) throw new BusinessException(label + " must be between " + min + " and " + max + ".");
            return n;
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " must be a whole number.");
        }
    }

    public static BigDecimal positiveDecimal(JTextField f, String label) throws BusinessException {
        String v = required(f, label).replace(",", "");
        try {
            BigDecimal d = new BigDecimal(v);
            if (d.signum() <= 0) throw new BusinessException(label + " must be greater than 0.");
            return d;
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " must be a number.");
        }
    }

    /** Shown after the caller finished updating the page behind it. */
    public static void info(Component parent, String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, msg, "Done", JOptionPane.INFORMATION_MESSAGE));
    }

    public static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Read-only table model over a TableData; headers are humanized for display. */
    static final class ResultTableModel extends AbstractTableModel {
        final TableData data;

        ResultTableModel(TableData data) { this.data = data; }

        @Override public int getRowCount() { return data.rows.size(); }
        @Override public int getColumnCount() { return data.columns.size(); }
        @Override public String getColumnName(int c) { return Theme.humanize(data.columns.get(c)); }
        @Override public Object getValueAt(int r, int c) { return data.rows.get(r)[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override public Class<?> getColumnClass(int c) {
            for (Object[] row : data.rows) if (row[c] != null) return row[c].getClass();
            return Object.class;
        }
    }
}
