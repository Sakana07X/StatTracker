package com.server.stattracker.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class VaultPermissionBridge implements PermissionBridge {

    private final Object vaultPerm;
    private final Method playerAdd;
    private final Method playerRemove;
    private final Method playerHas;

    public VaultPermissionBridge(JavaPlugin plugin) {
        Object perm = null;
        Method add = null;
        Method remove = null;
        Method has = null;
        try {
            Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(permissionClass);
            if (rsp != null) {
                perm = rsp.getProvider();
                add = perm.getClass().getMethod("playerAdd", Player.class, String.class);
                remove = perm.getClass().getMethod("playerRemove", Player.class, String.class);
                has = perm.getClass().getMethod("playerHas", Player.class, String.class);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault 权限服务不可用，Vault 权限桥禁用");
        }
        this.vaultPerm = perm;
        this.playerAdd = add;
        this.playerRemove = remove;
        this.playerHas = has;
    }

    @Override
    public boolean isAvailable() {
        return vaultPerm != null;
    }

    @Override
    public String getName() {
        return "Vault";
    }

    @Override
    public void grantPermission(UUID uuid, String permission) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        invoke(playerAdd, player, permission);
    }

    @Override
    public void revokePermission(UUID uuid, String permission) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        invoke(playerRemove, player, permission);
    }

    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return Boolean.TRUE.equals(invoke(playerHas, player, permission));
    }

    private Object invoke(Method method, Player player, String permission) {
        try {
            return method.invoke(vaultPerm, player, permission);
        } catch (Exception e) {
            return null;
        }
    }
}
