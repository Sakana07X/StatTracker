package com.server.stattracker.tracker;

// 收获追踪器：成熟作物收割按类型（小麦/胡萝卜/马铃薯等）
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;
public class HarvestTracker implements Listener {

    private final StatTrackerPlugin plugin;
    private static final Set<Material> CROPS = Set.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER, Material.PITCHER_PLANT
    );

    public HarvestTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;
        if (!CROPS.contains(block.getType())) return;

        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.HARVEST_PREFIX + block.getType().name());
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
