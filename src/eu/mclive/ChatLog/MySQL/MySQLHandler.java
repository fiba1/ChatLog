package eu.mclive.ChatLog.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;


import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.ChatColor;

import org.bukkit.entity.Player;

import eu.mclive.ChatLog.ChatLog;

public class MySQLHandler {
    private ChatLog plugin;
    private MySQL sql;

    private static final Map<String, List<String>> EXPECTED_COLUMNS = new HashMap<>();

    static {
        EXPECTED_COLUMNS.put("messages", Arrays.asList("id", "server", "world", "name", "message", "timestamp"));
        EXPECTED_COLUMNS.put("reportmessages", Arrays.asList("id", "server", "world", "name", "message", "timestamp", "reportid"));
    }

    public MySQLHandler(MySQL mysql, ChatLog plugin) {
        sql = mysql;
        this.plugin = plugin;
        createTablesIfNeeded();
        sanityCheckDatabaseSchema();
    }

    private void createTablesIfNeeded() {
        sql.queryUpdate("CREATE TABLE IF NOT EXISTS messages (" +
                "id INT NOT NULL AUTO_INCREMENT, " +
                "server VARCHAR(100), " +
                "world VARCHAR(100), " +
                "name VARCHAR(100), " +
                "message VARCHAR(400), " +
                "timestamp VARCHAR(50), " +
                "PRIMARY KEY (id)) " +
                "DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci");

        sql.queryUpdate("CREATE TABLE IF NOT EXISTS reportmessages (" +
                "id INT NOT NULL AUTO_INCREMENT, " +
                "server VARCHAR(100), " +
                "world VARCHAR(100), " +
                "name VARCHAR(100), " +
                "message VARCHAR(400), " +
                "timestamp VARCHAR(50), " +
                "reportid TEXT, " +
                "PRIMARY KEY (id)) " +
                "DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci");
    }

    private void sanityCheckDatabaseSchema() {
        Connection conn = sql.getConnection();
        try {
            for (Map.Entry<String, List<String>> entry : EXPECTED_COLUMNS.entrySet()) {
                String tableName = entry.getKey();
                List<String> expectedColumns = entry.getValue();

                for (String column : expectedColumns) {
                    if (!columnExists(conn, tableName, column)) {
                        addColumn(tableName, column);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.RED + "Failed to update database schema: " + e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String query = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private void addColumn(String tableName, String columnName) {
        String alterTableQuery = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " VARCHAR(100);";
        sql.queryUpdate(alterTableQuery);
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.YELLOW + "Column '" + columnName + "' added to table '" + tableName + "'.");
    }

    public void addMessage(String server, Player p, String msg, Long timestamp, String worldName) {
        String name;

        if (plugin.getConfig().getBoolean("use-UUIDs")) {
            UUID uuid = p.getUniqueId();
            name = uuid.toString().replace("-", "");
        } else {
            name = p.getName();
        }

        Connection conn = sql.getConnection();
        try (PreparedStatement st = conn.prepareStatement("INSERT INTO messages (server, world, name, message, timestamp) VALUES (?,?,?,?,?);")) {
            st.setString(1, server);
			st.setString(2, worldName);
            st.setString(3, name);
            st.setString(4, msg);
            st.setLong(5, timestamp);
            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int checkMessage(String server, String p2, Long pluginstart, Long timestamp) {
        String name = null;

        if (plugin.getConfig().getBoolean("use-UUIDs")) {
            name = plugin.UUIDHandler.getUUID(p2);
        } else {
            name = p2;
        }

        Connection conn = sql.getConnection();
        ResultSet rs = null;
        try (PreparedStatement st = conn.prepareStatement("SELECT COUNT(*) AS count FROM messages WHERE server = ? && name = ? && timestamp >= ? && timestamp <= ?;")) {
            st.setString(1, server);
            st.setString(2, name);
            st.setLong(3, pluginstart);
            st.setLong(4, timestamp);
            rs = st.executeQuery();
            rs.next();
            return rs.getInt("count");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setReport(String server, List<String> users, Long pluginstart, Long timestamp, String reportid) {
        Connection conn = sql.getConnection();
        ResultSet rs = null;
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.GREEN + "ReportID: " + ChatColor.YELLOW + reportid);
        for (String user : users) {
            if (plugin.getConfig().getBoolean("use-UUIDs")) {
                user = plugin.UUIDHandler.getUUID(user);
            }
            try (PreparedStatement st = conn.prepareStatement("SELECT * FROM messages WHERE server = ? && name = ? && timestamp >= ? && timestamp <= ?;")) {
                st.setString(1, server);
                st.setString(2, user);
                st.setLong(3, pluginstart);
                st.setLong(4, timestamp);
                rs = st.executeQuery();
                while (rs.next()) {
					String world = rs.getString("world");
                    try (PreparedStatement st2 = conn.prepareStatement("INSERT INTO reportmessages (server, name, world, message, timestamp, reportid) VALUES (?,?,?,?,?,?);")) {
                        st2.setString(1, server);
                        st2.setString(2, user);
                        st2.setString(3, world);
                        st2.setString(4, rs.getString("message"));
                        st2.setLong(5, rs.getLong("timestamp"));
                        st2.setString(6, reportid);
                        st2.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void delete(String server, Long timestamp) {
        Connection conn = sql.getConnection();
        try (PreparedStatement st = conn.prepareStatement("DELETE FROM messages WHERE server = ? AND timestamp < ? ")) {
            st.setString(1, server);
            st.setLong(2, timestamp);
            int rows = st.executeUpdate();
            if (rows > 0) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.YELLOW + "Cleanup Deleted " + ChatColor.AQUA + rows + ChatColor.GREEN + "messages!");
                Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.GREEN + "Cleanup complete.");
            } else {
                Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.YELLOW + "Cleanup had no messages to delete.");
                Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_GREEN + "[ChatLog] " + ChatColor.GREEN + "Cleanup complete.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
