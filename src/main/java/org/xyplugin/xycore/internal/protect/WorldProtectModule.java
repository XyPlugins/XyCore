package org.xyplugin.xycore.internal.protect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight per-world build and entity protection for RPG lobby/main worlds. */
public final class WorldProtectModule extends AbstractCoreModule implements Listener {

    private volatile WorldProtectRule defaultRule = WorldProtectRule.disabled();
    private volatile Map<String, WorldProtectRule> worlds = Collections.emptyMap();
    private final Map<String, Long> messageCooldowns = new ConcurrentHashMap<>();

    public WorldProtectModule(XyCorePlugin plugin) {
        super(plugin, "world-protect", "WorldProtect", "modules/world-protect.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadRules();
    }

    @Override
    protected void onReload() {
        loadRules();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        messageCooldowns.clear();
        worlds = Collections.emptyMap();
        defaultRule = WorldProtectRule.disabled();
    }

    public int getWorldCount() {
        return worlds.size();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getBlock().getWorld());
        if (!rule.enabled || !rule.blockBreak || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.blockBreak, "block-break");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getBlock().getWorld());
        if (!rule.enabled || !rule.blockPlace || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.blockPlace, "block-place");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getBlockClicked().getWorld());
        if (!rule.enabled || !rule.bucketFill || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.bucketFill, "bucket-fill");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getBlockClicked().getWorld());
        if (!rule.enabled || !rule.bucketEmpty || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.bucketEmpty, "bucket-empty");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getEntity().getWorld());
        if (!rule.enabled || !rule.hangingPlace || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.hangingPlace, "hanging-place");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!isEnabled()) return;
        if (event instanceof HangingBreakByEntityEvent) return;
        WorldProtectRule rule = rule(event.getEntity().getWorld());
        if (!rule.enabled || !rule.hangingBreak) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getEntity().getWorld());
        if (!rule.enabled || !rule.hangingBreak) return;
        Player player = responsiblePlayer(event.getRemover());
        if (bypass(player, rule)) return;
        deny(event, player, rule.messages.hangingBreak, "hanging-break");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        Entity target = event.getEntity();
        WorldProtectRule rule = rule(target.getWorld());
        if (!rule.enabled) return;
        Player player = responsiblePlayer(event.getDamager());
        if (bypass(player, rule)) return;

        if (target instanceof ArmorStand && rule.armorStandBreak) {
            deny(event, player, rule.messages.armorStandBreak, "armor-stand-break");
        } else if ((target instanceof ItemFrame || target instanceof Painting) && rule.hangingBreak) {
            deny(event, player, rule.messages.hangingBreak, "hanging-break");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isEnabled()) return;
        Entity target = event.getRightClicked();
        WorldProtectRule rule = rule(target.getWorld());
        if (!rule.enabled || bypass(event.getPlayer(), rule)) return;
        if (target instanceof ItemFrame && rule.itemFrameInteract) {
            deny(event, event.getPlayer(), rule.messages.itemFrameInteract, "item-frame-interact");
        } else if (target instanceof ArmorStand && rule.armorStandInteract) {
            deny(event, event.getPlayer(), rule.messages.armorStandInteract, "armor-stand-interact");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!isEnabled()) return;
        WorldProtectRule rule = rule(event.getRightClicked().getWorld());
        if (!rule.enabled || !rule.armorStandInteract || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.armorStandInteract, "armor-stand-interact");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block == null || !isFarmland(block.getType())) return;
            WorldProtectRule rule = rule(block.getWorld());
            if (!rule.enabled || !rule.cropTrample || bypass(event.getPlayer(), rule)) return;
            deny(event, event.getPlayer(), rule.messages.cropTrample, "crop-trample");
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !"ARMOR_STAND".equalsIgnoreCase(item.getType().name())) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        WorldProtectRule rule = rule(block.getWorld());
        if (!rule.enabled || !rule.armorStandPlace || bypass(event.getPlayer(), rule)) return;
        deny(event, event.getPlayer(), rule.messages.armorStandPlace, "armor-stand-place");
    }

    private void loadRules() {
        WorldProtectRule fallback = readRule(getModuleConfig().getConfigurationSection("defaults"),
                WorldProtectRule.disabled(),
                getModuleConfig().getBoolean("settings.default-enabled", false));

        Map<String, WorldProtectRule> loaded = new HashMap<>();
        ConfigurationSection section = getModuleConfig().getConfigurationSection("worlds");
        if (section != null) {
            for (String worldName : section.getKeys(false)) {
                ConfigurationSection world = section.getConfigurationSection(worldName);
                if (world == null) continue;
                boolean inherit = world.getBoolean("inherit-defaults", true);
                WorldProtectRule parent = inherit ? fallback : WorldProtectRule.disabled();
                loaded.put(worldName.toLowerCase(Locale.ROOT),
                        readRule(world, parent, world.getBoolean("enabled", parent.enabled)));
            }
        }
        defaultRule = fallback;
        worlds = Collections.unmodifiableMap(loaded);
        messageCooldowns.clear();
        plugin.getLogger().info("WorldProtect 已加载 " + loaded.size() + " 个世界配置，默认保护="
                + (fallback.enabled ? "on" : "off") + "。");
    }

    private WorldProtectRule readRule(ConfigurationSection section, WorldProtectRule parent, boolean enabled) {
        if (section == null) section = emptySection();
        WorldProtectRule.Messages messages = parent.messages;
        return new WorldProtectRule(
                enabled,
                section.getString("bypass-permission", parent.bypassPermission),
                section.getBoolean("deny.block-break", parent.blockBreak),
                section.getBoolean("deny.block-place", parent.blockPlace),
                section.getBoolean("deny.bucket-fill", parent.bucketFill),
                section.getBoolean("deny.bucket-empty", parent.bucketEmpty),
                section.getBoolean("deny.hanging-break", parent.hangingBreak),
                section.getBoolean("deny.hanging-place", parent.hangingPlace),
                section.getBoolean("deny.item-frame-interact", parent.itemFrameInteract),
                section.getBoolean("deny.armor-stand-break", parent.armorStandBreak),
                section.getBoolean("deny.armor-stand-place", parent.armorStandPlace),
                section.getBoolean("deny.armor-stand-interact", parent.armorStandInteract),
                section.getBoolean("deny.crop-trample", parent.cropTrample),
                new WorldProtectRule.Messages(
                        section.getString("messages.block-break", messages.blockBreak),
                        section.getString("messages.block-place", messages.blockPlace),
                        section.getString("messages.bucket-fill", messages.bucketFill),
                        section.getString("messages.bucket-empty", messages.bucketEmpty),
                        section.getString("messages.hanging-break", messages.hangingBreak),
                        section.getString("messages.hanging-place", messages.hangingPlace),
                        section.getString("messages.item-frame-interact", messages.itemFrameInteract),
                        section.getString("messages.armor-stand-break", messages.armorStandBreak),
                        section.getString("messages.armor-stand-place", messages.armorStandPlace),
                        section.getString("messages.armor-stand-interact", messages.armorStandInteract),
                        section.getString("messages.crop-trample", messages.cropTrample)
                )
        );
    }

    private ConfigurationSection emptySection() {
        return new org.bukkit.configuration.file.YamlConfiguration();
    }

    private WorldProtectRule rule(World world) {
        if (world == null) return defaultRule;
        WorldProtectRule rule = worlds.get(world.getName().toLowerCase(Locale.ROOT));
        return rule == null ? defaultRule : rule;
    }

    private boolean bypass(Player player, WorldProtectRule rule) {
        if (player == null) return false;
        if (getModuleConfig().getBoolean("settings.op-bypass", false) && player.isOp()) return true;
        String global = getModuleConfig().getString("settings.bypass-permission", "xycore.worldprotect.bypass");
        return hasPermission(player, global) || hasPermission(player, rule.bypassPermission);
    }

    private boolean hasPermission(Player player, String permission) {
        return permission != null && !permission.trim().isEmpty() && player.hasPermission(permission.trim());
    }

    private Player responsiblePlayer(Entity entity) {
        if (entity instanceof Player) return (Player) entity;
        if (entity instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) entity).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        return null;
    }

    private boolean isFarmland(Material material) {
        String name = material.name();
        return "SOIL".equals(name) || "FARMLAND".equals(name);
    }

    private void deny(org.bukkit.event.Cancellable event, Player player, String message, String key) {
        event.setCancelled(true);
        if (player == null || !getModuleConfig().getBoolean("settings.send-message", true)) return;
        long cooldown = Math.max(0L, getModuleConfig().getLong("settings.message-cooldown-ms", 0L));
        String cooldownKey = player.getUniqueId() + ":" + key;
        Long last = messageCooldowns.get(cooldownKey);
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown) return;
        messageCooldowns.put(cooldownKey, now);
        String formatted = formatMessage(player, message, key);
        if (!formatted.isEmpty()) player.sendMessage(formatted);
    }

    private String formatMessage(Player player, String message, String actionKey) {
        if (message == null || message.trim().isEmpty()) return "";
        String prefix = "";
        if (getModuleConfig().getBoolean("settings.use-prefix", true)) {
            prefix = getModuleConfig().getString("settings.prefix", "{core_prefix}");
            String corePrefix = plugin.getConfig().getString("messages.prefix", "&7[&bXyCore&7]&r");
            prefix = prefix.replace("{core_prefix}", corePrefix == null ? "" : corePrefix);
        }
        String body = message
                .replace("{world}", player.getWorld().getName())
                .replace("{action}", actionKey);
        return ChatColor.translateAlternateColorCodes('&', prefix + body);
    }
}
