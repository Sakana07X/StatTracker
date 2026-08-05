package com.server.stattracker.tracker;

// misc: golem/snowman/wither build, name tag, shield block, totem
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiscTracker implements Listener {

    private final StatTrackerPlugin plugin;
    private final Map<UUID, Long> recentPumpkinPlace = new HashMap<>(8);

    public MiscTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    // record pumpkin placement for golem/snowman attribution
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type != Material.CARVED_PUMPKIN && type != Material.JACK_O_LANTERN) return;
        recentPumpkinPlace.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    // attribute golem/snowman/wither spawn to player
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) {
            UUID placer = findRecentPlacer();
            if (placer != null) {
                plugin.getDataManager().get(placer).increment(StatKeys.GOLEM_BUILDS);
                plugin.getDataManager().markDirty(placer);
            }
        } else if (reason == CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN) {
            UUID placer = findRecentPlacer();
            if (placer != null) {
                plugin.getDataManager().get(placer).increment(StatKeys.SNOWMAN_BUILDS);
                plugin.getDataManager().markDirty(placer);
            }
        } else if (event.getEntity() instanceof Wither
            && reason == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            var loc = event.getLocation();
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                if (!(e instanceof Player p)) continue;
                plugin.getDataManager().get(p.getUniqueId()).increment(StatKeys.WITHER_SPAWNS);
                plugin.getDataManager().markDirty(p.getUniqueId());
                break;
            }
        }
    }

    private UUID findRecentPlacer() {
        long now = System.currentTimeMillis();
        UUID best = null;
        long bestTime = 0;
        for (var entry : recentPumpkinPlace.entrySet()) {
            if (now - entry.getValue() > 3000) continue;
            if (entry.getValue() > bestTime) {
                bestTime = entry.getValue();
                best = entry.getKey();
            }
        }
        recentPumpkinPlace.entrySet().removeIf(e -> now - e.getValue() > 5000);
        return best;
    }

    // name tag usage
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNameTag(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.NAME_TAG) return;
        if (!hand.hasItemMeta() || !hand.getItemMeta().hasDisplayName()) return;
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.NAME_TAG_USES);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    // shield block
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isBlocking()) return;
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.SHIELD_BLOCKS);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    // totem of undying
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.TOTEM_USES);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
