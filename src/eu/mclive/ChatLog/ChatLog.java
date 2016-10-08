package eu.mclive.ChatLog;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import eu.mclive.ChatLog.bstats.Metrics_McLive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

import eu.mclive.ChatLog.Commands.Chatreport;
import eu.mclive.ChatLog.MySQL.MySQL;
import eu.mclive.ChatLog.MySQL.MySQLHandler;

public class ChatLog extends JavaPlugin implements Listener {

    public UUIDHandler UUIDHandler;
    public Logger logger = getLogger();
    public MySQL sql;
    public Messages messages;
    public MySQLHandler sqlHandler;
    public Long pluginstart = null;
    private eu.mclive.ChatLog.bstats.Metrics bstats;

    /**
     * Issued ChatLogs since last submit.
     */
    private int issuedChatLogs = 0;

    /**
     * Logged Messages since last submit.
     */
    private int loggedMessages = 0;

    public void onEnable() {
        try {
            logger.info("Loading MySQL ...");
            sql = new MySQL(this);
            sqlHandler = new MySQLHandler(sql, this);
            startRefresh();
            logger.info("MySQL successfully loaded.");
        } catch (Exception e1) {
            logger.warning("Failled to load MySQL: " + e1.toString());
        }

        messages = new Messages(this);
        UUIDHandler = new UUIDHandler(this);

        getConfig().options().copyDefaults(true);
        saveConfig();

        if (getConfig().getBoolean("use-AsyncChatEvent")) {
            getServer().getPluginManager().registerEvents(new AsyncChatListener(this), this);
        } else {
            logger.info("Using NON-Async ChatEvent.");
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        }

        Date now = new Date();
        pluginstart = now.getTime() / 1000L;

        boolean metrics = getConfig().getBoolean("metrics");

        if (metrics) {
            logger.info("Loading Metrics ...");
            try {
                Metrics me = new Metrics(this);
                me.start();
                this.startBstats(new eu.mclive.ChatLog.bstats.Metrics(this));
                logger.info("Metrics successfully loaded.");
            } catch (IOException e) {
                logger.warning("Failled to load Metrics.");
            }
        }

        cleanup();

        this.registerCommands();

        logger.info("Plugin successfully started.");
    }

    public void onDisable() {
        //bstats.getTimer().cancel();
        logger.info("Plugin successfully stopped.");
    }

    private void registerCommands() {
        getCommand("chatreport").setExecutor(new Chatreport(this));
    }

    private void startBstats(eu.mclive.ChatLog.bstats.Metrics bstats) {
        bstats.addCustomChart(new eu.mclive.ChatLog.bstats.Metrics.SingleLineChart("issued_chatlogs") {
            @Override
            public int getValue() {
                int value = issuedChatLogs;
                issuedChatLogs = 0;
                return value;
            }
        });

        bstats.addCustomChart(new eu.mclive.ChatLog.bstats.Metrics.SingleLineChart("logged_messages") {
            @Override
            public int getValue() {
                int value = loggedMessages;
                loggedMessages = 0;
                return value;
            }
        });
    }

    public void incrementIssuedChatLogs() {
        issuedChatLogs++;
    }

    public void incrementLoggedMessages() {
        loggedMessages++;
    }

    public void addMessage(final Player p, final String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            public void run() {
                Date now = new Date();
                Long timestamp = now.getTime() / 1000;
                String server = getConfig().getString("server");
                String bypassChar = getConfig().getString("bypass-with-beginning-char");
                String bypassPermission = getConfig().getString("bypass-with-permission");
                //System.out.println(server + p + msg + timestamp);
                if (bypassChar.isEmpty() || (msg.startsWith(bypassChar) && !p.hasPermission(bypassPermission)) || !msg.startsWith(bypassChar)) {
                    sqlHandler.addMessage(server, p, msg, timestamp);
                }
            }
        });
    }

    public void cleanup() {
        final String server = getConfig().getString("server");
        boolean doCleanup = getConfig().getBoolean("Cleanup.enabled");
        int since = getConfig().getInt("Cleanup.since");

        if (doCleanup) {
            logger.info("Doing Cleanup...");
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            cal.add(Calendar.DATE, -since); // subtract days from config

            final Long timestamp = cal.getTimeInMillis() / 1000L;

            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                public void run() {
                    sqlHandler.delete(server, timestamp);
                }
            });

        } else {
            logger.info("Skipping Cleanup because it is disabled.");
        }
    }

    public void startRefresh() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            public void run() {
                try {
                    sql.refreshConnect();
                } catch (Exception e) {
                    logger.warning("Failed to reload MySQL: " + e.toString());
                }
            }
        }, 20L * 10, 20L * 1800);
    }
}
