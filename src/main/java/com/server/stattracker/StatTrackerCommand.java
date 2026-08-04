package com.server.stattracker;

import com.server.stattracker.condition.ConditionManager;
import com.server.stattracker.data.PlayerTrackData;
import com.server.stattracker.tracker.VanillaSeeder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StatTrackerCommand implements CommandExecutor, TabCompleter {

    private final StatTrackerPlugin plugin;

    public StatTrackerCommand(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "stats" -> stats(sender, args);
            case "conditions" -> conditions(sender, args);
            case "reset" -> reset(sender, args);
            case "seed" -> seed(sender, args);
            case "save" -> save(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("reload", "stats", "conditions", "reset", "seed", "save")
                .stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void reload(CommandSender sender) {
        plugin.reloadPlugin();
        int count = plugin.getConditionManager() != null
            ? plugin.getConditionManager().getAllConditions().size() : 0;
        sender.sendMessage("StatTracker 已重载: " + count + " 个条件");
    }

    private void stats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /stattracker stats <玩家>");
            return;
        }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("找不到玩家 " + args[1]);
            return;
        }
        PlayerTrackData data = plugin.getDataManager().getIfPresent(uuid);
        if (data == null) {
            sender.sendMessage(args[1] + " 暂无数据");
            return;
        }
        sender.sendMessage(args[1] + " 的统计数据:");
        for (Map.Entry<String, Long> e : data.getCountersMap().entrySet()) {
            sender.sendMessage("  " + e.getKey() + ": " + e.getValue());
        }
        for (Map.Entry<String, Double> e : data.getDoublesMap().entrySet()) {
            sender.sendMessage("  " + e.getKey() + ": " + String.format("%.1f", e.getValue()));
        }
        for (Map.Entry<String, Set<String>> e : data.getSetsMap().entrySet()) {
            sender.sendMessage("  " + e.getKey() + ": " + e.getValue().size() + " 项");
        }
    }

    private void conditions(CommandSender sender, String[] args) {
        UUID uuid;
        if (args.length >= 2) {
            uuid = resolveUuid(args[1]);
            if (uuid == null) {
                sender.sendMessage("找不到玩家 " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            uuid = player.getUniqueId();
        } else {
            sender.sendMessage("用法: /stattracker conditions [玩家]");
            return;
        }
        ConditionManager cm = plugin.getConditionManager();
        if (cm == null) {
            sender.sendMessage("条件系统未启用");
            return;
        }
        sender.sendMessage("条件状态:");
        for (ConditionManager.Condition c : cm.getAllConditions()) {
            sender.sendMessage("  " + c.id + ": " + (cm.isMet(uuid, c) ? "已达成" : "未达成"));
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /stattracker reset <玩家>");
            return;
        }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("找不到玩家 " + args[1]);
            return;
        }
        plugin.getDataManager().reset(uuid);
        sender.sendMessage("已重置 " + args[1] + " 的数据");
    }

    private void seed(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /stattracker seed <玩家>");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("玩家 " + args[1] + " 不在线");
            return;
        }
        VanillaSeeder seeder = plugin.getVanillaSeeder();
        if (seeder == null) {
            sender.sendMessage("原版回填未启用 (seed.vanilla_stats)");
            return;
        }
        seeder.seed(player);
        sender.sendMessage("已为 " + args[1] + " 重新回填原版统计");
    }

    private void save(CommandSender sender) {
        plugin.getDataManager().saveAll();
        sender.sendMessage("数据已保存");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("StatTracker 命令:");
        sender.sendMessage("  /stattracker reload - 重载配置与条件");
        sender.sendMessage("  /stattracker stats <玩家> - 查看玩家统计");
        sender.sendMessage("  /stattracker conditions [玩家] - 查看条件达成状态");
        sender.sendMessage("  /stattracker reset <玩家> - 重置玩家数据");
        sender.sendMessage("  /stattracker seed <玩家> - 重新回填原版统计");
        sender.sendMessage("  /stattracker save - 立即保存数据");
    }

    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline != null ? offline.getUniqueId() : null;
    }
}
