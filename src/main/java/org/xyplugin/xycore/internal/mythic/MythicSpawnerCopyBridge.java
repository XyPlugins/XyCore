package org.xyplugin.xycore.internal.mythic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 通过反射调用 MythicMobs 4.11 刷新点复制 API。 */
public final class MythicSpawnerCopyBridge {

    private final Object mythicMobs;
    private final Method getSpawnerManager;
    private final Method getSpawners;
    private final Method getSpawnerByName;
    private final Method copySpawner;
    private final Method adaptLocation;
    private final Method getSpawnerName;

    public MythicSpawnerCopyBridge() throws ReflectiveOperationException {
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
        Class<?> abstractLocationType = load(loader,
                "io.lumine.xikage.mythicmobs.adapters.AbstractLocation");
        Class<?> bukkitAdapterType = load(loader,
                "io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter");

        this.mythicMobs = invoke(mythicType.getMethod("inst"), null);
        if (mythicMobs == null) throw new IllegalStateException("MythicMobs 尚未完成初始化");

        this.getSpawnerManager = mythicType.getMethod("getSpawnerManager");
        this.getSpawners = managerType.getMethod("getSpawners");
        this.getSpawnerByName = managerType.getMethod("getSpawnerByName", String.class);
        this.copySpawner = managerType.getMethod("copySpawner",
                String.class, String.class, abstractLocationType);
        this.adaptLocation = bukkitAdapterType.getMethod("adapt", Location.class);
        this.getSpawnerName = spawnerType.getMethod("getName");
    }

    public CopyResult copy(String sourceId, String targetId, Location location)
            throws ReflectiveOperationException {
        Object manager = spawnerManager();
        if (findSpawner(manager, sourceId) == null) return CopyResult.SOURCE_NOT_FOUND;
        if (findSpawner(manager, targetId) != null) return CopyResult.TARGET_EXISTS;

        Object adaptedLocation = invoke(adaptLocation, null, location);
        boolean copied = Boolean.TRUE.equals(invoke(copySpawner, manager,
                sourceId, targetId, adaptedLocation));
        return copied ? CopyResult.SUCCESS : CopyResult.FAILED;
    }

    public String uniqueId(String preferred) throws ReflectiveOperationException {
        Object manager = spawnerManager();
        String base = normalizeId(preferred);
        if (base.isEmpty()) base = "xycore_spawner";
        if (findSpawner(manager, base) == null) return base;
        for (int index = 2; index < 10000; index++) {
            String candidate = base + "_" + index;
            if (findSpawner(manager, candidate) == null) return candidate;
        }
        throw new IllegalStateException("无法生成不重复的刷新点 ID");
    }

    public List<String> getSpawnerIds() throws ReflectiveOperationException {
        Object result = invoke(getSpawners, spawnerManager());
        List<String> ids = new ArrayList<>();
        if (!(result instanceof Collection)) return ids;
        for (Object spawner : (Collection<?>) result) {
            String id = text(invoke(getSpawnerName, spawner));
            if (!id.trim().isEmpty()) ids.add(id);
        }
        return ids;
    }

    public String suggestedId(String sourceId, Location location) {
        String world = location.getWorld() == null ? "world" : location.getWorld().getName();
        return normalizeId(sourceId) + "_" + normalizeId(world)
                + "_" + location.getBlockX()
                + "_" + location.getBlockY()
                + "_" + location.getBlockZ();
    }

    private Object spawnerManager() throws ReflectiveOperationException {
        Object manager = invoke(getSpawnerManager, mythicMobs);
        if (manager == null) throw new IllegalStateException("MythicMobs 刷新点管理器尚未初始化");
        return manager;
    }

    private Object findSpawner(Object manager, String id) throws ReflectiveOperationException {
        if (id == null || id.trim().isEmpty()) return null;
        return invoke(getSpawnerByName, manager, id);
    }

    private String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", "_");
        normalized = normalized.replace(':', '_').replace('/', '_').replace('\\', '_');
        return normalized.toLowerCase(Locale.ROOT);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public enum CopyResult {
        SUCCESS,
        SOURCE_NOT_FOUND,
        TARGET_EXISTS,
        FAILED
    }
}
