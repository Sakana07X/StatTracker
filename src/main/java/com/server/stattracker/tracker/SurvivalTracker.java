package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
public class SurvivalTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public SurvivalTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        data.setCounter(StatKeys.JOIN_TIME, now);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        long now = System.currentTimeMillis();

        long joinTime = data.getCounter(StatKeys.JOIN_TIME);
        if (joinTime > 0) {
            long survival = now - joinTime;
            if (survival > data.getCounter(StatKeys.LONGEST_SURVIVAL_MS)) {
                data.setCounter(StatKeys.LONGEST_SURVIVAL_MS, survival);
            }
        }

        data.increment(StatKeys.DEATHS);
        data.setCounter(StatKeys.LAST_DEATH_TIME, now);
        data.setCounter(StatKeys.JOIN_TIME, now);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
