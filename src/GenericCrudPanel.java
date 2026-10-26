import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Insert / Update / Delete / Search / Display screen for ANY table. Columns and PKs are read from Oracle. */
public class GenericCrudPanel extends JPanel {
    private final String tableName;
    private final List<String> cols = new ArrayList<>();
    private final List<Integer> types = new ArrayList<>();
    private final List<String> pks = new ArrayList<>();
    private final List<JTextField> fields = new ArrayList<>();
    private final JPanel form = new JPanel(new GridLayout(0, 4, 8, 4));
    private final JTable table = new JTable();
    private final JLabel status = new JLabel(" ");
    private boolean loaded = false;

    public GenericCrudPanel(String tableName) {
        this.tableName = tableName;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (String n : new String[]{"Insert", "Update", "Delete", "Search", "Display All", "Clear"}) {
            JButton b = new JButton(n);
            b.addActionListener(e -> action(n));
            btns.add(b);
        }
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(form, BorderLayout.CENTER);
        top.add(btns, BorderLayout.SOUTH);

        table.setDefaultEditor(Object.class, null);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int r = table.getSelectedRow();
            if (r < 0) return;
            for (int i = 0; i < fields.size() && i < table.getColumnCount(); i++)
                fields.get(i).setText(fmt(table.getValueAt(r, i)));
        });

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        // load lazily the first time the tab is shown
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && !loaded) {
                loaded = true;
                init();
            }
        });
    }

    private void init() {
        try {
            Connection c = DBConnection.get();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM " + q(tableName) + " WHERE 1=0")) {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    cols.add(md.getColumnName(i));
                    types.add(md.getColumnType(i));
                }
            }
            try (ResultSet rs = c.getMetaData().getPrimaryKeys(null, c.getSchema(), tableName)) {
                while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
            }
            for (String col : cols) {
                form.add(new JLabel(col + (pks.contains(col) ? "  (PK)" : "")));
                JTextField f = new JTextField();
                fields.add(f);
                form.add(f);
            }
            // pad so the 4-column grid stays aligned
            if (cols.size() % 2 == 1) { form.add(new JLabel()); form.add(new JLabel()); }
            form.revalidate();
            display();
        } catch (Exception ex) {
            error(ex);
        }
    }

    // ---------- actions ----------
    private void action(String a) {
        if (!loaded || cols.isEmpty()) return;
        try {
            Connection c = DBConnection.get();
            switch (a) {
                case "Insert" -> {
                    StringBuilder cl = new StringBuilder(), ph = new StringBuilder();
                    for (int i = 0; i < cols.size(); i++) {
                        cl.append(i > 0 ? "," : "").append(q(cols.get(i)));
                        ph.append(i > 0 ? ",?" : "?");
                    }
                    String sql = "INSERT INTO " + q(tableName) + " (" + cl + ") VALUES (" + ph + ")";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        for (int i = 0; i < cols.size(); i++) bind(ps, i + 1, fields.get(i).getText(), types.get(i));
                        ps.executeUpdate();
                    }
                    done(sql, "1 row inserted.");
                }
                case "Update" -> {
                    if (!needPk()) return;
                    List<Integer> setIdx = new ArrayList<>();
                    StringBuilder set = new StringBuilder();
                    for (int i = 0; i < cols.size(); i++) {
                        if (pks.contains(cols.get(i))) continue;
                        set.append(set.length() > 0 ? ", " : "").append(q(cols.get(i))).append("=?");
                        setIdx.add(i);
                    }
                    if (setIdx.isEmpty()) { info("All columns are part of the key - nothing to update."); return; }
                    String sql = "UPDATE " + q(tableName) + " SET " + set + " WHERE " + pkWhere();
                    int n;
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        int p = 1;
                        for (int i : setIdx) bind(ps, p++, fields.get(i).getText(), types.get(i));
                        bindPk(ps, p);
                        n = ps.executeUpdate();
                    }
                    done(sql, n + " row(s) updated.");
                }
                case "Delete" -> {
                    if (!needPk()) return;
                    String sql = "DELETE FROM " + q(tableName) + " WHERE " + pkWhere();
                    int n;
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        bindPk(ps, 1);
                        n = ps.executeUpdate();
                    }
                    done(sql, n + " row(s) deleted.");
                }
                case "Search" -> {
                    StringBuilder w = new StringBuilder();
                    List<String> params = new ArrayList<>();
                    for (int i = 0; i < cols.size(); i++) {
                        String t = fields.get(i).getText().trim();
                        if (t.isEmpty()) continue;
                        w.append(w.length() == 0 ? " WHERE " : " AND ")
                         .append("UPPER(TO_CHAR(").append(q(cols.get(i))).append(")) LIKE ?");
                        params.add("%" + t.toUpperCase() + "%");
                    }
                    String sql = "SELECT * FROM " + q(tableName) + w + " ORDER BY 1";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i));
                        try (ResultSet rs = ps.executeQuery()) { table.setModel(DBConnection.toModel(rs)); }
                    }
                    status.setText("SEARCH: " + sql + "   -> " + table.getRowCount() + " row(s)");
                }
                case "Display All" -> display();
                case "Clear" -> fields.forEach(f -> f.setText(""));
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Invalid value (numbers: 12, dates: YYYY-MM-DD):\n" + ex.getMessage(),
                    "Input error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void display() {
        try (Statement s = DBConnection.get().createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM " + q(tableName) + " ORDER BY 1")) {
            table.setModel(DBConnection.toModel(rs));
            status.setText("SELECT * FROM " + tableName + "   -> " + table.getRowCount() + " row(s)");
        } catch (Exception ex) {
            error(ex);
        }
    }

    // ---------- helpers ----------
    private static String q(String id) { return "\"" + id + "\""; }

    private String pkWhere() {
        StringBuilder sb = new StringBuilder();
        for (String pk : pks) sb.append(sb.length() > 0 ? " AND " : "").append(q(pk)).append("=?");
        return sb.toString();
    }

    private void bindPk(PreparedStatement ps, int start) throws SQLException {
        for (String pk : pks) {
            int i = cols.indexOf(pk);
            bind(ps, start++, fields.get(i).getText(), types.get(i));
        }
    }

    private boolean needPk() {
        if (pks.isEmpty()) {
            info("This table has no PRIMARY KEY constraint in Oracle, so Update/Delete are disabled.\n"
               + "Add it with ALTER TABLE " + tableName + " ADD PRIMARY KEY (...);");
            return false;
        }
        for (String pk : pks)
            if (fields.get(cols.indexOf(pk)).getText().trim().isEmpty()) {
                info("Fill in the key column(s) (or click a row first): " + pks);
                return false;
            }
        return true;
    }

    private static void bind(PreparedStatement ps, int idx, String text, int type) throws SQLException {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) { ps.setNull(idx, type); return; }
        switch (type) {
            case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                if (s.length() <= 10) ps.setDate(idx, Date.valueOf(s));
                else ps.setTimestamp(idx, Timestamp.valueOf(s));
            }
            case Types.NUMERIC, Types.DECIMAL, Types.INTEGER, Types.BIGINT, Types.SMALLINT,
                 Types.TINYINT, Types.FLOAT, Types.DOUBLE, Types.REAL -> ps.setBigDecimal(idx, new BigDecimal(s));
            default -> ps.setString(idx, s);
        }
    }

    private static String fmt(Object v) {
        if (v == null) return "";
        String s = v.toString();
        return s.endsWith(" 00:00:00.0") ? s.substring(0, 10) : s;
    }

    private void done(String sql, String msg) {
        status.setText(msg + "   [" + sql + "]");
        display();
    }

    private void info(String m) { JOptionPane.showMessageDialog(this, m); }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Database error", JOptionPane.ERROR_MESSAGE);
    }
}
