package com.server.stattracker.tracker;

// 物品拾取/丢弃追踪器：拾取按材质、钻石拾取、末地虚空拾取、丢弃计数
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class ItemPickupDropTracker implements Listener {

    private final StatTrackerPlugin plugin;
    private static final double VOID_MAX_Y = 0;

    // 热路径键串缓存：Material 枚举数即上界，避免每次拾取拼串产生垃圾
    private static final Map<Material, String> PICKUP_KEYS = new ConcurrentHashMap<>();
    private static final Map<Material, String> VOID_PICKUP_KEYS = new ConcurrentHashMap<>();

    private static String pickupKey(Material material) {
        return PICKUP_KEYS.computeIfAbsent(material, m -> StatKeys.PICKUP_PREFIX + m.name());
    }

    private static String voidPickupKey(Material material) {
        return VOID_PICKUP_KEYS.computeIfAbsent(material, m -> StatKeys.VOID_PICKUP_PREFIX + m.name());
    }

    public ItemPickupDropTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Material mat = event.getItem().getItemStack().getType();
        String name = mat.name();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.PICKUP_COUNT);
        data.increment(pickupKey(mat));

        if (name.equals("DIAMOND")) {
            data.increment(StatKeys.DIAMOND_PICKUPS);
        }

        if (player.isGliding()
            && player.getWorld().getEnvironment() == World.Environment.THE_END
            && player.getLocation().getY() < VOID_MAX_Y) {
            data.increment(StatKeys.VOID_PICKUPS);
            data.increment(voidPickupKey(mat));
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.DROP_COUNT);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
