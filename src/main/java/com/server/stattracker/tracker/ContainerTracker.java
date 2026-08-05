package com.server.stattracker.tracker;

// 宝箱/结构追踪器：宝箱开启附近结构检测（遗迹/海底/埋藏等）
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.structure.Structure;

import java.util.*;

public class ContainerTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Map<String, Structure> TREASURE_STRUCTURES = new LinkedHashMap<>();
    private static final Map<String, Structure> OCEAN_STRUCTURES = new LinkedHashMap<>();
    private static final Map<String, Structure> RUIN_STRUCTURES = new LinkedHashMap<>();
    private static final Map<String, Structure> COLLECTOR_STRUCTURES = new LinkedHashMap<>();

    static {
        TREASURE_STRUCTURES.put("DESERT_PYRAMID", Structure.DESERT_PYRAMID);
        TREASURE_STRUCTURES.put("JUNGLE_PYRAMID", Structure.JUNGLE_PYRAMID);
        TREASURE_STRUCTURES.put("STRONGHOLD", Structure.STRONGHOLD);
        TREASURE_STRUCTURES.put("ANCIENT_CITY", Structure.ANCIENT_CITY);
        TREASURE_STRUCTURES.put("IGLOO", Structure.IGLOO);
        TREASURE_STRUCTURES.put("SHIPWRECK", Structure.SHIPWRECK);
        TREASURE_STRUCTURES.put("MANSION", Structure.MANSION);
        TREASURE_STRUCTURES.put("MINESHAFT", Structure.MINESHAFT);
        TREASURE_STRUCTURES.put("BURIED_TREASURE", Structure.BURIED_TREASURE);
        TREASURE_STRUCTURES.put("PILLAGER_OUTPOST", Structure.PILLAGER_OUTPOST);
        TREASURE_STRUCTURES.put("TRAIL_RUINS", Structure.TRAIL_RUINS);

        OCEAN_STRUCTURES.put("OCEAN_RUIN_WARM", Structure.OCEAN_RUIN_WARM);
        OCEAN_STRUCTURES.put("OCEAN_RUIN_COLD", Structure.OCEAN_RUIN_COLD);
        OCEAN_STRUCTURES.put("SHIPWRECK", Structure.SHIPWRECK);
        OCEAN_STRUCTURES.put("SHIPWRECK_BEACHED", Structure.SHIPWRECK_BEACHED);
        OCEAN_STRUCTURES.put("MONUMENT", Structure.MONUMENT);

        RUIN_STRUCTURES.put("STRONGHOLD", Structure.STRONGHOLD);
        RUIN_STRUCTURES.put("MINESHAFT", Structure.MINESHAFT);
        RUIN_STRUCTURES.put("JUNGLE_PYRAMID", Structure.JUNGLE_PYRAMID);

        COLLECTOR_STRUCTURES.putAll(TREASURE_STRUCTURES);
        COLLECTOR_STRUCTURES.putAll(OCEAN_STRUCTURES);
        COLLECTOR_STRUCTURES.putAll(RUIN_STRUCTURES);
    }

    private final HashMap<UUID, CacheEntry> detectCache = new HashMap<>(32);
    private static final long CACHE_TTL_MS = 5000;
    private static final double CACHE_RANGE_SQ = 256;

    private static class CacheEntry {
        final Location location;
        final long timestamp;
        final Set<String> matchedStructures;
        CacheEntry(Location loc, long ts, Set<String> matched) {
            this.location = loc; this.timestamp = ts; this.matchedStructures = matched;
        }
    }

    public ContainerTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.CHEST && type != InventoryType.BARREL
            && type != InventoryType.DISPENSER && type != InventoryType.HOPPER) return;

        Location loc = event.getInventory().getLocation();
        if (loc == null) return;

        UUID uuid = player.getUniqueId();
        PlayerTrackData data = plugin.getDataManager().get(uuid);
        boolean updated = false;

        CacheEntry cached = detectCache.get(uuid);
        Set<String> matched;
        if (cached != null
            && cached.location.getWorld() == loc.getWorld()
            && cached.location.distanceSquared(loc) < CACHE_RANGE_SQ
            && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            matched = cached.matchedStructures;
        } else {
            matched = detectStructures(loc);
            detectCache.put(uuid, new CacheEntry(loc.clone(), System.currentTimeMillis(), matched));
        }

        if (checkMatched(matched, data, TREASURE_STRUCTURES, StatKeys.TREASURE_STRUCTS)) updated = true;
        if (checkMatched(matched, data, OCEAN_STRUCTURES, StatKeys.OCEAN_STRUCTS)) updated = true;

        if (matched.contains("BURIED_TREASURE")) {
            data.increment(StatKeys.BURIED_TREASURES);
            updated = true;
        }

        if (matched.contains("ANCIENT_CITY")) {
            if (data.addToSet(StatKeys.ANCIENT_CHESTS, type.name())) updated = true;
        }

        if (checkMatched(matched, data, RUIN_STRUCTURES, StatKeys.RUINS_FOUND)) updated = true;
        if (checkMatched(matched, data, COLLECTOR_STRUCTURES, StatKeys.COLLECTOR_CHESTS)) updated = true;

        if (updated) plugin.getDataManager().markDirty(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detectCache.remove(event.getPlayer().getUniqueId());
    }

    private Set<String> detectStructures(Location loc) {
        Set<String> result = new HashSet<>();
        Set<Structure> toCheck = new HashSet<>();
        Map<Structure, String> reverseMap = new HashMap<>();
        addAll(toCheck, reverseMap, TREASURE_STRUCTURES);
        addAll(toCheck, reverseMap, OCEAN_STRUCTURES);
        addAll(toCheck, reverseMap, RUIN_STRUCTURES);

        for (Structure structure : toCheck) {
            try {
                var found = loc.getWorld().locateNearestStructure(loc, structure, 10, false);
                if (found != null && found.getLocation().distanceSquared(loc) < 400) {
                    for (var entry : reverseMap.entrySet()) {
                        if (entry.getKey() == structure) result.add(entry.getValue());
                    }
                }
            } catch (Exception e) {
            }
        }
        return result;
    }

    private void addAll(Set<Structure> toCheck, Map<Structure, String> reverseMap, Map<String, Structure> source) {
        for (var entry : source.entrySet()) {
            toCheck.add(entry.getValue());
            reverseMap.put(entry.getValue(), entry.getKey());
        }
    }

    private boolean checkMatched(Set<String> matched, PlayerTrackData data,
                                  Map<String, Structure> structures, String setKey) {
        boolean updated = false;
        for (String key : structures.keySet()) {
            if (matched.contains(key) && data.addToSet(setKey, key)) updated = true;
        }
        return updated;
    }
}
