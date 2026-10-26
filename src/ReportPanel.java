import java.awt.*;
import java.sql.*;
import javax.swing.*;

/** Pre-written SELECT queries (simple, join, nested, functions). The SQL box is editable. */
public class ReportPanel extends JPanel {
    static final String[][] Q = {
        {"1. Simple - players sorted by name",
         "SELECT PLAYER_ID, PLAYER_NAME, EMAIL, LOCATION FROM PLAYER ORDER BY PLAYER_NAME"},

        {"2. Simple + function - high-XP heroes (UPPER)",
         "SELECT Ch_ID, UPPER(Name) AS Hero, Race, Class, XP FROM Characters WHERE XP > 1000 ORDER BY XP DESC"},

        {"3. Join - campaign with its Dungeon Master",
         "SELECT c.C_ID, c.Name AS Campaign, c.Difficulty, d.Name AS Dungeon_Master " +
         "FROM Campaign c JOIN Dungeon_Master d ON c.DM_ID = d.DM_ID ORDER BY c.Name"},

        {"4. Complex - Player > Character > Campaign > DM (4 tables)",
         "SELECT p.Name AS Player, ch.Name AS Hero, ch.Class, c.Name AS Campaign, d.Name AS DM " +
         "FROM Player p JOIN Characters ch ON ch.P_ID = p.P_ID " +
         "JOIN Campaign c ON ch.C_ID = c.C_ID " +
         "JOIN Dungeon_Master d ON c.DM_ID = d.DM_ID ORDER BY c.Name, p.Name"},

        {"5. Aggregate functions - stats per class",
         "SELECT Class, COUNT(*) AS Total, ROUND(AVG(HP),1) AS Avg_HP, MAX(XP) AS Max_XP, SUM(XP) AS Total_XP " +
         "FROM Characters GROUP BY Class ORDER BY Total DESC"},

        {"6. Nested - characters with above-average XP",
         "SELECT Name, Class, XP FROM Characters WHERE XP > (SELECT AVG(XP) FROM Characters) ORDER BY XP DESC"},

        {"7. Nested IN - campaigns that have quests",
         "SELECT C_ID, Name, Difficulty FROM Campaign WHERE C_ID IN (SELECT C_ID FROM Quest)"},

        {"8. Outer join + GROUP BY - encounters per location",
         "SELECT l.Name AS Location, l.Region, COUNT(e.E_ID) AS Encounters " +
         "FROM Locations l LEFT JOIN Encounter e ON e.L_ID = l.L_ID GROUP BY l.Name, l.Region ORDER BY Encounters DESC"},

        {"9. Join + HAVING - monsters used in encounters",
         "SELECT m.Name, COUNT(me.E_ID) AS Encounters, SUM(me.Quantity) AS Total_Spawned " +
         "FROM Monster m JOIN Monster_Encounter me ON m.M_ID = me.M_ID " +
         "GROUP BY m.Name HAVING COUNT(me.E_ID) >= 1 ORDER BY Encounters DESC"},

        {"10. Functions - item value by rarity",
         "SELECT Rarity, COUNT(*) AS Items, ROUND(AVG(Gold_Value),2) AS Avg_Value, MAX(Gold_Value) AS Max_Value " +
         "FROM Item GROUP BY Rarity ORDER BY Avg_Value DESC"},

        {"11. View - campaign overview (DM dashboard)",
         "SELECT * FROM v_campaign_overview"},

        {"12. View + function - quest board with progress %",
         "SELECT Quest, Campaign, Difficulty, Party, Party_Status, Progress_Pct FROM v_quest_board ORDER BY Campaign, Q_ID"},

        {"13. View - hero inventory value (player sheet)",
         "SELECT Hero, COUNT(*) AS Item_Types, SUM(Total_Value) AS Wealth_GP, SUM(Total_Weight) AS Weight_Lb " +
         "FROM v_character_inventory GROUP BY Hero ORDER BY Wealth_GP DESC"},

        {"14. Correlated - top-XP hero in each campaign",
         "SELECT c.Name AS Campaign, ch.Name AS Hero, ch.XP FROM Characters ch JOIN Campaign c ON c.C_ID = ch.C_ID " +
         "WHERE ch.XP = (SELECT MAX(x.XP) FROM Characters x WHERE x.C_ID = ch.C_ID)"},

        {"15. Set operation - everyone at the table (UNION)",
         "SELECT Name, 'Player' AS Kind FROM Player UNION SELECT Name, 'Dungeon Master' FROM Dungeon_Master ORDER BY Kind, Name"},

        {"16. Attendance - sessions attended per player",
         "SELECT p.Name AS Player, COUNT(*) AS Sessions, SUM(CASE WHEN a.Attended = 'Y' THEN 1 ELSE 0 END) AS Attended " +
         "FROM Session_Attendance a JOIN Player p ON p.P_ID = a.P_ID GROUP BY p.Name ORDER BY Attended DESC"},

        {"17. Faction standing - average reputation",
         "SELECT f.Name AS Faction, f.Alignment, COUNT(*) AS Heroes, ROUND(AVG(cf.Reputation),1) AS Avg_Reputation " +
         "FROM Faction f JOIN Character_Faction cf ON cf.Faction_ID = f.Faction_ID GROUP BY f.Name, f.Alignment ORDER BY Avg_Reputation DESC"}
    };

    private final JTable table = new JTable();
    private final JTextArea sqlArea = new JTextArea(6, 80);
    private final JComboBox<String> box = new JComboBox<>();

    public ReportPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String[] q : Q) box.addItem(q[0]);
        box.addActionListener(e -> sqlArea.setText(Q[box.getSelectedIndex()][1]));

        sqlArea.setText(Q[0][1]);
        sqlArea.setLineWrap(true);
        sqlArea.setWrapStyleWord(true);
        sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        table.setDefaultEditor(Object.class, null);

        JButton run = new JButton("Run Query");
        run.addActionListener(e -> run());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Query:"));
        row.add(box);
        row.add(run);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(row, BorderLayout.NORTH);
        top.add(new JScrollPane(sqlArea), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void run() {
        String sql = sqlArea.getText().trim().replaceAll(";\\s*$", "");
        try (Statement s = DBConnection.get().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            table.setModel(DBConnection.toModel(rs));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
