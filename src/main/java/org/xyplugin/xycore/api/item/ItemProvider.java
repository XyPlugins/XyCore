package org.xyplugin.xycore.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/** 某个物品库提供器，例如 minecraft 或 mythicmobs。 */
public interface ItemProvider {

    String getId();

    boolean isAvailable();

    Collection<String> getItemIds();

    Optional<ItemStack> createItem(String itemId, int amount);

    default Optional<String> identify(ItemStack item) {
        return Optional.empty();
    }

    /**
     * Checks whether an existing stack belongs to the requested provider-local item id.
     * Providers with stable item tags normally only need to implement {@link #identify(ItemStack)}.
     */
    default boolean matches(String itemId, ItemStack item) {
        if (itemId == null || itemId.trim().isEmpty() || item == null) return false;
        Optional<String> identified = identify(item);
        return identified.isPresent() && itemId.trim().equalsIgnoreCase(identified.get());
    }
}
