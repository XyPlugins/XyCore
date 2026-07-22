package org.xyplugin.xycore.api.attribute;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.OptionalDouble;

/** AttributePlus 的稳定桥接契约。 */
public interface AttributeService {

    boolean isAvailable();

    String getProviderName();

    String getRaw(LivingEntity entity, String attribute, AttributeValueMode mode);

    OptionalDouble getValue(LivingEntity entity, String attribute, AttributeValueMode mode);

    /** 读取物品上的 AttributePlus Lore，初代只暴露原文。 */
    List<String> getItemAttributeLines(ItemStack item);

    /**
     * 追加属性源。若当前适配器不支持写入，应返回 false 而不是修改物品的 Lore。
     */
    boolean addSource(LivingEntity entity, String source, List<String> attributeLines);

    boolean removeSource(LivingEntity entity, String source);
}
