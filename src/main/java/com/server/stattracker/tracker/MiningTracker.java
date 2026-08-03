package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;
public class MiningTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<String> LOG_MATERIALS = Set.of(
        "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG",
        "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG",
        "CHERRY_LOG", "CRIMSON_STEM", "WARPED_STEM"
    );

    public MiningTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material mat = event.getBlock().getType();
        String name = mat.name();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        data.increment(StatKeys.MINING_TOTAL);

        data.increment(StatKeys.MINING_PREFIX + name);

        if (LOG_MATERIALS.contains(name)) {
            data.increment(StatKeys.LOGS_PREFIX + name);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
