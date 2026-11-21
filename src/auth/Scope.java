package auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Row-level scoping helpers: who is in a manager's team, and SQL "IN (...)" filters. */
public final class Scope {

    private Scope() {}

    /** All direct and indirect reports of a manager (recursive Supervises relationship). */
    public static final String TEAM_SQL =
            "WITH RECURSIVE team AS (\n"
          + "    SELECT EmployeeID FROM Employee WHERE ManagerID = ?\n"
          + "    UNION\n"
          + "    SELECT e.EmployeeID FROM Employee e JOIN team t ON e.ManagerID = t.EmployeeID\n"
          + ")\n"
          + "SELECT EmployeeID FROM team";

    public static Set<Integer> teamOf(Connection c, int managerId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(TEAM_SQL)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        ids.remove(managerId);
        return ids;
    }

    /**
     * Appends " AND column IN (?, ?, ...)" and its parameters. ids == null means no restriction;
     * an empty set matches nothing.
     */
    public static void appendIn(StringBuilder sql, String column, Set<Integer> ids, List<Object> params) {
        if (ids == null) return;
        if (ids.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND ").append(column).append(" IN (");
        int i = 0;
        for (Integer id : ids) {
            sql.append(i++ == 0 ? "?" : ", ?");
            params.add(id);
        }
        sql.append(')');
    }
}
