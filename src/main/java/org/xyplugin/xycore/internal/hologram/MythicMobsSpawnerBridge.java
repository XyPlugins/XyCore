package org.xyplugin.xycore.internal.hologram;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 缓存 MythicMobs 4.11 刷新点与事件 API，避免每秒重复查找反射方法。 */
final class MythicMobsSpawnerBridge {

    private final Object mythicMobs;
    private final Method getSpawnerManager;
    private final Method getSpawners;
    private final Method getMobManager;
    private final Method getMythicMob;
    private final Method getMythicMobDisplayName;
    private final Method getPlaceholderString;

    private final Method getSpawnerName;
    private final Method getSpawnerTypeName;
    private final Method getSpawnerWorldName;
    private final Method getSpawnerBlockX;
    private final Method getSpawnerBlockY;
    private final Method getSpawnerBlockZ;
    private final Method getSpawnerMobCount;
    private final Method isSpawnerOnCooldown;
    private final Method getRemainingCooldownSeconds;
    private final Method isSpawnerOnWarmup;
    private final Method getRemainingWarmupSeconds;

    private final Class<? extends Event> spawnEventType;
    private final Class<? extends Event> deathEventType;
    private final Class<? extends Event> reloadEventType;
    private final Method spawnEventIsFromSpawner;
    private final Method spawnEventGetSpawner;
    private final Method spawnEventGetMob;
    private final Method deathEventGetMob;
    private final Method deathEventGetKiller;
    private final Method activeMobGetSpawner;
    private final Method activeMobGetDisplayName;

    @SuppressWarnings("unchecked")
    MythicMobsSpawnerBridge() throws ReflectiveOperationException {
        Plugin dependency = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (dependency == null || !dependency.isEnabled()) {
            throw new IllegalStateException("未安装或未启用 MythicMobs");
        }

        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> mythicType = load(loader, "io.lumine.xikage.mythicmobs.MythicMobs");
        Class<?> managerType = load(loader,
                "io.lumine.xikage.mythicmobs.spawning.spawners.SpawnerManager");
        Class<?> spawnerType = load(loader,
                "io.lumine.xikage.mythicmobs.spawning.spawners.MythicSpawner");
        Class<?> mobManagerType = load(loader, "io.lumine.xikage.mythicmobs.mobs.MobManager");
        Class<?> mythicMobType = load(loader, "io.lumine.xikage.mythicmobs.mobs.MythicMob");
        Class<?> placeholderStringType = load(loader,
                "io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderString");
        Class<?> activeMobType = load(loader, "io.lumine.xikage.mythicmobs.mobs.ActiveMob");

        this.mythicMobs = invoke(mythicType.getMethod("inst"), null);
        if (mythicMobs == null) throw new IllegalStateException("MythicMobs 尚未完成初始化");

        this.getSpawnerManager = mythicType.getMethod("getSpawnerManager");
        this.getSpawners = managerType.getMethod("getSpawners");
        this.getMobManager = mythicType.getMethod("getMobManager");
        this.getMythicMob = mobManagerType.getMethod("getMythicMob", String.class);
        this.getMythicMobDisplayName = mythicMobType.getMethod("getDisplayName");
        this.getPlaceholderString = placeholderStringType.getMethod("get");

        this.getSpawnerName = spawnerType.getMethod("getName");
        this.getSpawnerTypeName = spawnerType.getMethod("getTypeName");
        this.getSpawnerWorldName = spawnerType.getMethod("getWorldName");
        this.getSpawnerBlockX = spawnerType.getMethod("getBlockX");
        this.getSpawnerBlockY = spawnerType.getMethod("getBlockY");
        this.getSpawnerBlockZ = spawnerType.getMethod("getBlockZ");
        this.getSpawnerMobCount = spawnerType.getMethod("getNumberOfMobs");
        this.isSpawnerOnCooldown = spawnerType.getMethod("isOnCooldown");
        this.getRemainingCooldownSeconds =
                spawnerType.getMethod("getRemainingCooldownSeconds");
        this.isSpawnerOnWarmup = spawnerType.getMethod("isOnWarmup");
        this.getRemainingWarmupSeconds = spawnerType.getMethod("getRemainingWarmupSeconds");

        this.spawnEventType = (Class<? extends Event>) load(loader,
                "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent");
        this.deathEventType = (Class<? extends Event>) load(loader,
                "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent");
        this.reloadEventType = (Class<? extends Event>) load(loader,
                "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent");

        this.spawnEventIsFromSpawner = spawnEventType.getMethod("isFromMythicSpawner");
        this.spawnEventGetSpawner = spawnEventType.getMethod("getMythicSpawner");
        this.spawnEventGetMob = spawnEventType.getMethod("getMob");
        this.deathEventGetMob = deathEventType.getMethod("getMob");
        this.deathEventGetKiller = deathEventType.getMethod("getKiller");
        this.activeMobGetSpawner = activeMobType.getMethod("getSpawner");
        this.activeMobGetDisplayName = activeMobType.getMethod("getDisplayName");
    }

