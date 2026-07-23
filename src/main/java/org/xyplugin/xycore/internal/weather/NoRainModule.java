package org.xyplugin.xycore.internal.weather;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps configured worlds clear from rain and thunder. */
public final class NoRainModule extends AbstractCoreModule implements Listener {

    private volatile Set<String> worlds = Collections.emptySet();
    private int taskId = -1;

    public NoRainModule(XyCorePlugin plugin) {
        super(plugin, "no-rain", "NoRainModule", "modules/NoRainModule.yml");
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!isEnabled() || !matches(event.getWorld())) return;
        if (event.toWeatherState()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!isEnabled() || !matches(event.getWorld())) return;
        if (event.toThunderState()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled() || !matches(event.getWorld())) return;
        clear(event.getWorld());
    }

    private void startTask() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::applyAll, 20L, 100L).getTaskId();
    }

    private void applyAll() {
        for (World world : Bukkit.getWorlds()) {
            if (matches(world)) clear(world);
        }
    }

    private void clear(World world) {
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(12000);
        world.setThunderDuration(12000);
    }

    private void loadWorlds() {
        Set<String> loaded = new HashSet<>();
        for (String world : getModuleConfig().getStringList("NoRainModule")) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) loaded.add(normalized);
        }
        worlds = Collections.unmodifiableSet(loaded);
        plugin.getLogger().info("NoRainModule 已加载 " + worlds.size() + " 个永远不下雨世界。");
    }

    private boolean matches(World world) {
        if (world == null) return false;
        return worlds.contains("*") || worlds.contains(normalize(world.getName()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}