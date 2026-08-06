package com.server.stattracker.tracker;

// 战斗追踪器：击杀统计、武器、维度、生物群系、首杀、连杀
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
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

        // first kill timestamp
        String firstKey = StatKeys.FIRST_KILL_PREFIX + type;
        if (data.getCounter(firstKey) == 0) {
            data.setCounter(firstKey, System.currentTimeMillis());
        }

        // weapon
        ItemStack mainHand = killer.getInventory().getItemInMainHand();
        String weapon = mainHand.getType() == Material.AIR ? "HAND" : mainHand.getType().name();
        data.increment(StatKeys.KILL_WEAPON_PREFIX + weapon);

        // 交叉追踪：武器→生物类型集合（空手杀僵尸 → combat.kill_weapon.HAND 包含 ZOMBIE）
        data.addToSet(StatKeys.KILL_WEAPON_SET_PREFIX + weapon, type);

        // kill dimension
        String dim = killer.getWorld().getEnvironment().name();
        data.increment(StatKeys.KILL_DIM_PREFIX + dim);

        // kill biome
        String biome = killer.getLocation().getBlock().getBiome().name();
        data.increment(StatKeys.KILL_BIOME_PREFIX + biome);

        // 交叉追踪：生物→群系集合（深暗群系杀监守者 → combat.kill_in_biome.WARDEN 包含 DEEP_DARK）
        data.addToSet(StatKeys.KILL_TYPE_BIOME_SET_PREFIX + type, biome);

        // kill streak
        long streak = data.getCounter(StatKeys.KILL_STREAK) + 1;
        data.setCounter(StatKeys.KILL_STREAK, streak);
        long best = data.getCounter(StatKeys.BEST_KILL_STREAK);
        if (streak > best) data.setCounter(StatKeys.BEST_KILL_STREAK, streak);

        plugin.getDataManager().markDirty(killer.getUniqueId());
    }

    // reset streak on death
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        var uuid = event.getEntity().getUniqueId();
        plugin.getDataManager().get(uuid).setCounter(StatKeys.KILL_STREAK, 0);
        plugin.getDataManager().markDirty(uuid);
    }
}
