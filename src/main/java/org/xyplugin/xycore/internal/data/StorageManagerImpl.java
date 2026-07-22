package org.xyplugin.xycore.internal.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.xyplugin.xycore.api.data.DataContainer;
import org.xyplugin.xycore.api.storage.StorageManager;
import org.xyplugin.xycore.XyCorePlugin;

import java.io.File;
import java.util.UUID;

/** 创建并封装当前实际使用的存储后端。 */
public final class StorageManagerImpl implements StorageManager {

    private final XyCorePlugin plugin;
    private volatile StorageBackend backend;

    public StorageManagerImpl(XyCorePlugin plugin) {
        this.plugin = plugin;
        this.backend = createBackend(plugin.getConfig());
    }

    private StorageBackend createBackend(FileConfiguration config) {
        String type = config.getString("storage.type", "yaml").toLowerCase();
        int maximumPoolSize = config.getInt("storage.pool.maximum-pool-size", 10);
        int minimumIdle = config.getInt("storage.pool.minimum-idle", 2);
        long connectionTimeout = config.getLong("storage.pool.connection-timeout-ms", 10000L);
        long idleTimeout = config.getLong("storage.pool.idle-timeout-ms", 600000L);
        long maxLifetime = config.getLong("storage.pool.max-lifetime-ms", 1800000L);
        try {
            if ("sqlite".equals(type)) {
                String file = config.getString("storage.sqlite.file", "data.db");
                return new JdbcStorageBackend("sqlite",
                        "jdbc:sqlite:" + new File(plugin.getDataFolder(), file).getPath(), "", "", "xycore_player_module",
                        1, 1, connectionTimeout, idleTimeout, maxLifetime);
            }
            if ("mysql".equals(type) || "mariadb".equals(type)) {
                return new JdbcStorageBackend("mysql",
                        config.getString("storage.mysql.url"),
                        config.getString("storage.mysql.username", ""),
                        config.getString("storage.mysql.password", ""),
                        config.getString("storage.mysql.table", "xycore_player_module"),
                        maximumPoolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime);
            }
            return new YamlStorageBackend(plugin.getDataFolder());
        } catch (Exception failure) {
            if (!config.getBoolean("storage.fallback-to-yaml", true)) {
                throw new IllegalStateException("Unable to initialize " + type + " storage", failure);
            }
            plugin.getLogger().severe("无法初始化 " + type + " 存储，已降级为 YML：" + failure.getMessage());
            return new YamlStorageBackend(plugin.getDataFolder());
        }
    }

    public StoredModule load(UUID playerId, String moduleId) throws Exception {
        return backend.load(playerId, moduleId);
    }

    public void save(UUID playerId, String moduleId, int version, DataContainer data) throws Exception {
        backend.save(playerId, moduleId, version, data);
    }

    @Override
    public String getBackendName() {
        return backend.getName();
    }

    public void close() {
        backend.close();
    }
}
