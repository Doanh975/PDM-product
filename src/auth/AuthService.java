package auth;

import db.BusinessException;
import db.DBConnection;
import db.Jdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Login against the UserAccount table.
 *  - Real bcrypt hashes ($2a$/$2b$/$2y$, 60 chars) are verified with jBCrypt.
 *  - Placeholder hashes (as in the sample data) are accepted with any non-empty password
 *    only when auth.allowPlaceholderHash=true in config.properties.
 */
public final class AuthService {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private AuthService() {}

    public static boolean isBcrypt(String hash) {
        return hash != null && BCRYPT.matcher(hash).matches();
    }

    public static UserSession login(String username, char[] password) throws SQLException, BusinessException {
        if (username == null || username.isBlank()) throw new BusinessException("Please enter your username.");
        if (password == null || password.length == 0) throw new BusinessException("Please enter your password.");

        String sql = "SELECT ua.UserID, ua.EmployeeID, ua.Username, ua.PasswordHash, ua.Role, ua.AccountStatus, "
                + "e.FullName, e.EmploymentStatus "
                + "FROM UserAccount ua JOIN Employee e ON ua.EmployeeID = e.EmployeeID "
                + "WHERE ua.Username = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BusinessException("Wrong username or password.");

                String status = rs.getString("AccountStatus");
                if (!"Active".equalsIgnoreCase(status)) {
                    throw new BusinessException("This account is " + status + ". Please contact an administrator.");
                }
                if ("Terminated".equalsIgnoreCase(rs.getString("EmploymentStatus"))) {
                    throw new BusinessException("This employee is terminated and can no longer sign in.");
                }

                String hash = rs.getString("PasswordHash");
                boolean placeholder = !isBcrypt(hash);
                if (!checkPassword(hash, password)) throw new BusinessException("Wrong username or password.");

                String role = rs.getString("Role");
                int employeeId = rs.getInt("EmployeeID");
                Set<Integer> team = "MANAGER".equalsIgnoreCase(role) ? Scope.teamOf(c, employeeId) : null;
                UserSession s = new UserSession(rs.getInt("UserID"), employeeId,
                        rs.getString("Username"), rs.getString("FullName"), role, placeholder, team);

                try (PreparedStatement up = c.prepareStatement("UPDATE UserAccount SET LastLoginAt = NOW() WHERE UserID = ?")) {
                    up.setInt(1, s.userId);
                    up.executeUpdate();
                }
                return s;
            }
        }
    }

    private static boolean checkPassword(String hash, char[] password) throws BusinessException {
        String pw = new String(password);
        if (isBcrypt(hash)) {
            // jBCrypt 0.4 understands $2a$; $2b$/$2y$ use the same algorithm.
            String normalized = "$2a$" + hash.substring(4);
            try {
                return BCrypt.checkpw(pw, normalized);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (DBConnection.getBool("auth.allowPlaceholderHash", false)) return true;
        throw new BusinessException("This account has no real password yet. Ask an administrator to set one "
                + "(or enable auth.allowPlaceholderHash in config.properties for demos).");
    }

    /** Changes the caller's own password after verifying the current one. */
    public static void changeOwnPassword(UserSession s, char[] current, char[] next) throws SQLException, BusinessException {
        validateNew(next);
        String hash;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT PasswordHash FROM UserAccount WHERE UserID = ?")) {
            ps.setInt(1, s.userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BusinessException("Account not found.");
                hash = rs.getString(1);
            }
        }
        if (!checkPassword(hash, current)) throw new BusinessException("Current password is wrong.");
        storeHash(s.username, next);
    }

    /** Sets the password of any account; only allowed for admins. */
    public static void setPassword(UserSession by, String username, char[] next) throws SQLException, BusinessException {
        if (!by.canSetOtherPasswords() && !by.username.equals(username)) {
            throw new BusinessException("Only an administrator can set another user's password.");
        }
        storeHash(username, next);
    }

    private static void storeHash(String username, char[] next) throws SQLException, BusinessException {
        validateNew(next);
        String newHash = BCrypt.hashpw(new String(next), BCrypt.gensalt(12));
        int n = Jdbc.update("UPDATE UserAccount SET PasswordHash = ? WHERE Username = ?", newHash, username);
        if (n != 1) throw new BusinessException("Account '" + username + "' not found.");
    }

    private static void validateNew(char[] next) throws BusinessException {
        if (next == null || next.length < 6) throw new BusinessException("The new password must have at least 6 characters.");
    }
}
