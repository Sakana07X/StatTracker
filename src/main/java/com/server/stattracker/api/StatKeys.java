package com.server.stattracker.api;
public final class StatKeys {
    private StatKeys() {}

    // ---- Weapon / Kill Context ----
    public static final String KILL_WEAPON_PREFIX    = "combat.weapon.";
    public static final String KILL_METHOD_PREFIX    = "combat.kill_method.";
    public static final String KILL_DIM_PREFIX       = "combat.kill_dim.";
    public static final String KILL_BIOME_PREFIX     = "combat.kill_biome.";
    public static final String KILL_STREAK           = "combat.kill_streak";
    public static final String BEST_KILL_STREAK      = "combat.best_kill_streak";
    public static final String FIRST_KILL_PREFIX     = "combat.first_kill.";

    // ---- Death Detail ----
    public static final String DEATH_CAUSE_COUNT_PREFIX = "death.cause_count.";
    public static final String LOWEST_HEALTH         = "death.lowest_health";

    // ---- Breeding per-species ----
    public static final String BREED_SPECIES_PREFIX  = "breeding.";

    // ---- Fishing Detail ----
    public static final String FISHING_CASTS         = "fishing.casts";
    public static final String FISHING_CATCH_PREFIX  = "fishing.catch.";

    // ---- Enchanting Detail ----
    public static final String ENCHANT_TYPE_PREFIX   = "enchant.type.";
    public static final String ENCHANT_ITEM_PREFIX   = "enchant.item.";

    // ---- Consumption Detail ----
    public static final String CONSUME_FOOD_PREFIX   = "consume.food.";

    // ---- Interaction Detail ----
    public static final String INTERACT_DOOR_PREFIX  = "interact.door.";
    public static final String INTERACT_BUTTON_PREFIX = "interact.button.";

    // ---- Chat Detail ----
    public static final String CHAT_LENGTH_TOTAL     = "chat.length_total";
    public static final String CHAT_COMMANDS         = "chat.commands";

    // ---- Movement Detail ----
    public static final String HIGHEST_Y             = "movement.highest_y";
    public static final String LOWEST_Y              = "movement.lowest_y";
    public static final String VEHICLE_COUNT         = "movement.vehicle_count";
    public static final String FIREWORK_USES         = "movement.firework_uses";

    // ---- Misc Tracker ----
    public static final String GOLEM_BUILDS          = "misc.golem_builds";
    public static final String SNOWMAN_BUILDS        = "misc.snowman_builds";
    public static final String WITHER_SPAWNS         = "misc.wither_spawns";
    public static final String NAME_TAG_USES         = "misc.name_tag_uses";
    public static final String NETHERITE_UPGRADES    = "misc.netherite_upgrades";
    public static final String BEACON_INTERACTS      = "misc.beacon_interacts";
    public static final String CAMPFIRES_PLACED      = "misc.campfires_placed";
    public static final String LEASH_USES            = "misc.leash_uses";
    public static final String SHIELD_BLOCKS         = "misc.shield_blocks";
    public static final String TOTEM_USES            = "misc.totem_uses";
    public static final String SCAFFOLD_PLACED       = "misc.scaffold_placed";
    public static final String BANNER_PLACED         = "misc.banner_placed";

