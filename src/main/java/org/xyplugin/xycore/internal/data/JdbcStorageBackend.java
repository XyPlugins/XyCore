package org.xyplugin.xycore.internal.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.xyplugin.xycore.api.data.DataContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 不绑定具体驱动的 JDBC 后端，兼容 SQLite 和 MySQL/MariaDB。
 *
 * <p>驱动由服务器环境或额外插件提供。Core 不把数据库驱动打进自己的 JAR，
 * 避免和服务器已有版本冲突。</p>
 */
public final class JdbcStorageBackend implements StorageBackend {

    private final String name;
    private final String url;
    private final String username;
    private final String password;
    private final String table;
    private final HikariDataSource dataSource;

    public JdbcStorageBackend(String name, String url, String username, String password, String table,
                              int maximumPoolSize, int minimumIdle, long connectionTimeout,
                              long idleTimeout, long maxLifetime) throws Exception {
        this.name = name;
        this.url = url;
        this.username = username;
        this.password = password;
        this.table = sanitizeTable(table);
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("XyCore-" + name);
        pool.setJdbcUrl(url);
        if (username != null && !username.isEmpty()) pool.setUsername(username);
        if (password != null && !password.isEmpty()) pool.setPassword(password);
        pool.setMaximumPoolSize(Math.max(1, maximumPoolSize));
        pool.setMinimumIdle(Math.max(0, Math.min(minimumIdle, maximumPoolSize)));
        pool.setConnectionTimeout(Math.max(250L, connectionTimeout));
        pool.setIdleTimeout(Math.max(10000L, idleTimeout));
        pool.setMaxLifetime(Math.max(30000L, maxLifetime));
        pool.setAutoCommit(true);
        pool.setInitializationFailTimeout(connectionTimeout);
        dataSource = new HikariDataSource(pool);
        try {
            initialize();
        } catch (Exception failure) {
            dataSource.close();
            throw failure;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public StoredModule load(UUID playerId, String moduleId) throws Exception {
        String sql = "SELECT data_version, data_yaml FROM " + table
                + " WHERE player_uuid = ? AND module_id = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, moduleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(result.getString("data_yaml"));
                Map<String, Object> values = yaml.getConfigurationSection("data") == null
                        ? new LinkedHashMap<String, Object>()
                        : yaml.getConfigurationSection("data").getValues(false);
                return new StoredModule(result.getInt("data_version"), new DataContainer(values));
            }
        }
    }

    @Override
    public void save(UUID playerId, String moduleId, int version, DataContainer data) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("data", data.asMap());
        String delete = "DELETE FROM " + table + " WHERE player_uuid = ? AND module_id = ?";
        String insert = "INSERT INTO " + table
                + " (player_uuid, module_id, data_version, data_yaml) VALUES (?, ?, ?, ?)";
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement remove = connection.prepareStatement(delete);
                 PreparedStatement add = connection.prepareStatement(insert)) {
                remove.setString(1, playerId.toString());
                remove.setString(2, moduleId);
                remove.executeUpdate();
                add.setString(1, playerId.toString());
                add.setString(2, moduleId);
                add.setInt(3, version);
                add.setString(4, yaml.saveToString());
                add.executeUpdate();
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Connection connect() throws Exception {
        return dataSource.getConnection();
    }

    private void initialize() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "module_id VARCHAR(64) NOT NULL,"
                + "data_version INTEGER NOT NULL,"
                + "data_yaml TEXT NOT NULL,"
                + "PRIMARY KEY (player_uuid, module_id))";
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String sanitizeTable(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            return "xycore_player_module";
        }
        return value;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
