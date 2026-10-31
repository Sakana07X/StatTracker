package com.server.stattracker.tracker;

// 游戏时长追踪器：在线时长累计、最长存活时间（定时刷新）
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeTracker implements Listener {

    private final StatTrackerPlugin plugin;
    private final Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();

    public PlaytimeTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        loginTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flushPlaytime(event.getPlayer().getUniqueId());
        loginTimes.remove(event.getPlayer().getUniqueId());
    }

    // 只遍历内存 map，不调用任何 Bukkit API，可安全从全局调度线程调用（兼容 Folia）
    public void flushAll() {
        for (UUID uuid : loginTimes.keySet()) {
            flushPlaytime(uuid);
        }
    }

    private void flushPlaytime(UUID uuid) {
        Long loginTime = loginTimes.get(uuid);
        if (loginTime == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - loginTime;

        PlayerTrackData data = plugin.getDataManager().get(uuid);
        data.increment(StatKeys.PLAYTIME_MS, elapsed);

        long joinTime = data.getCounter(StatKeys.JOIN_TIME);
        if (joinTime > 0) {
            long currentSurvival = now - joinTime;
            if (currentSurvival > data.getCounter(StatKeys.LONGEST_SURVIVAL_MS)) {
                data.setCounter(StatKeys.LONGEST_SURVIVAL_MS, currentSurvival);
            }
        }

        // 只推进在线时长锚点；JOIN_TIME 归 SurvivalTracker 管理（join/death/quit），
        // 在此重置会把存活基准切成 10 分钟片、且退出时写回退出时刻导致离线时长被计入存活
        loginTimes.put(uuid, now);

        plugin.getDataManager().markDirty(uuid);
    }
}
