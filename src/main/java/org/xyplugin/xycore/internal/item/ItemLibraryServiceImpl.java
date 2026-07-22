package org.xyplugin.xycore.internal.item;

import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 默认物品库注册表。 */
public final class ItemLibraryServiceImpl implements ItemLibraryService {

    private final Map<String, ItemProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void registerProvider(ItemProvider provider) {
        if (provider == null || provider.getId() == null || provider.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Item provider must have an id");
        }
        providers.put(provider.getId().toLowerCase(), provider);
    }

    @Override
    public void unregisterProvider(String providerId) {
        if (providerId != null) providers.remove(providerId.toLowerCase());
    }

    @Override
    public Optional<ItemProvider> getProvider(String providerId) {
        return providerId == null ? Optional.empty() : Optional.ofNullable(providers.get(providerId.toLowerCase()));
    }

    @Override
    public Collection<ItemProvider> getProviders() {
        return Collections.unmodifiableList(new ArrayList<>(providers.values()));
    }

    @Override
    public Optional<ItemStack> create(String namespacedId, int amount) {
        if (namespacedId == null || amount <= 0) return Optional.empty();
        int separator = namespacedId.indexOf(':');
        if (separator <= 0 || separator >= namespacedId.length() - 1) return Optional.empty();
        String providerId = namespacedId.substring(0, separator);
        String itemId = namespacedId.substring(separator + 1);
        ItemProvider provider = providers.get(providerId.toLowerCase());
        return provider == null || !provider.isAvailable()
                ? Optional.empty()
                : provider.createItem(itemId, amount);
    }

    @Override
    public Collection<String> getItemIds(String providerId) {
        ItemProvider provider = providers.get(providerId == null ? "" : providerId.toLowerCase());
        return provider == null ? Collections.emptyList() : provider.getItemIds();
    }
}
