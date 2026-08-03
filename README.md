# StatTracker

[English](#english) | [中文](#中文)

---

## English

A lightweight Minecraft player behavior tracking plugin. Records what players do and exposes the data for other plugins to use. No game logic changes, no messages to players, just silent data collection.

Works as a data source for achievement/title systems like CyuTitles, anti-cheat checks, or anything that needs player stats. Read raw data through PAPI placeholders or the Java API, or define conditions in `conditions.yml` and let the plugin answer yes/no for you.

### Compatibility

Bukkit, Spigot, Paper, Purpur, Pufferfish, Folia — any server 1.13+. Auto-detects server type at startup.

### What It Tracks

27 trackers: mining, placing, crafting, smelting, combat (damage types + death coords), fishing (fish/treasure/junk), brewing, enchanting, trading (emerald stats), movement (walk/sprint/swim/elytra/boat/mount/jumps), biome discovery, dimension changes, portals, farming, redstone, maps, XP, advancements, chat, playtime, survival time, structure exploration (12 types), item pickup/drop, projectile accuracy, taming, breeding, consumables.

### Performance

- Movement: distance accumulation only, biome check every 50 blocks, structure check every 30
- Dirty data tracked per-player, flushed every 3 seconds
- Plain HashMap, full save on shutdown

### Usage

Put `StatTracker.jar` into `plugins`, restart. Zero dependencies.

Data: `plugins/StatTracker/tracker-data.json`

### Integrations

**PlaceholderAPI** — `%stattracker_mob_kills%`, `%stattracker_walk_distance%`, `%stattracker_playtime_hours%`, `%stattracker_biome_count%`, `%stattracker_arrow_accuracy%`, etc. Works with TAB, TrChat, PlayerTitle.

**PlayerTitle** — use PAPI placeholders in title templates.

**TrChat** — PAPI placeholders in channel templates. Chat behavior also tracked.

**CyuTitles / LuckPerms** — PAPI placeholders for conditions, or built-in LuckPerms bridge for permissions.

**Java API:**

```java
StatProvider api = statTracker.getAPI();
long kills = api.getCounter(player, StatKeys.MOB_KILLS);
int biomes = api.getSetSize(player, StatKeys.VISITED_BIOMES);
double walk = api.getDouble(player, StatKeys.WALK_DISTANCE);
```

### Condition System

Define conditions in `conditions.yml` and evaluate them against tracked stats. Useful for achievement/title unlocks, anti-cheat checks, or any logic that needs a yes/no answer from player data.

Toggle in `config.yml`: `conditions.enabled: true` (default on). Set it to `false` and the whole condition system is skipped, only raw data remains.

```yaml
conditions:
  mining_master:
    display: "Mining Master"
    type: COUNTER
    key: "mining.mat.STONE"
    operator: ">="
    value: 10000
```

Types: `COUNTER`, `DOUBLE`, `SET_SIZE`, `SET_CONTAINS`, `BOOLEAN`. Operators: `>=`, `<=`, `==`, `>`, `<`, `!=`.

Placeholders: `%statcond_<id>%` → `true`/`false`, `%statcond_<id>_display%`, `%statcond_met_count%`, `%statcond_met_percent%`.

Java API:

```java
StatTrackerPlugin plugin = (StatTrackerPlugin) Bukkit.getPluginManager().getPlugin("StatTracker");
boolean met = plugin.getConditionManager().isMet(player.getUniqueId(), "mining_master");
```

---

## 中文

一个 Minecraft 玩家行为追踪插件。记录玩家操作，把数据暴露给其他插件用。不改游戏逻辑，不发消息，只记数据。

可以用作成就/称号系统（比如 CyuTitles）、反作弊检测，或者任何需要玩家数据的功能的数据源。既能通过 PAPI 占位符或 Java API 读原始数据，也能在 `conditions.yml` 里定义条件，让插件直接给出是/否结果。

### 兼容性

Bukkit、Spigot、Paper、Purpur、Pufferfish、Folia，1.13 以上。启动时自动检测。

### 追踪内容

27 个追踪器：挖掘、放置、合成、熔炼、战斗（伤害类型+死亡坐标）、钓鱼（鱼/宝物/垃圾）、酿造、附魔、交易（绿宝石统计）、移动（步行/疾跑/游泳/鞘翅/船/骑乘/跳跃）、群系发现、维度切换、传送门、耕种、红石、地图、经验、进度、聊天、在线时长、存活时长、结构探索（12种）、物品拾取/丢弃、弹射物命中率、驯服、繁殖、消耗品。

### 性能

- 移动事件只做距离累加，群系检测每50格一次，结构检测每30格一次
- 脏数据按玩家追踪，每3秒批量写入
- 普通HashMap，关闭时全量保存

### 使用

把 `StatTracker.jar` 丢进 `plugins`，重启。不需要前置插件。

数据：`plugins/StatTracker/tracker-data.json`

### 联动

**PlaceholderAPI** — `%stattracker_mob_kills%`、`%stattracker_walk_distance%`、`%stattracker_playtime_hours%` 等。TAB、TrChat、PlayerTitle 直接可用。

**PlayerTitle** — 称号模板里写 PAPI 占位符。

**TrChat** — 频道模板里用 PAPI 占位符。聊天行为也会被追踪。

**CyuTitles / LuckPerms** — PAPI 占位符做条件判断，或用内置 LuckPerms 桥接授予权限。

**Java API：**

```java
StatProvider api = statTracker.getAPI();
long kills = api.getCounter(player, StatKeys.MOB_KILLS);
int biomes = api.getSetSize(player, StatKeys.VISITED_BIOMES);
double walk = api.getDouble(player, StatKeys.WALK_DISTANCE);
```

### 条件系统

在 `conditions.yml` 里定义条件，对追踪到的数据进行判断，适合做成就/称号解锁、反作弊检测，或者任何只需要“是/否”答案的逻辑。

开关：`config.yml` 里 `conditions.enabled: true`（默认开）。改成 `false` 会跳过整个条件系统，只保留原始数据。

```yaml
conditions:
  mining_master:
    display: "挖矿大师"
    type: COUNTER
    key: "mining.mat.STONE"
    operator: ">="
    value: 10000
```

类型：`COUNTER`、`DOUBLE`、`SET_SIZE`、`SET_CONTAINS`、`BOOLEAN`。运算符：`>=`、`<=`、`==`、`>`、`<`、`!=`。

占位符：`%statcond_<id>%` → `true`/`false`，`%statcond_<id>_display%`、`%statcond_met_count%`、`%statcond_met_percent%`。

Java API：

```java
StatTrackerPlugin plugin = (StatTrackerPlugin) Bukkit.getPluginManager().getPlugin("StatTracker");
boolean met = plugin.getConditionManager().isMet(player.getUniqueId(), "mining_master");
```
