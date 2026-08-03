package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.DataManager;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.generator.structure.Structure;

import java.util.*;

public class MovementTracker implements Listener {

    private final StatTrackerPlugin plugin;

    private static final Set<String> OCEAN_BIOMES = Set.of(
        "OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "DEEP_COLD_OCEAN",
        "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN", "LUKEWARM_OCEAN",
        "DEEP_LUKEWARM_OCEAN", "WARM_OCEAN"
    );

    private static final Set<String> NETHER_BIOME_NAMES = Set.of(
        "NETHER_WASTES", "CRIMSON_FOREST", "WARPED_FOREST",
        "SOUL_SAND_VALLEY", "BASALT_DELTAS"
    );

    private static final Set<String> SNOWY_BIOME_NAMES = Set.of(
        "SNOWY_PLAINS", "SNOWY_TAIGA", "SNOWY_BEACH", "SNOWY_SLOPES",
        "ICE_SPIKES", "FROZEN_PEAKS", "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN",
        "FROZEN_RIVER", "GROVE"
    );

    private static final Map<String, Structure> NETHER_STRUCTURES = new LinkedHashMap<>();
    static {
        NETHER_STRUCTURES.put("NETHER_FORTRESS", Structure.FORTRESS);
        NETHER_STRUCTURES.put("BASTION_REMNANT", Structure.BASTION_REMNANT);
        NETHER_STRUCTURES.put("RUINED_PORTAL", Structure.RUINED_PORTAL);
    }

    private static final double BIOME_CHECK_DIST_SQ = 2500;
    private static final double STRUCT_CHECK_DIST_SQ = 900;
    private static final long OCEAN_FLUSH_INTERVAL_MS = 1000;

    private final HashMap<UUID, PlayerMoveState> moveStates = new HashMap<>(64);
    private final HashSet<UUID> sprinting = new HashSet<>(16);
    private final HashSet<UUID> movedPlayers = new HashSet<>(64);

    private static class PlayerMoveState {
        Location lastBiomeCheck;
        Location lastStructCheck;
        String currentBiome;
        long lastOceanCheckMs;
        boolean biomeNeedsUpdate = true;
    }

    public MovementTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockZ() == to.getBlockZ()
            && from.getBlockY() == to.getBlockY()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        movedPlayers.add(uuid);
        PlayerTrackData data = plugin.getDataManager().get(uuid);

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistSq = dx * dx + dz * dz;

        if (horizontalDistSq > 0.000004) {
            double horizontalDist = Math.sqrt(horizontalDistSq);

            if (player.isOnGround() && !player.isGliding() && !player.isInsideVehicle()) {
                data.addDouble(StatKeys.WALK_DISTANCE, horizontalDist);
            }
            if (sprinting.contains(uuid) && player.isOnGround()) {
                data.addDouble(StatKeys.SPRINT_DISTANCE, horizontalDist);
            }
            if (player.isSwimming() || player.isInWater()) {
                data.addDouble(StatKeys.SWIM_DISTANCE, horizontalDist);
            }
            if (player.isGliding()) {
                if (player.isOnGround()) {
                    flushElytra(data);
                } else {
                    data.addDouble(StatKeys.ELYTRA_DISTANCE, horizontalDist);
                }
            }
        }

        double dy = to.getY() - from.getY();
        if (dy > 0.35 && dy < 1.5 && player.isOnGround()) {
            data.increment(StatKeys.JUMP_COUNT);
        }

        PlayerMoveState state = moveStates.computeIfAbsent(uuid, k -> new PlayerMoveState());

        if (state.biomeNeedsUpdate || shouldCheck(state.lastBiomeCheck, to, BIOME_CHECK_DIST_SQ)) {
            state.biomeNeedsUpdate = false;
            state.lastBiomeCheck = to.clone();
            String biome = to.getBlock().getBiome().name();

            if (!biome.equals(state.currentBiome)) {
                state.currentBiome = biome;
                data.addToSet(StatKeys.VISITED_BIOMES, biome);
                if (NETHER_BIOME_NAMES.contains(biome)) data.addToSet(StatKeys.NETHER_BIOMES, biome);
                if (SNOWY_BIOME_NAMES.contains(biome)) data.addToSet(StatKeys.SNOW_BIOMES, biome);
            }

            if (OCEAN_BIOMES.contains(biome)) {
                long now = System.currentTimeMillis();
                if (now - state.lastOceanCheckMs >= OCEAN_FLUSH_INTERVAL_MS) {
                    data.increment(StatKeys.OCEAN_BIOME_TICKS, Math.min((now - state.lastOceanCheckMs) / 50, 200));
                    state.lastOceanCheckMs = now;
                }
            }
        }

        if (to.getWorld().getEnvironment() == World.Environment.NETHER
            && shouldCheck(state.lastStructCheck, to, STRUCT_CHECK_DIST_SQ)) {
            state.lastStructCheck = to.clone();
            for (var entry : NETHER_STRUCTURES.entrySet()) {
                var result = to.getWorld().locateNearestStructure(to, entry.getValue(), 10, false);
                if (result != null && result.getLocation().distanceSquared(to) < 400) {
                    data.addToSet(StatKeys.NETHER_STRUCTURES, entry.getKey());
                    break;
                }
            }
        }
    }

    private boolean shouldCheck(Location last, Location to, double distSq) {
        if (last == null) return true;
        if (last.getWorld() != to.getWorld()) return true;
        return last.distanceSquared(to) >= distSq;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) sprinting.add(event.getPlayer().getUniqueId());
        else sprinting.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        moveStates.remove(uuid);
        sprinting.remove(uuid);
        movedPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()) return;
        double dist = from.distance(to);
        if (dist < 0.01) return;

        DataManager dm = plugin.getDataManager();
        if (vehicle instanceof Boat) {
            Entity passenger = vehicle.getPassenger();
            if (passenger instanceof Player player) {
                dm.get(player.getUniqueId()).addDouble(StatKeys.BOAT_DISTANCE, dist);
                dm.markDirty(player.getUniqueId());
            }
        } else {
            for (Entity passenger : vehicle.getPassengers()) {
                if (passenger instanceof Player player) {
                    dm.get(player.getUniqueId()).addDouble(StatKeys.RIDE_DISTANCE, dist);
                    dm.markDirty(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
            && event.getEntity() instanceof Player player) {
            flushElytra(plugin.getDataManager().get(player.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        flushElytra(plugin.getDataManager().get(event.getEntity().getUniqueId()));
    }

    private void flushElytra(PlayerTrackData data) {
        double current = data.getDouble(StatKeys.ELYTRA_DISTANCE);
        if (current > 0) {
            data.addDouble(StatKeys.ELYTRA_TOTAL, current);
            data.setDouble(StatKeys.ELYTRA_DISTANCE, 0);
        }
    }

    public void flushDirty() {
        if (movedPlayers.isEmpty()) return;
        DataManager dm = plugin.getDataManager();
        for (UUID uuid : movedPlayers) {
            dm.markDirty(uuid);
        }
        movedPlayers.clear();
    }
}
