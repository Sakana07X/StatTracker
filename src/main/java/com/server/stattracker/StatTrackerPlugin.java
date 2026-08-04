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
    private VanillaSeeder vanillaSeeder;
    
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
        scheduler.runAtFixedRate(60, 60, () -> dataManager.saveDirty());
        if (movementTracker != null) {
            scheduler.runAtFixedRate(60, 60, () -> movementTracker.flushDirty());
        }
        if (playtimeTracker != null) {
            scheduler.runAtFixedRate(600, 600, () -> playtimeTracker.flushAll());
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(this).register();
        }

        getLogger().info("StatTracker enabled - 27 trackers, "
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
            }
            if (conditionBridge != null) conditionBridge.flush();
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
        scheduler.runAtFixedRate(600, 600, () -> conditionBridge.flush());
        getLogger().info("ConditionPermissionBridge enabled - 权限后端: " + permBridge.getName());
    }

    private void initConditionSystem() {
        conditionManager = new ConditionManager(this);
        conditionManager.load();
        initConditionBridge();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ConditionPAPI(this, conditionManager).register();
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

        register(pm, movementTracker);
        register(pm, new MiningTracker(this));
        register(pm, new BlockPlaceTracker(this));
        register(pm, new CraftingTracker(this));
        register(pm, new CombatTracker(this));
        register(pm, new CombatDetailTracker(this));
        register(pm, new FishingTracker(this));
        register(pm, new BrewingTracker(this));
        register(pm, new ItemConsumeTracker(this));
        register(pm, new EnchantingTracker(this));
        register(pm, new TradingTracker(this));
        register(pm, new DimensionTracker(this));
        register(pm, new PortalTracker(this));
        register(pm, new ProjectileTracker(this));
        register(pm, new TamingTracker(this));
        register(pm, new FarmingTracker(this));
        register(pm, new HarvestTracker(this));
        register(pm, new RedstoneTracker(this));
        register(pm, new MapTracker(this));
        register(pm, new ContainerTracker(this));
        register(pm, new ItemPickupDropTracker(this));
        register(pm, new InteractionTracker(this));
        register(pm, new AdvancementTracker(this));
        register(pm, new ExperienceTracker(this));
        register(pm, new SurvivalTracker(this));
        register(pm, playtimeTracker);
        register(pm, new ChatTracker(this));
    }

    private void register(org.bukkit.plugin.PluginManager pm, org.bukkit.event.Listener listener) {
        pm.registerEvents(listener, this);
    }
}
