package com.server.stattracker.tracker;

import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.Material;
import java.util.Collections;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
public class TradingTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public TradingTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof MerchantInventory merchantInv)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        MerchantRecipe recipe = merchantInv.getSelectedRecipe();
        if (recipe == null) return;

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.increment(StatKeys.TRADE_COUNT);

        for (ItemStack ingredient : Collections.singletonList(recipe.getIngredients().get(0))) {
            if (ingredient.getType() == Material.EMERALD) {
                data.increment(StatKeys.EMERALDS_SPENT, ingredient.getAmount());
            }
        }
        if (result.getType() == Material.EMERALD) {
            data.increment(StatKeys.EMERALDS_EARNED, result.getAmount());
        }

        if (merchantInv.getHolder() instanceof Villager villager) {
            data.addToSet(StatKeys.TRADED_PROFESSIONS, villager.getProfession().name());
        }

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
