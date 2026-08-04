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
    private final Map<String, Condition> conditions = new ConcurrentHashMap<>();
    private static final PlayerTrackData EMPTY = new PlayerTrackData();
    private static final int MAX_DEPTH = 16;

    public enum Type { COUNTER, DOUBLE, SET_SIZE, SET_CONTAINS, BOOLEAN }

    // 组合条件的单个子项：引用条件 ID，或内联统计
    public static class SubRequirement {
        public final String conditionId;
        public final Type type;
        public final String key;
        public final String operator;
        public final double value;
        public final String contains;

        SubRequirement(String conditionId, Type type, String key, String operator,
                       double value, String contains) {
            this.conditionId = conditionId;
            this.type = type;
            this.key = key;
            this.operator = operator;
            this.value = value;
            this.contains = contains;
        }
    }

    public static class Condition {
        public final String id;
        public final String display;
        public final Type type;
        public final String key;
        public final String operator;
        public final double value;
        public final String contains;
        public final String permission;
        public final String revokeCommand;
        public final List<SubRequirement> requires;

        Condition(String id, String display, Type type, String key, String operator,
                  double value, String contains, String permission,
                  String revokeCommand, List<SubRequirement> requires) {
            this.id = id;
            this.display = display;
            this.type = type;
            this.key = key;
            this.operator = operator;
            this.value = value;
            this.contains = contains;
            this.permission = permission;
            this.revokeCommand = revokeCommand;
            this.requires = requires;
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
                String permission = cs.getString("permission", null);
                String revokeCommand = cs.getString("revoke-command", null);
                List<SubRequirement> requires = parseRequires(cs);

                conditions.put(id, new Condition(id, display, type, key, op, val,
                    contains, permission, revokeCommand, requires));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipped invalid condition '" + id + "': " + e.getMessage());
            }
        }

        validateReferences();
        plugin.getLogger().info("Loaded " + conditions.size() + " conditions");
    }

    // 组合条件引用的条件 ID 必须存在，否则整个条件作废
    private void validateReferences() {
        List<String> toRemove = new ArrayList<>();
        for (Condition c : conditions.values()) {
            if (c.requires == null) continue;
            for (SubRequirement r : c.requires) {
                if (r.conditionId != null && !conditions.containsKey(r.conditionId)) {
                    plugin.getLogger().warning("Condition '" + c.id + "' references missing '"
                        + r.conditionId + "', skipped");
                    toRemove.add(c.id);
                    break;
                }
            }
        }
        for (String id : toRemove) conditions.remove(id);
    }

    // requires 每项可以是字符串（条件 ID）或 map（内联统计），全部满足才算达成
    private List<SubRequirement> parseRequires(ConfigurationSection cs) {
        List<?> raw = cs.getList("requires");
        if (raw == null || raw.isEmpty()) return null;

        List<SubRequirement> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof String ref) {
                if (ref.isBlank()) throw new IllegalArgumentException("empty condition reference");
                result.add(new SubRequirement(ref, null, null, null, 0, null));
            } else if (item instanceof Map<?, ?> m) {
                String typeStr = mapGet(m, "type");
                if (typeStr == null) throw new IllegalArgumentException("requires entry missing type");
                Type t = Type.valueOf(typeStr.toUpperCase());
                String key = mapGet(m, "key");
                if (key == null || key.isEmpty()) throw new IllegalArgumentException("requires entry missing key");
                String op = mapGet(m, "operator");
                if (op == null || op.isEmpty()) op = ">=";
                String valueStr = mapGet(m, "value");
                double value = 0;
                if (t != Type.SET_CONTAINS) {
                    if (valueStr == null) throw new IllegalArgumentException("requires entry missing value");
                    value = Double.parseDouble(valueStr);
                }
                result.add(new SubRequirement(null, t, key, op, value, mapGet(m, "contains")));
            } else {
                throw new IllegalArgumentException("requires entry must be a condition id or a map");
            }
        }
        return result;
    }

    // 键名大小写不敏感读取
    private static String mapGet(Map<?, ?> m, String key) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) {
                Object v = e.getValue();
                return v != null ? String.valueOf(v) : null;
            }
        }
        return null;
    }

    public boolean isMet(UUID uuid, String conditionId) {
        Condition c = conditions.get(conditionId);
        if (c == null) return false;
        return evaluate(c, dataOf(uuid), 0);
    }

    public boolean isMet(UUID uuid, Condition c) {
        return evaluate(c, dataOf(uuid), 0);
    }

    public Map<String, Boolean> checkAll(UUID uuid) {
        PlayerTrackData data = dataOf(uuid);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (var entry : conditions.entrySet()) {
            result.put(entry.getKey(), evaluate(entry.getValue(), data, 0));
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

    // 是否存在需要权限桥管理的条件（带 permission）
    public boolean hasManagedConditions() {
        for (Condition c : conditions.values()) {
            if (c.permission != null && !c.permission.isEmpty()) return true;
        }
        return false;
    }

    private boolean evaluate(Condition c, PlayerTrackData data, int depth) {
        if (depth > MAX_DEPTH) return false; // 循环引用保护
        if (c.requires != null && !c.requires.isEmpty()) {
            for (SubRequirement r : c.requires) {
                if (!evaluateSub(r, data, depth)) return false;
            }
            return true;
        }
        return evaluateSingle(c.type, c.key, c.operator, c.value, c.contains, data);
    }

    private boolean evaluateSub(SubRequirement r, PlayerTrackData data, int depth) {
        if (r.conditionId != null) {
            Condition ref = conditions.get(r.conditionId);
            return ref != null && evaluate(ref, data, depth + 1);
        }
        return evaluateSingle(r.type, r.key, r.operator, r.value, r.contains, data);
    }

    private boolean evaluateSingle(Type type, String key, String operator, double value,
                                   String contains, PlayerTrackData data) {
        double actual;
        switch (type) {
            case COUNTER -> actual = data.getCounter(key);
            case DOUBLE -> actual = data.getDouble(key);
            case SET_SIZE -> actual = data.getSetSize(key);
            case SET_CONTAINS -> {
                return operator.equals("==")
                    ? data.setContains(key, contains)
                    : !data.setContains(key, contains);
            }
            case BOOLEAN -> {
                boolean flag = data.getBooleanFlag(key);
                return operator.equals("==") ? (value > 0) == flag : (value > 0) != flag;
            }
            default -> actual = 0;
        }
        return switch (operator) {
            case ">=" -> actual >= value;
            case "<=" -> actual <= value;
            case "==" -> actual == value;
            case ">" -> actual > value;
            case "<" -> actual < value;
            case "!=" -> actual != value;
            default -> false;
        };
    }
}
