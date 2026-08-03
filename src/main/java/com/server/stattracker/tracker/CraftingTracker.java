package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
public class CraftingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<String> ALL_FOODS = Set.of(
        "BREAD", "COOKIE", "CAKE", "PUMPKIN_PIE", "RABBIT_STEW",
        "BEETROOT_SOUP", "MUSHROOM_STEW", "SUSPICIOUS_STEW", "HONEY_BOTTLE",
        "DRIED_KELP", "GOLDEN_APPLE", "GOLDEN_CARROT",
        "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON",
        "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO"
    );

    public CraftingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        String name = result.getType().name();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.CRAFT_COUNT, result.getAmount());
        data.addToSet(StatKeys.CRAFTED_ITEMS, name);

        if (ALL_FOODS.contains(name)) {
            data.addToSet(StatKeys.CRAFTED_FOODS, name);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        String name = event.getItemType().name();
        PlayerTrackData data = plugin.getDataManager().get(event.getPlayer().getUniqueId());
        data.addToSet(StatKeys.CRAFTED_ITEMS, name);

        if (ALL_FOODS.contains(name)) {
            data.addToSet(StatKeys.CRAFTED_FOODS, name);
        }

        plugin.getDataManager().markDirty(event.getPlayer().getUniqueId());
    }
}
