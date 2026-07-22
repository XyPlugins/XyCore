package org.xyplugin.xycore.internal.module;

import org.xyplugin.xycore.XyCorePlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Enables and disables built-in modules from the main config.yml. */
public final class ModuleManager implements org.xyplugin.xycore.api.service.Reloadable {

    private final XyCorePlugin plugin;
    private final Map<String, CoreModule> modules = new LinkedHashMap<>();

    public ModuleManager(XyCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(CoreModule module) {
        if (module == null) throw new IllegalArgumentException("module");
        modules.put(module.getId().toLowerCase(), module);
    }

    @Override
    public String getId() {
        return "modules";
    }

    @Override
    public void reload() throws Exception {
        refreshConfiguredModules();
    }

    public void refreshConfiguredModules() throws Exception {
        for (CoreModule module : modules.values()) {
            if (isConfiguredEnabled(module)) {
                module.enable();
                plugin.getLogger().info("模块已启用: " + module.getDisplayName());
            } else {
                module.disable();
                if (module instanceof AbstractCoreModule) {
                    ((AbstractCoreModule) module).deleteConfigFile();
                }
            }
        }
    }

    public void disableAll() {
        for (CoreModule module : modules.values()) {
            module.disable();
        }
    }

    public CoreModule getModule(String id) {
        return id == null ? null : modules.get(id.toLowerCase());
    }

    public Collection<CoreModule> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public List<String> getStates() {
        List<String> states = new ArrayList<>();
        for (CoreModule module : modules.values()) {
            states.add(module.getId() + "=" + (module.isEnabled() ? "on" : "off"));
        }
        return states;
    }

    public List<CoreModule> getEnabledModules() {
        List<CoreModule> enabled = new ArrayList<>();
        for (CoreModule module : modules.values()) {
            if (module.isEnabled()) enabled.add(module);
        }
        return Collections.unmodifiableList(enabled);
    }

    public int getEnabledModuleCount() {
        int count = 0;
        for (CoreModule module : modules.values()) {
            if (module.isEnabled()) count++;
        }
        return count;
    }

    private boolean isConfiguredEnabled(CoreModule module) {
        String path = "modules." + module.getId();
        if (plugin.getConfig().isSet(path)) return plugin.getConfig().getBoolean(path);

        // 0.2.x compatibility: old installs used lore-command-bind.enabled directly.
        if ("lore-command-bind".equalsIgnoreCase(module.getId())
                && plugin.getConfig().isSet("lore-command-bind.enabled")) {
            return plugin.getConfig().getBoolean("lore-command-bind.enabled");
        }
        return false;
    }
}
