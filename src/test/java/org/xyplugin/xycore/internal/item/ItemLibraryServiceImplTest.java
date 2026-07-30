package org.xyplugin.xycore.internal.item;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.xyplugin.xycore.api.item.ItemProvider;

public class ItemLibraryServiceImplTest {
    @Test
    public void vanillaMatchRejectsStackIdentifiedByCustomProvider() {
        ItemLibraryServiceImpl library = new ItemLibraryServiceImpl();
        library.registerProvider(new VanillaItemProvider());
        library.registerProvider(new TaggedProvider());

        ItemStack plain = new ItemStack(Material.IRON_INGOT);
        ItemStack custom = new ItemStack(Material.IRON_INGOT);
        custom.setDurability((short) 7);

        assertTrue(library.matches("minecraft:IRON_INGOT", plain));
        assertFalse(library.matches("minecraft:IRON_INGOT", custom));
        assertTrue(library.matches("test:custom_ingot", custom));
        assertFalse(library.matches("test:other", custom));
    }

    private static final class TaggedProvider implements ItemProvider {
        public String getId() { return "test"; }
        public boolean isAvailable() { return true; }
        public Collection<String> getItemIds() { return Collections.singletonList("custom_ingot"); }
        public Optional<ItemStack> createItem(String itemId, int amount) { return Optional.empty(); }
        public Optional<String> identify(ItemStack item) {
            return item != null && item.getType() == Material.IRON_INGOT && item.getDurability() == 7
                    ? Optional.of("custom_ingot") : Optional.empty();
        }
    }
}