    // Mining
    public static final String MINING_TOTAL          = "mining.total";
    public static final String MINING_PREFIX         = "mining.mat.";
    public static final String STONE_BROKEN          = "mining.mat.STONE";
    public static final String DIAMOND_ORE_BROKEN    = "mining.mat.DIAMOND_ORE";
    public static final String DEEPSLATE_DIAMOND     = "mining.mat.DEEPSLATE_DIAMOND_ORE";
    public static final String ANCIENT_DEBRIS        = "mining.mat.ANCIENT_DEBRIS";
    public static final String LOGS_PREFIX           = "mining.log.";
    // Placement
    public static final String PLACEMENT_TOTAL       = "placement.total";
    public static final String PLACEMENT_PREFIX      = "placement.mat.";
    // Crafting
    public static final String CRAFT_COUNT           = "crafting.count";
    public static final String CRAFTED_ITEMS         = "crafting.items";
    public static final String CRAFTED_FOODS         = "crafting.foods";
    // Combat
    public static final String MOB_KILLS             = "combat.mob_kills";
    public static final String KILL_PREFIX           = "combat.kill.";
    public static final String PIGLIN_KILLS          = "combat.kill.PIGLIN";
    public static final String BLAZE_KILLS           = "combat.kill.BLAZE";
    public static final String WITHER_SKELLY_KILLS   = "combat.kill.WITHER_SKELETON";
    public static final String PLAYER_KILLS          = "combat.player_kills";
    public static final String DAMAGE_DEALT          = "combat.damage_dealt";
    public static final String DAMAGE_TAKEN          = "combat.damage_taken";
    public static final String DMG_TAKEN_PREFIX      = "combat.dmg_taken.";
    public static final String DMG_DEALT_PREFIX      = "combat.dmg_dealt.";
    // Death
    public static final String DEATHS                = "death.total";
    public static final String DEATH_CAUSE_PREFIX    = "death.cause.";
    public static final String LAST_DEATH_TIME       = "death.last_time_ms";
    public static final String LAST_DEATH_X          = "death.last_x";
    public static final String LAST_DEATH_Y          = "death.last_y";
    public static final String LAST_DEATH_Z          = "death.last_z";
    // Fishing
    public static final String FISH_CAUGHT           = "fishing.caught";
    public static final String TREASURE_CATCHES      = "fishing.treasure";
    public static final String JUNK_CATCHES          = "fishing.junk";
    // Brewing
    public static final String BREWING_PREFIX        = "brewing.";
    // Consumables
    public static final String CONSUME_TOTAL         = "consume.total";
    public static final String CONSUME_PREFIX        = "consume.mat.";
    public static final String FOOD_EATEN            = "consume.food_eaten";
    public static final String POTIONS_DRUNK         = "consume.potions_drunk";
    public static final String MILK_DRUNK            = "consume.milk";
    // Enchanting
    public static final String ENCHANT_COUNT         = "enchant.count";
    public static final String ENCHANT_LEVELS_USED   = "enchant.levels_used";
    public static final String ENCHANT_LEVEL_PREFIX  = "enchant.level.";
    // Trading
    public static final String TRADE_COUNT           = "trade.count";
    public static final String EMERALDS_SPENT        = "trade.emeralds_spent";
    public static final String EMERALDS_EARNED       = "trade.emeralds_earned";
    public static final String TRADED_PROFESSIONS    = "trade.professions";
    // Movement
    public static final String WALK_DISTANCE         = "movement.walk_distance";
    public static final String ELYTRA_DISTANCE       = "movement.elytra_distance";
    public static final String ELYTRA_TOTAL          = "movement.elytra_total";
    public static final String BOAT_DISTANCE         = "movement.boat_distance";
    public static final String RIDE_DISTANCE         = "movement.ride_distance";
    public static final String SPRINT_DISTANCE       = "movement.sprint_distance";
    public static final String SWIM_DISTANCE         = "movement.swim_distance";
    public static final String JUMP_COUNT            = "movement.jumps";
    public static final String SPRINT_TICKS          = "movement.sprint_ticks";
    public static final String VISITED_BIOMES        = "movement.biomes";
    public static final String NETHER_BIOMES         = "movement.nether_biomes";
    public static final String NETHER_STRUCTURES     = "movement.nether_structures";
    public static final String OCEAN_BIOME_TICKS     = "movement.ocean_ticks";
    // Dimension / Portal
    public static final String ENTERED_NETHER        = "dimension.nether";
    public static final String ENTERED_END           = "dimension.end";
    public static final String NETHER_PORTAL_USES    = "portal.nether";
    public static final String END_PORTAL_USES       = "portal.end";
    public static final String END_GATEWAY_USES      = "portal.end_gateway";
    public static final String ENDER_PEARL_THROWS    = "teleport.ender_pearl";
    public static final String ENDER_PEARL_TELEPORTS = "teleport.ender_pearl_success";
    public static final String CHORUS_FRUIT_USES     = "teleport.chorus";
    // Projectile
    public static final String ARROWS_SHOT           = "projectile.arrows_shot";
    public static final String ARROW_HITS            = "projectile.arrow_hits";
    public static final String TRIDENT_THROWS        = "projectile.trident_throws";
    // Taming / Breeding
    public static final String TAME_COUNT            = "taming.count";
    public static final String TAME_PREFIX           = "taming.";
    public static final String BREED_COUNT           = "breeding.count";
    public static final String BEE_BREEDS            = "breeding.bee";
    public static final String HONEY_HARVESTS        = "farming.honey";
    // Farming
    public static final String FARMLAND_TILLED       = "farming.tilled";
    public static final String SAPLINGS_PLANTED      = "farming.saplings";
    public static final String HARVEST_PREFIX        = "farming.harvest.";
    // Redstone
    public static final String REDSTONE_PLACED       = "redstone.placed";
    // Map
    public static final String MAPS_CRAFTED          = "map.crafted";
    // Container / Structure
    public static final String TREASURE_STRUCTS      = "container.treasure_types";
    public static final String OCEAN_STRUCTS         = "container.ocean_types";
    public static final String BURIED_TREASURES      = "container.buried_treasures";
    public static final String ANCIENT_CHESTS        = "container.ancient_types";
    public static final String RUINS_FOUND           = "container.ruins_types";
    public static final String SNOW_BIOMES           = "movement.snow_biomes";
    public static final String COLLECTOR_CHESTS      = "container.collector_types";
    // Item
    public static final String PICKUP_COUNT          = "item.pickup_count";
    public static final String DROP_COUNT            = "item.drop_count";
    public static final String PICKUP_PREFIX         = "item.pickup.";
    public static final String DIAMOND_PICKUPS       = "item.pickup.DIAMOND";
    public static final String VOID_PICKUPS          = "item.void_pickups";
    public static final String VOID_PICKUP_PREFIX    = "item.void_pickup.";
    // Interaction
    public static final String DOORS_OPENED          = "interact.doors";
    public static final String BUTTONS_PRESSED       = "interact.buttons";
    public static final String PRESSURE_PLATES       = "interact.pressure_plates";
    // Advancement
    public static final String ADVANCEMENT_COUNT     = "advancement.count";
    public static final String ADVANCEMENTS_DONE     = "advancement.keys";
    // XP
    public static final String XP_TOTAL              = "xp.total";
    // Chat
    public static final String CHAT_MESSAGES          = "chat.messages";
    public static final String CHAT_CHANNEL_PREFIX    = "chat.channel.";
    public static final String CHAT_CHANNELS_USED     = "chat.channels_used";
    // Playtime / Survival
    public static final String PLAYTIME_MS           = "playtime.total_ms";
    public static final String JOIN_TIME             = "survival.join_ms";
    public static final String LONGEST_SURVIVAL_MS   = "survival.longest_ms";
}
