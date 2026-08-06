package com.server.stattracker.tracker;

// 战斗细节追踪器：伤害来源、击杀方式（近战/远程/药水）、死亡原因累计
import com.server.stattracker.StatTrackerPlugin;
import com.server.stattracker.api.StatKeys;
import com.server.stattracker.data.PlayerTrackData;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Explosive;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
public class CombatDetailTracker implements Listener {

    private final StatTrackerPlugin plugin;

    public CombatDetailTracker(StatTrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        double amount = event.getFinalDamage();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.addDouble(StatKeys.DAMAGE_DEALT, amount);
        data.addDouble(StatKeys.DMG_DEALT_PREFIX + event.getCause().name(), amount);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    // track kill method via damage source
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKillMethod(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (victim.getHealth() - event.getFinalDamage() > 0) return; // not a kill hit

        Player killer = null;
        String method = "MELEE";

        if (event.getDamager() instanceof Player p) {
            killer = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            killer = p;
            if (proj instanceof Arrow) method = "RANGED";
            else if (proj instanceof Trident) method = "TRIDENT";
            else if (proj instanceof ThrownPotion) method = "POTION";
            else method = "PROJECTILE";
        } else if (event.getDamager() instanceof Explosive) {
            // TNT ignited by player - hard to track, skip
            return;
        }
        if (killer == null) return;

        PlayerTrackData data = plugin.getDataManager().get(killer.getUniqueId());
        data.increment(StatKeys.KILL_METHOD_PREFIX + method);

        // 护甲上下文：记录击杀时穿戴的护甲及无护甲击杀
        PlayerInventory inv = killer.getInventory();
        ItemStack head  = inv.getHelmet();
        ItemStack chest = inv.getChestplate();
        ItemStack legs  = inv.getLeggings();
        ItemStack feet  = inv.getBoots();

        boolean hasHelmet    = head  != null && head.getType()  != Material.AIR;
        boolean hasChestplate = chest != null && chest.getType() != Material.AIR;
        boolean hasLeggings  = legs  != null && legs.getType()  != Material.AIR;
        boolean hasBoots     = feet  != null && feet.getType()  != Material.AIR;

        boolean noArmor = !hasHelmet && !hasChestplate && !hasLeggings && !hasBoots;
        if (noArmor) {
            data.increment(StatKeys.NO_ARMOR_KILLS);
        } else {
            boolean fullArmor = hasHelmet && hasChestplate && hasLeggings && hasBoots;
            if (fullArmor) data.increment(StatKeys.FULL_ARMOR_KILLS);
            if (hasHelmet)    data.increment(StatKeys.ARMOR_HEAD_PREFIX  + head.getType().name());
            if (hasChestplate) data.increment(StatKeys.ARMOR_CHEST_PREFIX + chest.getType().name());
            if (hasLeggings)  data.increment(StatKeys.ARMOR_LEGS_PREFIX  + legs.getType().name());
            if (hasBoots)     data.increment(StatKeys.ARMOR_FEET_PREFIX  + feet.getType().name());
        }

        plugin.getDataManager().markDirty(killer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double amount = event.getFinalDamage();

        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());
        data.addDouble(StatKeys.DAMAGE_TAKEN, amount);
        data.addDouble(StatKeys.DMG_TAKEN_PREFIX + event.getCause().name(), amount);

        plugin.getDataManager().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTrackData data = plugin.getDataManager().get(player.getUniqueId());

        EntityDamageEvent lastDmg = player.getLastDamageCause();
        if (lastDmg instanceof EntityDamageByEntityEvent dmgByEntity) {
            if (dmgByEntity.getDamager() instanceof Player killer) {
                plugin.getDataManager().get(killer.getUniqueId()).increment(StatKeys.PLAYER_KILLS);
                plugin.getDataManager().markDirty(killer.getUniqueId());
            }
        }

        String cause = lastDmg != null ? lastDmg.getCause().name() : "UNKNOWN";
        data.increment(StatKeys.DEATH_CAUSE_PREFIX + cause);
        data.increment(StatKeys.DEATH_CAUSE_COUNT_PREFIX + cause);

        data.setCounter(StatKeys.LAST_DEATH_X, player.getLocation().getBlockX());
        data.setCounter(StatKeys.LAST_DEATH_Y, player.getLocation().getBlockY());
        data.setCounter(StatKeys.LAST_DEATH_Z, player.getLocation().getBlockZ());

        plugin.getDataManager().markDirty(player.getUniqueId());
    }
}
