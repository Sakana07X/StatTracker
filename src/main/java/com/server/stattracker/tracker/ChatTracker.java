package com.server.stattracker.tracker;

// 聊天追踪器：消息计数、消息总长度、频道使用、指令使用
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public ChatTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        data.increment(StatKeys.CHAT_MESSAGES);
        data.increment(StatKeys.CHAT_LENGTH_TOTAL, event.getMessage().length());

        data.addToSet(StatKeys.CHAT_CHANNELS_USED, "default");

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.CHAT_COMMANDS);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
