package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
public class DimensionTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public DimensionTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World.Environment env = player.getWorld().getEnvironment();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        if (env == World.Environment.NETHER) {
            data.setCounter(StatKeys.ENTERED_NETHER, 1);
            plugin.getDataManager().markDirty(player.getUniqueId());
        } else if (env == World.Environment.THE_END) {
            data.setCounter(StatKeys.ENTERED_END, 1);
            plugin.getDataManager().markDirty(player.getUniqueId());
        }
    }
}
