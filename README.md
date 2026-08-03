# StatTracker

[English](#english) | [中文](#中文)

---

# StatTracker



---

## StatTracker

A lightweight Minecraft player behavior tracking plugin. Records what players do — mining, killing, fishing, trading, moving — and exposes the data for other plugins to use.

No game logic changes. No messages to players. Just silent data collection.

### Compatibility

Works on Bukkit, Spigot, Paper, Purpur, Pufferfish, Folia — any server 1.13+. Auto-detects server type at startup, no config needed.

### What It Tracks

27 trackers covering: mining, placing, crafting, smelting, combat (damage types + death coordinates), fishing (fish/treasure/junk), brewing, enchanting, trading (emerald tracking), movement (walk/sprint/swim/elytra/boat/mount/jumps), biome discovery, dimension changes, portal usage, farming, redstone, maps, XP, advancements, chat, playtime, survival time, structure exploration (12 types), item pickup/drop, projectile accuracy, taming, breeding, consumable usage.

### Performance

- Movement: distance accumulation only, biome check every 50 blocks, structure check every 30
- Dirty data tracked per-player, flushed every 3 seconds
- Plain HashMap (no CAS overhead), full save on shutdown

### Usage

Put `StatTracker.jar` into `plugins`, restart. Zero dependencies required.

Data stored in `plugins/StatTracker/tracker-data.json`.

### Integrations

#### PlaceholderAPI

With PAPI installed, any plugin can read stats via `%stattracker_xxx%`. Works directly with TAB, TrChat, PlayerTitle, etc.

Common placeholders:

| Placeholder | Description |
|---|---|
| `%stattracker_mob_kills%` | Kill count |
| `%stattracker_walk_distance%` | Walk distance |
| `%stattracker_playtime_hours%` | Playtime (hours) |
| `%stattracker_biome_count%` | Biomes visited |
| `%stattracker_arrow_accuracy%` | Arrow accuracy (%) |
| `%stattracker_advancement_count%` | Advancements done |
| `%stattracker_chat_messages%` | Chat messages sent |

Generic: `%stattracker_counter_key%`, `%stattracker_set_key%`, `%stattracker_double_key%`

Full list in `PAPIExpansion.java`.

#### PlayerTitle

Use PAPI placeholders directly in title templates. No extra setup.

#### TrChat

PAPI placeholders work in channel templates. Chat behavior is also tracked.

#### CyuTitles / LuckPerms

Use PAPI placeholders for conditions, or use the built-in LuckPerms bridge to grant permissions programmatically.

#### Java API

```java
StatProvider api = statTracker.getAPI();
long kills = api.getCounter(player, StatKeys.MOB_KILLS);
int biomes = api.getSetSize(player, StatKeys.VISITED_BIOMES);
double walk = api.getDouble(player, StatKeys.WALK_DISTANCE);
boolean visited = api.getBooleanFlag(player, StatKeys.ENTERED_NETHER);
```

---

---

## StatTracker

一个 Minecraft 玩家行为追踪插件。记录玩家在服务器里的各种操作——挖矿、击杀、钓鱼、交易、移动——然后把数据暴露给其他插件用。

不改游戏逻辑，不给玩家发消息，只安安静静记数据。

### 兼容性

Bukkit、Spigot、Paper、Purpur、Pufferfish、Folia，1.13 以上都能跑。启动时自动检测服务器类型，不需要配置。

### 追踪内容

27 个追踪器：挖掘、放置、合成、熔炼、战斗（伤害类型 + 死亡坐标）、钓鱼（鱼/宝物/垃圾）、酿造、附魔、交易（绿宝石统计）、移动（步行/疾跑/游泳/鞘翅/船/骑乘/跳跃）、群系发现、维度切换、传送门使用、耕种、红石、地图、经验、进度、聊天、在线时长、存活时长、结构探索（12 种）、物品拾取/丢弃、弹射物命中率、驯服、繁殖、消耗品使用。

### 性能

- 移动事件只做距离累加，群系检测每 50 格一次，结构检测每 30 格一次
- 脏数据按玩家追踪，每 3 秒批量写入
- 内部用普通 HashMap，关闭时全量保存

### 使用

把 `StatTracker.jar` 丢进 `plugins`，重启，完事。不需要前置插件。

数据存在 `plugins/StatTracker/tracker-data.json`。

### 联动

#### PlaceholderAPI

装了 PAPI 后，所有支持占位符的插件都能读取数据。TAB、TrChat、PlayerTitle 等直接可用。

常用占位符：

| 占位符 | 含义 |
|---|---|
| `%stattracker_mob_kills%` | 击杀数 |
| `%stattracker_walk_distance%` | 步行距离 |
| `%stattracker_playtime_hours%` | 游玩时长（小时） |
| `%stattracker_biome_count%` | 已访问群系数 |
| `%stattracker_arrow_accuracy%` | 箭矢命中率 |
| `%stattracker_advancement_count%` | 完成进度数 |
| `%stattracker_chat_messages%` | 聊天消息数 |

通用格式：`%stattracker_counter_key%`、`%stattracker_set_key%`、`%stattracker_double_key%`

完整列表看 `PAPIExpansion.java`。

#### PlayerTitle

称号模板里直接写 PAPI 占位符，不用额外配置。

#### TrChat

频道模板里直接用 PAPI 占位符。聊天行为本身也会被追踪。

#### CyuTitles / LuckPerms

通过 PAPI 占位符做条件判断，或者用内置的 LuckPerms 桥接授予权限。

#### Java API

```java
StatProvider api = statTracker.getAPI();
long kills = api.getCounter(player, StatKeys.MOB_KILLS);
int biomes = api.getSetSize(player, StatKeys.VISITED_BIOMES);
double walk = api.getDouble(player, StatKeys.WALK_DISTANCE);
boolean visited = api.getBooleanFlag(player, StatKeys.ENTERED_NETHER);
```
