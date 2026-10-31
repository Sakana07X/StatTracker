package com.server.stattracker.compat;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.logging.Logger;

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
                // Folia 上回退 Bukkit 调度器必然抛 UnsupportedOperationException，直接报错而非静默降级
                throw new IllegalStateException("检测到 Folia，但获取 GlobalRegionScheduler 失败", e);
            }
        }
        plugin.getLogger().info("调度器: " + (folia ? "Folia GlobalRegionScheduler" : "BukkitScheduler"));
    }

    /** 可取消的调度任务句柄，Bukkit/Folia 双平台通用。 */
    public static final class TaskHandle {
        private final Object handle;   // BukkitTask 或 Folia ScheduledTask 实例
        private final boolean folia;
        private final Logger logger;

        TaskHandle(Object handle, boolean folia, Logger logger) {
            this.handle = handle;
            this.folia = folia;
            this.logger = logger;
        }

        /** 取消任务；重复取消安全，失败仅告警不抛出。 */
        public void cancel() {
            if (handle == null) {
                return;
            }
            try {
                if (folia) {
                    // Folia ScheduledTask 只用反射调 cancel()，不引入编译期依赖
                    Method m = handle.getClass().getMethod("cancel");
                    m.invoke(handle);
                } else {
                    ((BukkitTask) handle).cancel();
                }
            } catch (Exception e) {
                logger.warning("取消调度任务失败: " + e);
            }
        }
    }

    public TaskHandle runDelayed(long delayTicks, Runnable task) {
        if (folia) {
            return runFoliaDelayed(delayTicks, task);
        }
        return new TaskHandle(
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks),
            false, plugin.getLogger());
    }

    public TaskHandle runAtFixedRate(long initialDelayTicks, long periodTicks, Runnable task) {
        if (folia) {
            return runFoliaFixedRate(initialDelayTicks, periodTicks, task);
        }
        return new TaskHandle(
            plugin.getServer().getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks),
            false, plugin.getLogger());
    }

    private TaskHandle runFoliaDelayed(long delayTicks, Runnable task) {
        try {
            Method m = globalScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            Object scheduledTask = m.invoke(globalScheduler, plugin,
                (java.util.function.Consumer<?>) scheduled -> task.run(), delayTicks);
            return new TaskHandle(scheduledTask, true, plugin.getLogger());
        } catch (Exception e) {
            // Folia 上回退 Bukkit 调度器必然抛 UnsupportedOperationException，直接报错而非静默降级
            throw new IllegalStateException("Folia GlobalRegionScheduler 调度失败（runDelayed）", e);
        }
    }

    private TaskHandle runFoliaFixedRate(long initialDelay, long period, Runnable task) {
        try {
            Method m = globalScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            Object scheduledTask = m.invoke(globalScheduler, plugin,
                (java.util.function.Consumer<?>) scheduled -> task.run(), initialDelay, period);
            return new TaskHandle(scheduledTask, true, plugin.getLogger());
        } catch (Exception e) {
            // Folia 上回退 Bukkit 调度器必然抛 UnsupportedOperationException，直接报错而非静默降级
            throw new IllegalStateException("Folia GlobalRegionScheduler 调度失败（runAtFixedRate）", e);
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
