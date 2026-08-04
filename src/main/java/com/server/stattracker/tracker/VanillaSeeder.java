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
// 部分统计在不同版本改名（BLOCK_BREAK -> MINE_BLOCK），按名字解析兼容
public class VanillaSeeder implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Statistic BLOCK_BREAK_STAT = findStat("MINE_BLOCK", "BLOCK_BREAK");
    private static final Statistic BLOCK_PLACED_STAT = findStat("BLOCK_PLACED");
    private static final Statistic KILL_ENTITY_STAT = findStat("KILL_ENTITY", "MOB_KILLS");
    private static final Statistic RIDE_STRIDER_STAT = findStat("STRIDER_ONE_CM");

    public VanillaSeeder(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDataManager().hasData(player.getUniqueId())) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        boolean updated = false;

        long pickupTotal = 0;
        long craftTotal = 0;

        for (Material mat : Material.values()) {
            if (mat == Material.AIR) continue;
            boolean isBlock = mat.isBlock();

            if (isBlock && BLOCK_BREAK_STAT != null) {
                int broken = stat(player, BLOCK_BREAK_STAT, mat);
                if (broken > 0) {
                    data.setCounter(StatKeys.MINING_PREFIX + mat.name(), broken);
                    updated = true;
                }
            }
            if (isBlock && BLOCK_PLACED_STAT != null) {
                int placed = stat(player, BLOCK_PLACED_STAT, mat);
                if (placed > 0) {
                    data.setCounter(StatKeys.PLACEMENT_PREFIX + mat.name(), placed);
                    updated = true;
                }
            }

            int picked = stat(player, Statistic.PICKUP, mat);
            if (picked > 0) {
                data.setCounter(StatKeys.PICKUP_PREFIX + mat.name(), picked);
                pickupTotal += picked;
                updated = true;
            }
            int crafted = stat(player, Statistic.CRAFT_ITEM, mat);
            if (crafted > 0) {
                craftTotal += crafted;
                data.addToSet(StatKeys.CRAFTED_ITEMS, mat.name());
                updated = true;
            }
        }

        if (pickupTotal > 0) {
            data.setCounter(StatKeys.PICKUP_COUNT, pickupTotal);
            updated = true;
        }
        if (craftTotal > 0) {
            data.setCounter(StatKeys.CRAFT_COUNT, craftTotal);
            updated = true;
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
        updated |= seed(player, data, Statistic.JUMP, StatKeys.JUMP_COUNT);
        updated |= seed(player, data, Statistic.ANIMALS_BRED, StatKeys.BREED_COUNT);
        updated |= seed(player, data, Statistic.DROP_COUNT, StatKeys.DROP_COUNT);

        updated |= seedCm(player, data, Statistic.WALK_ONE_CM, StatKeys.WALK_DISTANCE);
        updated |= seedCm(player, data, Statistic.SPRINT_ONE_CM, StatKeys.SPRINT_DISTANCE);
        updated |= seedCm(player, data, Statistic.SWIM_ONE_CM, StatKeys.SWIM_DISTANCE);
        updated |= seedCm(player, data, Statistic.AVIATE_ONE_CM, StatKeys.ELYTRA_DISTANCE);
        updated |= seedCm(player, data, Statistic.BOAT_ONE_CM, StatKeys.BOAT_DISTANCE);

        long rideCm = simple(player, Statistic.MINECART_ONE_CM)
            + simple(player, Statistic.PIG_ONE_CM)
            + simple(player, Statistic.HORSE_ONE_CM)
            + (RIDE_STRIDER_STAT != null ? simple(player, RIDE_STRIDER_STAT) : 0);
        if (rideCm > 0) {
            data.setDouble(StatKeys.RIDE_DISTANCE, rideCm / 100.0);
            updated = true;
        }

        int playTicks = simple(player, Statistic.PLAY_ONE_MINUTE);
        if (playTicks > 0) {
            data.setCounter(StatKeys.PLAYTIME_MS, playTicks * 50L);
            updated = true;
        }

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
        int value = simple(player, stat);
        if (value <= 0) return false;
        data.setCounter(key, value);
        return true;
    }

    private boolean seedCm(Player player, PlayerTrackData data, Statistic stat, String key) {
        int value = simple(player, stat);
        if (value <= 0) return false;
        data.setDouble(key, value / 100.0);
        return true;
    }

    private int simple(Player player, Statistic stat) {
        try {
            return player.getStatistic(stat);
        } catch (Exception e) {
            return 0;
        }
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
