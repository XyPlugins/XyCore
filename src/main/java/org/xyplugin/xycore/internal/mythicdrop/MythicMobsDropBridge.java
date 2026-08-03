package org.xyplugin.xycore.internal.mythicdrop;

import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicDropLoadEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;

/** Registers provider:item drops for MythicMobs 4.11 through XyCore's item library. */
public final class MythicMobsDropBridge implements Listener {

    private final XyCorePlugin plugin;
    private final Set<String> warnedInvalidIds = new HashSet<String>();
    private boolean registered;

    public MythicMobsDropBridge(XyCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        if (registered) return true;
        if (!plugin.getConfig().getBoolean("integrations.mythicmobs-drop-bridge.enabled", true)) return false;
        if (!hasMythicMobs()) return false;
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
            return true;
        } catch (Throwable failure) {
            plugin.getLogger().warning("MythicMobs 掉落表物品库桥接启用失败: " + failure.getMessage());
            return false;
        }
    }

    public void unregister() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    @EventHandler
    public void onDropLoad(MythicDropLoadEvent event) {
        if (!registered || event == null) return;
        String namespacedId = normalize(event.getDropName());
        if (namespacedId == null || !isAllowedProvider(namespacedId) || !hasAvailableProvider(namespacedId)) return;
        if (!canCreate(namespacedId)) {
            warnInvalidOnce(namespacedId);
            return;
        }
        event.register(new XyCoreLibraryDrop(plugin, namespacedId, event.getContainer().getConfigLine(), event.getConfig()));
    }

    private boolean hasMythicMobs() {
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        return mythic != null && mythic.isEnabled();
    }

    private String normalize(String dropName) {
        if (dropName == null) return null;
        String trimmed = dropName.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0 || separator >= trimmed.length() - 1) return null;
        String provider = trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String item = trimmed.substring(separator + 1).trim();
        if (!provider.matches("[a-z0-9_-]+") || item.isEmpty()) return null;
        return provider + ":" + item;
    }

    private boolean isAllowedProvider(String namespacedId) {
        int separator = namespacedId.indexOf(':');
        String provider = namespacedId.substring(0, separator).toLowerCase(Locale.ROOT);
        Set<String> allowed = allowedProviders();
        return allowed.contains("*") || allowed.contains(provider);
    }

    private Set<String> allowedProviders() {
        List<String> configured = plugin.getConfig().getStringList("integrations.mythicmobs-drop-bridge.providers");
        if (configured == null || configured.isEmpty()) {
            configured = new ArrayList<String>();
            configured.add("*");
        }
        Set<String> result = new HashSet<String>();
        for (String value : configured) {
            if (value == null) continue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        if (result.isEmpty()) result.add("*");
        return result;
    }

    private boolean hasAvailableProvider(String namespacedId) {
        int separator = namespacedId.indexOf(':');
        String providerId = namespacedId.substring(0, separator);
        return plugin.getApi().getItems().getProvider(providerId)
                .map(provider -> provider.isAvailable())
                .orElse(false);
    }

    private boolean canCreate(String namespacedId) {
        try {
            return plugin.getApi().getItems().create(namespacedId, 1).isPresent();
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void warnInvalidOnce(String namespacedId) {
        if (!warnedInvalidIds.add(namespacedId)) return;
        plugin.getLogger().warning("MythicMobs 掉落表引用的物品库ID无法生成: " + namespacedId);
    }
}
