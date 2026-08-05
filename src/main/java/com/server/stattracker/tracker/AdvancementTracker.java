package com.server.stattracker.tracker;

// 进度追踪器：完成进度计数、进度列表（排除配方）
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
public class AdvancementTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public AdvancementTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        String key = event.getAdvancement().getKey().toString();
        if (key.startsWith("minecraft:recipes/")) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        if (data.addToSet(StatKeys.ADVANCEMENTS_DONE, key)) {
            data.increment(StatKeys.ADVANCEMENT_COUNT);
            plugin.getDataManager().markDirty(player.getUniqueId());
        }
    }
}
