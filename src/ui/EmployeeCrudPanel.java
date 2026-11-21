package ui;

import dao.EmployeeDao;
import dao.EmployeeForm;
import dao.Item;
import dao.LookupDao;
import db.BusinessException;
import db.DBConnection;
import db.SchemaInfo;
import db.SqlErrorHandler;
import db.SqlTrace;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

/**
 * Manage employees. Saving writes Employee and its FullTime/PartTime row in one transaction
 * (total + disjoint ISA). Delete asks for confirmation; blocked deletes are explained in plain words.
 */
public class EmployeeCrudPanel extends JPanel implements Refreshable {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SSN = Pattern.compile("^\\d{9}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+ ()-]{6,20}$");

    private final JTable table = UiUtil.newTable();
    private final JTextField quickFilter = new JTextField(18);
    private final JTextArea sql = UiUtil.sqlArea();
    private final JLabel formTitle = new JLabel();

    private final JTextField idField = new JTextField(8);
    private final JTextField nameField = new JTextField(20);
    private final JTextField ssnField = new JTextField(20);
    private final JComboBox<String> sexBox = new JComboBox<>();
    private final JTextField phoneField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final DatePicker hireField = new DatePicker();
    private final JComboBox<String> statusBox = new JComboBox<>();
    private final JComboBox<Item> deptBox = new JComboBox<>();
    private final JComboBox<Item> posBox = new JComboBox<>();
    private final JComboBox<Item> mgrBox = new JComboBox<>();
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"Full-time", "Part-time"});
    private final JLabel payLabel = new JLabel();
    private final JTextField payField = new JTextField(20);
    private final JLabel hoursLabel = new JLabel();
    private final JTextField hoursField = new JTextField(20);
    private final JButton deleteBtn = Theme.danger("Delete");
    private boolean filling;

    public EmployeeCrudPanel() {
        super(new BorderLayout());
        idField.setEditable(false);
        // The list only needs the essentials; full details are in the form on the right.
        UiUtil.hideColumns(table, "Ssn", "Sex", "Email", "Phone", "HireDate", "Supervisor");
        Theme.placeholder(nameField, "e.g. Nguyen Van An");
        Theme.placeholder(ssnField, "9 digits");
        Theme.placeholder(phoneField, "e.g. 0901234567");
        Theme.placeholder(emailField, "name@company.vn");
        Theme.placeholder(quickFilter, "Filter list...");
        quickFilter.putClientProperty("JTextField.showClearButton", true);
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, formTitle.getFont().getSize2D() + 3f));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int y = 0;
        y = section(form, y, "Personal information");
        y = row(form, y, "Full name", nameField);
        y = row(form, y, "SSN", ssnField);
        y = row(form, y, "Sex", sexBox);
        y = row(form, y, "Phone", phoneField);
        y = row(form, y, "Email", emailField);
        y = section(form, y, "Job");
        y = row(form, y, "Department", deptBox);
        y = row(form, y, "Position", posBox);
        y = row(form, y, "Supervisor", mgrBox);
        y = row(form, y, "Hire date", hireField);
        y = row(form, y, "Status", statusBox);
        y = section(form, y, "Contract & pay");
        y = row(form, y, "Contract type", typeBox);
        form.add(payLabel, UiUtil.gbc(0, y));
        form.add(payField, UiUtil.gbc(1, y++));
        form.add(hoursLabel, UiUtil.gbc(0, y));
        form.add(hoursField, UiUtil.gbc(1, y++));
        GridBagConstraints filler = UiUtil.gbc(0, y);
        filler.weighty = 1;
        form.add(new JLabel(), filler);

        JButton newBtn = new JButton("New employee");
        JButton saveBtn = Theme.primary("Save");
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(Theme.row(saveBtn, newBtn), BorderLayout.WEST);
        buttons.add(Theme.row(deleteBtn), BorderLayout.EAST);

        JPanel formCard = new JPanel(new BorderLayout(0, 8));
        formCard.setOpaque(false);
        formCard.add(formTitle, BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formCard.add(formScroll, BorderLayout.CENTER);
        formCard.add(buttons, BorderLayout.SOUTH);

        JPanel listHead = new JPanel(new BorderLayout());
        listHead.setOpaque(false);
        JLabel listTitle = new JLabel("All employees");
        listTitle.setFont(listTitle.getFont().deriveFont(Font.BOLD, listTitle.getFont().getSize2D() + 1f));
        listHead.add(listTitle, BorderLayout.WEST);
        listHead.add(quickFilter, BorderLayout.EAST);
        JPanel list = new JPanel(new BorderLayout(0, 8));
        list.setOpaque(false);
        list.add(listHead, BorderLayout.NORTH);
        list.add(Theme.tableScroll(table), BorderLayout.CENTER);

        JPanel left = Theme.card(list);
        JPanel right = Theme.card(formCard);
        right.setPreferredSize(new Dimension(440, 400));
        right.setMinimumSize(new Dimension(380, 300));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setOpaque(false);

        add(Theme.page("Manage Employees", "Add a new employee, or select one in the list to edit or delete.", null, split, sql));

        typeBox.addActionListener(e -> { if (!filling) { updateTypeLabels(); defaultHours(); } });
        deptBox.addActionListener(e -> { if (!filling) loadPositions(null); });
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelected(); });
        newBtn.addActionListener(e -> clearForm());
        saveBtn.addActionListener(e -> doSave());
        deleteBtn.addActionListener(e -> doDelete());
        quickFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        updateTypeLabels();
    }

    private static int section(JPanel p, int y, String title) {
        GridBagConstraints g = UiUtil.gbc(0, y);
        g.gridwidth = 2;
        p.add(Theme.sectionLabel(title), g);
        return y + 1;
    }

    private static int row(JPanel p, int y, String label, Component c) {
        p.add(new JLabel(label), UiUtil.gbc(0, y));
        p.add(c, UiUtil.gbc(1, y));
        return y + 1;
    }

    private void applyFilter() {
        if (!(table.getRowSorter() instanceof TableRowSorter<?> sorter)) return;
        String q = quickFilter.getText().trim();
        sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(q)));
    }

    private boolean isFullTime() { return typeBox.getSelectedIndex() == 0; }

    private void updateTypeLabels() {
        payLabel.setText(isFullTime() ? "Monthly salary (VND)" : "Hourly rate (VND)");
        hoursLabel.setText(isFullTime() ? "Hours per month" : "Hours per week (1-40)");
    }

    private void defaultHours() {
        String h = hoursField.getText();
        if (h.isBlank() || "160".equals(h) || "20".equals(h)) hoursField.setText(isFullTime() ? "160" : "20");
    }

    @Override public void refresh() {
        SqlTrace.clear();
        filling = true;
        try {
            fillCombo(sexBox, SchemaInfo.enumValues("Employee", "Sex"));
            fillCombo(statusBox, SchemaInfo.enumValues("Employee", "EmploymentStatus"));
            Item keepDept = (Item) deptBox.getSelectedItem();
            deptBox.removeAllItems();
            for (Item i : LookupDao.departments()) deptBox.addItem(i);
            if (keepDept != null) deptBox.setSelectedItem(keepDept);
            mgrBox.removeAllItems();
            mgrBox.addItem(new Item(null, "No supervisor (top management)"));
            for (Item i : LookupDao.employees()) mgrBox.addItem(i);
            filling = false;
            loadPositions(null);
            UiUtil.setData(table, EmployeeDao.search(null, null, null));
            applyFilter();
            if (idField.getText().isBlank()) clearForm();
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load employees", ex);
        } finally {
            filling = false;
            sql.setText(SqlTrace.dump());
        }
    }

    private static void fillCombo(JComboBox<String> box, List<String> values) {
        Object keep = box.getSelectedItem();
        box.removeAllItems();
        for (String v : values) box.addItem(v);
        if (keep != null) box.setSelectedItem(keep);
    }

    private void loadPositions(Integer select) {
        Item d = (Item) deptBox.getSelectedItem();
        posBox.removeAllItems();
        if (d == null) return;
        try {
            for (Item i : LookupDao.positionsOf(d.id)) posBox.addItem(i);
            if (select != null) posBox.setSelectedItem(new Item(select, ""));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load positions", ex);
        }
    }

    private void loadSelected() {
        Integer id = UiUtil.selectedInt(table, "EmployeeID");
        if (id == null) return;
        try {
            EmployeeForm f = EmployeeDao.load(id);
            filling = true;
            idField.setText(String.valueOf(f.employeeId));
            formTitle.setText("Edit employee #" + f.employeeId);
            nameField.setText(f.fullName);
            ssnField.setText(f.ssn);
            sexBox.setSelectedItem(f.sex);
            phoneField.setText(f.phone);
            emailField.setText(f.email);
            hireField.setDate(f.hireDate);
            statusBox.setSelectedItem(f.employmentStatus);
            deptBox.setSelectedItem(new Item(f.departmentId, ""));
            loadPositions(f.positionId);
            mgrBox.setSelectedItem(new Item(f.managerId, ""));
            typeBox.setSelectedIndex(EmployeeForm.PART_TIME.equals(f.type) ? 1 : 0);
            payField.setText(f.pay == null ? "" : new DecimalFormat("#,##0.##").format(f.pay));
            hoursField.setText(f.type == null ? "" : String.valueOf(f.hours));
            updateTypeLabels();
            deleteBtn.setEnabled(true);
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load employee", ex);
        } finally {
            filling = false;
        }
    }

    private void clearForm() {
        filling = true;
        table.clearSelection();
        idField.setText("");
        formTitle.setText("New employee");
        nameField.setText("");
        ssnField.setText("");
        phoneField.setText("");
        emailField.setText("");
        hireField.setDate(DBConnection.referenceDate());
        if (statusBox.getItemCount() > 0) statusBox.setSelectedItem("Active");
        if (mgrBox.getItemCount() > 0) mgrBox.setSelectedIndex(0);
        typeBox.setSelectedIndex(0);
        payField.setText("");
        hoursField.setText("160");
        deleteBtn.setEnabled(false);
        filling = false;
        updateTypeLabels();
    }

    private EmployeeForm readForm() throws BusinessException {
        EmployeeForm f = new EmployeeForm();
        String id = idField.getText().trim();
        f.employeeId = id.isEmpty() ? null : Integer.valueOf(id);
        f.fullName = UiUtil.required(nameField, "Full name");
        f.ssn = UiUtil.required(ssnField, "SSN");
        if (!SSN.matcher(f.ssn).matches()) throw new BusinessException("SSN must be exactly 9 digits.");
        f.sex = (String) sexBox.getSelectedItem();
        f.phone = UiUtil.required(phoneField, "Phone");
        if (!PHONE.matcher(f.phone).matches()) throw new BusinessException("Phone may contain only digits, spaces, + ( ) - (6 to 20 characters).");
        f.email = UiUtil.required(emailField, "Email");
        if (!EMAIL.matcher(f.email).matches()) throw new BusinessException("Email address is not valid.");
        f.hireDate = hireField.getDate("Hire date");
        f.employmentStatus = (String) statusBox.getSelectedItem();
        Item d = (Item) deptBox.getSelectedItem();
        Item p = (Item) posBox.getSelectedItem();
        if (d == null) throw new BusinessException("Please choose a department.");
        if (p == null) throw new BusinessException("This department has no positions yet. Choose another department.");
        f.departmentId = d.id;
        f.positionId = p.id;
        Item m = (Item) mgrBox.getSelectedItem();
        f.managerId = m == null ? null : m.id;
        if (f.managerId != null && f.managerId.equals(f.employeeId)) throw new BusinessException("An employee cannot supervise themselves.");
        f.type = isFullTime() ? EmployeeForm.FULL_TIME : EmployeeForm.PART_TIME;
        f.pay = UiUtil.positiveDecimal(payField, isFullTime() ? "Monthly salary" : "Hourly rate");
        f.hours = isFullTime() ? UiUtil.integer(hoursField, "Hours per month", 1, 744)
                               : UiUtil.integer(hoursField, "Hours per week", 1, 40);
        return f;
    }

    /** Insert when the form is new, update when an employee is selected. */
    private void doSave() {
        SqlTrace.clear();
        try {
            EmployeeForm f = readForm();
            int id;
            if (f.employeeId == null) {
                id = EmployeeDao.insert(f);
                UiUtil.info(this, f.fullName + " was added as employee #" + id + ".");
            } else {
                EmployeeDao.update(f);
                id = f.employeeId;
                UiUtil.info(this, "Changes to " + f.fullName + " were saved.");
            }
            reloadAndSelect(id);
        } catch (Exception ex) {
            sql.setText(SqlTrace.dump());
            SqlErrorHandler.show(this, "Save employee", ex);
        }
    }

    private void doDelete() {
        SqlTrace.clear();
        try {
            if (idField.getText().isBlank()) throw new BusinessException("Select an employee in the list first.");
            int id = Integer.parseInt(idField.getText().trim());
            if (!UiUtil.confirm(this, "Delete " + nameField.getText() + " (#" + id + ")?\n\n"
                    + "Their contract details, user account and leave balances will be deleted too.\nThis cannot be undone.")) return;
            EmployeeDao.delete(id);
            String keepSql = SqlTrace.dump();
            refresh();
            clearForm();
            sql.setText(keepSql);
            UiUtil.info(this, "Employee #" + id + " was deleted.");
        } catch (Exception ex) {
            sql.setText(SqlTrace.dump());
            SqlErrorHandler.show(this, "Delete employee", ex);
        }
    }

    private void reloadAndSelect(int id) {
        String keepSql = SqlTrace.dump();
        refresh();
        UiUtil.selectById(table, "EmployeeID", id);
        sql.setText(keepSql);
    }
}
