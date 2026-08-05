package com.server.stattracker.tracker;

// 附魔追踪器：附魔次数、等级分层、按附魔类型、按物品类型
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import java.util.Map;
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

        // per-enchantment type
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            data.increment(StatKeys.ENCHANT_TYPE_PREFIX + entry.getKey().getKey().getKey().toUpperCase());
        }

        // enchanted item type
        ItemStack item = event.getItem();
        if (item != null) {
            data.increment(StatKeys.ENCHANT_ITEM_PREFIX + item.getType().name());
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
