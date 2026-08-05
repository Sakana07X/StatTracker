package com.server.stattracker.tracker;

// 驯养/繁殖追踪器：驯养按实体类型、繁殖按物种
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
public class TamingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public TamingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        String type = event.getEntity().getType().name();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.TAME_COUNT);
        data.increment(StatKeys.TAME_PREFIX + type);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.BREED_COUNT);
        data.increment(StatKeys.BREED_SPECIES_PREFIX + event.getEntity().getType().name());
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
