package com.server.stattracker.data;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;

public class DataManager {

    private final JavaPlugin plugin;
    private final Path dataFile;
    // 玩家分片目录：dataFile 同目录下 players/，每个玩家一个 <uuid>.json
    private final Path shardsDir;
    private final ConcurrentHashMap<UUID, PlayerTrackData> cache = new ConcurrentHashMap<>(64);
    // 分片用紧凑格式序列化，与旧文件中每 UUID 值的 {c,s,d} schema 完全一致，双向兼容
    private final Gson gson = new Gson();
    // 关服合并快照用 pretty 格式写回旧格式文件，供旧版本插件回滚后读取
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    // 旧文件中无法解析的条目（保留原始键与元素）：不进 cache、不写分片，
    // 仅在关服合并快照中原样回写，保证损坏数据不丢失
    private final Map<String, JsonElement> unknownEntries = new LinkedHashMap<>();

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StatTracker-IO");
        t.setDaemon(true);
        return t;
    });

    public DataManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve(fileName);
        this.shardsDir = dataFile.resolveSibling("players");
    }

    public void load() {
        Path bakPath = dataFile.resolveSibling(dataFile.getFileName().toString() + ".bak");
        // 分片目录与旧文件（含备份）都不存在 → 全新安装，直接返回
        if (!Files.isDirectory(shardsDir) && !Files.exists(dataFile) && !Files.exists(bakPath)) return;
        Map<UUID, FileTime> shardMtimes = new HashMap<>();
        int skipped = loadShards(shardMtimes);
        skipped += loadLegacyFile(bakPath, shardMtimes);
        if (skipped > 0) {
            plugin.getLogger().info("已加载 " + cache.size() + " 条玩家数据，跳过 " + skipped + " 条损坏条目");
        } else {
            plugin.getLogger().info("已加载 " + cache.size() + " 条玩家数据");
        }
    }

    // 扫描分片目录加载 players/*.json；损坏分片只告警跳过，文件保留在磁盘上不动（天然不丢）
    private int loadShards(Map<UUID, FileTime> shardMtimes) {
        if (!Files.isDirectory(shardsDir)) return 0;
        int skipped = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shardsDir, "*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                UUID uuid;
                try {
                    uuid = UUID.fromString(name.substring(0, name.length() - ".json".length()));
                } catch (Exception e) {
                    plugin.getLogger().warning("跳过无法识别的分片文件 " + name);
                    continue;
                }
                try {
                    FileTime mtime = Files.getLastModifiedTime(file);
                    JsonObject parsed;
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        parsed = gson.fromJson(reader, JsonObject.class);
                    }
                    // 空文件（如 0 字节）Gson 返回 null 而不抛异常，按损坏处理
                    if (parsed == null) throw new IllegalStateException("文件内容为空");
                    cache.put(uuid, deserializeData(parsed));
                    shardMtimes.put(uuid, mtime);
                } catch (Exception e) {
                    skipped++;
                    plugin.getLogger().warning("跳过损坏的玩家分片 " + name + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("扫描玩家分片目录失败: " + e.getMessage());
        }
        return skipped;
    }

    // 读取旧版单文件并合并进分片体系；.bak 三路回退只作用于旧文件
    private int loadLegacyFile(Path bakPath, Map<UUID, FileTime> shardMtimes) {
        // 主文件缺失但备份存在时改从备份加载
        Path source = dataFile;
        if (!Files.exists(dataFile) && Files.exists(bakPath)) {
            plugin.getLogger().warning("主数据文件缺失，改从备份 " + bakPath.getFileName() + " 加载");
            source = bakPath;
        }
        if (!Files.exists(source)) return 0;
        int skipped = 0;
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
            if (parsed == null) return skipped;
            // 解析成功后才备份，且仅当来源是主文件：.bak 始终保存上一次成功解析的内容，
            // 解析失败时回退读到的才是真正的旧数据
            if (source.equals(dataFile)) {
                try {
                    Files.copy(dataFile, bakPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    plugin.getLogger().warning("备份玩家数据失败: " + e.getMessage());
                }
            }
            FileTime legacyTime;
            try {
                legacyTime = Files.getLastModifiedTime(source);
            } catch (IOException e) {
                // 取不到旧文件修改时间时按 0 处理：分片视为永不落后于旧文件，保留分片数据
                legacyTime = FileTime.fromMillis(0L);
            }
            for (Map.Entry<String, JsonElement> entry : parsed.entrySet()) {
                // 单条解析失败不丢弃：原始键与元素存入 unknownEntries，仅在关服合并快照中原样回写
                UUID uuid;
                PlayerTrackData data;
                try {
                    uuid = UUID.fromString(entry.getKey());
                    data = deserializeData(entry.getValue().getAsJsonObject());
                } catch (Exception e) {
                    skipped++;
                    unknownEntries.put(entry.getKey(), entry.getValue());
                    plugin.getLogger().warning("旧数据条目 " + entry.getKey() + " 无法解析，已保留待关服快照回写: " + e.getMessage());
                    continue;
                }
                if (!cache.containsKey(uuid)) {
                    // 仅存在于旧文件的玩家：进 cache 并标记脏，首轮增量保存固化为分片
                    cache.put(uuid, data);
                    dirtyPlayers.add(uuid);
                    continue;
                }
                // 分片与旧文件都有：比较修改时间，分片较新（>=）时分片胜、保持不动
                FileTime shardTime = shardMtimes.get(uuid);
                if (shardTime != null && shardTime.compareTo(legacyTime) >= 0) continue;
                // 旧文件较新（或取不到分片时间）：旧文件胜，重标脏以便覆盖写回分片
                cache.put(uuid, data);
                dirtyPlayers.add(uuid);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家数据失败: " + e.getMessage());
        }
        return skipped;
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
            savePlayerShard(uuid, data);
        }
    }

    public void saveAll() {
        ioExecutor.execute(() -> {
            try {
                for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
                    // 先移出脏集合再序列化，保存期间的新写入保留到下一轮增量保存
                    dirtyPlayers.remove(entry.getKey());
                    savePlayerShard(entry.getKey(), entry.getValue());
                }
                // 关服向后兼容：cache 全体 + unknownEntries 原样并入，pretty 写回旧格式文件，
                // 旧版本插件回滚后仍能读到关服时点数据
                writeMergedSnapshot();
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
            try {
                Files.deleteIfExists(shardFile(uuid));
            } catch (NoSuchFileException e) {
                // 分片文件本就不存在，符合预期
            } catch (IOException e) {
                plugin.getLogger().warning("删除玩家分片 " + uuid + " 失败: " + e.getMessage());
            }
            // 该玩家已不在 cache，重建的合并快照自然不含其数据
            writeMergedSnapshot();
        });
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            // 分片与合并快照串行于单线程，关服等待需给足时间
            ioExecutor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 单玩家分片写盘：先序列化再原子写；任一步失败则告警并重新标记脏，下一轮重试
    private void savePlayerShard(UUID uuid, PlayerTrackData data) {
        try {
            String json = gson.toJson(serializeData(data));
            writeFileAtomic(shardFile(uuid), json);
        } catch (Exception e) {
            // 分片化后没有内存 root 兜底，不重标脏则该玩家数据滞留内存到关服
            plugin.getLogger().warning("写入玩家分片 " + uuid + " 失败: " + e.getMessage());
            dirtyPlayers.add(uuid);
        }
    }

    // 构建并写回旧格式合并快照：cache 全体 + unknownEntries 原样并入（同键时后并入者生效）
    private void writeMergedSnapshot() {
        JsonObject merged = new JsonObject();
        for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
            try {
                merged.add(entry.getKey().toString(), serializeData(entry.getValue()));
            } catch (Exception e) {
                plugin.getLogger().warning("合并快照序列化玩家 " + entry.getKey() + " 失败: " + e.getMessage());
            }
        }
        for (Map.Entry<String, JsonElement> entry : unknownEntries.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        try {
            writeFileAtomic(dataFile, prettyGson.toJson(merged));
        } catch (Exception e) {
            plugin.getLogger().warning("写入合并快照失败: " + e.getMessage());
        }
    }

    private Path shardFile(UUID uuid) {
        return shardsDir.resolve(uuid.toString() + ".json");
    }

    // 临时文件 + 原子替换（不支持原子移动时降级为普通替换），分片与合并快照共用
    private void writeFileAtomic(Path target, String json) throws IOException {
        Files.createDirectories(target.getParent());
        // 先写同目录临时文件，再原子替换，避免写盘中断留下损坏的半截文件
        Path tmpFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(tmpFile, json, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING);
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
