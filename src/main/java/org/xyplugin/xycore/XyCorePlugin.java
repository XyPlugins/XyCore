package org.xyplugin.xycore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.xyplugin.xycore.api.XyCoreApi;
import org.xyplugin.xycore.internal.CoreApiImpl;
import org.xyplugin.xycore.internal.command.CoreCommand;
import org.xyplugin.xycore.internal.listener.CoreListener;
import org.xyplugin.xycore.internal.lore.LoreCommandBindService;
import org.xyplugin.xycore.internal.module.CoreModule;
import org.xyplugin.xycore.internal.module.ModuleManager;
import org.xyplugin.xycore.internal.permission.WorldPermissionModule;
import org.xyplugin.xycore.internal.placeholder.ModernPapiBridge;
import org.xyplugin.xycore.internal.protect.WorldProtectModule;

/** XyCore Paper 1.12.2 插件入口。 */
public final class XyCorePlugin extends JavaPlugin {

    private static XyCorePlugin instance;
    private CoreApiImpl api;
    private ModernPapiBridge papiBridge;
    private ModuleManager moduleManager;
    private int autosaveTask = -1;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            api = new CoreApiImpl(this);
        } catch (Exception failure) {
            getLogger().severe("XyCore 初始化失败，插件将禁用: " + failure.getMessage());
            failure.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        refreshPlaceholderBridge();

        Bukkit.getPluginManager().registerEvents(new CoreListener(this, api.getPlayerData()), this);
        moduleManager = new ModuleManager(this);
        moduleManager.register(new LoreCommandBindService(this));
        moduleManager.register(new WorldProtectModule(this));
        moduleManager.register(new WorldPermissionModule(this));
        api.getReloads().register(moduleManager);
        try {
            moduleManager.refreshConfiguredModules();
        } catch (Exception failure) {
            getLogger().warning("模块初始化失败，Core 将继续启动: " + failure.getMessage());
            failure.printStackTrace();
        }
        if (getCommand("xycore") != null) getCommand("xycore").setExecutor(new CoreCommand(this));

        long minutes = Math.max(1L, getConfig().getLong("player-data.autosave-minutes", 5L));
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> api.getPlayerData().saveAll(), minutes * 1200L, minutes * 1200L).getTaskId();
        getLogger().info("XyCore " + api.getVersion() + " 已启用，存储后端: " + api.getStorage().getBackendName());
    }

    @Override
    public void onDisable() {
        if (autosaveTask != -1) Bukkit.getScheduler().cancelTask(autosaveTask);
        if (moduleManager != null) moduleManager.disableAll();
        if (papiBridge != null) {
            papiBridge.unregister();
            papiBridge = null;
        }
        if (api != null) {
            api.getReloads().unregister("modules");
            api.getClientBridge().shutdown();
            api.getPlayerDataInternal().shutdown();
            api.getStorageInternal().close();
        }
        instance = null;
    }

    /** 只重载配置和已注册扩展，不执行 Bukkit/Paper 完整热重载。 */
    public void reloadCore() {
        reloadConfig();
        api.refreshOptionalIntegrations();
        refreshPlaceholderBridge();
        api.getReloads().reloadAll();
    }

    /** 根据配置和 PlaceholderAPI 的实际启用状态刷新正式 Expansion。 */
    private void refreshPlaceholderBridge() {
        if (papiBridge != null) {
            papiBridge.unregister();
            papiBridge = null;
        }
        if (!getConfig().getBoolean("integrations.placeholderapi", true)) return;

        org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            getLogger().info("未检测到已启用的 PlaceholderAPI，保留内部变量注册表。");
            return;
        }
        try {
            papiBridge = new ModernPapiBridge(this,
                    (org.xyplugin.xycore.internal.placeholder.PlaceholderRegistryImpl) api.getPlaceholders());
            papiBridge.register();
        } catch (Exception | LinkageError failure) {
            // PAPI 缺少新版 PlaceholderExpansion 时，Core 仍可继续提供内部变量服务。
            papiBridge = null;
            getLogger().warning("PlaceholderExpansion 不兼容，已禁用 PAPI 桥接: " + failure.getMessage());
        }
    }

    public static XyCorePlugin getInstance() {
        return instance;
    }

    public XyCoreApi getApi() {
        return api;
    }

    public CoreApiImpl getApiInternal() {
        return api;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public LoreCommandBindService getLoreCommandBind() {
        if (moduleManager == null) return null;
        CoreModule module = moduleManager.getModule("lore-command-bind");
        return module instanceof LoreCommandBindService ? (LoreCommandBindService) module : null;
    }
}
