package com.server.stattracker.condition;

import com.server.stattracker.StatTrackerPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

public class ConditionPAPI extends PlaceholderExpansion {

    private final StatTrackerPlugin plugin;
    private final ConditionManager cm;

    public ConditionPAPI(StatTrackerPlugin plugin, ConditionManager cm) {
        this.plugin = plugin;
        this.cm = cm;
    }

    @Override public String getIdentifier() { return "statcond"; }
    @Override public String getAuthor()      { return "Sakana"; }
    @Override public String getVersion()     { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()       { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return null;
        return resolve(player.getUniqueId().toString(), params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return null;
        return resolve(player.getUniqueId().toString(), params);
    }

    private String resolve(String uuidStr, String params) {
        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
        String lower = params.toLowerCase();

        if ("met_count".equals(lower)) {
            return String.valueOf(cm.checkAll(uuid).values().stream().filter(b -> b).count());
        }

        if ("total".equals(lower)) {
            return String.valueOf(cm.getAllConditions().size());
        }

        if ("met_percent".equals(lower)) {
            Map<String, Boolean> all = cm.checkAll(uuid);
            int met = (int) all.values().stream().filter(b -> b).count();
            return all.isEmpty() ? "0" : String.format("%.1f", met * 100.0 / all.size());
        }

        // %statcond_xxx_display% -> display name
        if (lower.endsWith("_display")) {
            String id = lower.substring(0, lower.length() - 8);
            ConditionManager.Condition c = cm.getCondition(id);
            return c != null ? c.display : null;
        }

        // %statcond_xxx% -> true/false
        if (cm.getCondition(lower) != null) {
            return cm.isMet(uuid, lower) ? "true" : "false";
        }

        return null;
    }
}
