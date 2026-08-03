package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
public class CombatDetailTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public CombatDetailTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        double amount = event.getFinalDamage();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.addDouble(StatKeys.DAMAGE_DEALT, amount);
        data.addDouble(StatKeys.DMG_DEALT_PREFIX + event.getCause().name(), amount);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double amount = event.getFinalDamage();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.addDouble(StatKeys.DAMAGE_TAKEN, amount);
        data.addDouble(StatKeys.DMG_TAKEN_PREFIX + event.getCause().name(), amount);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        EntityDamageEvent lastDmg = player.getLastDamageCause();
        if (lastDmg instanceof EntityDamageByEntityEvent dmgByEntity) {
            if (dmgByEntity.getDamager() instanceof Player killer) {
                plugin.getDataManager().get(killer.getUniqueId()).increment(StatKeys.PLAYER_KILLS);
            }
        }

        String cause = lastDmg != null ? lastDmg.getCause().name() : "UNKNOWN";
        data.increment(StatKeys.DEATH_CAUSE_PREFIX + cause);

        data.setCounter(StatKeys.LAST_DEATH_X, player.getLocation().getBlockX());
        data.setCounter(StatKeys.LAST_DEATH_Y, player.getLocation().getBlockY());
        data.setCounter(StatKeys.LAST_DEATH_Z, player.getLocation().getBlockZ());

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
