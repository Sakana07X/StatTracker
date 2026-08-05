package com.server.stattracker.tracker;

// 生存追踪器：在线时长、最长存活记录、死亡原因累计、最低血量
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class SurvivalTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public SurvivalTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        data.setCounter(StatKeys.JOIN_TIME, now);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    // track lowest health
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double remaining = player.getHealth() - event.getFinalDamage();
        if (remaining <= 0) return;
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        double current = data.getDouble(StatKeys.LOWEST_HEALTH);
        if (current == 0 || remaining < current) {
            data.setDouble(StatKeys.LOWEST_HEALTH, remaining);
            plugin.getDataManager().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        long now = System.currentTimeMillis();

        long joinTime = data.getCounter(StatKeys.JOIN_TIME);
        if (joinTime > 0) {
            long survival = now - joinTime;
            if (survival > data.getCounter(StatKeys.LONGEST_SURVIVAL_MS)) {
                data.setCounter(StatKeys.LONGEST_SURVIVAL_MS, survival);
            }
        }

        data.increment(StatKeys.DEATHS);
        data.setCounter(StatKeys.LAST_DEATH_TIME, now);
        data.setCounter(StatKeys.JOIN_TIME, now);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
