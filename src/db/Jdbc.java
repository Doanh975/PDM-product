package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Small helpers around PreparedStatement. Every value is bound with '?' placeholders. */
public final class Jdbc {

    private Jdbc() {}

    public static void bind(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Runs a SELECT on an existing connection. */
    public static TableData query(Connection c, String sql, Object... params) throws SQLException {
        SqlTrace.record(sql, params);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return toTable(rs);
            }
        }
    }

    /** Opens a connection, runs a SELECT, closes everything. */
    public static TableData query(String sql, Object... params) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            return query(c, sql, params);
        }
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        SqlTrace.record(sql, params);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            int n = ps.executeUpdate();
            SqlTrace.note("-- " + n + " row(s) affected");
            return n;
        }
    }

    public static int update(String sql, Object... params) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            return update(c, sql, params);
        }
    }

    /** INSERT that returns the AUTO_INCREMENT key. */
    public static int insertReturningKey(Connection c, String sql, Object... params) throws SQLException {
        SqlTrace.record(sql, params);
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public static TableData toTable(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> cols = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] r = new Object[n];
            for (int i = 1; i <= n; i++) r[i - 1] = rs.getObject(i);
            rows.add(r);
        }
        return new TableData(cols, rows);
    }
}
