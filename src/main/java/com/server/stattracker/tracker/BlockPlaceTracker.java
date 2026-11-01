package com.server.stattracker.tracker;

// 方块放置追踪器：总放置量、按方块材质
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class BlockPlaceTracker implements Listener {

    private final StatTrackerPlugin plugin;

    // 热路径键串缓存：Material 枚举数即上界，避免每次放方块拼串产生垃圾
    private static final Map<Material, String> PLACE_KEYS = new ConcurrentHashMap<>();

    private static String placeKey(Material material) {
        return PLACE_KEYS.computeIfAbsent(material, m -> StatKeys.PLACEMENT_PREFIX + m.name());
    }

    public BlockPlaceTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material mat = event.getBlockPlaced().getType();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.PLACEMENT_TOTAL);
        data.increment(placeKey(mat));
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
