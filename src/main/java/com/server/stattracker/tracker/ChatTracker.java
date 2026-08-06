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

    // 指令防刷：每人每 1000ms 最多计 1 次
    private final java.util.Map<java.util.UUID, Long> cmdCooldown = new java.util.HashMap<>(16);
    private static final long CMD_COOLDOWN_MS = 1000;
    private static final int MAX_MSG_LENGTH = 256;

    public ChatTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        data.increment(StatKeys.CHAT_MESSAGES);
        data.increment(StatKeys.CHAT_LENGTH_TOTAL, Math.min(event.getMessage().length(), MAX_MSG_LENGTH));

        data.addToSet(StatKeys.CHAT_CHANNELS_USED, "default");

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cmdCooldown.get(player.getUniqueId());
        if (last != null && now - last < CMD_COOLDOWN_MS) return;
        cmdCooldown.put(player.getUniqueId(), now);

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.CHAT_COMMANDS);
        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
