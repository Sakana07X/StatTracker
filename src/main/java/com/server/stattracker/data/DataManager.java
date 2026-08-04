package com.server.stattracker.data;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class DataManager {

    private final JavaPlugin plugin;
    private final Path dataFile;
    private final ConcurrentHashMap<UUID, PlayerTrackData> cache = new ConcurrentHashMap<>(64);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    private JsonObject root = new JsonObject();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StatTracker-IO");
        t.setDaemon(true);
        return t;
    });

    public DataManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve(fileName);
    }

    public void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject parsed = gson.fromJson(reader, JsonObject.class);
            if (parsed == null) return;
            root = parsed;
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

        Set<UUID> toSave = new HashSet<>(dirtyPlayers);
        for (UUID uuid : toSave) {
            PlayerTrackData data = cache.get(uuid);
            if (data != null) {
                data.dirty = false;
                root.add(uuid.toString(), serializeData(data));
                if (data.dirty) {
                    dirtyPlayers.add(uuid);
                } else {
                    dirtyPlayers.remove(uuid);
                }
            } else {
                dirtyPlayers.remove(uuid);
            }
        }

        writeAsync(gson.toJson(root));
    }

    public void saveAll() {
        try {
            JsonObject newRoot = new JsonObject();
            for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
                PlayerTrackData data = entry.getValue();
                data.dirty = false;
                newRoot.add(entry.getKey().toString(), serializeData(data));
                if (data.dirty) {
                    dirtyPlayers.add(entry.getKey());
                } else {
                    dirtyPlayers.remove(entry.getKey());
                }
            }
            root = newRoot;
            writeAsync(gson.toJson(root));
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
        }
    }

    public PlayerTrackData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new PlayerTrackData());
    }

    public boolean hasData(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public PlayerTrackData getIfPresent(UUID uuid) {
        return cache.get(uuid);
    }

    public void markDirty(UUID uuid) {
        PlayerTrackData data = cache.get(uuid);
        if (data != null && !data.dirty) {
            data.dirty = true;
            dirtyPlayers.add(uuid);
        }
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeAsync(String json) {
        ioExecutor.execute(() -> {
            try {
                Files.createDirectories(dataFile.getParent());
                Files.writeString(dataFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
            }
        });
    }

    private JsonObject serializeData(PlayerTrackData data) {
        JsonObject obj = new JsonObject();

        JsonObject counters = new JsonObject();
        for (Map.Entry<String, Long> e : data.getCountersMap().entrySet()) {
            counters.addProperty(e.getKey(), e.getValue());
        }
        obj.add("c", counters);

        JsonObject sets = new JsonObject();
        for (Map.Entry<String, Set<String>> e : data.getSetsMap().entrySet()) {
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
