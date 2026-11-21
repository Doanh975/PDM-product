package dao;

import db.Jdbc;
import db.TableData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Combo-box contents, loaded from the database. */
public final class LookupDao {

    private LookupDao() {}

    public static List<Item> departments() throws SQLException {
        return items(Jdbc.query("SELECT DepartmentID, DepartmentName FROM Department ORDER BY DepartmentName"));
    }

    /** Only positions the department actually offers (DepartmentPosition). */
    public static List<Item> positionsOf(int departmentId) throws SQLException {
        return items(Jdbc.query(
                "SELECT p.PositionID, p.PositionName FROM DepartmentPosition dp "
                        + "JOIN `Position` p ON dp.PositionID = p.PositionID "
                        + "WHERE dp.DepartmentID = ? ORDER BY p.PositionName", departmentId));
    }

    public static List<Item> employees() throws SQLException {
        TableData t = Jdbc.query("SELECT EmployeeID, CONCAT('#', EmployeeID, ' - ', FullName) FROM Employee ORDER BY EmployeeID");
        return items(t);
    }

    private static List<Item> items(TableData t) {
        List<Item> out = new ArrayList<>();
        for (Object[] r : t.rows) out.add(new Item(((Number) r[0]).intValue(), String.valueOf(r[1])));
        return out;
    }
}
