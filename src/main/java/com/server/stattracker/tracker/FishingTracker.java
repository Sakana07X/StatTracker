package com.server.stattracker.tracker;

// 钓鱼追踪器：钓获次数、逐物品、宝物/垃圾分类、甩竿次数
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;

public class FishingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<Material> TREASURE_MATERIALS = Set.of(
        Material.HEART_OF_THE_SEA, Material.NAUTILUS_SHELL,
        Material.NAME_TAG, Material.SADDLE, Material.ENCHANTED_BOOK,
        Material.BOW, Material.FISHING_ROD
    );

    private static final Set<Material> FISH_MATERIALS = Set.of(
        Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    );

    public FishingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        // count every cast attempt
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            data.increment(StatKeys.FISHING_CASTS);
            plugin.getDataManager().markDirty(player.getUniqueId());
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (!(caught instanceof Item item)) return;

        data.increment(StatKeys.FISH_CAUGHT);

        Material type = item.getItemStack().getType();
        data.increment(StatKeys.FISHING_CATCH_PREFIX + type.name());

        boolean isTreasure = TREASURE_MATERIALS.contains(type)
            || (item.getItemStack().hasItemMeta() && !item.getItemStack().getItemMeta().getEnchants().isEmpty());
        if (isTreasure) {
            data.increment(StatKeys.TREASURE_CATCHES);
        } else if (!FISH_MATERIALS.contains(type)) {
            data.increment(StatKeys.JUNK_CATCHES);
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
