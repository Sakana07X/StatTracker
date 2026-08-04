package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

// 首次进服把原版统计回填进计数器，对齐称号判定的数据源
// 方块/实体统计在不同版本改名（BLOCK_BREAK -> MINE_BLOCK），按名字解析兼容
public class VanillaSeeder implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Statistic BLOCK_BREAK_STAT = findStat("MINE_BLOCK", "BLOCK_BREAK");
    private static final Statistic BLOCK_PLACED_STAT = findStat("BLOCK_PLACED");
    private static final Statistic KILL_ENTITY_STAT = findStat("KILL_ENTITY", "MOB_KILLS");

    public VanillaSeeder(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDataManager().hasData(player.getUniqueId())) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        boolean updated = false;

        if (BLOCK_BREAK_STAT != null) {
            for (Material mat : Material.values()) {
                if (mat == Material.AIR || !mat.isBlock()) continue;
                int broken = stat(player, BLOCK_BREAK_STAT, mat);
                if (broken > 0) {
                    data.setCounter(StatKeys.MINING_PREFIX + mat.name(), broken);
                    updated = true;
                }
            }
        }

        if (BLOCK_PLACED_STAT != null) {
            for (Material mat : Material.values()) {
                if (mat == Material.AIR || !mat.isBlock()) continue;
                int placed = stat(player, BLOCK_PLACED_STAT, mat);
                if (placed > 0) {
                    data.setCounter(StatKeys.PLACEMENT_PREFIX + mat.name(), placed);
                    updated = true;
                }
            }
        }

        if (KILL_ENTITY_STAT != null) {
            for (EntityType type : EntityType.values()) {
                int killed = stat(player, KILL_ENTITY_STAT, type);
                if (killed > 0) {
                    data.setCounter(StatKeys.KILL_PREFIX + type.name(), killed);
                    updated = true;
                }
            }
        }

        updated |= seed(player, data, Statistic.DEATHS, StatKeys.DEATHS);
        updated |= seed(player, data, Statistic.MOB_KILLS, StatKeys.MOB_KILLS);
        updated |= seed(player, data, Statistic.PLAYER_KILLS, StatKeys.PLAYER_KILLS);
        updated |= seed(player, data, Statistic.FISH_CAUGHT, StatKeys.FISH_CAUGHT);
        updated |= seed(player, data, Statistic.ITEM_ENCHANTED, StatKeys.ENCHANT_COUNT);

        if (updated) plugin.getDataManager().markDirty(player.getUniqueId());
    }

    private static Statistic findStat(String... names) {
        for (String name : names) {
            try {
                return Enum.valueOf(Statistic.class, name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private boolean seed(Player player, PlayerTrackData data, Statistic stat, String key) {
        int value = player.getStatistic(stat);
        if (value <= 0) return false;
        data.setCounter(key, value);
        return true;
    }

    private int stat(Player player, Statistic stat, Material mat) {
        try {
            return player.getStatistic(stat, mat);
        } catch (Exception e) {
            return 0;
        }
    }

    private int stat(Player player, Statistic stat, EntityType type) {
        try {
            return player.getStatistic(stat, type);
        } catch (Exception e) {
            return 0;
        }
    }
}
