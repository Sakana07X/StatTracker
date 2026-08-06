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
import org.bukkit.event.entity.ProjectileLaunchEvent;
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
        }
        // 三叉戟命中不重复计数投掷次数（投掷已在 onPearlThrow 类似逻辑中处理）
        // 如果需要命中追踪，应使用独立的 key

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    // 三叉戟投掷计数（单独监听 ProjectileLaunchEvent）
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) return;
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.TRIDENT_THROWS);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
