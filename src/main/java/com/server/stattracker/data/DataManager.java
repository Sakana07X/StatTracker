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
        Path bakPath = dataFile.resolveSibling(dataFile.getFileName().toString() + ".bak");
        // 主文件缺失但备份存在时改从备份加载
        Path source = dataFile;
        if (!Files.exists(dataFile) && Files.exists(bakPath)) {
            plugin.getLogger().warning("主数据文件缺失，改从备份 " + bakPath.getFileName() + " 加载");
            source = bakPath;
        }
        if (!Files.exists(source)) return;
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonObject parsed;
            try {
                parsed = gson.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                if (source.equals(dataFile) && Files.exists(bakPath)) {
                    plugin.getLogger().warning("主数据文件解析失败，改从备份加载: " + e.getMessage());
                    try (Reader bakReader = Files.newBufferedReader(bakPath, StandardCharsets.UTF_8)) {
                        parsed = gson.fromJson(bakReader, JsonObject.class);
                    }
                    source = bakPath;
                } else {
                    throw e;
                }
            }
            // 主文件为空（如 0 字节）时 Gson 返回 null 而不抛异常，同样走备份回退
            if (parsed == null && source.equals(dataFile) && Files.exists(bakPath)) {
                plugin.getLogger().warning("主数据文件内容为空，改从备份加载");
                try (Reader bakReader = Files.newBufferedReader(bakPath, StandardCharsets.UTF_8)) {
                    parsed = gson.fromJson(bakReader, JsonObject.class);
                }
                source = bakPath;
            }
            if (parsed == null) return;
            root = parsed;
            // 解析成功后才备份，且仅当来源是主文件：.bak 始终保存上一次成功解析的内容，
            // 解析失败时回退读到的才是真正的旧数据
            if (source.equals(dataFile)) {
                try {
                    Files.copy(dataFile, bakPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    plugin.getLogger().warning("备份玩家数据失败: " + e.getMessage());
                }
            }
            int skipped = 0;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                // 单条损坏只跳过该条，未解析的条目留在 root 中避免被增量保存覆盖丢失
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    PlayerTrackData data = deserializeData(entry.getValue().getAsJsonObject());
                    cache.put(uuid, data);
                } catch (Exception e) {
                    skipped++;
                    plugin.getLogger().warning("跳过损坏的玩家数据条目 " + entry.getKey() + ": " + e.getMessage());
                }
            }
            if (skipped > 0) {
                plugin.getLogger().info("已加载 " + cache.size() + " 条玩家数据，跳过 " + skipped + " 条损坏条目");
            } else {
                plugin.getLogger().info("已加载 " + cache.size() + " 条玩家数据");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家数据失败: " + e.getMessage());
        }
    }

    public void saveDirty() {
        if (dirtyPlayers.isEmpty()) return;
        ioExecutor.execute(this::saveDirtyTask);
    }

    private void saveDirtyTask() {
        // 弱一致迭代：先移出脏集合再序列化，序列化期间发生的新写入会重新标记，下一轮补存
        for (UUID uuid : dirtyPlayers) {
            dirtyPlayers.remove(uuid);
            PlayerTrackData data = cache.get(uuid);
            if (data == null) continue;
            root.add(uuid.toString(), serializeData(data));
        }

        writeJson(gson.toJson(root));
    }

    public void saveAll() {
        ioExecutor.execute(() -> {
            try {
                JsonObject newRoot = new JsonObject();
                for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
                    // 先移出脏集合再序列化，保存期间的新写入保留到下一轮增量保存
                    dirtyPlayers.remove(entry.getKey());
                    newRoot.add(entry.getKey().toString(), serializeData(entry.getValue()));
                }
                root = newRoot;
                writeJson(gson.toJson(root));
            } catch (Exception e) {
                plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
            }
        });
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
        if (cache.get(uuid) == null) return;
        // 无条件加入：add 幂等且廉价，脏集合为唯一事实源，不做 check-then-act
        dirtyPlayers.add(uuid);
    }

    public void reset(UUID uuid) {
        cache.remove(uuid);
        dirtyPlayers.remove(uuid);
        ioExecutor.execute(() -> {
            root.remove(uuid.toString());
            writeJson(gson.toJson(root));
        });
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeJson(String json) {
        try {
            Files.createDirectories(dataFile.getParent());
            // 先写同目录临时文件，再原子替换，避免写盘中断留下损坏的半截文件
            Path tmpFile = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
            Files.writeString(tmpFile, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmpFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
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