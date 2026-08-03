package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
public class CombatTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public CombatTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerTrackData data = plugin.getDataManager().get(killer.getUniqueId());
        String type = event.getEntity().getType().name();

        data.increment(StatKeys.MOB_KILLS);
        data.increment(StatKeys.KILL_PREFIX + type);

        plugin.getDataManager().markDirty(killer.getUniqueId());
    }
}
