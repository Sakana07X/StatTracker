package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
public class ItemPickupDropTracker implements Listener {

    private final StatTrackerPlugin plugin;
    private static final double VOID_MAX_Y = 0;

    public ItemPickupDropTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String mat = event.getItem().getItemStack().getType().name();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.PICKUP_COUNT);
        data.increment(StatKeys.PICKUP_PREFIX + mat);

        if (mat.equals("DIAMOND")) {
            data.increment(StatKeys.DIAMOND_PICKUPS);
        }

        if (player.isGliding()
            && player.getWorld().getEnvironment() == World.Environment.THE_END
            && player.getLocation().getY() < VOID_MAX_Y) {
            data.increment(StatKeys.VOID_PICKUPS);
            data.increment(StatKeys.VOID_PICKUP_PREFIX + mat);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.DROP_COUNT);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
