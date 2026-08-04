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
        return getCounter(player.getUniqueId(), key);
    }

    public long getCounter(UUID uuid, String key) {
        PlayerTrackData data = dataManager.getIfPresent(uuid);
        return data != null ? data.getCounter(key) : 0L;
    }

    public int getSetSize(Player player, String key) {
        return getSetSize(player.getUniqueId(), key);
    }

    public int getSetSize(UUID uuid, String key) {
        PlayerTrackData data = dataManager.getIfPresent(uuid);
        return data != null ? data.getSetSize(key) : 0;
    }

    public boolean setContains(Player player, String key, String value) {
        return setContains(player.getUniqueId(), key, value);
    }

    public boolean setContains(UUID uuid, String key, String value) {
        PlayerTrackData data = dataManager.getIfPresent(uuid);
        return data != null && data.setContains(key, value);
    }

    public double getDouble(Player player, String key) {
        return getDouble(player.getUniqueId(), key);
    }

    public double getDouble(UUID uuid, String key) {
        PlayerTrackData data = dataManager.getIfPresent(uuid);
        return data != null ? data.getDouble(key) : 0.0;
    }

    public boolean getBooleanFlag(Player player, String key) {
        return getBooleanFlag(player.getUniqueId(), key);
    }

    public boolean getBooleanFlag(UUID uuid, String key) {
        PlayerTrackData data = dataManager.getIfPresent(uuid);
        return data != null && data.getBooleanFlag(key);
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
