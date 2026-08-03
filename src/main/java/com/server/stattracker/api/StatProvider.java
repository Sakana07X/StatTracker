package com.server.stattracker.api;

import com.server.stattracker.data.DataManager;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StatProvider {

    private final DataManager dataManager;

    public StatProvider(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public long getCounter(Player player, String key) {
        return dataManager.get(player.getUniqueId()).getCounter(key);
    }

    public long getCounter(UUID uuid, String key) {
        return dataManager.get(uuid).getCounter(key);
    }

    public int getSetSize(Player player, String key) {
        return dataManager.get(player.getUniqueId()).getSetSize(key);
    }

    public int getSetSize(UUID uuid, String key) {
        return dataManager.get(uuid).getSetSize(key);
    }

    public boolean setContains(Player player, String key, String value) {
        return dataManager.get(player.getUniqueId()).setContains(key, value);
    }

    public double getDouble(Player player, String key) {
        return dataManager.get(player.getUniqueId()).getDouble(key);
    }

    public double getDouble(UUID uuid, String key) {
        return dataManager.get(uuid).getDouble(key);
    }

    public boolean getBooleanFlag(Player player, String key) {
        return dataManager.get(player.getUniqueId()).getBooleanFlag(key);
    }

    public boolean getBooleanFlag(UUID uuid, String key) {
        return dataManager.get(uuid).getBooleanFlag(key);
    }

    public PlayerTrackData getRawData(Player player) {
        return dataManager.get(player.getUniqueId());
    }

    public PlayerTrackData getRawData(UUID uuid) {
        return dataManager.get(uuid);
    }

    public void increment(Player player, String key) {
        dataManager.get(player.getUniqueId()).increment(key);
        dataManager.markDirty(player.getUniqueId());
    }

    public void increment(Player player, String key, long amount) {
        dataManager.get(player.getUniqueId()).increment(key, amount);
        dataManager.markDirty(player.getUniqueId());
    }
}
