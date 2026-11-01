package com.server.stattracker.tracker;

// 挖掘追踪器：总挖掘量、按方块材质、按原木类型
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class MiningTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<String> LOG_MATERIALS = Set.of(
        "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG",
        "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG",
        "CHERRY_LOG", "CRIMSON_STEM", "WARPED_STEM"
    );

    // 热路径键串缓存：Material 枚举数即上界，避免每次挖方块拼串产生垃圾
    private static final Map<Material, String> MINE_KEYS = new ConcurrentHashMap<>();
    private static final Map<Material, String> LOG_KEYS = new ConcurrentHashMap<>();

    private static String mineKey(Material material) {
        return MINE_KEYS.computeIfAbsent(material, m -> StatKeys.MINING_PREFIX + m.name());
    }

    private static String logKey(Material material) {
        return LOG_KEYS.computeIfAbsent(material, m -> StatKeys.LOGS_PREFIX + m.name());
    }

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

        data.increment(mineKey(mat));

        if (LOG_MATERIALS.contains(name)) {
            data.increment(logKey(mat));
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
