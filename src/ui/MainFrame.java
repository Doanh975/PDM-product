package ui;

import auth.UserSession;
import db.DBConnection;
import db.SchemaInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Main window: sidebar navigation with the pages allowed for the signed-in role (see UserSession). */
public class MainFrame extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
    private final Set<Component> loaded = new HashSet<>();
    private final Map<String, Integer> pageIndex = new HashMap<>();
    private Runnable onLogout = () -> {};

    public MainFrame(UserSession session) {
        super("Attendance & Shift Scheduling");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.putClientProperty("JTabbedPane.tabAlignment", SwingConstants.LEADING);
        tabs.putClientProperty("JTabbedPane.tabWidthMode", "equal");
        tabs.putClientProperty("JTabbedPane.minimumTabWidth", 210);
        tabs.putClientProperty("JTabbedPane.tabInsets", new Insets(0, 22, 0, 16));
        tabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
        tabs.putClientProperty("JTabbedPane.leadingComponent", brand());
        tabs.putClientProperty("JTabbedPane.trailingComponent", userBox(session));
        tabs.setFont(tabs.getFont().deriveFont(tabs.getFont().getSize2D() + 1f));

        if (session.isEmployee()) {
            MasterDetailPanel mine = new MasterDetailPanel(session);
            LeaveRequestCrudPanel myLeave = new LeaveRequestCrudPanel(session);
            addPage("Home", "Home", new EmployeeHomePanel(session, (page, view) -> {
                Integer idx = pageIndex.get(page);
                if (idx != null) tabs.setSelectedIndex(idx);
            }));
            addPage("Schedules", "My Shifts", mine);
            addPage("Leave", "My Leave Requests", myLeave);
        } else {
            boolean mgr = session.isManager();
            MasterDetailPanel schedules = new MasterDetailPanel(session);
            LeaveRequestCrudPanel leave = new LeaveRequestCrudPanel(session);
            ReportPanel reports = new ReportPanel();
            TopicQueriesPanel insights = new TopicQueriesPanel();
            addPage("Overview", mgr ? "Team Overview" : "Overview", new OverviewPanel(session, (page, view) -> {
                Integer idx = pageIndex.get(page);
                if (idx == null) return;
                tabs.setSelectedIndex(idx);
                if (page.equals("Leave") && view == 0) leave.showPendingOnly();
                if (page.equals("Reports")) reports.show(view);
                if (page.equals("Insights")) insights.show(view);
            }));
            addPage("Employees", mgr ? "My Team" : "Employees", new SearchPanel(session));
            addPage("Schedules", mgr ? "Team Schedules" : "Schedules & Attendance", schedules);
            if (session.canManageEmployees()) addPage("Manage", "Manage Employees", new EmployeeCrudPanel());
            addPage("Leave", mgr ? "Team Leave Requests" : "Leave Requests", leave);
            if (session.canSeeReports()) {
                addPage("Reports", "Reports", reports);
                addPage("Insights", "Insights (Q1-Q5)", insights);
            }
            if (session.canBrowseData()) addPage("Data", "Data Browser", new BrowsePanel());
        }
        tabs.addChangeListener(e -> loadSelectedTab(false));

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem reload = new JMenuItem("Reload this page");
        JMenuItem password = new JMenuItem("Change password...");
        JMenuItem logout = new JMenuItem("Sign out");
        JMenuItem exit = new JMenuItem("Exit");
        file.add(reload);
        file.add(password);
        file.addSeparator();
        file.add(logout);
        file.add(exit);
        JMenu view = new JMenu("View");
        JCheckBoxMenuItem showSql = new JCheckBoxMenuItem("Show SQL statements", Theme.isSqlVisible());
        view.add(showSql);
        bar.add(file);
        bar.add(view);
        setJMenuBar(bar);

        reload.addActionListener(e -> { SchemaInfo.clearCache(); loadSelectedTab(true); });
        password.addActionListener(e -> new PasswordDialog(this, session).setVisible(true));
        logout.addActionListener(e -> signOut());
        exit.addActionListener(e -> System.exit(0));
        showSql.addActionListener(e -> Theme.setSqlVisible(showSql.isSelected()));
        view.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) { showSql.setSelected(Theme.isSqlVisible()); }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });

        getContentPane().add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1360, 840));
        setMinimumSize(new Dimension(1100, 680));
        pack();
        setLocationRelativeTo(null);
        loadSelectedTab(false);
    }

    private void addPage(String key, String title, JComponent page) {
        pageIndex.put(key, tabs.getTabCount());
        tabs.addTab(title, page);
    }

    private JComponent brand() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 16));
        JLabel name = new JLabel("Attendance");
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 6f));
        name.setForeground(Theme.ACCENT);
        JLabel sub = Theme.muted("& Shift Scheduling");
        p.add(name);
        p.add(sub);
        return p;
    }

    private JComponent userBox(UserSession s) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(BorderFactory.createEmptyBorder(12, 22, 18, 16));
        JLabel name = new JLabel(s.fullName);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        String role = s.roleLabel();
        inner.add(name);
        inner.add(Theme.muted(role + " · " + s.username));
        if (s.placeholderPassword) {
            JLabel demo = new JLabel("Demo login");
            demo.setToolTipText("This account has no real password yet. Set one in File > Change password.");
            demo.setForeground(new Color(0xB45309));
            demo.setFont(demo.getFont().deriveFont(demo.getFont().getSize2D() - 1f));
            inner.add(demo);
        }
        inner.add(Box.createVerticalStrut(8));
        JButton out = new JButton("Sign out");
        out.addActionListener(e -> signOut());
        inner.add(out);
        inner.add(Box.createVerticalStrut(10));
        String url = DBConnection.url();
        String db = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        db = db.replace("jdbc:mysql://", "");
        JLabel dbl = Theme.muted(db);
        dbl.setFont(dbl.getFont().deriveFont(dbl.getFont().getSize2D() - 2f));
        dbl.setToolTipText(url);
        inner.add(dbl);
        p.add(inner, BorderLayout.SOUTH);
        return p;
    }

    private void signOut() {
        dispose();
        onLogout.run();
    }

    public void setOnLogout(Runnable r) { this.onLogout = r; }

    public JTabbedPane getTabs() { return tabs; }

    /** Pages load the first time they are shown; "Reload this page" forces it. */
    private void loadSelectedTab(boolean force) {
        Component c = tabs.getSelectedComponent();
        if (c instanceof Refreshable r && (force || loaded.add(c))) r.refresh();
    }
}
