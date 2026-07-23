package org.xyplugin.xycore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.xyplugin.xycore.api.XyCoreApi;
import org.xyplugin.xycore.api.item.ItemProvider;
import org.xyplugin.xycore.internal.CoreApiImpl;
import org.xyplugin.xycore.internal.death.DeathKeepModule;
import org.xyplugin.xycore.internal.command.CoreCommand;
import org.xyplugin.xycore.internal.listener.CoreListener;
import org.xyplugin.xycore.internal.lore.LoreCommandBindService;
import org.xyplugin.xycore.internal.module.CoreModule;
import org.xyplugin.xycore.internal.module.ModuleManager;
import org.xyplugin.xycore.internal.permission.WorldPermissionModule;
import org.xyplugin.xycore.internal.placeholder.ModernPapiBridge;
import org.xyplugin.xycore.internal.pvp.PvpProtectModule;
import org.xyplugin.xycore.internal.protect.WorldProtectModule;
import org.xyplugin.xycore.internal.time.AlwaysDayModule;
import org.xyplugin.xycore.internal.weather.NoRainModule;

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
        moduleManager.register(new DeathKeepModule(this));
        moduleManager.register(new PvpProtectModule(this));
        moduleManager.register(new AlwaysDayModule(this));
        moduleManager.register(new NoRainModule(this));
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
        logStartupSummary();
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

    /** 输出启动摘要：模块数量、已开启模块和软依赖挂钩状态。 */
    private void logStartupSummary() {
        getLogger().info("============================================================");
        getLogger().info("XyCore " + api.getVersion() + " 已启用，存储后端: " + api.getStorage().getBackendName());
        logModuleSummary();
        logSoftDependencySummary();
        getLogger().info("============================================================");
    }

    private void logModuleSummary() {
        int count = moduleManager == null ? 0 : moduleManager.getEnabledModuleCount();
        getLogger().info("当前" + count + "个模块已开启。");
        if (count <= 0) {
            getLogger().info("模块: 无");
            return;
        }

        StringBuilder names = new StringBuilder();
        for (CoreModule module : moduleManager.getEnabledModules()) {
            if (names.length() > 0) names.append(", ");
            names.append(module.getDisplayName()).append("(").append(module.getId()).append(")");
        }
        getLogger().info("模块: " + names);
    }

    private void logSoftDependencySummary() {
        getLogger().info("软依赖挂钩状态:");
        getLogger().info(" - Vault: " + hookStatus("Vault", "integrations.vault",
                api.getEconomy().isAvailable(), api.getEconomy().getProviderName(), "未挂钩（未找到经济插件服务）"));
        getLogger().info(" - PlaceholderAPI: " + hookStatus("PlaceholderAPI", "integrations.placeholderapi",
                papiBridge != null, "%xycore_*%", "未挂钩（Expansion 不兼容或注册失败）"));
        getLogger().info(" - MythicMobs: " + hookStatus("MythicMobs", "integrations.mythicmobs",
                hasAvailableItemProvider("mythicmobs"), "ItemProvider", "未挂钩（物品 API 不兼容）"));
        getLogger().info(" - AttributePlus: " + hookStatus("AttributePlus", "integrations.attributeplus.enabled",
                api.getAttributes().isAvailable(), api.getAttributes().getProviderName(), "未挂钩（API 不兼容或 PlaceholderAPI 不可用）"));
        getLogger().info(" - DragonCore: " + hookStatus("DragonCore", "integrations.dragoncore.enabled",
                api.getClientBridge().isAvailable(), api.getClientBridge().getProviderName(), "未挂钩（客户端 API 不兼容）"));
    }

    private String hookStatus(String pluginName, String configPath, boolean hooked,
                              String providerName, String unavailableReason) {
        if (!getConfig().getBoolean(configPath, true)) return "配置关闭";
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency == null) return "未安装";
        if (!dependency.isEnabled()) return "插件未启用";
        if (hooked) {
            String label = shortProviderName(providerName);
            return label.isEmpty() ? "已挂钩" : "已挂钩（" + label + "）";
        }
        return unavailableReason;
    }

    private String shortProviderName(String providerName) {
        if (providerName == null) return "";
        String trimmed = providerName.trim();
        if (trimmed.isEmpty() || "unavailable".equalsIgnoreCase(trimmed)) return "";
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 && dot + 1 < trimmed.length() ? trimmed.substring(dot + 1) : trimmed;
    }

    private boolean hasAvailableItemProvider(String id) {
        if (api == null || id == null) return false;
        for (ItemProvider provider : api.getItems().getProviders()) {
            if (id.equalsIgnoreCase(provider.getId()) && provider.isAvailable()) return true;
        }
        return false;
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
