package com.server.stattracker.integration;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

public class CyuTitlesBridge implements Listener {

    private final StatTrackerPlugin plugin;
    private final LuckPermsBridge lp;
    private final Set<UUID> checked = new HashSet<>();

    private static final long SURVIVOR_MS = 8_400_000L;   // 7 MC days
    private static final long IMMORTAL_MS = 36_000_000L;  // 30 MC days

    // Omniverse traveler: all biomes (same list as old BiomeTrackerListener)
    private static final Set<String> ALL_BIOMES = Set.of(
        "PLAINS","SUNFLOWER_PLAINS","DESERT","SAVANNA","SAVANNA_PLATEAU","WINDSWEPT_SAVANNA",
        "FOREST","FLOWER_FOREST","BIRCH_FOREST","OLD_GROWTH_BIRCH_FOREST","DARK_FOREST",
        "OLD_GROWTH_PINE_TAIGA","OLD_GROWTH_SPRUCE_TAIGA","TAIGA","SNOWY_TAIGA",
        "SNOWY_PLAINS","ICE_SPIKES","JUNGLE","SPARSE_JUNGLE","BAMBOO_JUNGLE",
        "BEACH","SNOWY_BEACH","STONY_SHORE","MUSHROOM_FIELDS","SWAMP","MANGROVE_SWAMP",
        "RIVER","FROZEN_RIVER","OCEAN","DEEP_OCEAN","COLD_OCEAN","DEEP_COLD_OCEAN",
        "FROZEN_OCEAN","DEEP_FROZEN_OCEAN","LUKEWARM_OCEAN","DEEP_LUKEWARM_OCEAN","WARM_OCEAN",
        "STONY_PEAKS","JAGGED_PEAKS","FROZEN_PEAKS","MEADOW","CHERRY_GROVE","GROVE",
        "SNOWY_SLOPES","WINDSWEPT_HILLS","WINDSWEPT_GRAVELLY_HILLS","WINDSWEPT_FOREST",
        "BADLANDS","WOODED_BADLANDS","ERODED_BADLANDS","DRIPSTONE_CAVES","LUSH_CAVES","DEEP_DARK",
        "NETHER_WASTES","CRIMSON_FOREST","WARPED_FOREST","SOUL_SAND_VALLEY","BASALT_DELTAS",
        "THE_END","END_HIGHLANDS","END_MIDLANDS","END_BARRENS","SMALL_END_ISLANDS"
    );

    // Snow visitor biomes
    private static final Set<String> SNOWY_BIOMES = Set.of(
        "SNOWY_PLAINS","SNOWY_TAIGA","SNOWY_BEACH","SNOWY_SLOPES",
        "ICE_SPIKES","FROZEN_PEAKS","FROZEN_OCEAN","DEEP_FROZEN_OCEAN","FROZEN_RIVER","GROVE"
    );

    // All 15 brewable potion types
    private static final Set<String> BREWABLE = Set.of(
        "NIGHT_VISION","INVISIBILITY","JUMP","FIRE_RESISTANCE","SPEED","SLOWNESS",
        "WATER_BREATHING","INSTANT_HEAL","INSTANT_DAMAGE","POISON","REGEN",
        "STRENGTH","WEAKNESS","TURTLE_MASTER","SLOW_FALLING"
    );

    // All 20 crafted foods
    private static final Set<String> ALL_FOODS = Set.of(
        "BREAD","COOKIE","CAKE","PUMPKIN_PIE","RABBIT_STEW","BEETROOT_SOUP","MUSHROOM_STEW",
        "SUSPICIOUS_STEW","HONEY_BOTTLE","DRIED_KELP","GOLDEN_APPLE","GOLDEN_CARROT",
        "COOKED_BEEF","COOKED_PORKCHOP","COOKED_CHICKEN","COOKED_MUTTON","COOKED_RABBIT",
        "COOKED_COD","COOKED_SALMON","BAKED_POTATO"
    );

    // Treasure hunter structures (12)
    private static final Set<String> TREASURE_STRUCTS = Set.of(
        "DESERT_PYRAMID","JUNGLE_PYRAMID","STRONGHOLD","ANCIENT_CITY","IGLOO","SHIPWRECK",
        "MANSION","MINESHAFT","BURIED_TREASURE","PILLAGER_OUTPOST","TRAIL_RUINS","TRIAL_CHAMBERS"
    );

    // Ocean structures (5)
    private static final Set<String> OCEAN_STRUCTS = Set.of(
        "OCEAN_RUIN_WARM","OCEAN_RUIN_COLD","SHIPWRECK","SHIPWRECK_BEACHED","MONUMENT"
    );

