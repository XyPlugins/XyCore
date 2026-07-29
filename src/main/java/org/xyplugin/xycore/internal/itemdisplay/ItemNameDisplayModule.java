package org.xyplugin.xycore.internal.itemdisplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用掉落物实体自己的名称显示单行文本。
 *
 * <p>模块只在物品生成、区块加载和配置重载时处理实体，不创建盔甲架，
 * 也不使用周期扫描任务。</p>
 */
public final class ItemNameDisplayModule extends AbstractCoreModule implements Listener {

    private static final String METADATA_KEY = "xycore-item-name-display";

    private Set<String> worlds = Collections.emptySet();
    private Map<String, String> materialNames = Collections.emptyMap();
    private String format = "{name}";
    private boolean customNameOnly = true;
    private boolean overwriteExistingEntityName;

    public ItemNameDisplayModule(XyCorePlugin plugin) {
        super(plugin, "item-name-display", "ItemNameDisplay",
                "modules/item-name-display.yml");
    }

    @Override
    protected void onEnable() {
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        applyToLoadedItems();
    }

    @Override
    protected void onReload() {
        restoreLoadedItems();
        loadSettings();
        applyToLoadedItems();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        restoreLoadedItems();
        worlds = Collections.emptySet();
        materialNames = Collections.emptyMap();
        format = "{name}";
        customNameOnly = true;
        overwriteExistingEntityName = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!isEnabled()) return;
        apply(event.getEntity());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) return;
        boolean enabledInWorld = matches(event.getWorld());
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Item)) continue;
            if (enabledInWorld) {
                apply((Item) entity);
            } else {
                restore((Item) entity);
            }
        }
    }

    private void loadSettings() {
        Set<String> configuredWorlds = new HashSet<>();
        for (String world : getModuleConfig().getStringList("worlds")) {
            String normalized = normalize(world);
            if (!normalized.isEmpty()) configuredWorlds.add(normalized);
        }
        worlds = Collections.unmodifiableSet(configuredWorlds);

        format = getModuleConfig().getString("display.format", "{name}");
        if (format == null || format.trim().isEmpty()) format = "{name}";
        customNameOnly = getModuleConfig().getBoolean("display.custom-name-only", true);
        overwriteExistingEntityName = getModuleConfig().getBoolean(
                "display.overwrite-existing-entity-name", false);

        Map<String, String> configuredNames = new HashMap<>();
        ConfigurationSection section = getModuleConfig().getConfigurationSection("material-names");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.trim().isEmpty()) {
                    configuredNames.put(normalize(key), value);
                }
            }
        }
        materialNames = Collections.unmodifiableMap(configuredNames);
    }

    private void applyToLoadedItems() {
        for (World world : Bukkit.getWorlds()) {
            if (!matches(world)) continue;
            for (Item item : world.getEntitiesByClass(Item.class)) apply(item);
        }
    }

    private void restoreLoadedItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) restore(item);
        }
    }

    private void apply(Item item) {
        if (item == null || !matches(item.getWorld())) return;
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String itemName = resolveName(stack);
        if (itemName == null || itemName.trim().isEmpty()) {
            restore(item);
            return;
        }

        ManagedName managed = getManagedName(item);
        if (managed != null && !Objects.equals(item.getCustomName(), managed.appliedName)) {
            // 其他插件在 XyCore 之后修改了名称，主动放弃管理，避免重载时覆盖它。
            item.removeMetadata(METADATA_KEY, plugin);
            managed = null;
        }

        if (managed == null) {
            String existingName = item.getCustomName();
            if (!overwriteExistingEntityName && existingName != null && !existingName.isEmpty()) return;
            managed = new ManagedName(existingName, item.isCustomNameVisible(), "");
        }

        String rendered = render(itemName, stack.getType().name());
        ManagedName updated = new ManagedName(managed.originalName,
                managed.originalNameVisible, rendered);
        item.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, updated));
        if (!rendered.equals(item.getCustomName())) item.setCustomName(rendered);
        if (!item.isCustomNameVisible()) item.setCustomNameVisible(true);
    }

    private String resolveName(ItemStack stack) {
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                if (displayName != null && !displayName.trim().isEmpty()) return displayName;
            }
        }
        if (customNameOnly) return null;
        String material = stack.getType().name();
        return materialNames.getOrDefault(normalize(material), material);
    }

    private String render(String itemName, String material) {
        String rendered = format
                .replace("{material}", material)
                .replace("{name}", itemName)
                .replace('\r', ' ')
                .replace('\n', ' ');
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private void restore(Item item) {
        ManagedName managed = getManagedName(item);
        if (managed == null) return;
        if (Objects.equals(item.getCustomName(), managed.appliedName)) {
            item.setCustomName(managed.originalName);
            item.setCustomNameVisible(managed.originalNameVisible);
        }
        item.removeMetadata(METADATA_KEY, plugin);
    }

    private ManagedName getManagedName(Item item) {
        for (MetadataValue metadata : item.getMetadata(METADATA_KEY)) {
            if (metadata.getOwningPlugin() != plugin) continue;
            Object value = metadata.value();
            if (value instanceof ManagedName) return (ManagedName) value;
        }
        return null;
    }

    private boolean matches(World world) {
        if (world == null) return false;
        return worlds.contains("*") || worlds.contains(normalize(world.getName()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ManagedName {
        private final String originalName;
        private final boolean originalNameVisible;
        private final String appliedName;

        private ManagedName(String originalName, boolean originalNameVisible, String appliedName) {
            this.originalName = originalName;
            this.originalNameVisible = originalNameVisible;
            this.appliedName = appliedName;
        }
    }
}
