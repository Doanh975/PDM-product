package ui;

import auth.UserSession;
import dao.Item;
import dao.LeaveDao;
import dao.LookupDao;
import db.BusinessException;
import db.DBConnection;
import db.SchemaInfo;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;

/**
 * Leave requests: create, edit/delete while Pending, and for MANAGER/ADMIN approve (multi-table
 * transaction) or reject. USER accounts only see and edit their own requests.
 */
public class LeaveRequestCrudPanel extends JPanel implements Refreshable {

    private final UserSession session;
    private final JTable table = UiUtil.newTable();
    private final JTable balanceTable = UiUtil.newTable();
    private final JLabel balanceTitle = new JLabel("Leave balance");
    private final JTextArea sql = UiUtil.sqlArea();
    private final JLabel countLabel = Theme.muted("");

    private final JComboBox<String> statusFilter = new JComboBox<>();
    private final JComboBox<Item> employeeFilter = new JComboBox<>();

    private final JLabel formTitle = new JLabel();
    private Integer selectedId;
    private final JComboBox<Item> employeeBox = new JComboBox<>();
    private final JComboBox<String> typeBox = new JComboBox<>();
    private final DatePicker startField = new DatePicker();
    private final DatePicker endField = new DatePicker();
    private final JTextArea reasonArea = new JTextArea(3, 20);
    private final JButton saveBtn = Theme.primary("Submit request");
    private final JButton deleteBtn = Theme.danger("Delete");
    private final JButton approveBtn = Theme.success("Approve");
    private final JButton rejectBtn = Theme.danger("Reject");
    private final JLabel reviewHint = Theme.muted(" ");
    private boolean filling;

