import java.sql.*;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;

public class DBConnection {
    // ====== CHANGE THESE THREE ======
    private static final String URL  = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";
    private static final String USER = "dnd";
    private static final String PASS = "DndPass_123";
    // ================================

    private static Connection conn;

    /** One shared connection, so Commit / Rollback buttons (TCL) work across all screens. */
    public static synchronized Connection get() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(URL, USER, PASS);
            conn.setAutoCommit(true);
        }
        return conn;
    }

    public static DefaultTableModel toModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        Vector<String> cols = new Vector<>();
        for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
        Vector<Vector<Object>> rows = new Vector<>();
        while (rs.next()) {
            Vector<Object> r = new Vector<>();
            for (int i = 1; i <= n; i++) r.add(rs.getObject(i));
            rows.add(r);
        }
        return new DefaultTableModel(rows, cols);
    }
}
