package com.server.stattracker;

import com.server.stattracker.api.StatProvider;
import com.server.stattracker.compat.ServerScheduler;
import com.server.stattracker.condition.ConditionManager;
import com.server.stattracker.condition.ConditionPAPI;
import com.server.stattracker.data.DataManager;
import com.server.stattracker.integration.ConditionPermissionBridge;
import com.server.stattracker.integration.LuckPermsBridge;
import com.server.stattracker.integration.PAPIExpansion;
import com.server.stattracker.integration.PermissionBridge;
import com.server.stattracker.integration.VaultPermissionBridge;
import com.server.stattracker.tracker.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class StatTrackerPlugin extends JavaPlugin {

    private DataManager dataManager;
    private StatProvider statProvider;
    private LuckPermsBridge luckPermsBridge;
    private ServerScheduler scheduler;
    private MovementTracker movementTracker;
    private PlaytimeTracker playtimeTracker;
    private ConditionManager conditionManager;
    private ConditionPermissionBridge conditionBridge;
    private ConditionPAPI conditionPapi;
    private VanillaSeeder vanillaSeeder;
    // 周期任务句柄；条件桥 flush 需在关闭条件系统时取消，其余统一存句柄便于后续管理
    private ServerScheduler.TaskHandle dataSaveHandle;
    private ServerScheduler.TaskHandle movementFlushHandle;
    private ServerScheduler.TaskHandle playtimeFlushHandle;
    private ServerScheduler.TaskHandle conditionFlushHandle;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        scheduler = new ServerScheduler(this);

        dataManager = new DataManager(this, "tracker-data.json");
        dataManager.load();

        statProvider = new StatProvider(dataManager);
        luckPermsBridge = new LuckPermsBridge(this);

        if (getConfig().getBoolean("conditions.enabled", true)) {
            initConditionSystem();
        }

        registerTrackers();
        dataSaveHandle = scheduler.runAtFixedRate(60, 60, () -> dataManager.saveDirty());
        // 离线玩家驱逐清扫：6000 tick（5 分钟）一轮，纯内存操作；
        // DataManager 构造器内已自注册 join/quit 监听，插件侧不再重复注册
        scheduler.runAtFixedRate(6000, 6000, dataManager::sweepEvictable);
        if (movementTracker != null) {
            movementFlushHandle = scheduler.runAtFixedRate(60, 60, () -> movementTracker.flushDirty());
        }
        if (playtimeTracker != null) {
            playtimeFlushHandle = scheduler.runAtFixedRate(600, 600, () -> playtimeTracker.flushAll());
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(this).register();
        }

        getLogger().info("StatTracker enabled - 28+ trackers, "
            + (scheduler.isFolia() ? "Folia" : "Bukkit/Spigot/Paper") + " mode"
            + (conditionManager != null ? ", conditions ON" : ", conditions OFF"));

        StatTrackerCommand command = new StatTrackerCommand(this);
        getCommand("stattracker").setExecutor(command);
        getCommand("stattracker").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
            dataManager.shutdown();
        }
    }

    public DataManager getDataManager()        { return dataManager; }
    public StatProvider getAPI()                { return statProvider; }
    public ConditionManager getConditionManager()  { return conditionManager; }
    public ConditionPermissionBridge getConditionBridge() { return conditionBridge; }
    public VanillaSeeder getVanillaSeeder()         { return vanillaSeeder; }
    public LuckPermsBridge getLuckPermsBridge() { return luckPermsBridge; }
    public ServerScheduler getScheduler()       { return scheduler; }

    public void reloadPlugin() {
        reloadConfig();
        if (getConfig().getBoolean("conditions.enabled", true)) {
            if (conditionManager == null) {
                initConditionSystem();
            } else {
                conditionManager.load();
                if (conditionBridge == null && conditionManager.hasManagedConditions()) {
                    initConditionBridge();
                }
            }
            // 修复注册缺口：占位符扩展只在首次 initConditionSystem 注册，"关条件→再开条件"后需补注册
            if (conditionPapi == null && getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                conditionPapi = new ConditionPAPI(this, conditionManager);
                conditionPapi.register();
            }
            if (conditionBridge != null) conditionBridge.flush();
        } else {
            // 关闭条件系统：取消桥 flush 任务、注销监听与占位符；conditionManager 实例保留，
            // getConditionManager() 的 Java API 调用方契约不变
            if (conditionFlushHandle != null) {
                conditionFlushHandle.cancel();
                conditionFlushHandle = null;
            }
            if (conditionBridge != null) {
                HandlerList.unregisterAll(conditionBridge);
                conditionBridge = null;
            }
            if (conditionPapi != null) {
                conditionPapi.unregister();
                conditionPapi = null;
            }
            getLogger().info("条件系统已关闭：权限桥与占位符扩展已注销");
        }
    }

    private void initConditionBridge() {
        PermissionBridge permBridge = luckPermsBridge.isAvailable()
            ? luckPermsBridge
            : new VaultPermissionBridge(this);
        if (!permBridge.isAvailable()) {
            getLogger().warning("没有可用的权限后端，条件权限桥未启用");
            return;
        }
        conditionBridge = new ConditionPermissionBridge(this, conditionManager, permBridge);
        getServer().getPluginManager().registerEvents(conditionBridge, this);
        conditionFlushHandle = scheduler.runAtFixedRate(600, 600, () -> conditionBridge.flush());
        getLogger().info("ConditionPermissionBridge enabled - 权限后端: " + permBridge.getName());
    }

    private void initConditionSystem() {
        conditionManager = new ConditionManager(this);
        conditionManager.load();
        if (conditionManager.hasManagedConditions()) {
            initConditionBridge();
        } else {
            getLogger().info("没有带 permission 的条件，条件权限桥未启用");
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            conditionPapi = new ConditionPAPI(this, conditionManager);
            conditionPapi.register();
        }
    }

    private void registerTrackers() {
        var pm = getServer().getPluginManager();

        if (getConfig().getBoolean("seed.vanilla_stats", true)) {
            vanillaSeeder = new VanillaSeeder(this);
            register(pm, vanillaSeeder);
        }
        movementTracker = new MovementTracker(this);
        playtimeTracker = new PlaytimeTracker(this);

        registerIf("movement", pm, movementTracker);
        registerIf("mining", pm, new MiningTracker(this));
        registerIf("block_place", pm, new BlockPlaceTracker(this));
        registerIf("crafting", pm, new CraftingTracker(this));
        registerIf("combat", pm, new CombatTracker(this));
        registerIf("combat_detail", pm, new CombatDetailTracker(this));
        registerIf("fishing", pm, new FishingTracker(this));
        registerIf("brewing", pm, new BrewingTracker(this));
        registerIf("item_consume", pm, new ItemConsumeTracker(this));
        registerIf("enchanting", pm, new EnchantingTracker(this));
        registerIf("trading", pm, new TradingTracker(this));
        registerIf("dimension", pm, new DimensionTracker(this));
        registerIf("portal", pm, new PortalTracker(this));
        registerIf("projectile", pm, new ProjectileTracker(this));
        registerIf("taming", pm, new TamingTracker(this));
        registerIf("farming", pm, new FarmingTracker(this));
        registerIf("harvest", pm, new HarvestTracker(this));
        registerIf("redstone", pm, new RedstoneTracker(this));
        registerIf("map", pm, new MapTracker(this));
        registerIf("container", pm, new ContainerTracker(this));
        registerIf("item_pickup_drop", pm, new ItemPickupDropTracker(this));
        registerIf("interaction", pm, new InteractionTracker(this));
        registerIf("advancement", pm, new AdvancementTracker(this));
        registerIf("experience", pm, new ExperienceTracker(this));
        registerIf("survival", pm, new SurvivalTracker(this));
        registerIf("playtime", pm, playtimeTracker);
        registerIf("chat", pm, new ChatTracker(this));
        registerIf("misc", pm, new MiscTracker(this));
    }

    private void register(org.bukkit.plugin.PluginManager pm, org.bukkit.event.Listener listener) {
        pm.registerEvents(listener, this);
    }

    // tracker 配置门禁：trackers.<key> 默认全开（零行为变化），false 则不注册监听器
    private void registerIf(String key, org.bukkit.plugin.PluginManager pm, org.bukkit.event.Listener listener) {
        if (getConfig().getBoolean("trackers." + key, true)) {
            register(pm, listener);
        }
    }
}
