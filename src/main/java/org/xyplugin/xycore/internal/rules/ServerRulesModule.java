package org.xyplugin.xycore.internal.rules;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 服务器规则模块。
 *
 * <p>0.3.3 起，死亡不掉落、禁止 PVP、白天规则、天气规则统一由一个模块和一个
 * server-rules.yml 管理，避免四个轻量规则各自生成配置文件导致维护碎片化。</p>
 */
public final class ServerRulesModule extends AbstractCoreModule implements Listener {

    private static final long DAY_RESET_WHEN = 11500L;
    private static final long DAY_RESET_TO = 6000L;
    private static final long DAY_CHECK_INTERVAL_TICKS = 200L;
    private static final long WEATHER_CHECK_INTERVAL_TICKS = 100L;
    private static final int WEATHER_DURATION = 12000;
    private static final int THUNDER_DURATION = 12000;

    private RuleSet deathKeep = RuleSet.empty();
    private RuleSet pvpProtect = RuleSet.empty();
    private RuleSet alwaysDay = RuleSet.empty();
    private RuleSet noRain = RuleSet.empty();

    private int dayTaskId = -1;
    private int weatherTaskId = -1;

    public ServerRulesModule(XyCorePlugin plugin) {
        super(plugin, "server-rules", "ServerRules", "modules/server-rules.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadRules();
        startTasks();
        applyDayRules();
        applyWeatherRules();
    }

    @Override
    protected void onReload() throws Exception {
        loadRules();
        startTasks();
        applyDayRules();
        applyWeatherRules();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        cancelTasks();
        deathKeep = RuleSet.empty();
        pvpProtect = RuleSet.empty();
        alwaysDay = RuleSet.empty();
        noRain = RuleSet.empty();
    }

    public int getDeathKeepWorldCount() {
        return countWorlds(deathKeep);
    }

    public int getPvpProtectWorldCount() {
        return countWorlds(pvpProtect);
    }

    public int getAlwaysDayWorldCount() {
        return countWorlds(alwaysDay);
    }

    public int getNoRainWorldCount() {
        return countWorlds(noRain);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEnabled() || event.getEntity() == null) return;
        if (!matches(deathKeep, event.getEntity().getWorld())) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!matches(pvpProtect, victim.getWorld())) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!isEnabled() || !matches(noRain, event.getWorld())) return;
        if (event.toWeatherState()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!isEnabled() || !matches(noRain, event.getWorld())) return;
        if (event.toThunderState()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        applyDayRule(event.getWorld());
        clearWeather(event.getWorld());
    }

    private void loadRules() {
        deathKeep = loadRuleSet("death-keep", "DeathKeepModule.yml", "DeathKeepModule");
        pvpProtect = loadRuleSet("pvp-protect", "PvpProtectModule.yml", "PvpProtectModule");
        alwaysDay = loadRuleSet("always-day", "AlwaysDayModule.yml", "AlwaysDayModule");
        noRain = loadRuleSet("no-rain", "NoRainModule.yml", "NoRainModule");

        plugin.getLogger().info("ServerRules 已加载：死亡不掉落 " + getDeathKeepWorldCount()
                + " 个世界，禁止 PVP " + getPvpProtectWorldCount()
                + " 个世界，白天规则 " + getAlwaysDayWorldCount()
                + " 个世界，天气规则 " + getNoRainWorldCount() + " 个世界。");
    }

    private RuleSet loadRuleSet(String section, String legacyFileName, String legacyKey) {
        FileConfiguration config = getModuleConfig();
        Set<String> worlds = new HashSet<>();
        List<String> configured = config.getStringList(section);
        // 兼容 0.3.3 开发中短暂出现过的 section.worlds 写法；正式默认配置只使用根节点世界列表。
        if (configured.isEmpty()) configured = config.getStringList(section + ".worlds");
        if (configured.isEmpty()) {
            configured = loadLegacyWorlds(legacyFileName, legacyKey);
            if (!configured.isEmpty()) {
                plugin.getLogger().info("ServerRules 兼容读取旧配置 " + legacyFileName
                        + "，建议手动复制到 server-rules.yml 的 " + section + " 列表后删除旧文件。");
            }
        }
        for (String world : configured) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) worlds.add(normalized);
        }
        return new RuleSet(Collections.unmodifiableSet(worlds));
    }

    private void startTasks() {
        cancelTasks();

        if (!alwaysDay.worlds.isEmpty()) {
            dayTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::applyDayRules,
                    DAY_CHECK_INTERVAL_TICKS, DAY_CHECK_INTERVAL_TICKS).getTaskId();
        }

        if (!noRain.worlds.isEmpty()) {
            weatherTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::applyWeatherRules,
                    WEATHER_CHECK_INTERVAL_TICKS, WEATHER_CHECK_INTERVAL_TICKS).getTaskId();
        }
    }

    private void cancelTasks() {
        if (dayTaskId != -1) Bukkit.getScheduler().cancelTask(dayTaskId);
        if (weatherTaskId != -1) Bukkit.getScheduler().cancelTask(weatherTaskId);
        dayTaskId = -1;
        weatherTaskId = -1;
    }

    private void applyDayRules() {
        if (!isEnabled()) return;
        for (World world : Bukkit.getWorlds()) applyDayRule(world);
    }

    private void applyDayRule(World world) {
        if (!isEnabled() || !matches(alwaysDay, world)) return;
        long time = normalizeTime(world.getTime());
        if (time >= DAY_RESET_WHEN) world.setTime(DAY_RESET_TO);
    }

    private void applyWeatherRules() {
        if (!isEnabled()) return;
        for (World world : Bukkit.getWorlds()) clearWeather(world);
    }

    private void clearWeather(World world) {
        if (!isEnabled() || !matches(noRain, world)) return;
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(WEATHER_DURATION);
        world.setThunderDuration(THUNDER_DURATION);
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

    private boolean matches(RuleSet rule, World world) {
        if (world == null || rule.worlds.isEmpty()) return false;
        return rule.worlds.contains("*") || rule.worlds.contains(normalize(world.getName()));
    }

    private int countWorlds(RuleSet rule) {
        if (rule.worlds.isEmpty()) return 0;
        return rule.worlds.contains("*") ? Bukkit.getWorlds().size() : rule.worlds.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private long normalizeTime(long time) {
        long value = time % 24000L;
        return value < 0L ? value + 24000L : value;
    }

    private List<String> loadLegacyWorlds(String fileName, String legacyKey) {
        File file = new File(plugin.getDataFolder(), "modules" + File.separator + fileName);
        if (!file.isFile()) return Collections.emptyList();
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        return legacy.getStringList(legacyKey);
    }

    private static final class RuleSet {
        private final Set<String> worlds;

        private RuleSet(Set<String> worlds) {
            this.worlds = worlds;
        }

        private static RuleSet empty() {
            return new RuleSet(Collections.emptySet());
        }
    }
}
