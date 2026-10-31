package com.server.stattracker.tracker;

// 结构定位缓存：locateNearestStructure 是重量级同步扫描（radius=10 时最多 21×21 区块），
// 按 (世界, 结构) 维度缓存最近一次查询结果，查询点在复用窗口内漂移时直接复用，避免重复扫描。
// Folia 下事件可能由多区域线程并发触发，全部使用 ConcurrentHashMap，Entry 不可变。
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureLocateCache {

    // 复用窗口 128 格（水平距离平方 <= 128*128）：命中的结果来自某次真实 locate 的结构原点，无假阳性；
    // 代价是最坏情况下更近的同类型结构会延迟最多 128 格行程才被发现（有界延迟，最终必被发现）。
    private static final double REUSE_WINDOW_SQ = 128 * 128;

    // 外层键用 world.getUID() 而非 World 引用，Entry 只存坐标不存 Location，均不持有已卸载世界的强引用；
    // locateNearestStructure 是世界作用域查询，且 Location.distanceSquared 跨世界会抛异常，键必须含世界。
    private final Map<UUID, Map<String, Entry>> cache = new ConcurrentHashMap<>();

    // present=false 表示否定缓存（附近无该类型结构），命中时返回 null 与 locate 未找到语义一致；
    // 只存坐标三元组不存 Location，命中时按查询世界重建瞬态 Location，缓存不持有任何对象引用
    private static final class Entry {
        final boolean present;
        final double originX;
        final double originY;
        final double originZ;
        final double queryX;
        final double queryZ;
        Entry(boolean present, double originX, double originY, double originZ, double queryX, double queryZ) {
            this.present = present;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.queryX = queryX;
            this.queryZ = queryZ;
        }
        static Entry of(Location origin, double queryX, double queryZ) {
            return origin == null
                ? new Entry(false, 0.0, 0.0, 0.0, queryX, queryZ)
                : new Entry(true, origin.getX(), origin.getY(), origin.getZ(), queryX, queryZ);
        }
    }

    public StructureLocateCache() {
    }

    // 带缓存的 locateNearestStructure：命中复用窗口时直接返回缓存结果（可能为 null，调用方按"未找到"处理）；
    // 未命中或查询点漂移超窗口时真正定位，并把结果（含 null）与本次查询点写回缓存。
    public Location locate(World world, Structure structure, Location query, int radius, boolean findUnexplored) {
        UUID worldId = world.getUID();
        String structureKey = structure.getKey().toString();
        Map<String, Entry> byStructure = cache.get(worldId);
        if (byStructure != null) {
            Entry entry = byStructure.get(structureKey);
            if (entry != null) {
                double dx = query.getX() - entry.queryX;
                double dz = query.getZ() - entry.queryZ;
                if (dx * dx + dz * dz <= REUSE_WINDOW_SQ) {
                    // 命中：用本次查询的世界重建瞬态 Location，用完即弃，缓存不保留
                    return entry.present
                        ? new Location(world, entry.originX, entry.originY, entry.originZ)
                        : null;
                }
            }
        }

        var result = world.locateNearestStructure(query, structure, radius, findUnexplored);
        Location origin = result == null ? null : result.getLocation();
        cache.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>(16))
             .put(structureKey, Entry.of(origin, query.getX(), query.getZ()));
        return origin;
    }
}
