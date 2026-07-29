package org.xyplugin.xycore.internal.hologram;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 为 MythicMobs 4.11 刷新点自动创建四行 HolographicDisplays 全息。
 *
 * <p>全部扫描、事件处理和文本更新都在主线程执行。全息只更新发生变化的 TextLine，
 * 不会像旧实现一样每秒清空并重建所有盔甲架行。</p>
 */
public final class MythicSpawnerHologramModule extends AbstractCoreModule {

    private static final long MIN_INTERVAL_TICKS = 20L;
    private static final int MAX_LINES = 8;
    private static final long WARNING_INTERVAL_MS = 30000L;
    private static final String DRAGONCORE_HEALTHBAR_GUARD = "\u200B";

    private final Listener mythicEventListener = new Listener() { };
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, String> lastKillers = new HashMap<>();
    private final Map<String, String> worldAliasCache = new HashMap<>();

    private MythicMobsSpawnerBridge mythic;
    private HolographicDisplaysBridge holograms;
    private int updateTaskId = -1;
    private int scanTaskId = -1;
    private long updateIntervalTicks = 20L;
    private long scanIntervalTicks = 100L;
    private double heightOffset = 3.0D;
    private List<String> lineTemplates = Collections.emptyList();
    private Set<String> excludedSpawners = Collections.emptySet();
    private Set<String> excludedWorlds = Collections.emptySet();
    private Map<String, String> nameOverrides = Collections.emptyMap();
    private String aliveText = "&a存活中";
    private String readyText = "&a即将刷新";
    private String noKillerText = "&7暂无";
    private String hoursFormat = "{hours}时{minutes}分{seconds}秒";
    private String minutesFormat = "{minutes}分{seconds}秒";
    private String secondsFormat = "{seconds}秒";
    private String worldNameMode = "alias";
    private boolean dragonCoreHealthbarGuard = true;
    private boolean armorStandMarkerGuard = true;
    private boolean hideWhileMobAlive = true;
    private long lastWarningAt;

