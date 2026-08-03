package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
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
        flushPlaytime(event.getPlayer());
        loginTimes.remove(event.getPlayer().getUniqueId());
    }

        public void flushAll() {
        for (var entry : loginTimes.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                flushPlaytime(player);
            }
        }
    }

    private void flushPlaytime(Player player) {
        Long loginTime = loginTimes.get(player.getUniqueId());
        if (loginTime == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - loginTime;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.PLAYTIME_MS, elapsed);

        long currentSurvival = now - data.getCounter(StatKeys.JOIN_TIME);
        if (currentSurvival > data.getCounter(StatKeys.LONGEST_SURVIVAL_MS)) {
            data.setCounter(StatKeys.LONGEST_SURVIVAL_MS, currentSurvival);
        }

        loginTimes.put(player.getUniqueId(), now);
        data.setCounter(StatKeys.JOIN_TIME, now);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
