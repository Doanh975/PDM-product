package ui;

import db.DBConnection;
import db.ScriptRunner;
import db.SqlErrorHandler;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Edit db.url / db.user / db.password in config.properties, with a "Test connection" button. */
public class DbSettingsDialog extends JDialog {

    private final JTextField url = new JTextField(DBConnection.url(), 45);
    private final JTextField user = new JTextField(DBConnection.user(), 20);
    private final JPasswordField password = new JPasswordField(DBConnection.password(), 20);

    public DbSettingsDialog(Window owner) {
        super(owner, "Database settings", ModalityType.APPLICATION_MODAL);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        form.add(new JLabel("JDBC URL"), UiUtil.gbc(0, 0));
        form.add(url, UiUtil.gbc(1, 0));
        form.add(new JLabel("User"), UiUtil.gbc(0, 1));
        form.add(user, UiUtil.gbc(1, 1));
        form.add(new JLabel("Password"), UiUtil.gbc(0, 2));
        form.add(password, UiUtil.gbc(1, 2));
        form.add(new JLabel("Saved to: " + DBConnection.getConfigPath()), UiUtil.gbc(1, 3));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton install = new JButton("Install database...");
        install.setToolTipText("Create employee_attendance_db and load the sample data (runs the sql/ scripts)");
        JButton test = new JButton("Test connection");
        JButton save = Theme.primary("Save");
        JButton cancel = new JButton("Cancel");
        buttons.add(install);
        buttons.add(test);
        buttons.add(save);
        buttons.add(cancel);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        test.addActionListener(e -> {
            try {
                DBConnection.testConnection(url.getText().trim(), user.getText().trim(), new String(password.getPassword()));
                UiUtil.info(this, "Connection successful. The database is ready.");
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1049 && serverReachable()) {
                    UiUtil.info(this, "MySQL is reachable, but the database is not installed yet.\n"
                            + "Click \"Install database...\" to create it with the sample data.");
                } else {
                    SqlErrorHandler.show(this, "Test connection", ex);
                }
            }
        });
        install.addActionListener(e -> installDatabase(this, url.getText().trim(), user.getText().trim(),
                new String(password.getPassword()), true));
        save.addActionListener(e -> {
            try {
                DBConnection.saveDbSettings(url.getText().trim(), user.getText().trim(), new String(password.getPassword()));
                dispose();
            } catch (Exception ex) {
                SqlErrorHandler.show(this, "Save settings", ex);
            }
        });
        cancel.addActionListener(e -> dispose());
        pack();
        setLocationRelativeTo(owner);
    }

    private boolean serverReachable() {
        try {
            DBConnection.testConnection(ScriptRunner.serverUrl(url.getText().trim()), user.getText().trim(),
                    new String(password.getPassword()));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Saves the settings and runs the SQL scripts. Returns true on success.
     * interactive: ask before replacing an existing database and confirm when done.
     * The sign-in screen passes false: it has already asked and signs in right after.
     */
    public static boolean installDatabase(Component parent, String url, String user, String password, boolean interactive) {
        if (interactive && !UiUtil.confirm(parent, "This creates employee_attendance_db with the team's sample data.\n\n"
                + "If the database already exists it is DELETED and recreated.\nContinue?")) return false;
        Cursor old = parent.getCursor();
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            DBConnection.saveDbSettings(url, user, password);
            ScriptRunner.installDatabase(url, user, password, msg -> System.out.println("[install] " + msg));
            if (interactive) UiUtil.info(parent, "Database installed. You can sign in now (for example admin.tri).");
            return true;
        } catch (Exception ex) {
            SqlErrorHandler.show(parent, "Install database", ex);
            return false;
        } finally {
            parent.setCursor(old);
        }
    }
}
