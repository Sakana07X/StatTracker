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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    // 热路径键串缓存：Material 枚举数即上界，避免每次进食拼串产生垃圾
    private static final Map<Material, String> CONSUME_KEYS = new ConcurrentHashMap<>();
    private static final Map<Material, String> FOOD_KEYS = new ConcurrentHashMap<>();

    private static String consumeKey(Material material) {
        return CONSUME_KEYS.computeIfAbsent(material, m -> StatKeys.CONSUME_PREFIX + m.name());
    }

    private static String foodKey(Material material) {
        return FOOD_KEYS.computeIfAbsent(material, m -> StatKeys.CONSUME_FOOD_PREFIX + m.name());
    }

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
        data.increment(consumeKey(mat));

        if (POTIONS.contains(mat)) {
            data.increment(StatKeys.POTIONS_DRUNK);
        } else if (mat == Material.MILK_BUCKET) {
            data.increment(StatKeys.MILK_DRUNK);
        } else if (FOOD_NAMES.contains(name)) {
            data.increment(StatKeys.FOOD_EATEN);
            data.increment(foodKey(mat));
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
