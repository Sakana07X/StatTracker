package com.server.stattracker.tracker;

// 交互追踪器：门、活板门、按钮、压力板，按材质细分
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;
public class InteractionTracker implements Listener {

    private final StatTrackerPlugin plugin;

    // 压力板防刷：每人每 500ms 最多计 1 次
    private final java.util.Map<java.util.UUID, Long> plateCooldown = new java.util.HashMap<>(16);
    private static final long PLATE_COOLDOWN_MS = 500;

    private static final Set<Material> BUTTONS = Set.of(
        Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
        Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON,
        Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
        Material.POLISHED_BLACKSTONE_BUTTON
    );

    private static final Set<Material> PRESSURE_PLATES = Set.of(
        Material.STONE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE,
        Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
        Material.SPRUCE_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE,
        Material.JUNGLE_PRESSURE_PLATE, Material.ACACIA_PRESSURE_PLATE,
        Material.DARK_OAK_PRESSURE_PLATE, Material.MANGROVE_PRESSURE_PLATE,
        Material.CHERRY_PRESSURE_PLATE, Material.BAMBOO_PRESSURE_PLATE,
        Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE,
        Material.POLISHED_BLACKSTONE_PRESSURE_PLATE
    );

    public InteractionTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material mat = block.getType();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        if (block.getBlockData() instanceof Door || block.getBlockData() instanceof TrapDoor) {
            // 只在门从关变开时计数，避免反复右键刷数据
            boolean isOpen = false;
            if (block.getBlockData() instanceof Door door) isOpen = door.isOpen();
            else if (block.getBlockData() instanceof TrapDoor trap) isOpen = trap.isOpen();
            if (!isOpen) {
                data.increment(StatKeys.DOORS_OPENED);
                data.increment(StatKeys.INTERACT_DOOR_PREFIX + mat.name());
                plugin.getDataManager().markDirty(player.getUniqueId());
            }
        } else if (BUTTONS.contains(mat)) {
            data.increment(StatKeys.BUTTONS_PRESSED);
            data.increment(StatKeys.INTERACT_BUTTON_PREFIX + mat.name());
            plugin.getDataManager().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPressurePlate(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;

        Material mat = event.getClickedBlock().getType();
        if (!PRESSURE_PLATES.contains(mat)) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = plateCooldown.get(player.getUniqueId());
        if (last != null && now - last < PLATE_COOLDOWN_MS) return;
        plateCooldown.put(player.getUniqueId(), now);

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.PRESSURE_PLATES);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
