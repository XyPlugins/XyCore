package org.xyplugin.xycore.internal.pvp;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Blocks player-versus-player damage in configured worlds. */
public final class PvpProtectModule extends AbstractCoreModule implements Listener {

    private volatile Set<String> worlds = Collections.emptySet();

    public PvpProtectModule(XyCorePlugin plugin) {
        super(plugin, "pvp-protect", "PvpProtectModule", "modules/PvpProtectModule.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadWorlds();
    }

    @Override
    protected void onReload() {
        loadWorlds();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        worlds = Collections.emptySet();
    }

    public int getWorldCount() {
        return worlds.contains("*") ? Bukkit.getWorlds().size() : worlds.size();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!matches(victim.getWorld())) return;

        event.setCancelled(true);
    }

    private void loadWorlds() {
        Set<String> loaded = new HashSet<>();
        for (String world : getModuleConfig().getStringList("PvpProtectModule")) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) loaded.add(normalized);
        }
        worlds = Collections.unmodifiableSet(loaded);
        plugin.getLogger().info("PvpProtectModule 已加载 " + worlds.size() + " 个禁止 PVP 世界。");
    }

    private boolean matches(World world) {
        if (world == null) return false;
        return worlds.contains("*") || worlds.contains(normalize(world.getName()));
    }

    private Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        if (damager instanceof Tameable) {
            org.bukkit.entity.AnimalTamer owner = ((Tameable) damager).getOwner();
            return owner instanceof Player ? (Player) owner : null;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}