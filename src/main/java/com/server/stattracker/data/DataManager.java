package com.server.stattracker.data;

import com.google.gson.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;

public class DataManager implements Listener {

    // 驱逐 TTL：离线且超过该时长未被访问的玩家才允许驱逐出 cache（在线玩家与待保存数据永不驱逐）
    public static final long EVICT_TTL_MS = 30 * 60 * 1000L;

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

    // 在线玩家集合：join/quit 簿记，sweep 据此跳过在线玩家（反映服务器在线状态而非数据状态）
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    // 最近访问时间：get/getIfPresent/join 时刷新，sweep 据此判定驱逐 TTL
    private final ConcurrentHashMap<UUID, Long> lastAccess = new ConcurrentHashMap<>();

    // reset 墓碑集合：reset 生效到分片删除完成之间，加载/保存/存在性检查一律按无数据处理，
    // 堵住"按需加载读到未删旧分片 → putIfAbsent 回生 → markDirty 重写回分片"的窗口
    private final Set<UUID> pendingReset = ConcurrentHashMap.newKeySet();

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
        // 构造器内自注册（插件侧不得重复注册）：构造点在 onEnable、先于所有 tracker 注册，
        // join/quit 簿记自插件启用即随事件分发生效。两监听只用 MONITOR 做纯簿记，不干预事件
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // join 簿记：登记在线并刷新访问时间。MONITOR 在常规监听之后执行，其之前任何
    // tracker 的 get() 已 touch，touch + online 双重覆盖 join 窗口，sweep 不会误驱逐
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        online.add(uuid);
        touch(uuid);
    }

    // quit 簿记：【顺序不可反】先保守标脏再移除在线标记——Folia 退出尾窗内（本监听之后
    // 同 tick 的收尾阶段）其他 tracker 仍可能改动该玩家数据并 markDirty，先标脏可保证
    // sweep 在下轮保存完成前绝不驱逐，避免"已退出但最新改动尚未落盘"的窗口内丢数据
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dirtyPlayers.add(uuid);
        online.remove(uuid);
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

    // 刷新访问时间：所有查询命中路径统一走这里，被高频查询的数据不会被反复驱逐-重读
    private void touch(UUID uuid) {
        lastAccess.put(uuid, System.currentTimeMillis());
    }

    // 按需加载：分片存在则同步读取解析；文件不存在返回 null；
    // 损坏文件告警后返回 null，与 loadShards 跳过损坏分片语义一致
    private PlayerTrackData loadShardIfPresent(UUID uuid) {
        // 墓碑：reset 生效期间分片虽未及删除，也按无数据处理，防止旧数据回生
        if (pendingReset.contains(uuid)) return null;
        Path file = shardFile(uuid);
        if (!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject parsed = gson.fromJson(reader, JsonObject.class);
            // 空文件（如 0 字节）Gson 返回 null 而不抛异常，按损坏处理
            if (parsed == null) throw new IllegalStateException("文件内容为空");
            return deserializeData(parsed);
        } catch (Exception e) {
            plugin.getLogger().warning("按需加载玩家分片 " + uuid + " 失败，按无数据处理: " + e.getMessage());
            return null;
        }
    }

    public PlayerTrackData get(UUID uuid) {
        PlayerTrackData data = cache.get(uuid);
        if (data == null) {
            PlayerTrackData loaded = loadShardIfPresent(uuid);
            // 无分片也缓存空对象：否则在线新玩家每个事件都会触发一次文件 stat，热路径倒退
            PlayerTrackData candidate = (loaded != null) ? loaded : new PlayerTrackData();
            // putIfAbsent：并发双方各加载一份时后到者放弃副本改用已在 cache 者，
            // 同一玩家全服唯一实例（与原 computeIfAbsent 语义一致）
            PlayerTrackData existing = cache.putIfAbsent(uuid, candidate);
            data = (existing != null) ? existing : candidate;
        }
        touch(uuid);
        return data;
    }

    // 双条件：cache 或磁盘分片任一存在即视为有数据。防止被驱逐玩家重进时
    // VanillaSeeder 误判为新玩家重新播种并 setCounter 覆盖历史（进服频率，一次 stat 可接受）
    public boolean hasData(UUID uuid) {
        // 墓碑：reset 生效期间按无数据处理——重进玩家被视为新玩家重新播种，正符合 reset 语义
        if (pendingReset.contains(uuid)) return false;
        return cache.containsKey(uuid) || Files.exists(shardFile(uuid));
    }

    // 纯读语义：无数据（cache 未命中且无分片）返回 null、不建空对象——
    // stats 命令"暂无数据"与 ConditionManager EMPTY 回退依赖该语义
    public PlayerTrackData getIfPresent(UUID uuid) {
        PlayerTrackData data = cache.get(uuid);
        if (data == null) {
            PlayerTrackData loaded = loadShardIfPresent(uuid);
            if (loaded == null) return null;
            PlayerTrackData existing = cache.putIfAbsent(uuid, loaded);
            data = (existing != null) ? existing : loaded;
        }
        // 命中（含按需加载）也刷新访问时间，避免被高频查询的离线数据反复驱逐-重读
        touch(uuid);
        return data;
    }

    // 驱逐清扫：由 StatTrackerPlugin 以 6000 tick（5 分钟）周期调度，本方法不自调度。
    // 纯内存操作，无文件 IO、无 Bukkit API。三重防护：
    // 1) TTL——按 lastAccess 判定（getOrDefault 0L 使启动加载、从未查询的玩家同样被正确评估）；
    // 2) 在线/脏跳过——在线玩家与待保存玩家绝不驱逐；
    // 3) 删除后复查回填——remove 与复查之间并发上线/标脏的条目会被放回，不丢失
    public void sweepEvictable() {
        long cutoff = System.currentTimeMillis() - EVICT_TTL_MS;
        for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
            UUID uuid = entry.getKey();
            if (lastAccess.getOrDefault(uuid, 0L) >= cutoff) continue;
            if (online.contains(uuid) || dirtyPlayers.contains(uuid)) continue;
            PlayerTrackData removed = cache.remove(uuid);
            if (removed == null) continue;
            lastAccess.remove(uuid);
            // 删除后复查：期间若并发落入脏标志或玩家上线，回填本次移除的条目；
            // putIfAbsent 防止覆盖并发按需加载刚放入的等价新对象
            if (dirtyPlayers.contains(uuid) || online.contains(uuid)) {
                cache.putIfAbsent(uuid, removed);
            }
        }
    }

    public void markDirty(UUID uuid) {
        if (cache.get(uuid) == null) return;
        // 无条件加入：add 幂等且廉价，脏集合为唯一事实源，不做 check-then-act
        dirtyPlayers.add(uuid);
    }

    public void reset(UUID uuid) {
        // 墓碑先行：先登记 reset 意图再清内存。此后到分片删除完成之间，
        // 加载/保存/存在性检查一律按无数据处理，防止旧数据经按需加载回生
        pendingReset.add(uuid);
        cache.remove(uuid);
        dirtyPlayers.remove(uuid);
        // 清访问记录；不动 online——它反映服务器在线状态而非数据状态
        lastAccess.remove(uuid);
        ioExecutor.execute(() -> {
            try {
                try {
                    Files.deleteIfExists(shardFile(uuid));
                } catch (NoSuchFileException e) {
                    // 分片文件本就不存在，符合预期
                } catch (IOException e) {
                    plugin.getLogger().warning("删除玩家分片 " + uuid + " 失败: " + e.getMessage());
                }
                // 该玩家已不在 cache，重建的合并快照自然不含其数据
                writeMergedSnapshot();
            } finally {
                // 墓碑最后撤销：即使删除/快照异常也不泄漏，撤销后该玩家恢复正常加载与保存
                pendingReset.remove(uuid);
            }
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
        // 墓碑：reset 生效期间不写分片——in-flight 保存可能已取到旧对象，写回会复活旧数据；
        // 重标脏保证墓碑撤销后的下一轮保存能持久化 reset 后立刻重进的新会话数据
        if (pendingReset.contains(uuid)) {
            dirtyPlayers.add(uuid);
            return;
        }
        try {
            String json = gson.toJson(serializeData(data));
            writeFileAtomic(shardFile(uuid), json);
        } catch (Exception e) {
            // 分片化后没有内存 root 兜底，不重标脏则该玩家数据滞留内存到关服
            plugin.getLogger().warning("写入玩家分片 " + uuid + " 失败: " + e.getMessage());
            dirtyPlayers.add(uuid);
        }
    }

    // 构建并写回旧格式合并快照：cache 全体 + 被驱逐玩家的磁盘分片补缺 + unknownEntries 原样并入
    // （同键时后并入者生效）。补缺保证驱逐后关服快照仍等于全量数据，旧版回滚契约不破
    private void writeMergedSnapshot() {
        JsonObject merged = new JsonObject();
        for (Map.Entry<UUID, PlayerTrackData> entry : cache.entrySet()) {
            try {
                merged.add(entry.getKey().toString(), serializeData(entry.getValue()));
            } catch (Exception e) {
                plugin.getLogger().warning("合并快照序列化玩家 " + entry.getKey() + " 失败: " + e.getMessage());
            }
        }
        // 磁盘分片补缺：被驱逐的非脏玩家不在 cache，其分片权威且最新，必须补入快照，
        // 否则关服快照缺失该玩家、旧版回滚后读不到。顺序：cache → 磁盘分片补缺 → unknownEntries
        // 全新安装尚无分片目录时无需补缺，预判跳过，避免扫描抛 NoSuchFileException 的无害告警
        if (Files.isDirectory(shardsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shardsDir, "*.json")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(name.substring(0, name.length() - ".json".length()));
                    } catch (Exception e) {
                        plugin.getLogger().warning("合并快照跳过无法识别的分片文件 " + name);
                        continue;
                    }
                    // cache 中已有的玩家以内存为准，上面已并入
                    if (cache.containsKey(uuid)) continue;
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject parsed = gson.fromJson(reader, JsonObject.class);
                        // 空文件（如 0 字节）Gson 返回 null 而不抛异常，按损坏处理
                        if (parsed == null) throw new IllegalStateException("文件内容为空");
                        // 经 deserializeData 校验结构（损坏分片跳过，与 loadShards 语义一致），
                        // 分片本就由 serializeData 写出，回程序列化无损
                        PlayerTrackData data = deserializeData(parsed);
                        merged.add(uuid.toString(), serializeData(data));
                    } catch (Exception e) {
                        plugin.getLogger().warning("合并快照跳过损坏的玩家分片 " + name + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("扫描分片目录补全合并快照失败: " + e.getMessage());
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
