package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;
public class RedstoneTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<Material> REDSTONE_COMPONENTS = Set.of(
        Material.REDSTONE_WIRE, Material.REDSTONE_TORCH, Material.REPEATER,
        Material.COMPARATOR, Material.PISTON, Material.STICKY_PISTON,
        Material.OBSERVER, Material.DISPENSER, Material.DROPPER,
        Material.HOPPER, Material.REDSTONE_LAMP, Material.TARGET,
        Material.DAYLIGHT_DETECTOR, Material.LEVER, Material.STONE_BUTTON,
        Material.OAK_BUTTON, Material.STONE_PRESSURE_PLATE,
        Material.OAK_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
        Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.TRIPWIRE_HOOK,
        Material.NOTE_BLOCK
    );

    public RedstoneTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!REDSTONE_COMPONENTS.contains(event.getBlockPlaced().getType())) return;

        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.REDSTONE_PLACED);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
