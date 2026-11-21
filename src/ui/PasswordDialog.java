package ui;

import auth.AuthService;
import auth.UserSession;
import db.BusinessException;
import db.Jdbc;
import db.SqlErrorHandler;
import db.TableData;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * Change password. Everyone can change their own; ADMIN can also set the password of
 * any account (stored as a real bcrypt hash).
 */
public class PasswordDialog extends JDialog {

    private final UserSession session;
    private final JComboBox<String> account = new JComboBox<>();
    private final JPasswordField current = new JPasswordField(18);
    private final JPasswordField next = new JPasswordField(18);
    private final JPasswordField confirm = new JPasswordField(18);

    public PasswordDialog(Window owner, UserSession session) {
        super(owner, "Change password", ModalityType.APPLICATION_MODAL);
        this.session = session;

        account.addItem(session.username);
        if (session.canSetOtherPasswords()) {
            try {
                TableData t = Jdbc.query("SELECT Username FROM UserAccount ORDER BY Username");
                for (Object[] r : t.rows) if (!session.username.equals(r[0])) account.addItem((String) r[0]);
            } catch (Exception ex) {
                SqlErrorHandler.show(owner, "Load accounts", ex);
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        form.add(new JLabel("Account"), UiUtil.gbc(0, 0));
        form.add(account, UiUtil.gbc(1, 0));
        JLabel currentLabel = new JLabel("Current password");
        form.add(currentLabel, UiUtil.gbc(0, 1));
        form.add(current, UiUtil.gbc(1, 1));
        form.add(new JLabel("New password (min 6)"), UiUtil.gbc(0, 2));
        form.add(next, UiUtil.gbc(1, 2));
        form.add(new JLabel("Confirm new password"), UiUtil.gbc(0, 3));
        form.add(confirm, UiUtil.gbc(1, 3));
        account.setEnabled(session.canSetOtherPasswords());
        account.addActionListener(e -> {
            boolean own = session.username.equals(account.getSelectedItem());
            current.setEnabled(own);
            currentLabel.setEnabled(own);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        buttons.add(save);
        buttons.add(cancel);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);

        save.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        try {
            if (!Arrays.equals(next.getPassword(), confirm.getPassword())) {
                throw new BusinessException("The new passwords do not match.");
            }
            String target = (String) account.getSelectedItem();
            if (session.username.equals(target)) {
                AuthService.changeOwnPassword(session, current.getPassword(), next.getPassword());
            } else {
                AuthService.setPassword(session, target, next.getPassword());
            }
            UiUtil.info(this, "Password updated for " + target + ".");
            dispose();
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Change password", ex);
        }
    }
}
