package db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads structure from the live database instead of hard-coding it, so the app keeps
 * working when the team changes ENUM values or adds tables:
 *   - ENUM option lists (Sex, EmploymentStatus, LeaveType, ...) come from information_schema
 *   - the Browse tab's table allowlist and ORDER BY keys come from DatabaseMetaData
 */
public final class SchemaInfo {

    private static final Map<String, List<String>> ENUM_CACHE = new HashMap<>();
    private static Map<String, List<String>> tableCache;

    private SchemaInfo() {}

    public static synchronized void clearCache() {
        ENUM_CACHE.clear();
        tableCache = null;
    }

    /** Allowed values of an ENUM column, in declaration order. */
    public static synchronized List<String> enumValues(String table, String column) throws SQLException {
        String key = (table + "." + column).toLowerCase();
        List<String> cached = ENUM_CACHE.get(key);
        if (cached != null) return cached;

        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        List<String> values = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) values = parseEnum(rs.getString(1));
            }
        }
        ENUM_CACHE.put(key, values);
        return values;
    }

    static List<String> parseEnum(String columnType) {
        List<String> out = new ArrayList<>();
        if (columnType == null || !columnType.toLowerCase().startsWith("enum(")) return out;
        String body = columnType.substring(5, columnType.length() - 1);
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'') {
                if (inQuote && i + 1 < body.length() && body.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                else if (inQuote) { out.add(cur.toString()); cur.setLength(0); inQuote = false; }
                else inQuote = true;
            } else if (inQuote) {
                cur.append(ch);
            }
        }
        return out;
    }

    /** Table name -> primary-key columns, for every base table and view in the current schema. */
    public static synchronized Map<String, List<String>> tables() throws SQLException {
        if (tableCache != null) return tableCache;
        Map<String, List<String>> map = new LinkedHashMap<>();
        try (Connection c = DBConnection.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            String catalog = c.getCatalog();
            List<String> names = new ArrayList<>();
            try (ResultSet rs = md.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) names.add(rs.getString("TABLE_NAME"));
            }
            for (String t : names) {
                List<String> pk = new ArrayList<>();
                Map<Short, String> ordered = new TreeMap<>();
                try (ResultSet rs = md.getPrimaryKeys(catalog, null, t)) {
                    while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
                }
                pk.addAll(ordered.values());
                map.put(t, pk);
            }
        }
        tableCache = map;
        return map;
    }
}
