package com.server.stattracker.compat;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class ServerScheduler {

    private final JavaPlugin plugin;
    private final boolean folia;

    private Object globalScheduler;

    public boolean isFolia() { return folia; }

    public ServerScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        if (folia) {
            try {
                this.globalScheduler = plugin.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            } catch (Exception e) {
                // Fallback to BukkitScheduler
            }
        }
        plugin.getLogger().info("调度器: " + (folia ? "Folia GlobalRegionScheduler" : "BukkitScheduler"));
    }

    public void runDelayed(long delayTicks, Runnable task) {
        if (folia) {
            runFoliaDelayed(delayTicks, task);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public Object runAtFixedRate(long initialDelayTicks, long periodTicks, Runnable task) {
        if (folia) {
            return runFoliaFixedRate(initialDelayTicks, periodTicks, task);
        } else {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task,
                initialDelayTicks, periodTicks);
        }
    }

    private void runFoliaDelayed(long delayTicks, Runnable task) {
        try {
            Method m = globalScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            m.invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) scheduled -> task.run(), delayTicks);
        } catch (Exception e) {
            // Fallback
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private Object runFoliaFixedRate(long initialDelay, long period, Runnable task) {
        try {
            Method m = globalScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            return m.invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) scheduled -> task.run(), initialDelay, period);
        } catch (Exception e) {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task, initialDelay, period);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
