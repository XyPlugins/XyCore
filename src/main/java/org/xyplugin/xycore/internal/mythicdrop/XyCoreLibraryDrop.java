package org.xyplugin.xycore.internal.mythicdrop;

import io.lumine.xikage.mythicmobs.adapters.AbstractItemStack;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.drops.Drop;
import io.lumine.xikage.mythicmobs.drops.DropMetadata;
import io.lumine.xikage.mythicmobs.drops.IItemDrop;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.XyCorePlugin;

/** MythicMobs item drop backed by XyCore's provider:item item library. */
public final class XyCoreLibraryDrop extends Drop implements IItemDrop {

    private static final Set<String> WARNED_MISSING = new HashSet<String>();

    private final XyCorePlugin plugin;
    private final String namespacedId;

    public XyCoreLibraryDrop(XyCorePlugin plugin, String namespacedId, String line, MythicLineConfig config) {
        super(line, config);
        this.plugin = plugin;
        this.namespacedId = namespacedId;
    }

    @Override
    public AbstractItemStack getDrop(DropMetadata metadata) {
        int amount = Math.max(1, (int) Math.round(getAmount()));
        Optional<ItemStack> created = plugin.getApi().getItems().create(namespacedId, amount);
        if (!created.isPresent()) {
            warnMissingOnce();
            return new BukkitItemStack(new ItemStack(Material.AIR, 1));
        }
        ItemStack item = created.get();
        item.setAmount(amount);
        return new BukkitItemStack(item);
    }

    private void warnMissingOnce() {
        synchronized (WARNED_MISSING) {
            if (!WARNED_MISSING.add(namespacedId)) return;
        }
        plugin.getLogger().warning("MythicMobs 掉落表引用的物品库ID无法生成: " + namespacedId);
    }
}
