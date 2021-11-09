package me.william278.huskbungeertp;

import me.william278.huskbungeertp.command.HuskBungeeRtpCommand;
import me.william278.huskbungeertp.command.RtpCommand;
import me.william278.huskbungeertp.config.Group;
import me.william278.huskbungeertp.config.Settings;
import me.william278.huskbungeertp.jedis.RedisMessenger;
import me.william278.huskbungeertp.mysql.DataHandler;
import me.william278.huskbungeertp.plan.PlanHook;
import me.william278.huskbungeertp.plan.PlanQueryAccessor;
import me.william278.huskbungeertp.randomtp.processor.AbstractRtp;
import me.william278.huskbungeertp.randomtp.processor.DefaultRtp;
import me.william278.huskbungeertp.randomtp.processor.JakesRtp;
import me.william278.huskbungeertp.randomtp.processor.LeafRtp;
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

    private static long lastPlanFetch;
    private static boolean usePlan = false;
    private static HashMap<String, Long> planPlayTimes = new HashMap<>();

    public static HashMap<String,Long> getPlanPlayTimes() {
        return planPlayTimes;
    }

    public static boolean usePlanIntegration() {
        return usePlan;
    }

    public static HashSet<String> getServerIdsWithLowestPlayTime(Collection<Group.Server> servers) {
        HashSet<String> lowestIdServers = new HashSet<>();
        long lowestPlayTime = Long.MAX_VALUE;
        for (Group.Server server : servers) {
            if (planPlayTimes.containsKey(server.getName())) {
                String serverName = server.getName();
                long playTime = planPlayTimes.get(serverName);
                if (playTime < lowestPlayTime) {
                    lowestPlayTime = playTime;
                    lowestIdServers.clear();
                    lowestIdServers.add(serverName);
                } else if (playTime == lowestPlayTime) {
                    lowestIdServers.add(serverName);
                }
            } else {
                getInstance().getLogger().warning("A server in a RTP group failed to return Plan playtime data.");
            }
        }
        return lowestIdServers;
    }

    public static void updatePlanPlayTimes() {
        try {
            Optional<PlanQueryAccessor> planHook = new PlanHook().hookIntoPlan();
            planHook.ifPresent(hook -> {
                usePlan = true;
                planPlayTimes = hook.getPlayTimes();
                getInstance().getLogger().info("Fetched latest playtime data from Plan");
            });
            lastPlanFetch = Instant.now().getEpochSecond();
        } catch (NoClassDefFoundError ignored) {
        }
    }

    public static void fetchPlanIfNeeded() {
        if ((lastPlanFetch + (getSettings().getUpdatePlanDataMinutes() * 60L)) <= Instant.now().getEpochSecond()) {
            updatePlanPlayTimes();
        }
    }

    private static AbstractRtp abstractRtp;

    public static AbstractRtp getAbstractRtp() {
        return abstractRtp;
    }

    private void setAbstractRtp() {
        if (Bukkit.getPluginManager().isPluginEnabled("JakesRTP")) {
            abstractRtp = new JakesRtp();
        } else if(Bukkit.getPluginManager().isPluginEnabled("RTP")){
            abstractRtp = new LeafRtp();
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

        // Use plan integration if enabled
        if (getSettings().isUsePlan()) {
            updatePlanPlayTimes();
        }

        // Jedis subscriber initialisation
        RedisMessenger.subscribe();

        // bStats initialisation
        try {
            Metrics metrics = new Metrics(this, METRICS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("plan_integration", () -> Boolean.toString(usePlanIntegration())));
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
