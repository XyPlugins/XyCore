package org.xyplugin.xycore.internal.death;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps inventory and experience on death in configured worlds. */
public final class DeathKeepModule extends AbstractCoreModule implements Listener {

    private volatile Set<String> worlds = Collections.emptySet();

    public DeathKeepModule(XyCorePlugin plugin) {
        super(plugin, "death-keep", "DeathKeepModule", "modules/DeathKeepModule.yml");
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEnabled() || event.getEntity() == null) return;
        if (!matches(event.getEntity().getWorld())) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    private void loadWorlds() {
        Set<String> loaded = new HashSet<>();
        for (String world : getModuleConfig().getStringList("DeathKeepModule")) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) loaded.add(normalized);
        }
        worlds = Collections.unmodifiableSet(loaded);
        plugin.getLogger().info("DeathKeepModule 已加载 " + worlds.size() + " 个死亡不掉落世界。");
    }

    private boolean matches(World world) {
        if (world == null) return false;
        return worlds.contains("*") || worlds.contains(normalize(world.getName()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}