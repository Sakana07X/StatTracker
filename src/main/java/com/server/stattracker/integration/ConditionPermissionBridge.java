package com.server.stattracker.integration;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.condition.ConditionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionPermissionBridge implements Listener {

    private final StatTrackerPlugin plugin;
    private final ConditionManager cm;
    private final PermissionBridge permBridge;

    // 每个玩家上次的条件状态，用于检测"满足 -> 不满足"翻转
    private final Map<UUID, Map<String, Boolean>> lastState = new ConcurrentHashMap<>();

    public ConditionPermissionBridge(StatTrackerPlugin plugin,
                                     ConditionManager cm,
                                     PermissionBridge permBridge) {
        this.plugin = plugin;
        this.cm = cm;
        this.permBridge = permBridge;
    }

    // 进服延迟 40 tick 检查一次，等数据加载完成
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getScheduler().runDelayed(40, () -> checkAll(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastState.remove(event.getPlayer().getUniqueId());
    }

    // 由主类定时调用（建议每 30 秒）
    public void flush() {
        if (!permBridge.isAvailable()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAll(player);
        }
    }

    private void checkAll(Player player) {
        if (!permBridge.isAvailable()) return;
        UUID uuid = player.getUniqueId();

        Map<String, Boolean> now = new HashMap<>();
        for (ConditionManager.Condition c : cm.getAllConditions()) {
            if (c.permission == null || c.permission.isEmpty()) continue;
            now.put(c.id, cm.isMet(uuid, c));
        }

        // 首次见到该玩家：以权限实际状态为基线，重启后也能正确回收
        Map<String, Boolean> prev = lastState.get(uuid);
        if (prev == null) {
            prev = new HashMap<>();
            for (String id : now.keySet()) {
                prev.put(id, permBridge.hasPermission(uuid, cm.getCondition(id).permission));
            }
        }

        for (Map.Entry<String, Boolean> e : now.entrySet()) {
            String id = e.getKey();
            boolean met = e.getValue();
            boolean wasMet = prev.getOrDefault(id, false);
            if (met == wasMet) continue;

            ConditionManager.Condition c = cm.getCondition(id);
            if (met) {
                permBridge.grantPermission(uuid, c.permission);
                plugin.getLogger().info("[" + player.getName() + "] 条件达成 " + id
                    + " -> 授予 " + c.permission);
            } else {
                permBridge.revokePermission(uuid, c.permission);
                plugin.getLogger().info("[" + player.getName() + "] 条件失效 " + id
                    + " -> 回收 " + c.permission);
            }
        }
        lastState.put(uuid, now);
    }
}
