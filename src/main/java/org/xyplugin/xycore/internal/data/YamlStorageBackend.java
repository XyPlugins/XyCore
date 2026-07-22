package org.xyplugin.xycore.internal.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.xyplugin.xycore.api.data.DataContainer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YML 存储后端。
 *
 * <p>它是初代默认后端，适合开发和小规模测试。每个玩家一个文件，避免
 * 多个玩家同时保存时锁住整份数据。</p>
 */
public final class YamlStorageBackend implements StorageBackend {

    private final File folder;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public YamlStorageBackend(File dataFolder) {
        folder = new File(dataFolder, "data");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create " + folder);
        }
    }

    @Override
    public String getName() {
        return "yaml";
    }

    @Override
    public StoredModule load(UUID playerId, String moduleId) {
        File file = fileOf(playerId);
        if (!file.isFile()) return null;
        synchronized (lockFor(playerId)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String base = "modules." + moduleId;
            if (!yaml.contains(base)) return null;
            int version = yaml.getInt(base + ".version", 0);
            ConfigurationSection dataSection = yaml.getConfigurationSection(base + ".data");
            Map<String, Object> values = dataSection == null
                    ? new java.util.LinkedHashMap<String, Object>()
                    : dataSection.getValues(false);
            return new StoredModule(version, new DataContainer(values));
        }
    }

    @Override
    public void save(UUID playerId, String moduleId, int version, DataContainer data) throws IOException {
        synchronized (lockFor(playerId)) {
            File file = fileOf(playerId);
            YamlConfiguration yaml = file.isFile()
                    ? YamlConfiguration.loadConfiguration(file)
                    : new YamlConfiguration();
            String base = "modules." + moduleId;
            yaml.set(base + ".version", version);
            yaml.set(base + ".data", data.asMap());
            yaml.save(file);
        }
    }

    private File fileOf(UUID playerId) {
        return new File(folder, playerId.toString() + ".yml");
    }

    private Object lockFor(UUID playerId) {
        return locks.computeIfAbsent(playerId, ignored -> new Object());
    }

    @Override
    public void close() {
        locks.clear();
    }
}
