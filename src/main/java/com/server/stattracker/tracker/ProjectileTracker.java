package com.server.stattracker.tracker;

// 投射物追踪器：弓箭射击/命中、三叉戟投掷
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.projectiles.ProjectileSource;
public class ProjectileTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public ProjectileTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getProjectile() instanceof AbstractArrow) {
            PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
            data.increment(StatKeys.ARROWS_SHOT);
            plugin.getDataManager().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile proj)) return;
        ProjectileSource shooter = proj.getShooter();
        if (!(shooter instanceof Player player)) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        if (proj instanceof Arrow) {
            data.increment(StatKeys.ARROW_HITS);
        } else if (proj instanceof Trident) {
            data.increment(StatKeys.TRIDENT_THROWS);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
