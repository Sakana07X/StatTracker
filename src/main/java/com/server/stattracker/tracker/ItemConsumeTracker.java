package com.server.stattracker.tracker;

// 消耗品追踪器：食物、药水、牛奶，按物品细分
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Set;
public class ItemConsumeTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<Material> POTIONS = Set.of(
        Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION
    );

    private static final Set<String> FOOD_NAMES = Set.of(
        "APPLE", "BREAD", "PORKCHOP", "COOKED_PORKCHOP", "BEEF", "COOKED_BEEF",
        "CHICKEN", "COOKED_CHICKEN", "MUTTON", "COOKED_MUTTON", "RABBIT",
        "COOKED_RABBIT", "COD", "COOKED_COD", "SALMON", "COOKED_SALMON",
        "TROPICAL_FISH", "PUFFERFISH", "COOKIE", "MELON_SLICE", "DRIED_KELP",
        "SWEET_BERRIES", "GLOW_BERRIES", "PUMPKIN_PIE", "CARROT", "POTATO",
        "BAKED_POTATO", "BEETROOT", "BEETROOT_SOUP", "MUSHROOM_STEW",
        "RABBIT_STEW", "SUSPICIOUS_STEW", "CAKE", "HONEY_BOTTLE",
        "GOLDEN_APPLE", "GOLDEN_CARROT", "ENCHANTED_GOLDEN_APPLE",
        "CHORUS_FRUIT"
    );

    public ItemConsumeTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material mat = event.getItem().getType();
        String name = mat.name();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.CONSUME_TOTAL);
        data.increment(StatKeys.CONSUME_PREFIX + name);

        if (POTIONS.contains(mat)) {
            data.increment(StatKeys.POTIONS_DRUNK);
        } else if (mat == Material.MILK_BUCKET) {
            data.increment(StatKeys.MILK_DRUNK);
        } else if (FOOD_NAMES.contains(name)) {
            data.increment(StatKeys.FOOD_EATEN);
            data.increment(StatKeys.CONSUME_FOOD_PREFIX + name);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
