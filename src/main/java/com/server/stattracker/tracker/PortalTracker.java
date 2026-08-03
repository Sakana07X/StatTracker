package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
public class PortalTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public PortalTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        switch (cause) {
            case NETHER_PORTAL -> data.increment(StatKeys.NETHER_PORTAL_USES);
            case END_PORTAL -> data.increment(StatKeys.END_PORTAL_USES);
            case END_GATEWAY -> data.increment(StatKeys.END_GATEWAY_USES);
            case ENDER_PEARL -> data.increment(StatKeys.ENDER_PEARL_TELEPORTS);
            case CHORUS_FRUIT -> data.increment(StatKeys.CHORUS_FRUIT_USES);
            default -> { return; }
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
