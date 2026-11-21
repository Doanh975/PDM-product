import auth.AuthService;
import auth.UserSession;
import db.DBConnection;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import ui.LoginDialog;
import ui.MainFrame;
import ui.ReportPanel;
import ui.Theme;
import ui.TopicQueriesPanel;
import ui.UiUtil;

/**
 * Drives the real GUI (clicks buttons, opens dialogs) and captures the screen, for checking
 * the UI and for the report.  Usage: DemoShots out-dir   (needs a display, e.g. Xvfb)
 */
public class DemoShots {

    static File out;
    static Robot robot;
    static int n = 0;
    static Rectangle area = new Rectangle(0, 0, 1360, 840);

    public static void main(String[] args) throws Exception {
        out = new File(args[0]);
        out.mkdirs();
        DBConnection.load();
        SwingUtilities.invokeAndWait(Theme::install);
        robot = new Robot();

        // ---- login
        LoginDialog[] ld = new LoginDialog[1];
        SwingUtilities.invokeLater(() -> { ld[0] = new LoginDialog(null); ld[0].setLocation(400, 180); ld[0].setVisible(true); });
        Thread.sleep(900);
        shot("login", new Rectangle(380, 160, 520, 520));
        SwingUtilities.invokeAndWait(() -> {
            find(ld[0], JTextField.class, c -> !(c instanceof JPasswordField)).setText("thao.ta");
            find(ld[0], JPasswordField.class, c -> true).setText("secret");
            click(ld[0], "Sign in");
        });
        Thread.sleep(600);
        shot("login_disabled_account_error", new Rectangle(380, 160, 520, 520));
        SwingUtilities.invokeAndWait(() -> ld[0].dispose());

        // ---- manager / admin
        UserSession admin = AuthService.login("admin.tri", "x".toCharArray());
        MainFrame f = openFrame(admin);
        String[] names = {"overview", "employees", "schedules", "manage_employees", "leave_requests", "reports", "insights", "data_browser"};
        for (int i = 0; i < names.length; i++) {
            select(f, i);
            shot("page" + (i + 1) + "_" + names[i]);
        }

        // search
        select(f, 1);
        Container search = page(f);
        SwingUtilities.invokeAndWait(() -> {
            find(search, JTextField.class, c -> c.isEditable()).setText("Tran");
            click(search, "Search");
        });
        Thread.sleep(400);
        shot("employees_search_Tran");

        // schedules: employee #6
        select(f, 2);
        selectRow(page(f), "EmployeeID", 6);
        Thread.sleep(500);
        shot("schedules_employee6");

        // manage: select #6, open calendar, delete blocked
        select(f, 3);
        Container crud = page(f);
        selectRow(crud, "EmployeeID", 6);
        Thread.sleep(500);
        shot("manage_edit_employee6");
        SwingUtilities.invokeLater(() -> click(crud, null)); // opens the date picker popup
        Thread.sleep(600);
        shot("manage_date_picker");
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) if (w.getClass().getName().contains("Popup")) w.setVisible(false); });
        robot.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
        robot.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
        Thread.sleep(300);
        SwingUtilities.invokeLater(() -> click(crud, "Delete"));
        Thread.sleep(800);
        shot("manage_delete_confirm");
        clickInDialogs("Yes");
        Thread.sleep(900);
        shot("manage_delete_blocked");
        closeDialogs();

        // leave: approve #3 (commit) and #14 (rollback), with SQL visible
        select(f, 4);
        Container leave = page(f);
        selectRow(leave, "LeaveRequestID", 3);
        Thread.sleep(500);
        shot("leave_selected3");
        SwingUtilities.invokeLater(() -> click(leave, "Approve"));
        Thread.sleep(900);
        shot("leave_approve3_commit");
        closeDialogs();
        SwingUtilities.invokeAndWait(() -> Theme.setSqlVisible(true));
        Thread.sleep(400);
        shot("leave_approve3_sql");
        selectRow(leave, "LeaveRequestID", 14);
        Thread.sleep(400);
        SwingUtilities.invokeLater(() -> click(leave, "Approve"));
        Thread.sleep(900);
        shot("leave_approve14_rollback");
        closeDialogs();
        Thread.sleep(400);
        shot("leave_approve14_sql");
        SwingUtilities.invokeAndWait(() -> Theme.setSqlVisible(false));

        // reports
        select(f, 5);
        for (int r = 1; r < 3; r++) {
            int rr = r;
            SwingUtilities.invokeAndWait(() -> ((ReportPanel) f.getTabs().getSelectedComponent()).show(rr));
            Thread.sleep(400);
            shot("report_" + (r + 1));
        }

        // insights Q2..Q5
        select(f, 6);
        for (int q = 1; q < 5; q++) {
            int qq = q;
            SwingUtilities.invokeAndWait(() -> ((TopicQueriesPanel) f.getTabs().getSelectedComponent()).show(qq));
            Thread.sleep(400);
            shot("insight_Q" + (q + 1));
        }
        SwingUtilities.invokeAndWait(() -> Theme.setSqlVisible(true));
        Thread.sleep(300);
        shot("insight_Q5_with_sql");
        SwingUtilities.invokeAndWait(() -> Theme.setSqlVisible(false));

        // overview after the changes
        select(f, 0);
        SwingUtilities.invokeAndWait(() -> ((ui.Refreshable) f.getTabs().getSelectedComponent()).refresh());
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(f::dispose);

        // ---- MANAGER role (mgr.ngan): only her team
        MainFrame m = openFrame(AuthService.login("mgr.ngan", "x".toCharArray()));
        String[] mNames = {"team_overview", "my_team", "team_schedules", "team_leave", "reports", "insights"};
        for (int i = 0; i < m.getTabs().getTabCount() && i < mNames.length; i++) {
            select(m, i);
            shot("manager_" + mNames[i]);
        }
        select(m, 3);
        selectRow(page(m), "LeaveRequestID", 7);
        Thread.sleep(500);
        shot("manager_team_request7_can_review");
        SwingUtilities.invokeAndWait(m::dispose);

        // ---- USER role (binh.dinh): only themselves
        MainFrame u = openFrame(AuthService.login("binh.dinh", "x".toCharArray()));
        shot("employee_home");
        select(u, 1);
        shot("employee_my_shifts");
        select(u, 2);
        shot("employee_my_leave");
        System.exit(0);
    }

    static MainFrame openFrame(UserSession s) throws Exception {
        MainFrame[] h = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            h[0] = new MainFrame(s);
            h[0].setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            h[0].setBounds(area);
            h[0].setVisible(true);
        });
        Thread.sleep(900);
        return h[0];
    }

    static void select(MainFrame f, int i) throws Exception {
        SwingUtilities.invokeAndWait(() -> f.getTabs().setSelectedIndex(i));
        Thread.sleep(600);
    }

    static Container page(MainFrame f) { return (Container) f.getTabs().getSelectedComponent(); }

    static void shot(String name) throws Exception { shot(name, area); }

    static void shot(String name, Rectangle r) throws Exception {
        robot.waitForIdle();
        var img = robot.createScreenCapture(r);
        File file = new File(out, String.format("%02d_%s.png", ++n, name));
        ImageIO.write(img, "png", file);
        System.out.println("saved " + file.getName());
    }

    /** Clicks the button with this text; text == null clicks the first icon-only button (date picker). */
    static void click(Container root, String text) {
        JButton b = find(root, JButton.class, c -> text == null ? c.getIcon() != null && c.getText().isEmpty() : text.equals(c.getText()));
        if (b == null) throw new IllegalStateException("button not found: " + text);
        b.doClick();
    }

    static void clickInDialogs(String text) {
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    JButton b = find(d, JButton.class, c -> text.equals(c.getText()));
                    if (b != null) { b.doClick(); return; }
                }
            }
        });
    }

    static void closeDialogs() throws Exception {
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && !(d instanceof LoginDialog)) {
                    JButton ok = find(d, JButton.class, c -> "OK".equals(c.getText()));
                    if (ok != null) ok.doClick(); else d.dispose();
                }
            }
        });
        Thread.sleep(500);
    }

    static void selectRow(Container root, String rawColumn, int id) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable t = find(root, JTable.class, c -> UiUtil.data(c).columnIndex(rawColumn) >= 0);
            UiUtil.selectById(t, rawColumn, id);
        });
    }

    @SuppressWarnings("unchecked")
    static <T extends Component> T find(Container root, Class<T> type, java.util.function.Predicate<T> p) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && c.isShowing() && p.test((T) c)) return (T) c;
            if (c instanceof Container k) {
                T r = find(k, type, p);
                if (r != null) return r;
            }
        }
        return null;
    }
}
