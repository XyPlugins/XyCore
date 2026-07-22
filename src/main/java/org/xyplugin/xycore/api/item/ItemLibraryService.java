package org.xyplugin.xycore.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/** 多物品库统一入口。ID 使用 provider:item 格式。 */
public interface ItemLibraryService {

    void registerProvider(ItemProvider provider);

    void unregisterProvider(String providerId);

    Optional<ItemProvider> getProvider(String providerId);

    Collection<ItemProvider> getProviders();

    Optional<ItemStack> create(String namespacedId, int amount);

    Collection<String> getItemIds(String providerId);
}
