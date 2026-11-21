package app;

import auth.UserSession;
import db.DBConnection;
import javax.swing.SwingUtilities;
import ui.LoginDialog;
import ui.MainFrame;
import ui.Theme;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        DBConnection.load();
        // macOS menu bar and app name; has to be set before AWT starts
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Attendance & Shifts");
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            showLogin();
        });
    }

    static void showLogin() {
        LoginDialog login = new LoginDialog(null);
        login.setVisible(true);
        UserSession s = login.getSession();
        if (s == null) System.exit(0);
        MainFrame frame = new MainFrame(s);
        frame.setOnLogout(Main::showLogin);
        frame.setVisible(true);
    }
}
