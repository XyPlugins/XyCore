package org.xyplugin.xycore.internal.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.placeholder.PlaceholderProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 使用 PlaceholderAPI 2.11 PlaceholderExpansion 的正式变量桥接。 */
public final class ModernPapiBridge {

    private final XyCorePlugin plugin;
    private final PlaceholderRegistryImpl registry;
    private final Map<String, XyExpansion> expansions = new ConcurrentHashMap<>();

    public ModernPapiBridge(XyCorePlugin plugin, PlaceholderRegistryImpl registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void register() {
        registry.setChangeListeners(this::registerProvider, this::unregisterProvider);
        for (PlaceholderProvider provider : registry.getProviders()) registerProvider(provider);
    }

    public void unregister() {
        registry.setChangeListeners(null, null);
        for (XyExpansion expansion : expansions.values()) expansion.unregister();
        expansions.clear();
    }

    private void registerProvider(PlaceholderProvider provider) {
        String namespace = provider.getNamespace().toLowerCase();
        unregisterProvider(namespace);
        XyExpansion expansion = new XyExpansion(plugin, provider);
        if (expansion.register()) {
            expansions.put(namespace, expansion);
            plugin.getLogger().info("已注册 PlaceholderExpansion: %" + namespace + "_*%");
        } else {
            plugin.getLogger().warning("PlaceholderExpansion 注册失败: " + namespace);
        }
    }

    private void unregisterProvider(String namespace) {
        XyExpansion previous = expansions.remove(namespace.toLowerCase());
        if (previous != null) previous.unregister();
    }

    private static final class XyExpansion extends PlaceholderExpansion {

        private final XyCorePlugin plugin;
        private final PlaceholderProvider provider;

        private XyExpansion(XyCorePlugin plugin, PlaceholderProvider provider) {
            this.plugin = plugin;
            this.provider = provider;
        }

        @Override
        public String getIdentifier() {
            return provider.getNamespace().toLowerCase();
        }

        @Override
        public String getAuthor() {
            return "XyPlugin";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer offlinePlayer, String params) {
            Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
            return provider.resolve(player, params == null ? "" : params);
        }
    }
}
