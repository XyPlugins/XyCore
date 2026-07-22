package org.xyplugin.xycore.internal.module;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.xyplugin.xycore.XyCorePlugin;

import java.io.File;

/** Common lifecycle and config loading code for built-in Core modules. */
public abstract class AbstractCoreModule implements CoreModule {

    protected final XyCorePlugin plugin;
    private final String id;
    private final String displayName;
    private final String configResourcePath;
    private volatile boolean enabled;
    private FileConfiguration config;

    protected AbstractCoreModule(XyCorePlugin plugin, String id, String displayName, String configResourcePath) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.configResourcePath = configResourcePath;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getDisplayName() {
        return displayName;
    }

    @Override
    public final String getConfigResourcePath() {
        return configResourcePath;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final void enable() throws Exception {
        ensureConfigFile();
        loadConfig();
        if (!enabled) {
            enabled = true;
            onEnable();
        } else {
            onReload();
        }
    }

    @Override
    public final void reload() throws Exception {
        if (!enabled) return;
        ensureConfigFile();
        loadConfig();
        onReload();
    }

    @Override
    public final void disable() {
        if (!enabled) return;
        try {
            onDisable();
        } catch (Exception failure) {
            plugin.getLogger().warning(displayName + " 模块关闭失败: " + failure.getMessage());
        } finally {
            enabled = false;
        }
    }

    protected FileConfiguration getModuleConfig() {
        return config;
    }

    protected File getConfigFile() {
        return new File(plugin.getDataFolder(), configResourcePath.replace('/', File.separatorChar));
    }

    public final void deleteConfigFile() {
        File file = getConfigFile();
        if (!file.isFile()) return;
        if (!file.delete()) {
            plugin.getLogger().warning(displayName + " 模块配置删除失败: " + file.getPath());
        }
    }

    protected void onEnable() throws Exception {
        onReload();
    }

    protected void onReload() throws Exception {
        // Optional for simple modules.
    }

    protected void onDisable() throws Exception {
        // Optional for simple modules.
    }

    private void ensureConfigFile() throws Exception {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        if (!file.isFile()) {
            beforeCreateConfig(file);
            if (!file.isFile()) plugin.saveResource(configResourcePath, false);
        }
    }

    private void loadConfig() {
        config = YamlConfiguration.loadConfiguration(getConfigFile());
    }

    protected void beforeCreateConfig(File file) throws Exception {
        // Modules may migrate legacy config files before the bundled default is saved.
    }
}
