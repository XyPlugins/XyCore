package org.xyplugin.xycore.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** 1.12.2 NBT 元数据抽象，避免功能插件直接依赖 NMS。 */
public interface ItemTagService {

    boolean isAvailable();

    Optional<String> getString(ItemStack item, String key);

    ItemStack setString(ItemStack item, String key, String value);

    ItemStack remove(ItemStack item, String key);
}
