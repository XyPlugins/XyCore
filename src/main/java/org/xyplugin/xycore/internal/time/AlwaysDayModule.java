package org.xyplugin.xycore.internal.time;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps configured worlds at daytime without changing persistent gamerules. */
public final class AlwaysDayModule extends AbstractCoreModule implements Listener {

    private static final long DAY_TIME = 6000L;

    private volatile Set<String> worlds = Collections.emptySet();
    private int taskId = -1;

    public AlwaysDayModule(XyCorePlugin plugin) {
        super(plugin, "always-day", "AlwaysDayModule", "modules/AlwaysDayModule.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadWorlds();
        startTask();
        applyAll();
    }

    @Override
    protected void onReload() {
        loadWorlds();
        applyAll();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        worlds = Collections.emptySet();
    }

    public int getWorldCount() {
        return worlds.contains("*") ? Bukkit.getWorlds().size() : worlds.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled() || !matches(event.getWorld())) return;
        event.getWorld().setTime(DAY_TIME);
    }

    private void startTask() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::applyAll, 20L, 20L).getTaskId();
    }

    private void applyAll() {
        for (World world : Bukkit.getWorlds()) {
            if (matches(world)) world.setTime(DAY_TIME);
        }
    }

    private void loadWorlds() {
        Set<String> loaded = new HashSet<>();
        for (String world : getModuleConfig().getStringList("AlwaysDayModule")) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) loaded.add(normalized);
        }
        worlds = Collections.unmodifiableSet(loaded);
        plugin.getLogger().info("AlwaysDayModule 已加载 " + worlds.size() + " 个永远白天世界。");
    }

    private boolean matches(World world) {
        if (world == null) return false;
        return worlds.contains("*") || worlds.contains(normalize(world.getName()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}