package com.server.stattracker.condition;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionManager {

    private final StatTrackerPlugin plugin;
    private final File configFile;
    private final Map<String, Condition> conditions = new LinkedHashMap<>();
    private static final PlayerTrackData EMPTY = new PlayerTrackData();

    public enum Type { COUNTER, DOUBLE, SET_SIZE, SET_CONTAINS, BOOLEAN }

    public static class Condition {
        public final String id;
        public final String display;
        public final Type type;
        public final String key;
        public final String operator;
        public final double value;
        public final String contains;

        Condition(String id, String display, Type type, String key, String operator, double value, String contains) {
            this.id = id;
            this.display = display;
            this.type = type;
            this.key = key;
            this.operator = operator;
            this.value = value;
            this.contains = contains;
        }
    }

    public ConditionManager(StatTrackerPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "conditions.yml");
    }

    public void load() {
        conditions.clear();
        if (!configFile.exists()) {
            plugin.saveResource("conditions.yml", false);
        }
        if (!configFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection sec = config.getConfigurationSection("conditions");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection cs = sec.getConfigurationSection(id);
            if (cs == null) continue;

            try {
                Type type = Type.valueOf(cs.getString("type", "COUNTER").toUpperCase());
                String display = cs.getString("display", id);
                String key = cs.getString("key", "");
                String op = cs.getString("operator", ">=");
                double val = cs.getDouble("value", 0);
                String contains = cs.getString("contains", null);

                conditions.put(id, new Condition(id, display, type, key, op, val, contains));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipped invalid condition '" + id + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + conditions.size() + " conditions");
    }

    public boolean isMet(UUID uuid, String conditionId) {
        Condition c = conditions.get(conditionId);
        if (c == null) return false;
        return evaluate(c, dataOf(uuid));
    }

    public boolean isMet(UUID uuid, Condition c) {
        return evaluate(c, dataOf(uuid));
    }

    public Map<String, Boolean> checkAll(UUID uuid) {
        PlayerTrackData data = dataOf(uuid);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (var entry : conditions.entrySet()) {
            result.put(entry.getKey(), evaluate(entry.getValue(), data));
        }
        return result;
    }

    private PlayerTrackData dataOf(UUID uuid) {
        PlayerTrackData data = plugin.getDataManager().getIfPresent(uuid);
        return data != null ? data : EMPTY;
    }

    public Condition getCondition(String id) {
        return conditions.get(id);
    }

    public Collection<Condition> getAllConditions() {
        return Collections.unmodifiableCollection(conditions.values());
    }

    private boolean evaluate(Condition c, PlayerTrackData data) {
        double actual;
        switch (c.type) {
            case COUNTER -> actual = data.getCounter(c.key);
            case DOUBLE -> actual = data.getDouble(c.key);
            case SET_SIZE -> actual = data.getSetSize(c.key);
            case SET_CONTAINS -> {
                return c.operator.equals("==")
                    ? data.setContains(c.key, c.contains)
                    : !data.setContains(c.key, c.contains);
            }
            case BOOLEAN -> {
                boolean flag = data.getBooleanFlag(c.key);
                return c.operator.equals("==") ? (c.value > 0) == flag : (c.value > 0) != flag;
            }
            default -> actual = 0;
        }
        return switch (c.operator) {
            case ">=" -> actual >= c.value;
            case "<=" -> actual <= c.value;
            case "==" -> actual == c.value;
            case ">" -> actual > c.value;
            case "<" -> actual < c.value;
            case "!=" -> actual != c.value;
            default -> false;
        };
    }
}
