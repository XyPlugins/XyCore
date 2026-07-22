package org.xyplugin.xycore.internal.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.api.item.ItemProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/** 原版 Material 的最小物品提供器，用于 Core 自己的基础物品和 GUI。 */
public final class VanillaItemProvider implements ItemProvider {

    @Override
    public String getId() {
        return "minecraft";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Collection<String> getItemIds() {
        Collection<String> ids = new ArrayList<>();
        for (Material material : Material.values()) ids.add(material.name());
        return Collections.unmodifiableCollection(ids);
    }

    @Override
    public Optional<ItemStack> createItem(String itemId, int amount) {
        Material material = Material.matchMaterial(itemId);
        if (material == null) return Optional.empty();
        return Optional.of(new ItemStack(material, amount));
    }
}
