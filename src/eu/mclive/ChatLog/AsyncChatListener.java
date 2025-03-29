package eu.mclive.ChatLog;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class AsyncChatListener implements Listener {
    private ChatLog plugin;

    public AsyncChatListener(ChatLog plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerChat(final AsyncPlayerChatEvent e) {
        if (e.isCancelled()) {
            return;
        }
		String worldName = e.getPlayer().getWorld().getName();
        plugin.getUtils().logMessage(e.getPlayer(), worldName, e.getMessage());
    }
}
