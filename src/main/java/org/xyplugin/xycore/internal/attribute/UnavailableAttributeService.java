package org.xyplugin.xycore.internal.attribute;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.api.attribute.AttributeService;
import org.xyplugin.xycore.api.attribute.AttributeValueMode;

import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/** AttributePlus 不可用时的空实现。 */
public final class UnavailableAttributeService implements AttributeService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "unavailable";
    }

    @Override
    public String getRaw(LivingEntity entity, String attribute, AttributeValueMode mode) {
        return "";
    }

    @Override
    public OptionalDouble getValue(LivingEntity entity, String attribute, AttributeValueMode mode) {
        return OptionalDouble.empty();
    }

    @Override
    public List<String> getItemAttributeLines(ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public boolean addSource(LivingEntity entity, String source, List<String> attributeLines) {
        return false;
    }

    @Override
    public boolean removeSource(LivingEntity entity, String source) {
        return false;
    }
}