    public LeaveRequestCrudPanel(UserSession session) {
        super(new BorderLayout());
        this.session = session;
        if (!session.isEmployee()) UiUtil.hideColumns(table, "EmployeeID");
        else UiUtil.hideColumns(table, "EmployeeID", "Applicant");
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        Theme.placeholder(reasonArea, "Why do you need this leave?");
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, formTitle.getFont().getSize2D() + 3f));
        balanceTitle.setFont(balanceTitle.getFont().deriveFont(Font.BOLD, balanceTitle.getFont().getSize2D() + 1f));

        JPanel toolbar = !session.isEmployee()
                ? Theme.row(statusFilter, employeeFilter, countLabel)
                : Theme.row(statusFilter, countLabel);
        JPanel list = new JPanel(new BorderLayout(0, 8));
        list.setOpaque(false);
        list.add(toolbar, BorderLayout.NORTH);
        list.add(Theme.tableScroll(table), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int y = 0;
        form.add(new JLabel("Employee"), UiUtil.gbc(0, y));
        form.add(employeeBox, UiUtil.gbc(1, y++));
        form.add(new JLabel("Leave type"), UiUtil.gbc(0, y));
        form.add(typeBox, UiUtil.gbc(1, y++));
        form.add(new JLabel("From"), UiUtil.gbc(0, y));
        form.add(startField, UiUtil.gbc(1, y++));
        form.add(new JLabel("To"), UiUtil.gbc(0, y));
        form.add(endField, UiUtil.gbc(1, y++));
        GridBagConstraints rl = UiUtil.gbc(0, y);
        rl.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Reason"), rl);
        JScrollPane reasonScroll = new JScrollPane(reasonArea);
        reasonScroll.setPreferredSize(new Dimension(200, 70));
        form.add(reasonScroll, UiUtil.gbc(1, y++));
        // Only admins file requests on someone else's behalf; everyone else files their own.
        employeeBox.setEnabled(session.isAdmin());

        JButton newBtn = new JButton("New request");
        JPanel crud = new JPanel(new BorderLayout());
        crud.setOpaque(false);
        crud.add(Theme.row(saveBtn, newBtn), BorderLayout.WEST);
        crud.add(Theme.row(deleteBtn), BorderLayout.EAST);

        JPanel formPanel = new JPanel(new BorderLayout(0, 8));
        formPanel.setOpaque(false);
        formPanel.add(formTitle, BorderLayout.NORTH);
        formPanel.add(form, BorderLayout.CENTER);
        formPanel.add(crud, BorderLayout.SOUTH);

        JPanel rightStack = new JPanel(new BorderLayout(0, 12));
        rightStack.setOpaque(false);
        rightStack.add(Theme.card(formPanel), BorderLayout.NORTH);

        if (session.canReviewLeave()) {
            JPanel review = new JPanel(new BorderLayout(0, 6));
            review.setOpaque(false);
            JLabel rt = new JLabel("Review");
            rt.setFont(rt.getFont().deriveFont(Font.BOLD, rt.getFont().getSize2D() + 1f));
            review.add(rt, BorderLayout.NORTH);
            review.add(reviewHint, BorderLayout.CENTER);
            review.add(Theme.row(approveBtn, rejectBtn), BorderLayout.SOUTH);
            approveBtn.setToolTipText("Approve and deduct the days from the leave balance (one transaction)");
            JPanel mid = new JPanel(new BorderLayout(0, 12));
            mid.setOpaque(false);
            mid.add(Theme.card(review), BorderLayout.NORTH);
            mid.add(balanceCard(), BorderLayout.CENTER);
            rightStack.add(mid, BorderLayout.CENTER);
        } else {
            rightStack.add(balanceCard(), BorderLayout.CENTER);
        }
        rightStack.setPreferredSize(new Dimension(430, 400));
        rightStack.setMinimumSize(new Dimension(380, 300));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Theme.card(list), rightStack);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setOpaque(false);

        String title = session.isEmployee() ? "My Leave Requests" : session.isManager() ? "Team Leave Requests" : "Leave Requests";
        String sub = session.isEmployee() ? "Ask for time off and follow the status of your requests."
                : session.isManager() ? "Your own requests and your team's. You can approve or reject your team's requests."
                : "All requests in the company. Approving also deducts the days from the employee's leave balance.";
        add(Theme.page(title, sub, null, split, sql));

        statusFilter.addActionListener(e -> { if (!filling) loadList(); });
        employeeFilter.addActionListener(e -> { if (!filling) loadList(); });
        employeeBox.addActionListener(e -> { if (!filling) loadBalance(); });
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelected(); });
        newBtn.addActionListener(e -> clearForm());
        saveBtn.addActionListener(e -> doSave());
        deleteBtn.addActionListener(e -> doDelete());
        approveBtn.addActionListener(e -> doApprove());
        rejectBtn.addActionListener(e -> doReject());
    }

    private JPanel balanceCard() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(balanceTitle, BorderLayout.NORTH);
        JScrollPane sp = Theme.tableScroll(balanceTable);
        sp.setPreferredSize(new Dimension(300, 120));
        p.add(sp, BorderLayout.CENTER);
        return Theme.card(p);
    }

    /** Edit/delete restriction: admins any request, everyone else only their own. */
    private Integer ownerRestriction() { return session.isAdmin() ? null : session.employeeId; }

    @Override public void refresh() {
        filling = true;
        try {
            Object keepStatus = statusFilter.getSelectedItem();
            statusFilter.removeAllItems();
            statusFilter.addItem("All statuses");
            for (String s : SchemaInfo.enumValues("LeaveRequest", "RequestStatus")) statusFilter.addItem(s);
            if (keepStatus != null) statusFilter.setSelectedItem(keepStatus);

            Object keepType = typeBox.getSelectedItem();
            typeBox.removeAllItems();
            for (String s : SchemaInfo.enumValues("LeaveRequest", "LeaveType")) typeBox.addItem(s);
            if (keepType != null) typeBox.setSelectedItem(keepType);

            employeeBox.removeAllItems();
            Item keepFilter = (Item) employeeFilter.getSelectedItem();
            employeeFilter.removeAllItems();
            employeeFilter.addItem(new Item(null, "All employees"));
            Set<Integer> visible = session.visibleEmployees();
            for (Item i : LookupDao.employees()) {
                if (visible != null && !visible.contains(i.id)) continue;
                employeeFilter.addItem(i);
                if (session.isAdmin()) employeeBox.addItem(i);
            }
            if (!session.isAdmin()) employeeBox.addItem(new Item(session.employeeId, session.fullName));
            if (keepFilter != null) employeeFilter.setSelectedItem(keepFilter);
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load lists", ex);
        } finally {
            filling = false;
        }
        loadList();
        if (selectedId == null) clearForm();
    }

    /** Used by the Overview page: show only pending requests. */
    public void showPendingOnly() {
        statusFilter.setSelectedItem("Pending");
    }

    private void loadList() {
        SqlTrace.clear();
        try {
            String s = (String) statusFilter.getSelectedItem();
            Integer emp = null;
            if (!session.isEmployee()) {
                Item f = (Item) employeeFilter.getSelectedItem();
                emp = f == null ? null : f.id;
            }
            UiUtil.setData(table, LeaveDao.list(emp, s == null || s.startsWith("All") ? null : s, session.visibleEmployees()));
            countLabel.setText(table.getRowCount() == 0 ? "No requests" : UiUtil.plural(table.getRowCount(), "request"));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load leave requests", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }

    private void loadSelected() {
        Integer id = UiUtil.selectedInt(table, "LeaveRequestID");
        if (id == null) return;
        filling = true;
        try {
            selectedId = id;
            String status = String.valueOf(UiUtil.selected(table, "RequestStatus"));
            boolean pending = "Pending".equals(status);
            formTitle.setText("Request #" + id + "  ·  " + status);
            Integer emp = UiUtil.selectedInt(table, "EmployeeID");
            Item applicant = new Item(emp, String.valueOf(UiUtil.selected(table, "Applicant")));
            // Non-admins only have themselves in the list; show the applicant of the selected request.
            if (((DefaultComboBoxModel<Item>) employeeBox.getModel()).getIndexOf(applicant) < 0) {
                employeeBox.addItem(applicant);
            }
            employeeBox.setSelectedItem(applicant);
            typeBox.setSelectedItem(String.valueOf(UiUtil.selected(table, "LeaveType")));
            startField.setDate(LocalDate.parse(String.valueOf(UiUtil.selected(table, "StartDate"))));
            endField.setDate(LocalDate.parse(String.valueOf(UiUtil.selected(table, "EndDate"))));
            Object reason = UiUtil.selected(table, "Reason");
            reasonArea.setText(reason == null ? "" : reason.toString());
            saveBtn.setText("Save changes");
            boolean editable = pending && emp != null && session.canEditRequestOf(emp);
            saveBtn.setEnabled(editable);
            deleteBtn.setEnabled(editable);
            boolean own = emp != null && emp == session.employeeId;
            boolean reviewable = pending && emp != null && session.canReview(emp);
            approveBtn.setEnabled(reviewable);
            rejectBtn.setEnabled(reviewable);
            reviewHint.setText(!pending ? "This request was already " + status.toLowerCase() + "."
                    : own ? "Your own request is reviewed by your manager." : reviewable ? "Waiting for your decision."
                    : "Only this employee's own managers can review it.");
        } finally {
            filling = false;
        }
        loadBalance();
    }

    private void loadBalance() {
        Item emp = (Item) employeeBox.getSelectedItem();
        if (emp == null || emp.id == null) {
            UiUtil.setData(balanceTable, TableData.empty());
            balanceTitle.setText("Leave balance");
            return;
        }
        String keepSql = sql.getText();
        try {
            UiUtil.setData(balanceTable, LeaveDao.balances(emp.id));
            String name = emp.label.contains(" - ") ? emp.label.substring(emp.label.indexOf(" - ") + 3) : emp.label;
            balanceTitle.setText("Leave balance · " + name);
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load leave balance", ex);
        }
        sql.setText(keepSql);
    }

    private void clearForm() {
        filling = true;
        table.clearSelection();
        selectedId = null;
        formTitle.setText("New leave request");
        if (!session.isAdmin()) {
            employeeBox.removeAllItems();
            employeeBox.addItem(new Item(session.employeeId, session.fullName));
        }
        if (employeeBox.getItemCount() > 0) {
            employeeBox.setSelectedItem(new Item(session.employeeId, ""));
            if (employeeBox.getSelectedIndex() < 0) employeeBox.setSelectedIndex(0);
        }
        LocalDate d = DBConnection.referenceDate().plusDays(7);
        startField.setDate(d);
        endField.setDate(d);
        reasonArea.setText("");
        saveBtn.setText("Submit request");
        saveBtn.setEnabled(true);
        deleteBtn.setEnabled(false);
        approveBtn.setEnabled(false);
        rejectBtn.setEnabled(false);
        reviewHint.setText("Select a pending request in the list.");
        filling = false;
        loadBalance();
    }

    private void doSave() {
        SqlTrace.clear();
        try {
            Item emp = (Item) employeeBox.getSelectedItem();
            if (emp == null) throw new BusinessException("Please choose an employee.");
            String type = (String) typeBox.getSelectedItem();
            LocalDate start = startField.getDate("Start date");
            LocalDate end = endField.getDate("End date");
            if (end.isBefore(start)) throw new BusinessException("The end date must be on or after the start date.");
            String reason = reasonArea.getText().trim();
            if (reason.isEmpty()) throw new BusinessException("Please write a short reason.");
            int id;
            if (selectedId == null) {
                int employeeId = session.isAdmin() ? emp.id : session.employeeId;
                id = LeaveDao.insert(employeeId, type, start, end, reason);
                UiUtil.info(this, "Leave request #" + id + " was submitted and is waiting for approval.");
            } else {
                id = selectedId;
                LeaveDao.update(id, ownerRestriction(), type, start, end, reason);
                UiUtil.info(this, "Request #" + id + " was updated.");
            }
            afterChange(id);
        } catch (Exception ex) {
            sql.setText(SqlTrace.dump());
            SqlErrorHandler.show(this, "Save leave request", ex);
        }
    }

    private void doDelete() {
        SqlTrace.clear();
        try {
            if (selectedId == null) throw new BusinessException("Select a request in the list first.");
            int id = selectedId;
            if (!UiUtil.confirm(this, "Delete leave request #" + id + "?")) return;
            LeaveDao.delete(id, ownerRestriction());
            String keep = SqlTrace.dump();
            loadList();
            clearForm();
            sql.setText(keep);
            UiUtil.info(this, "Leave request #" + id + " was deleted.");
        } catch (Exception ex) {
            sql.setText(SqlTrace.dump());
            SqlErrorHandler.show(this, "Delete leave request", ex);
        }
    }

    private void doApprove() {
        SqlTrace.clear();
        Integer id = selectedId;
        try {
            if (id == null) throw new BusinessException("Select a request in the list first.");
            BigDecimal days = LeaveDao.approve(id, session.employeeId, session.reviewableEmployees());
            afterChange(id);
            UiUtil.info(this, "Request #" + id + " was approved.\n" + days.stripTrailingZeros().toPlainString()
                    + " day(s) were deducted from the leave balance.");
        } catch (Exception ex) {
            afterChange(id);
            SqlErrorHandler.show(this, "Approve request (nothing was changed)", ex);
        }
    }

    private void doReject() {
        SqlTrace.clear();
        Integer id = selectedId;
        try {
            if (id == null) throw new BusinessException("Select a request in the list first.");
            if (!UiUtil.confirm(this, "Reject leave request #" + id + "?")) return;
            LeaveDao.reject(id, session.employeeId, session.reviewableEmployees());
            afterChange(id);
            UiUtil.info(this, "Request #" + id + " was rejected.");
        } catch (Exception ex) {
            afterChange(id);
            SqlErrorHandler.show(this, "Reject request", ex);
        }
    }

    /** Reload the list, keep the request selected, and show the SQL of the action (not of the reload). */
    private void afterChange(Integer id) {
        String keep = SqlTrace.dump();
        loadList();
        if (id != null && !UiUtil.selectById(table, "LeaveRequestID", id)) clearForm();
        sql.setText(keep);
    }
}
