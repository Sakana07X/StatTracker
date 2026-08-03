package com.server.stattracker.data;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final JavaPlugin plugin;
    private final Path dataFile;
    private final HashMap<UUID, PlayerTrackData> cache = new HashMap<>(64);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    private volatile Map<UUID, PlayerTrackData> snapshot = Collections.emptyMap();

    public DataManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve(fileName);
    }

    public void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                PlayerTrackData data = deserializeData(entry.getValue().getAsJsonObject());
                cache.put(uuid, data);
            }
            plugin.getLogger().info("已加载 " + cache.size() + " 条玩家数据");
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家数据失败: " + e.getMessage());
        }
    }

        public void saveDirty() {
        if (dirtyPlayers.isEmpty()) return;

        JsonObject root = loadExistingRoot();

        synchronized (cache) {
            for (UUID uuid : dirtyPlayers) {
                PlayerTrackData data = cache.get(uuid);
                if (data != null) {
                    root.add(uuid.toString(), serializeData(data));
                    data.dirty = false;
                }
            }
        }

        dirtyPlayers.clear();

        try {
            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, gson.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
        }
    }

        public void saveAll() {
        try {
            Files.createDirectories(dataFile.getParent());
            JsonObject root = new JsonObject();
            synchronized (cache) {
                for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
                    root.add(entry.getKey().toString(), serializeData(entry.getValue()));
                    entry.getValue().dirty = false;
                }
            }
            Files.writeString(dataFile, gson.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            dirtyPlayers.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
        }
    }

    private JsonObject loadExistingRoot() {
        if (!Files.exists(dataFile)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            return root != null ? root : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public PlayerTrackData get(UUID uuid) {
        synchronized (cache) {
            return cache.computeIfAbsent(uuid, k -> new PlayerTrackData());
        }
    }

    public boolean hasData(UUID uuid) {
        synchronized (cache) {
            return cache.containsKey(uuid);
        }
    }

        public void markDirty(UUID uuid) {
        PlayerTrackData data;
        synchronized (cache) {
            data = cache.get(uuid);
        }
        if (data != null && !data.dirty) {
            data.dirty = true;
            dirtyPlayers.add(uuid);
        }
    }

    private JsonObject serializeData(PlayerTrackData data) {
        JsonObject obj = new JsonObject();

        JsonObject counters = new JsonObject();
        for (Map.Entry<String, Long> e : data.getCountersMap().entrySet()) {
            counters.addProperty(e.getKey(), e.getValue());
        }
        obj.add("c", counters);

        JsonObject sets = new JsonObject();
        for (Map.Entry<String, HashSet<String>> e : data.getSetsMap().entrySet()) {
            JsonArray arr = new JsonArray(e.getValue().size());
            for (String v : e.getValue()) arr.add(v);
            sets.add(e.getKey(), arr);
        }
        obj.add("s", sets);

        JsonObject doubles = new JsonObject();
        for (Map.Entry<String, Double> e : data.getDoublesMap().entrySet()) {
            doubles.addProperty(e.getKey(), e.getValue());
        }
        obj.add("d", doubles);

        return obj;
    }

    private PlayerTrackData deserializeData(JsonObject obj) {
        PlayerTrackData data = new PlayerTrackData();

        JsonObject counters = obj.has("c") ? obj.getAsJsonObject("c")
            : obj.has("counters") ? obj.getAsJsonObject("counters") : null;
        if (counters != null) {
            for (Map.Entry<String, JsonElement> e : counters.entrySet()) {
                data.setCounter(e.getKey(), e.getValue().getAsLong());
            }
        }

        JsonObject sets = obj.has("s") ? obj.getAsJsonObject("s")
            : obj.has("sets") ? obj.getAsJsonObject("sets") : null;
        if (sets != null) {
            for (Map.Entry<String, JsonElement> e : sets.entrySet()) {
                for (JsonElement v : e.getValue().getAsJsonArray()) {
                    data.addToSet(e.getKey(), v.getAsString());
                }
            }
        }

        JsonObject doubles = obj.has("d") ? obj.getAsJsonObject("d")
            : obj.has("doubles") ? obj.getAsJsonObject("doubles") : null;
        if (doubles != null) {
            for (Map.Entry<String, JsonElement> e : doubles.entrySet()) {
                data.setDouble(e.getKey(), e.getValue().getAsDouble());
            }
        }

        return data;
    }
}
