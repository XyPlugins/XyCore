package org.xyplugin.xycore.internal.permission;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gives temporary Bukkit permissions while a player is standing in configured worlds. */
public final class WorldPermissionModule extends AbstractCoreModule implements Listener {

    private volatile WorldPermissionRule defaultRule = WorldPermissionRule.disabled();
    private volatile Map<String, WorldPermissionRule> worlds = Collections.emptyMap();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public WorldPermissionModule(XyCorePlugin plugin) {
        super(plugin, "world-permission", "WorldPermission", "modules/world-permission.yml");
    }

    @Override
    protected void onEnable() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadRules();
        if (getModuleConfig().getBoolean("settings.refresh-online-on-reload", true)) refreshOnlinePlayers();
    }

    @Override
    protected void onReload() {
        loadRules();
        if (getModuleConfig().getBoolean("settings.refresh-online-on-reload", true)) refreshOnlinePlayers();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        clearAllAttachments();
        worlds = Collections.emptyMap();
        defaultRule = WorldPermissionRule.disabled();
    }

    public int getWorldCount() {
        return worlds.size();
    }

    public int getActiveAttachmentCount() {
        return attachments.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!getModuleConfig().getBoolean("settings.refresh-on-join", true)) return;
        apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!getModuleConfig().getBoolean("settings.refresh-on-world-change", true)) return;
        apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!getModuleConfig().getBoolean("settings.remove-on-quit", true)) return;
        clearAttachment(event.getPlayer());
    }

    private void loadRules() {
        WorldPermissionRule fallback = readRule(getModuleConfig().getConfigurationSection("defaults"),
                WorldPermissionRule.disabled(),
                getModuleConfig().getBoolean("settings.default-enabled", false));
        Map<String, WorldPermissionRule> loaded = new HashMap<>();
        ConfigurationSection section = getModuleConfig().getConfigurationSection("worlds");
        if (section != null) {
            for (String worldName : section.getKeys(false)) {
                ConfigurationSection world = section.getConfigurationSection(worldName);
                if (world == null) continue;
                boolean inherit = world.getBoolean("inherit-defaults", true);
                WorldPermissionRule parent = inherit ? fallback : WorldPermissionRule.disabled();
                loaded.put(worldName.toLowerCase(Locale.ROOT),
                        readRule(world, parent, world.getBoolean("enabled", parent.enabled)));
            }
        }
        defaultRule = fallback;
        worlds = Collections.unmodifiableMap(loaded);
        plugin.getLogger().info("WorldPermission 已加载 " + loaded.size() + " 个世界配置，默认权限="
                + (fallback.enabled ? "on" : "off") + "。");
    }

    private WorldPermissionRule readRule(ConfigurationSection section, WorldPermissionRule parent, boolean enabled) {
        if (section == null) return parent;
        Set<String> grants = new LinkedHashSet<>(parent.grantPermissions);
        Set<String> denials = new LinkedHashSet<>(parent.denyPermissions);
        if (section.isList("grant-permissions")) {
            grants.clear();
            grants.addAll(section.getStringList("grant-permissions"));
        }
        if (section.isList("deny-permissions")) {
            denials.clear();
            denials.addAll(section.getStringList("deny-permissions"));
        }
        return new WorldPermissionRule(enabled,
                new java.util.ArrayList<>(grants),
                new java.util.ArrayList<>(denials));
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    private void apply(Player player) {
        clearAttachment(player);
        WorldPermissionRule rule = rule(player.getWorld());
        if (!rule.enabled || rule.isEmpty()) return;
        if (getModuleConfig().getBoolean("settings.op-bypass", false) && player.isOp()) return;

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String permission : rule.grantPermissions) {
            attachment.setPermission(permission, true);
        }
        for (String permission : rule.denyPermissions) {
            attachment.setPermission(permission, false);
        }
        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    private WorldPermissionRule rule(World world) {
        if (world == null) return defaultRule;
        WorldPermissionRule rule = worlds.get(world.getName().toLowerCase(Locale.ROOT));
        return rule == null ? defaultRule : rule;
    }

    private void clearAllAttachments() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAttachment(player);
        }
        attachments.clear();
    }

    private void clearAttachment(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment == null) return;
        try {
            player.removeAttachment(attachment);
        } catch (IllegalArgumentException ignored) {
            // The attachment may already be gone if another plugin recalculated/removes it during shutdown.
        }
        player.recalculatePermissions();
    }
}
