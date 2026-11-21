package db;

import java.util.ArrayList;
import java.util.List;

/** A detached query result: column labels plus rows, safe to use after the connection closes. */
public final class TableData {

    public final List<String> columns;
    public final List<Object[]> rows;

    public TableData(List<String> columns, List<Object[]> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public static TableData empty() { return new TableData(new ArrayList<>(), new ArrayList<>()); }

    public int rowCount() { return rows.size(); }

    public int columnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) if (columns.get(i).equalsIgnoreCase(name)) return i;
        return -1;
    }

    public Object value(int row, String column) {
        int idx = columnIndex(column);
        return idx < 0 ? null : rows.get(row)[idx];
    }
}
