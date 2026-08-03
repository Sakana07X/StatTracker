package com.server.stattracker.integration;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.api.StatProvider;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PAPIExpansion extends PlaceholderExpansion {

    private final StatTrackerPlugin plugin;
    private final StatProvider api;

    private static final Map<String, String> COUNTER_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, String> DOUBLE_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, String> SET_KEYS = new ConcurrentHashMap<>();

    static {
        COUNTER_KEYS.put("mob_kills",           StatKeys.MOB_KILLS);
        COUNTER_KEYS.put("player_kills",        StatKeys.PLAYER_KILLS);
        COUNTER_KEYS.put("deaths",              StatKeys.DEATHS);
        COUNTER_KEYS.put("fish_caught",         StatKeys.FISH_CAUGHT);
        COUNTER_KEYS.put("treasure_catches",    StatKeys.TREASURE_CATCHES);
        COUNTER_KEYS.put("junk_catches",        StatKeys.JUNK_CATCHES);
        COUNTER_KEYS.put("trade_count",         StatKeys.TRADE_COUNT);
        COUNTER_KEYS.put("emeralds_spent",      StatKeys.EMERALDS_SPENT);
        COUNTER_KEYS.put("emeralds_earned",     StatKeys.EMERALDS_EARNED);
        COUNTER_KEYS.put("enchant_count",       StatKeys.ENCHANT_COUNT);
        COUNTER_KEYS.put("enchant_levels_used", StatKeys.ENCHANT_LEVELS_USED);
        COUNTER_KEYS.put("mining_total",        StatKeys.MINING_TOTAL);
        COUNTER_KEYS.put("placement_total",     StatKeys.PLACEMENT_TOTAL);
        COUNTER_KEYS.put("craft_count",         StatKeys.CRAFT_COUNT);
        COUNTER_KEYS.put("consume_total",       StatKeys.CONSUME_TOTAL);
        COUNTER_KEYS.put("food_eaten",          StatKeys.FOOD_EATEN);
        COUNTER_KEYS.put("potions_drunk",       StatKeys.POTIONS_DRUNK);
        COUNTER_KEYS.put("milk_drunk",          StatKeys.MILK_DRUNK);
        COUNTER_KEYS.put("advancement_count",   StatKeys.ADVANCEMENT_COUNT);
        COUNTER_KEYS.put("xp_total",            StatKeys.XP_TOTAL);
        COUNTER_KEYS.put("redstone_placed",     StatKeys.REDSTONE_PLACED);
        COUNTER_KEYS.put("maps_crafted",        StatKeys.MAPS_CRAFTED);
        COUNTER_KEYS.put("farming_tilled",      StatKeys.FARMLAND_TILLED);
        COUNTER_KEYS.put("farming_saplings",    StatKeys.SAPLINGS_PLANTED);
        COUNTER_KEYS.put("jumps",               StatKeys.JUMP_COUNT);
        COUNTER_KEYS.put("sprint_ticks",        StatKeys.SPRINT_TICKS);
        COUNTER_KEYS.put("nether_portals",      StatKeys.NETHER_PORTAL_USES);
        COUNTER_KEYS.put("end_portals",         StatKeys.END_PORTAL_USES);
        COUNTER_KEYS.put("end_gateways",        StatKeys.END_GATEWAY_USES);
        COUNTER_KEYS.put("ender_pearls",        StatKeys.ENDER_PEARL_TELEPORTS);
        COUNTER_KEYS.put("chorus_fruit",        StatKeys.CHORUS_FRUIT_USES);
        COUNTER_KEYS.put("tame_count",          StatKeys.TAME_COUNT);
        COUNTER_KEYS.put("breed_count",         StatKeys.BREED_COUNT);
        COUNTER_KEYS.put("honey_harvests",      StatKeys.HONEY_HARVESTS);
        COUNTER_KEYS.put("doors_opened",        StatKeys.DOORS_OPENED);
        COUNTER_KEYS.put("buttons_pressed",     StatKeys.BUTTONS_PRESSED);
        COUNTER_KEYS.put("pickup_count",        StatKeys.PICKUP_COUNT);
        COUNTER_KEYS.put("drop_count",          StatKeys.DROP_COUNT);
        COUNTER_KEYS.put("chat_messages",        StatKeys.CHAT_MESSAGES);
        COUNTER_KEYS.put("diamond_pickups",     StatKeys.DIAMOND_PICKUPS);
        COUNTER_KEYS.put("arrows_shot",         StatKeys.ARROWS_SHOT);
        COUNTER_KEYS.put("arrow_hits",          StatKeys.ARROW_HITS);
        COUNTER_KEYS.put("ocean_ticks",         StatKeys.OCEAN_BIOME_TICKS);
        COUNTER_KEYS.put("playtime_ms",         StatKeys.PLAYTIME_MS);

        DOUBLE_KEYS.put("walk_distance",    StatKeys.WALK_DISTANCE);
        DOUBLE_KEYS.put("elytra_total",     StatKeys.ELYTRA_TOTAL);
        DOUBLE_KEYS.put("elytra_distance",  StatKeys.ELYTRA_DISTANCE);
        DOUBLE_KEYS.put("boat_distance",    StatKeys.BOAT_DISTANCE);
        DOUBLE_KEYS.put("ride_distance",    StatKeys.RIDE_DISTANCE);
        DOUBLE_KEYS.put("sprint_distance",  StatKeys.SPRINT_DISTANCE);
        DOUBLE_KEYS.put("swim_distance",    StatKeys.SWIM_DISTANCE);
        DOUBLE_KEYS.put("damage_dealt",     StatKeys.DAMAGE_DEALT);
        DOUBLE_KEYS.put("damage_taken",     StatKeys.DAMAGE_TAKEN);

        SET_KEYS.put("biome_count",        StatKeys.VISITED_BIOMES);
        SET_KEYS.put("snow_biome_count",   StatKeys.SNOW_BIOMES);
        SET_KEYS.put("nether_biome_count", StatKeys.NETHER_BIOMES);
    }

    public PAPIExpansion(StatTrackerPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getAPI();
    }

    @Override public String getIdentifier() { return "stattracker"; }
    @Override public String getAuthor()      { return "Sakana"; }
    @Override public String getVersion()     { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()                 { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return null;

        String lower = params.toLowerCase();

        if ("playtime_hours".equals(lower)) {
            return formatHours(api.getCounter(player.getUniqueId(), StatKeys.PLAYTIME_MS) / 3600000.0);
        }
        if ("playtime_days".equals(lower)) {
            return formatHours(api.getCounter(player.getUniqueId(), StatKeys.PLAYTIME_MS) / 86400000.0);
        }
        if ("arrow_accuracy".equals(lower)) {
            long shot = api.getCounter(player.getUniqueId(), StatKeys.ARROWS_SHOT);
            long hits = api.getCounter(player.getUniqueId(), StatKeys.ARROW_HITS);
            return shot > 0 ? String.format("%.1f", hits * 100.0 / shot) : "0.0";
        }
        if ("survival_hours".equals(lower)) {
            long joinTime = api.getCounter(player.getUniqueId(), StatKeys.JOIN_TIME);
            if (joinTime <= 0) return "0.0";
            return formatHours((System.currentTimeMillis() - joinTime) / 3600000.0);
        }
        if ("longest_survival_hours".equals(lower)) {
            return formatHours(api.getCounter(player.getUniqueId(), StatKeys.LONGEST_SURVIVAL_MS) / 3600000.0);
        }

        if (lower.startsWith("counter_")) {
            return String.valueOf(api.getCounter(player.getUniqueId(), params.substring(8)));
        }
        if (lower.startsWith("set_")) {
            return String.valueOf(api.getSetSize(player.getUniqueId(), params.substring(4)));
        }
        if (lower.startsWith("double_")) {
            return String.valueOf((long) api.getDouble(player.getUniqueId(), params.substring(7)));
        }
        if (lower.startsWith("bool_")) {
            return api.getBooleanFlag(player.getUniqueId(), params.substring(5)) ? "是" : "否";
        }

        String counterKey = COUNTER_KEYS.get(lower);
        if (counterKey != null) {
            return String.valueOf(api.getCounter(player.getUniqueId(), counterKey));
        }

        String doubleKey = DOUBLE_KEYS.get(lower);
        if (doubleKey != null) {
            return String.valueOf((long) api.getDouble(player.getUniqueId(), doubleKey));
        }

        String setKey = SET_KEYS.get(lower);
        if (setKey != null) {
            return String.valueOf(api.getSetSize(player.getUniqueId(), setKey));
        }

        return null;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || !player.isOnline()) return null;
        return onPlaceholderRequest(player.getPlayer(), params);
    }

    private String formatHours(double hours) {
        return hours < 10 ? String.format("%.1f", hours) : String.valueOf((long) hours);
    }
}
