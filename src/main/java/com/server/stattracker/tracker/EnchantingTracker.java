package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
public class EnchantingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public EnchantingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        int level = event.getExpLevelCost();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.ENCHANT_COUNT);
        data.increment(StatKeys.ENCHANT_LEVELS_USED, level);

        String tier = level <= 10 ? "low" : level <= 20 ? "mid" : "high";
        data.increment(StatKeys.ENCHANT_LEVEL_PREFIX + tier);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