    // Ruins explorer structures (3)
    private static final Set<String> RUIN_STRUCTS = Set.of("STRONGHOLD","MINESHAFT","JUNGLE_PYRAMID");

    // Nether structures (3)
    private static final Set<String> NETHER_STRUCTS = Set.of("NETHER_FORTRESS","BASTION_REMNANT","RUINED_PORTAL");

    // World conqueror advancements
    private static final String[] KEY_ADVANCEMENTS = {
        "minecraft:end/elytra","minecraft:end/respawn_dragon","minecraft:end/dragon_breath",
        "minecraft:nether/obtain_ancient_debris","minecraft:nether/uneasy_alliance",
        "minecraft:nether/all_potions","minecraft:nether/all_effects",
        "minecraft:adventure/adventuring_time","minecraft:adventure/hero_of_the_village",
        "minecraft:adventure/arbalistic","minecraft:husbandry/bred_all_animals",
        "minecraft:husbandry/complete_catalogue","minecraft:story/elytra"
    };

    public CyuTitlesBridge(StatTrackerPlugin plugin) {
        this.plugin = plugin;
        this.lp = plugin.getLuckPermsBridge();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!lp.isAvailable()) return;
        // Delay check to let data load
        plugin.getScheduler().runDelayed(40, () -> checkAll(event.getPlayer()));
    }

    // Called periodically by main class
    public void flush() {
        if (!lp.isAvailable()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAll(player);
        }
    }

    private void checkAll(Player player) {
        if (!lp.isAvailable()) return;
        UUID uuid = player.getUniqueId();
        PlayerTrackData data = plugin.getDataManager().get(uuid);

        grant(uuid, "void_scavenger",          data.getBooleanFlag("void_scavenger_awarded"));
        grant(uuid, "treasure_hunter",         data.getSetSize(StatKeys.TREASURE_STRUCTS) >= 12);
        grant(uuid, "alchemist",               checkAlchemist(data));
        grant(uuid, "bee_whisperer",           data.getCounter(StatKeys.BEE_BREEDS) >= 50 && data.getCounter(StatKeys.HONEY_HARVESTS) >= 1);
        grant(uuid, "master_chef",             data.getSetSize(StatKeys.CRAFTED_FOODS) >= 20);
        grant(uuid, "deep_ocean_tracker",      data.getSetSize(StatKeys.OCEAN_STRUCTS) >= 5 && data.getCounter(StatKeys.BURIED_TREASURES) >= 3);
        grant(uuid, "nether_guide",            data.getSetSize(StatKeys.NETHER_BIOMES) >= 5 && data.getSetSize(StatKeys.NETHER_STRUCTURES) >= 3);
        grant(uuid, "omniverse_traveler",      data.getSetSize(StatKeys.VISITED_BIOMES) >= ALL_BIOMES.size());
        grant(uuid, "wing_chaser",             data.getDouble(StatKeys.ELYTRA_TOTAL) >= 1000);
        grant(uuid, "ancient_city_scout",      data.getSetSize(StatKeys.ANCIENT_CHESTS) >= 4);
        grant(uuid, "rock_pioneer",            data.getCounter(StatKeys.STONE_BROKEN) >= 25000);
        grant(uuid, "deep_prospector",         data.getCounter(StatKeys.DIAMOND_ORE_BROKEN) + data.getCounter(StatKeys.DEEPSLATE_DIAMOND) >= 256);
        grant(uuid, "ancient_debris_hunter",   data.getCounter(StatKeys.ANCIENT_DEBRIS) >= 32);
        grant(uuid, "forest_logger",           checkForestLogger(data));
        grant(uuid, "harvest_messenger",       checkHarvestMessenger(data));
        grant(uuid, "lake_fisher",             data.getCounter(StatKeys.FISH_CAUGHT) >= 500);
        grant(uuid, "demon_purifier",          data.getCounter(StatKeys.MOB_KILLS) >= 10000);
        grant(uuid, "nether_conqueror",        checkNetherConqueror(data));
        grant(uuid, "farmer_tiller",           data.getCounter(StatKeys.FARMLAND_TILLED) >= 200);
        grant(uuid, "tree_planter",            data.getCounter(StatKeys.SAPLINGS_PLANTED) >= 1000);
        grant(uuid, "treasure_fisher",         data.getCounter(StatKeys.TREASURE_CATCHES) >= 30);
        grant(uuid, "long_sailor",             data.getDouble(StatKeys.BOAT_DISTANCE) >= 10000);
        grant(uuid, "ocean_voyager",           data.getDouble(StatKeys.BOAT_DISTANCE) >= 50000);
        grant(uuid, "tide_walker",             data.getCounter(StatKeys.OCEAN_BIOME_TICKS) >= 72000);
        grant(uuid, "nether_stranger",         data.getCounter(StatKeys.ENTERED_NETHER) > 0);
        grant(uuid, "end_walker",              data.getCounter(StatKeys.ENTERED_END) > 0);
        grant(uuid, "ruins_explorer",          data.getSetSize(StatKeys.RUINS_FOUND) >= 3);
        grant(uuid, "snow_visitor",            data.getSetSize(StatKeys.SNOW_BIOMES) >= SNOWY_BIOMES.size());
        grant(uuid, "map_master",              data.getCounter(StatKeys.MAPS_CRAFTED) >= 10);
        grant(uuid, "redstone_tech",           data.getCounter(StatKeys.REDSTONE_PLACED) >= 500);
        grant(uuid, "redstone_master",         data.getCounter(StatKeys.REDSTONE_PLACED) >= 5000);
        grant(uuid, "survivor",                checkSurvival(player, data, SURVIVOR_MS));
        grant(uuid, "immortal_walker",         checkSurvival(player, data, IMMORTAL_MS));
        grant(uuid, "prudent_one",             player.getStatistic(Statistic.DEATHS) < 20);
        grant(uuid, "world_conqueror",         checkWorldConqueror(player));
        grant(uuid, "treasure_collector",      data.getSetSize(StatKeys.COLLECTOR_CHESTS) >= 12);
        grant(uuid, "jack_of_all",             checkJackOfAll(player));

        plugin.getDataManager().markDirty(uuid);
    }

    private void grant(UUID uuid, String title, boolean condition) {
        if (condition) {
            lp.grantPermission(uuid, "cyutitles.obtain." + title);
        }
    }

    private boolean checkAlchemist(PlayerTrackData data) {
        for (String type : BREWABLE) {
            if (data.getCounter(StatKeys.BREWING_PREFIX + type) < 3) return false;
        }
        return true;
    }

    private boolean checkForestLogger(PlayerTrackData data) {
        return data.getCounter(StatKeys.LOGS_PREFIX + "OAK_LOG") >= 3000
            && data.getCounter(StatKeys.LOGS_PREFIX + "SPRUCE_LOG") >= 2000
            && data.getCounter(StatKeys.LOGS_PREFIX + "BIRCH_LOG") >= 2000
            && data.getCounter(StatKeys.LOGS_PREFIX + "JUNGLE_LOG") >= 1000
            && data.getCounter(StatKeys.LOGS_PREFIX + "ACACIA_LOG") >= 1000
            && data.getCounter(StatKeys.LOGS_PREFIX + "DARK_OAK_LOG") >= 1000;
    }

    private boolean checkHarvestMessenger(PlayerTrackData data) {
        return data.getCounter(StatKeys.HARVEST_PREFIX + "WHEAT") >= 250
            && data.getCounter(StatKeys.HARVEST_PREFIX + "CARROTS") >= 250
            && data.getCounter(StatKeys.HARVEST_PREFIX + "POTATOES") >= 250
            && data.getCounter(StatKeys.HARVEST_PREFIX + "BEETROOTS") >= 250;
    }

    private boolean checkNetherConqueror(PlayerTrackData data) {
        return data.getCounter(StatKeys.PIGLIN_KILLS) >= 400
            && data.getCounter(StatKeys.BLAZE_KILLS) >= 300
            && data.getCounter(StatKeys.WITHER_SKELLY_KILLS) >= 300;
    }

    private boolean checkSurvival(Player player, PlayerTrackData data, long thresholdMs) {
        long joinTime = data.getCounter(StatKeys.JOIN_TIME);
        if (joinTime <= 0) return false;
        return (System.currentTimeMillis() - joinTime) >= thresholdMs;
    }

    private boolean checkWorldConqueror(Player player) {
        for (String adv : KEY_ADVANCEMENTS) {
            NamespacedKey key = NamespacedKey.fromString(adv);
            if (key == null) return false;
            var bukkitAdv = Bukkit.getAdvancement(key);
            if (bukkitAdv == null) return false;
            if (!player.getAdvancementProgress(bukkitAdv).isDone()) return false;
        }
        return true;
    }

    private boolean checkJackOfAll(Player player) {
        String[] titles = {
            "rock_pioneer","deep_prospector","ancient_debris_hunter","forest_logger",
            "harvest_messenger","lake_fisher","demon_purifier","nether_conqueror"
        };
        for (String t : titles) {
            if (!lp.hasPermission(player.getUniqueId(), "cyutitles.obtain." + t)) return false;
        }
        return true;
    }
}
