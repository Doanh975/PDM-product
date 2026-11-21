package db;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the SQL statements executed for the current UI action so each tab can show
 * them in its "Underlying SQL Statement" box. Parameters are always bound through
 * PreparedStatement; the values are only listed here for display.
 */
public final class SqlTrace {

    private static final List<String> LINES = new ArrayList<>();

    private SqlTrace() {}

    public static synchronized void clear() { LINES.clear(); }

    public static synchronized void note(String text) { LINES.add(text); }

    public static synchronized void record(String sql, Object... params) {
        StringBuilder sb = new StringBuilder(format(sql.strip()));
        if (!sb.toString().endsWith(";")) sb.append(';');
        if (params != null && params.length > 0) {
            sb.append("\n-- bound parameters (?): ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                Object p = params[i];
                sb.append(p == null ? "NULL" : (p instanceof Number ? p.toString() : "'" + p + "'"));
            }
        }
        LINES.add(sb.toString());
    }

    public static synchronized String dump() { return String.join("\n\n", LINES); }

    /** Puts main clauses of a one-line statement on their own lines (display only). */
    static String format(String sql) {
        if (sql.contains("\n")) return sql;
        return sql.replaceAll(" (FROM|LEFT JOIN|JOIN|WHERE|GROUP BY|HAVING|ORDER BY|VALUES|SET) ", "\n$1 ")
                  .replaceAll(" AND ", "\n  AND ");
    }
}
