package ui;

import auth.AuthService;
import auth.UserSession;
import db.DBConnection;
import db.SqlErrorHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Sign in with a UserAccount (username + password). Errors are shown inline. */
public class LoginDialog extends JDialog {

    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JLabel error = new JLabel(" ");
    private final JLabel dbLabel = Theme.muted("");
    private UserSession session;

    public LoginDialog(Window owner) {
        super(owner, "Sign in", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Theme.placeholder(username, "Username");
        Theme.placeholder(password, "Password");
        password.putClientProperty("JPasswordField.showRevealButton", true);
        error.setForeground(Theme.DANGER);

        JLabel title = new JLabel("Welcome back");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 9f));
        JLabel sub = Theme.muted("Sign in to Attendance & Shift Scheduling");
        JButton login = Theme.primary("Sign in");
        JButton settings = Theme.subtle("Database settings");

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(34, 40, 22, 40));
        add(form, title);
        form.add(Box.createVerticalStrut(4));
        add(form, sub);
        form.add(Box.createVerticalStrut(24));
        add(form, label("Username"));
        form.add(Box.createVerticalStrut(4));
        add(form, username);
        form.add(Box.createVerticalStrut(12));
        add(form, label("Password"));
        form.add(Box.createVerticalStrut(4));
        add(form, password);
        form.add(Box.createVerticalStrut(8));
        add(form, error);
        form.add(Box.createVerticalStrut(10));
        add(form, login);
        form.add(Box.createVerticalStrut(18));

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.add(dbLabel, BorderLayout.WEST);
        foot.add(settings, BorderLayout.EAST);
        add(form, foot);

        for (JComponent c : new JComponent[]{username, password, login}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            c.setPreferredSize(new Dimension(320, 38));
        }
        getContentPane().add(form, BorderLayout.CENTER);
        getRootPane().setDefaultButton(login);
        updateDbLabel();

        login.addActionListener(e -> doLogin());
        settings.addActionListener(e -> {
            new DbSettingsDialog(this).setVisible(true);
            updateDbLabel();
        });
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static void add(JPanel p, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private void updateDbLabel() {
        String url = DBConnection.url();
        String shortUrl = (url.contains("?") ? url.substring(0, url.indexOf('?')) : url).replace("jdbc:mysql://", "");
        dbLabel.setText(shortUrl);
        dbLabel.setToolTipText(url + "  (user: " + DBConnection.user() + ")");
        dbLabel.setFont(dbLabel.getFont().deriveFont(dbLabel.getFont().getSize2D() - 1f));
    }

    private void doLogin() {
        try {
            session = AuthService.login(username.getText(), password.getPassword());
            dispose();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1049 && UiUtil.confirm(this, "The database employee_attendance_db does not exist on this MySQL server yet.\n\n"
                    + "Install it now with the team's sample data?")) {
                if (DbSettingsDialog.installDatabase(this, DBConnection.url(), DBConnection.user(), DBConnection.password(), false)) {
                    error.setText(" ");
                    return;
                }
            }
            showError(ex);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        password.setText("");
        error.setText(SqlErrorHandler.message(ex));
        pack();
    }

    /** The signed-in user, or null if the dialog was closed. */
    public UserSession getSession() { return session; }
}