    public MythicSpawnerHologramModule(XyCorePlugin plugin) {
        super(plugin, "mythic-spawner-hologram", "MythicSpawnerHologram",
                "modules/mythic-spawner-hologram.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        loadSettings();
        mythic = new MythicMobsSpawnerBridge();
        holograms = new HolographicDisplaysBridge(plugin);
        registerMythicEvents();
        startTasks();
        reconcileSafely();
        plugin.getLogger().info("MythicSpawnerHologram 已创建 " + entries.size() + " 个刷新点全息。");
    }

    @Override
    protected void onReload() {
        loadSettings();
        stopTasks();
        deleteAllHolograms();
        startTasks();
        reconcileSafely();
        plugin.getLogger().info("MythicSpawnerHologram 已重载，当前 " + entries.size() + " 个全息。");
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(mythicEventListener);
        stopTasks();
        deleteAllHolograms();
        lastKillers.clear();
        mythic = null;
        holograms = null;
    }

    public int size() {
        return entries.size();
    }

    private void loadSettings() {
        heightOffset = getModuleConfig().getDouble("settings.height-offset", 3.0D);
        updateIntervalTicks = Math.max(MIN_INTERVAL_TICKS,
                getModuleConfig().getLong("settings.update-interval-ticks", 20L));
        scanIntervalTicks = Math.max(MIN_INTERVAL_TICKS,
                getModuleConfig().getLong("settings.scan-interval-ticks", 100L));
        excludedSpawners = normalizedSet(getModuleConfig().getStringList(
                "settings.excluded-spawners"));
        excludedWorlds = normalizedSet(getModuleConfig().getStringList(
                "settings.excluded-worlds"));

        List<String> configuredLines = new ArrayList<>(getModuleConfig().getStringList("display.lines"));
        if (configuredLines.isEmpty()) configuredLines.addAll(defaultLines());
        if (configuredLines.size() > MAX_LINES) {
            plugin.getLogger().warning("MythicSpawnerHologram 最多支持 " + MAX_LINES
                    + " 行，超出的配置已忽略。");
            configuredLines = new ArrayList<>(configuredLines.subList(0, MAX_LINES));
        }
        lineTemplates = Collections.unmodifiableList(configuredLines);

        aliveText = getModuleConfig().getString("display.alive-text", "&a存活中");
        readyText = getModuleConfig().getString("display.ready-text", "&a即将刷新");
        noKillerText = getModuleConfig().getString("display.no-killer-text", "&7暂无");
        hoursFormat = getModuleConfig().getString("display.time-format.hours",
                "{hours}时{minutes}分{seconds}秒");
        minutesFormat = getModuleConfig().getString("display.time-format.minutes",
                "{minutes}分{seconds}秒");
        secondsFormat = getModuleConfig().getString("display.time-format.seconds", "{seconds}秒");
        worldNameMode = getModuleConfig().getString("display.world-name-mode", "alias");
        if (worldNameMode == null || !worldNameMode.equalsIgnoreCase("raw")) {
            worldNameMode = "alias";
        } else {
            worldNameMode = "raw";
        }
        dragonCoreHealthbarGuard = getModuleConfig().getBoolean(
                "display.dragoncore-healthbar-guard", true);
        armorStandMarkerGuard = getModuleConfig().getBoolean("display.armorstand-marker-guard", true);
        hideWhileMobAlive = getModuleConfig().getBoolean("display.hide-while-mob-alive", true);
        worldAliasCache.clear();

        Map<String, String> overrides = new HashMap<>();
        ConfigurationSection section = getModuleConfig().getConfigurationSection("name-overrides");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String configuredName = section.getString(id, "");
                String name = configuredName == null ? "" : configuredName.trim();
                if (!name.isEmpty()) overrides.put(normalize(id), name);
            }
        }
        nameOverrides = Collections.unmodifiableMap(overrides);
    }

    private void registerMythicEvents() {
        registerEvent(mythic.getSpawnEventType(), this::onMythicSpawn);
        registerEvent(mythic.getDeathEventType(), this::onMythicDeath);
        registerEvent(mythic.getReloadEventType(), this::onMythicReload);
    }

    private void registerEvent(Class<? extends Event> eventType, Consumer<Event> handler) {
        EventExecutor executor = (listener, event) -> {
            if (isEnabled() && eventType.isInstance(event)) handler.accept(event);
        };
        Bukkit.getPluginManager().registerEvent(eventType, mythicEventListener,
                EventPriority.MONITOR, executor, plugin, true);
    }

    private void onMythicSpawn(Event event) {
        try {
            Object spawner = mythic.getSpawnEventSpawner(event);
            if (spawner == null) return;
            Entry entry = entries.get(normalize(mythic.getSpawnerId(spawner)));
            if (entry == null) return;
            String observedName = mythic.getSpawnEventDisplayName(event);
            if (observedName != null && !observedName.trim().isEmpty()) {
                entry.observedMobName = observedName;
            }
            updateEntry(entry);
        } catch (Exception failure) {
            warn("处理 MythicMobs 生成事件失败", failure);
        }
    }

    private void onMythicDeath(Event event) {
        try {
            Object spawner = mythic.getDeathEventSpawner(event);
            Player killer = mythic.getDeathEventPlayerKiller(event);
            if (spawner == null || killer == null) return;
            String key = normalize(mythic.getSpawnerId(spawner));
            if (key.isEmpty()) return;
            lastKillers.put(key, killer.getName());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isEnabled()) return;
                Entry entry = entries.get(key);
                if (entry != null) updateEntrySafely(entry);
            }, 1L);
        } catch (Exception failure) {
            warn("处理 MythicMobs 死亡事件失败", failure);
        }
    }

    private void onMythicReload(Event event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isEnabled()) return;
            deleteAllHolograms();
            reconcileSafely();
        }, 2L);
    }

    private void startTasks() {
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllSafely,
                updateIntervalTicks, updateIntervalTicks).getTaskId();
        scanTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileSafely,
                scanIntervalTicks, scanIntervalTicks).getTaskId();
    }

    private void stopTasks() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
        if (scanTaskId != -1) Bukkit.getScheduler().cancelTask(scanTaskId);
        updateTaskId = -1;
        scanTaskId = -1;
    }

    private void reconcileSafely() {
        if (!isEnabled() || mythic == null || holograms == null) return;
        try {
            reconcile();
        } catch (Exception failure) {
            warn("同步 MythicMobs 刷新点失败", failure);
        }
    }

    private void reconcile() throws ReflectiveOperationException {
        Collection<?> spawners = mythic.getSpawners();
        Set<String> activeKeys = new HashSet<>();
        for (Object spawner : spawners) {
            MythicMobsSpawnerBridge.SpawnerInfo info = mythic.describe(spawner);
            String key = normalize(info.id);
            if (key.isEmpty() || isExcluded(key, normalize(info.world))) continue;

            World world = Bukkit.getWorld(info.world);
            if (world == null) continue;
            activeKeys.add(key);

            Entry entry = entries.get(key);
            if (entry != null && entry.info.locationKey().equals(info.locationKey())) {
                if (!entry.info.mobType.equals(info.mobType)) entry.observedMobName = "";
                entry.info = info;
                updateEntry(entry);
                continue;
            }

            if (entry != null) removeEntry(key, entry);
            Entry created = new Entry(key, info);
            entries.put(key, created);
            updateEntry(created);
        }

        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Entry> mapped = iterator.next();
            if (activeKeys.contains(mapped.getKey())) continue;
            holograms.delete(mapped.getValue().hologram);
            iterator.remove();
        }
    }

    private void updateAllSafely() {
        if (!isEnabled()) return;
        for (Entry entry : new ArrayList<>(entries.values())) updateEntrySafely(entry);
    }

    private void updateEntrySafely(Entry entry) {
        try {
            updateEntry(entry);
        } catch (Exception failure) {
            warn("更新刷新点 " + entry.info.id + " 的全息失败", failure);
            // 全息可能被外部插件删除；移除本地句柄后由下一次扫描自动重建。
            if (entries.get(entry.key) == entry) removeEntry(entry.key, entry);
        }
    }

    private void updateEntry(Entry entry) throws ReflectiveOperationException {
        if (hideWhileMobAlive && mythic.getMobCount(entry.info.handle) > 0) {
            deleteEntryHologram(entry);
            return;
        }
        if (entry.hologram == null) {
            World world = Bukkit.getWorld(entry.info.world);
            if (world == null) return;
            Location location = location(world, entry.info);
            List<String> lines = renderLines(entry);
            entry.hologram = holograms.create(location, lines);
            applyArmorStandMarkerGuard(location, lines);
            return;
        }
        holograms.update(entry.hologram, renderLines(entry));
    }

    private void deleteEntryHologram(Entry entry) {
        if (entry.hologram == null) return;
        holograms.delete(entry.hologram);
        entry.hologram = null;
    }

    private void applyArmorStandMarkerGuard(Location center, List<String> lines) {
        if (!armorStandMarkerGuard || center == null || center.getWorld() == null || lines.isEmpty()) return;
        Set<String> expectedNames = new HashSet<>(lines);
        double verticalRadius = Math.max(2.0D, lines.size() * 0.35D + 1.0D);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 1.5D, verticalRadius, 1.5D)) {
            if (!(entity instanceof ArmorStand)) continue;
            String customName = entity.getCustomName();
            if (customName == null || !expectedNames.contains(customName)) continue;
            ArmorStand stand = (ArmorStand) entity;
            try {
                stand.setMarker(true);
                stand.setVisible(false);
                stand.setGravity(false);
            } catch (RuntimeException ignored) {
                // 某些 HolographicDisplays 实现会自行管理盔甲架状态；失败时保留原行为。
            }
        }
    }

    private List<String> renderLines(Entry entry) throws ReflectiveOperationException {
        String mobName = firstNonEmpty(nameOverrides.get(entry.key),
                entry.observedMobName, entry.info.defaultMobName, entry.info.mobType, "未知怪物");
        String displayedMobName = dragonCoreHealthbarGuard ? breakContainsMatch(mobName) : mobName;
        String respawn;
        if (mythic.getMobCount(entry.info.handle) > 0) {
            respawn = aliveText;
        } else if (mythic.isOnWarmup(entry.info.handle)) {
            int seconds = mythic.getRemainingWarmupSeconds(entry.info.handle);
            respawn = seconds > 0 ? formatTime(seconds) : readyText;
        } else if (mythic.isOnCooldown(entry.info.handle)) {
            int seconds = mythic.getRemainingSeconds(entry.info.handle);
            respawn = seconds > 0 ? formatTime(seconds) : readyText;
        } else {
            respawn = readyText;
        }
        String killer = lastKillers.getOrDefault(entry.key, noKillerText);
        String worldRaw = entry.info.world;
        String worldAlias = resolveWorldAlias(worldRaw);
        String worldDisplay = worldNameMode.equals("raw") ? worldRaw : worldAlias;

        List<String> rendered = new ArrayList<>(lineTemplates.size());
        for (String template : lineTemplates) {
            String line = template
                    .replace("{world_raw}", worldRaw)
                    .replace("{world_alias}", worldAlias)
                    .replace("{world}", worldDisplay)
                    .replace("{spawner}", entry.info.id)
                    .replace("{mob_id}", entry.info.mobType)
                    .replace("{mob_name}", displayedMobName)
                    .replace("{respawn}", respawn)
                    .replace("{killer}", killer);
            if (dragonCoreHealthbarGuard && template.contains("{mob_name}")) {
                line = DRAGONCORE_HEALTHBAR_GUARD + line;
            }
            rendered.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return rendered;
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        String format = hours > 0L ? hoursFormat : minutes > 0L ? minutesFormat : secondsFormat;
        return format
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", hours > 0L ? twoDigits(minutes) : String.valueOf(minutes))
                .replace("{seconds}", (hours > 0L || minutes > 0L)
                        ? twoDigits(seconds) : String.valueOf(seconds));
    }

    private String twoDigits(long value) {
        return value < 10L ? "0" + value : String.valueOf(value);
    }

    private String breakContainsMatch(String text) {
        if (text == null || text.length() < 2) return text;
        StringBuilder builder = new StringBuilder(text.length() * 2);
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            builder.append(current);
            if ((current == '&' || current == ChatColor.COLOR_CHAR) && index + 1 < text.length()) {
                builder.append(text.charAt(++index));
                continue;
            }
            if (index + 1 < text.length()) {
                char next = text.charAt(index + 1);
                if (next != '&' && next != ChatColor.COLOR_CHAR) {
                    builder.append(DRAGONCORE_HEALTHBAR_GUARD);
                }
            }
        }
        return builder.toString();
    }

    private Location location(World world, MythicMobsSpawnerBridge.SpawnerInfo info) {
        return new Location(world, info.x + 0.5D, info.y + heightOffset, info.z + 0.5D);
    }

    private boolean isExcluded(String spawner, String world) {
        return excludedSpawners.contains("*") || excludedSpawners.contains(spawner)
                || excludedWorlds.contains("*") || excludedWorlds.contains(world);
    }

    private void removeEntry(String key, Entry entry) {
        holograms.delete(entry.hologram);
        entries.remove(key);
    }

    private void deleteAllHolograms() {
        if (holograms != null) {
            for (Entry entry : entries.values()) holograms.delete(entry.hologram);
        }
        entries.clear();
    }

    private Set<String> normalizedSet(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String key = normalize(value);
            if (!key.isEmpty()) normalized.add(key);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return "";
    }

    private String resolveWorldAlias(String worldName) {
        String raw = firstNonEmpty(worldName, "未知世界");
        String key = normalize(raw);
        if (key.isEmpty()) return raw;
        String cached = worldAliasCache.get(key);
        if (cached != null) return cached;

        String alias = lookupMultiverseAlias(raw);
        worldAliasCache.put(key, alias);
        return alias;
    }

    private String lookupMultiverseAlias(String worldName) {
        Plugin multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (multiverse == null || !multiverse.isEnabled()) return worldName;
        try {
            Object worldManager = multiverse.getClass().getMethod("getMVWorldManager").invoke(multiverse);
            if (worldManager == null) return worldName;
            Object mvWorld = worldManager.getClass().getMethod("getMVWorld", String.class)
                    .invoke(worldManager, worldName);
            if (mvWorld == null) return worldName;
            Method getAlias = mvWorld.getClass().getMethod("getAlias");
            Object alias = getAlias.invoke(mvWorld);
            String value = alias == null ? "" : String.valueOf(alias).trim();
            return value.isEmpty() ? worldName : value;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                 | RuntimeException ignored) {
            return worldName;
        }
    }

    private List<String> defaultLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&7当前世界: &f{world}");
        lines.add("&c{mob_name}");
        lines.add("&e复活倒计时: &f{respawn}");
        lines.add("&7上一任击杀者: &f{killer}");
        return lines;
    }

    private void warn(String action, Exception failure) {
        long now = System.currentTimeMillis();
        if (now - lastWarningAt < WARNING_INTERVAL_MS) return;
        lastWarningAt = now;
        String message = failure.getMessage();
        plugin.getLogger().warning(action + ": "
                + (message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName() : message));
    }

    private static final class Entry {
        private final String key;
        private MythicMobsSpawnerBridge.SpawnerInfo info;
        private HolographicDisplaysBridge.HologramHandle hologram;
        private String observedMobName = "";

        private Entry(String key, MythicMobsSpawnerBridge.SpawnerInfo info) {
            this.key = key;
            this.info = info;
        }
    }
}
