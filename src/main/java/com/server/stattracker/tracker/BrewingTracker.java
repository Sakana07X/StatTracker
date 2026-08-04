package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Set;
public class BrewingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<PotionType> TRACKED_TYPES = Set.of(
        PotionType.NIGHT_VISION, PotionType.INVISIBILITY, PotionType.JUMP,
        PotionType.FIRE_RESISTANCE, PotionType.SPEED, PotionType.SLOWNESS,
        PotionType.WATER_BREATHING, PotionType.INSTANT_HEAL, PotionType.INSTANT_DAMAGE,
        PotionType.POISON, PotionType.REGEN, PotionType.STRENGTH,
        PotionType.WEAKNESS, PotionType.TURTLE_MASTER, PotionType.SLOW_FALLING
    );

    public BrewingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        var loc = event.getBlock().getLocation();
        Player brewer = null;
        double best = 25; // 5 blocks squared
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.isDead()) continue;
            double dist = p.getLocation().distanceSquared(loc);
            if (dist < best) {
                best = dist;
                brewer = p;
            }
        }
        if (brewer == null) return;

        PlayerTrackData data = plugin.getDataManager().get(brewer.getUniqueId());
        boolean updated = false;

        for (ItemStack item : event.getContents().getContents()) {
            if (item == null || item.getType() != Material.POTION) continue;
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) continue;

            PotionType type = meta.getBasePotionData().getType();
            if (type == null || !TRACKED_TYPES.contains(type)) continue;

            data.increment(StatKeys.BREWING_PREFIX + type.name());
            updated = true;
        }

        if (updated) plugin.getDataManager().markDirty(brewer.getUniqueId());
    }
}
