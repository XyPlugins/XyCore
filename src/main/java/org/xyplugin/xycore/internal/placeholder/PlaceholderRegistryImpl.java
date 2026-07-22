package org.xyplugin.xycore.internal.placeholder;

import org.bukkit.entity.Player;
import org.xyplugin.xycore.api.placeholder.PlaceholderProvider;
import org.xyplugin.xycore.api.placeholder.PlaceholderRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Core 内部的变量注册表。PAPI 桥接会把这里的变量映射到 PAPI。 */
public final class PlaceholderRegistryImpl implements PlaceholderRegistry {

    private final Map<String, PlaceholderProvider> providers = new ConcurrentHashMap<>();
    private volatile Consumer<PlaceholderProvider> registerListener = provider -> { };
    private volatile Consumer<String> unregisterListener = namespace -> { };

    @Override
    public void register(PlaceholderProvider provider) {
        if (provider == null || provider.getNamespace() == null) {
            throw new IllegalArgumentException("Placeholder provider cannot be null");
        }
        String namespace = provider.getNamespace().toLowerCase();
        providers.put(namespace, provider);
        registerListener.accept(provider);
    }

    @Override
    public void unregister(String namespace) {
        if (namespace != null) {
            String normalized = namespace.toLowerCase();
            providers.remove(normalized);
            unregisterListener.accept(normalized);
        }
    }

    @Override
    public Optional<PlaceholderProvider> get(String namespace) {
        return namespace == null ? Optional.empty() : Optional.ofNullable(providers.get(namespace.toLowerCase()));
    }

    @Override
    public Collection<PlaceholderProvider> getProviders() {
        return Collections.unmodifiableList(new ArrayList<>(providers.values()));
    }

    @Override
    public String resolve(Player player, String namespace, String params) {
        PlaceholderProvider provider = providers.get(namespace == null ? "" : namespace.toLowerCase());
        return provider == null ? "" : provider.resolve(player, params == null ? "" : params);
    }

    public void setChangeListeners(Consumer<PlaceholderProvider> registerListener,
                                   Consumer<String> unregisterListener) {
        this.registerListener = registerListener == null ? provider -> { } : registerListener;
        this.unregisterListener = unregisterListener == null ? namespace -> { } : unregisterListener;
    }
}
