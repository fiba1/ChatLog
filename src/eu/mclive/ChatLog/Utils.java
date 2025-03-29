package eu.mclive.ChatLog;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Utils {
    private ChatLog plugin;

    public Utils(ChatLog plugin) {
        this.plugin = plugin;
    }

    public void logMessage(Player p, String worldName, String msg) {
        plugin.addMessage(p, ChatColor.stripColor(msg));
        plugin.incrementLoggedMessages();
    }
}