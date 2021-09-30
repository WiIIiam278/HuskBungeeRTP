package me.william278.huskbungeertp;

import me.william278.huskbungeertp.command.HuskBungeeRtpCommand;
import me.william278.huskbungeertp.command.RtpCommand;
import me.william278.huskbungeertp.config.Group;
import me.william278.huskbungeertp.config.Settings;
import me.william278.huskbungeertp.jedis.RedisMessage;
import me.william278.huskbungeertp.plan.PlanDataManager;
import me.william278.huskbungeertp.jedis.RedisMessenger;
import me.william278.huskbungeertp.mysql.DataHandler;
import me.william278.huskbungeertp.randomtp.processor.AbstractRtp;
import me.william278.huskbungeertp.randomtp.processor.DefaultRtp;
import me.william278.huskbungeertp.randomtp.processor.JakesRtp;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;

public final class HuskBungeeRTP extends JavaPlugin {

    // Metrics ID for bStats integration
    private static final int METRICS_PLUGIN_ID = 12830;

    private static HuskBungeeRTP instance;

    public static HuskBungeeRTP getInstance() {
        return instance;
    }

    private static Settings settings;

    public static Settings getSettings() {
        return settings;
    }

    private void setSettings(Configuration config) {
        settings = new Settings(config);
    }

    public static HashMap<String,Integer> serverPlayerCounts = new HashMap<>();
    public static void updateServerPlayerCounts() {
        for (String server : getSettings().getAllServers()) {
            RedisMessenger.publish(new RedisMessage(server, RedisMessage.RedisMessageType.GET_PLAYER_COUNT,
                    getSettings().getServerId() + "#" + Instant.now().getEpochSecond()));
        }
    }
    public static HashSet<String> getServerIdsWithFewestPlayers(Collection<Group.Server> servers) {
        HashSet<String> lowestIdServers = new HashSet<>();
        long lowestPlayTime = Long.MAX_VALUE;
        for (Group.Server server : servers) {
            if (serverPlayerCounts.containsKey(server.getName())) {
                String serverName = server.getName();
                long playTime = serverPlayerCounts.get(serverName);
                if (playTime < lowestPlayTime) {
                    lowestPlayTime = playTime;
                    lowestIdServers.clear();
                    lowestIdServers.add(serverName);
                } else if (playTime == lowestPlayTime) {
                    lowestIdServers.add(serverName);
                }
            } else {
                HuskBungeeRTP.getInstance().getLogger().warning("A server in a RTP group failed to return play count data.");
            }
        }
        return lowestIdServers;
    }

    private static AbstractRtp abstractRtp;

    public static AbstractRtp getAbstractRtp() {
        return abstractRtp;
    }

    private void setAbstractRtp() {
        if (Bukkit.getPluginManager().getPlugin("JakesRTP") != null) {
            abstractRtp = new JakesRtp();
        } else {
            abstractRtp = new DefaultRtp();
        }
        abstractRtp.initialize();
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    public static void reloadConfigFile() {
        HuskBungeeRTP instance = HuskBungeeRTP.getInstance();
        instance.reloadConfig();
        instance.saveDefaultConfig();
        instance.setSettings(instance.getConfig());
        MessageManager.loadMessages();
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        // Load settings and messages
        reloadConfigFile();

        // Load database
        DataHandler.loadDatabase(getInstance());

        // Set RTP handler
        setAbstractRtp();

        // Register command
        Objects.requireNonNull(getCommand("rtp")).setExecutor(new RtpCommand());
        Objects.requireNonNull(getCommand("rtp")).setTabCompleter(new RtpCommand.RtpTabCompleter());
        Objects.requireNonNull(getCommand("huskbungeertp")).setExecutor(new HuskBungeeRtpCommand());
        Objects.requireNonNull(getCommand("huskbungeertp")).setTabCompleter(new HuskBungeeRtpCommand.HuskBungeeRtpTabCompleter());

        // Register events
        getServer().getPluginManager().registerEvents(new EventListener(), this);

        // Setup plan integration / fetch player counts
        switch (getSettings().getLoadBalancingMethod()) {
            case PLAN -> PlanDataManager.updatePlanPlayTimes();
            case PLAYER_COUNTS -> updateServerPlayerCounts();
        }

        // Jedis subscriber initialisation
        RedisMessenger.subscribe();

        // bStats initialisation
        try {
            Metrics metrics = new Metrics(this, METRICS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("plan_integration", () -> Boolean.toString(PlanDataManager.usePlanIntegration())));
            metrics.addCustomChart(new SimplePie("jakes_rtp", () -> Boolean.toString(abstractRtp instanceof JakesRtp)));
        } catch (Exception e) {
            getLogger().warning("An exception occurred initialising metrics; skipping.");
        }

        // Log to console
        getLogger().info("Successfully enabled HuskBungeeRTP v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Disabled HuskBungeeRTP v" + getDescription().getVersion());
    }
}