    Collection<?> getSpawners() throws ReflectiveOperationException {
        Object manager = invoke(getSpawnerManager, mythicMobs);
        Object result = manager == null ? null : invoke(getSpawners, manager);
        return result instanceof Collection ? (Collection<?>) result : Collections.emptyList();
    }

    SpawnerInfo describe(Object spawner) throws ReflectiveOperationException {
        String id = text(invoke(getSpawnerName, spawner));
        String mobType = text(invoke(getSpawnerTypeName, spawner));
        String world = text(invoke(getSpawnerWorldName, spawner));
        int x = number(invoke(getSpawnerBlockX, spawner)).intValue();
        int y = number(invoke(getSpawnerBlockY, spawner)).intValue();
        int z = number(invoke(getSpawnerBlockZ, spawner)).intValue();
        return new SpawnerInfo(spawner, id, world, mobType, resolveMobDisplayName(mobType), x, y, z);
    }

    int getMobCount(Object spawner) throws ReflectiveOperationException {
        return Math.max(0, number(invoke(getSpawnerMobCount, spawner)).intValue());
    }

    boolean isOnCooldown(Object spawner) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(isSpawnerOnCooldown, spawner));
    }

    int getRemainingSeconds(Object spawner) throws ReflectiveOperationException {
        return Math.max(0, number(invoke(getRemainingCooldownSeconds, spawner)).intValue());
    }

    boolean isOnWarmup(Object spawner) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(isSpawnerOnWarmup, spawner));
    }

    int getRemainingWarmupSeconds(Object spawner) throws ReflectiveOperationException {
        return Math.max(0, number(invoke(getRemainingWarmupSeconds, spawner)).intValue());
    }

    Object getSpawnEventSpawner(Event event) throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(invoke(spawnEventIsFromSpawner, event))) return null;
        return invoke(spawnEventGetSpawner, event);
    }

    String getSpawnEventDisplayName(Event event) throws ReflectiveOperationException {
        Object activeMob = invoke(spawnEventGetMob, event);
        return activeMob == null ? "" : text(invoke(activeMobGetDisplayName, activeMob));
    }

    Object getDeathEventSpawner(Event event) throws ReflectiveOperationException {
        Object activeMob = invoke(deathEventGetMob, event);
        return activeMob == null ? null : invoke(activeMobGetSpawner, activeMob);
    }

    Player getDeathEventPlayerKiller(Event event) throws ReflectiveOperationException {
        Object killer = invoke(deathEventGetKiller, event);
        return killer instanceof Player ? (Player) killer : null;
    }

    String getSpawnerId(Object spawner) throws ReflectiveOperationException {
        return text(invoke(getSpawnerName, spawner));
    }

    Class<? extends Event> getSpawnEventType() {
        return spawnEventType;
    }

    Class<? extends Event> getDeathEventType() {
        return deathEventType;
    }

    Class<? extends Event> getReloadEventType() {
        return reloadEventType;
    }

    private String resolveMobDisplayName(String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) return "未知怪物";
        try {
            Object manager = invoke(getMobManager, mythicMobs);
            if (manager == null) return rawType;

            List<String> names = new ArrayList<>();
            for (String candidate : mobTypeCandidates(rawType)) {
                Object mob = invoke(getMythicMob, manager, candidate);
                if (mob == null) continue;
                Object displayName = invoke(getMythicMobDisplayName, mob);
                String resolved = displayName == null ? "" : text(invoke(getPlaceholderString, displayName));
                if (!resolved.trim().isEmpty() && !names.contains(resolved)) names.add(resolved);
            }
            return names.isEmpty() ? rawType : String.join(" / ", names);
        } catch (Exception ignored) {
            return rawType;
        }
    }

    private Set<String> mobTypeCandidates(String rawType) {
        Set<String> candidates = new LinkedHashSet<>();
        String trimmed = rawType.trim();
        candidates.add(trimmed);
        for (String part : trimmed.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) continue;
            candidates.add(candidate);
            int cut = firstPositive(candidate.indexOf('%'), candidate.indexOf(':'),
                    candidate.indexOf(' '));
            if (cut > 0) candidates.add(candidate.substring(0, cut).trim());
        }
        return candidates;
    }

    private int firstPositive(int... values) {
        int result = Integer.MAX_VALUE;
        for (int value : values) if (value > 0 && value < result) result = value;
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    private Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw new IllegalStateException(cause == null ? failure.getMessage() : cause.getMessage(),
                    cause == null ? failure : cause);
        }
    }

    private Number number(Object value) {
        return value instanceof Number ? (Number) value : 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static final class SpawnerInfo {
        final Object handle;
        final String id;
        final String world;
        final String mobType;
        final String defaultMobName;
        final int x;
        final int y;
        final int z;

        private SpawnerInfo(Object handle, String id, String world, String mobType,
                            String defaultMobName, int x, int y, int z) {
            this.handle = handle;
            this.id = id;
            this.world = world;
            this.mobType = mobType;
            this.defaultMobName = defaultMobName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String locationKey() {
            return world.toLowerCase(Locale.ROOT) + ':' + x + ':' + y + ':' + z;
        }
    }
}
