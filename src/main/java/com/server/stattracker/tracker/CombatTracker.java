package com.server.stattracker.tracker;

// 鎴樻枟杩借釜鍣細鍑绘潃缁熻銆佹鍣ㄣ€佺淮搴︺€佺敓鐗╃兢绯汇€侀鏉€銆佽繛鏉€
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

        // 浜ゅ弶杩借釜锛氭鍣ㄢ啋鐢熺墿绫诲瀷闆嗗悎锛堢┖鎵嬫潃鍍靛案 鈫?combat.kill_weapon.HAND 鍖呭惈 ZOMBIE锛?        data.addToSet(StatKeys.KILL_WEAPON_SET_PREFIX + weapon, type);

        // kill dimension
        String dim = killer.getWorld().getEnvironment().name();
        data.increment(StatKeys.KILL_DIM_PREFIX + dim);

        // kill biome
        String biome = killer.getLocation().getBlock().getBiome().name();
        data.increment(StatKeys.KILL_BIOME_PREFIX + biome);

        // 浜ゅ弶杩借釜锛氱敓鐗┾啋缇ょ郴闆嗗悎锛堟繁鏆楃兢绯绘潃鐩戝畧鑰?鈫?combat.kill_in_biome.WARDEN 鍖呭惈 DEEP_DARK锛?        data.addToSet(StatKeys.KILL_TYPE_BIOME_SET_PREFIX + type, biome);

        // kill streak（原子操作，Folia 多线程安全）
        long streak = data.increment(StatKeys.KILL_STREAK);
        data.setCounterMax(StatKeys.BEST_KILL_STREAK, streak);

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
