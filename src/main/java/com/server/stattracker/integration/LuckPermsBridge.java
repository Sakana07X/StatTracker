package com.server.stattracker.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public class LuckPermsBridge {

    private final LuckPerms luckPerms;
    private final JavaPlugin plugin;

    public LuckPermsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = initLuckPerms();
    }

    private LuckPerms initLuckPerms() {
        try {
            return LuckPermsProvider.get();
        } catch (Exception e) {
            plugin.getLogger().info("LuckPerms 未检测到，权限桥接已禁用");
            return null;
        }
    }

        public boolean isAvailable() {
        return luckPerms != null;
    }

        public void grantPermission(UUID uuid, String permission) {
        if (luckPerms == null) return;
        luckPerms.getUserManager().modifyUser(uuid, user ->
            user.data().add(PermissionNode.builder(permission).build()));
    }

        public void revokePermission(UUID uuid, String permission) {
        if (luckPerms == null) return;
        luckPerms.getUserManager().modifyUser(uuid, user ->
            user.data().remove(PermissionNode.builder(permission).build()));
    }

        public boolean hasPermission(UUID uuid, String permission) {
        if (luckPerms == null) return false;
        var user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return false;
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }
}
