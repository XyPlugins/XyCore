package org.xyplugin.xycore.internal.offhand;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

/** Server-side guard for the vanilla offhand slot used by DragonCore container_45. */
public final class OffhandLoreGuardModule extends AbstractCoreModule implements Listener {

    private static final int VANILLA_OFFHAND_RAW_SLOT = 45;
    private static final int BUKKIT_OFFHAND_SLOT = 40;

    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<UUID, Long>();
    private volatile String requiredLore;
    private volatile String matchMode;
    private volatile boolean ignoreSpaces;
    private volatile boolean ignoreColors;
    private volatile boolean delayedCleanup;
    private volatile boolean dropOverflow;
    private volatile List<Long> syncRepeatTicks = Collections.emptyList();

    public OffhandLoreGuardModule(XyCorePlugin plugin) {
        super(plugin, "offhand-lore-guard", "OffhandLoreGuard", "modules/offhand-lore-guard.yml");
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadSettings();
        for (Player player : Bukkit.getOnlinePlayers()) cleanupOffhand(player, false);
    }

    @Override
    protected void onReload() {
        loadSettings();
        messageCooldowns.clear();
        for (Player player : Bukkit.getOnlinePlayers()) cleanupOffhand(player, false);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        messageCooldowns.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled() || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        boolean offhandClick = isOffhandClick(event);
        if (offhandClick) {
            ItemStack candidate = candidateForOffhandClick(event, player);
            if (!isEmpty(candidate) && !isAllowed(candidate)) {
                event.setCancelled(true);
                sendDenied(player);
                player.updateInventory();
                return;
            }
        }

        if (offhandClick) scheduleCleanup(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isEnabled() || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getNewItems().containsKey(VANILLA_OFFHAND_RAW_SLOT)) {
            ItemStack candidate = event.getNewItems().get(VANILLA_OFFHAND_RAW_SLOT);
            if (!isEmpty(candidate) && !isAllowed(candidate)) {
                event.setCancelled(true);
                sendDenied(player);
                player.updateInventory();
                return;
            }
            scheduleCleanup(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!isEnabled()) return;
        ItemStack candidate = event.getMainHandItem();
        if (!isEmpty(candidate) && !isAllowed(candidate)) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
            event.getPlayer().updateInventory();
            return;
        }
        scheduleCleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        scheduleCleanup(event.getPlayer());
    }

    private void loadSettings() {
        requiredLore = color(getModuleConfig().getString("match.required-lore", "&7类型: &f副武器"));
        matchMode = getModuleConfig().getString("match.mode", "contains");
        ignoreSpaces = getModuleConfig().getBoolean("match.ignore-spaces", true);
        ignoreColors = getModuleConfig().getBoolean("match.ignore-colors", false);
        delayedCleanup = getModuleConfig().getBoolean("settings.delayed-cleanup", true);
        dropOverflow = getModuleConfig().getBoolean("settings.drop-overflow", true);
        syncRepeatTicks = readSyncRepeatTicks();
    }

    private boolean isOffhandClick(InventoryClickEvent event) {
        if (event.getRawSlot() == VANILLA_OFFHAND_RAW_SLOT) return true;
        return event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == BUKKIT_OFFHAND_SLOT;
    }

    private ItemStack candidateForOffhandClick(InventoryClickEvent event, Player player) {
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            return hotbar >= 0 ? player.getInventory().getItem(hotbar) : null;
        }
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor)) return cursor;
        return null;
    }

    private void scheduleCleanup(final Player player) {
        if (!delayedCleanup || player == null) return;
        for (final Long delay : syncRepeatTicks) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    cleanupOffhand(player, true);
                    if (player.isOnline()) player.updateInventory();
                }
            }, Math.max(1L, delay == null ? 1L : delay.longValue()));
        }
    }

    private List<Long> readSyncRepeatTicks() {
        List<Integer> configured = getModuleConfig().getIntegerList("settings.sync-repeat-ticks");
        List<Long> result = new ArrayList<Long>();
        if (configured != null) {
            for (Integer value : configured) {
                if (value == null || value.intValue() <= 0 || value.intValue() > 40) continue;
                Long tick = Long.valueOf(value.longValue());
                if (!result.contains(tick)) result.add(tick);
            }
        }
        if (result.isEmpty()) {
            result.add(1L);
            result.add(3L);
            result.add(6L);
        }
        return Collections.unmodifiableList(result);
    }

    private void cleanupOffhand(Player player, boolean notify) {
        if (player == null || !player.isOnline()) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isEmpty(offhand) || isAllowed(offhand)) return;

        ItemStack illegal = offhand.clone();
        player.getInventory().setItemInOffHand(null);
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(illegal);
        if (!leftovers.isEmpty() && dropOverflow) {
            for (ItemStack leftover : leftovers.values()) {
                if (!isEmpty(leftover)) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        if (notify) sendDenied(player);
        player.updateInventory();
    }

    private boolean isAllowed(ItemStack item) {
        if (isEmpty(item)) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        String expected = normalize(requiredLore);
        for (String line : lore) {
            String current = normalize(color(line));
            if ("exact".equalsIgnoreCase(matchMode)) {
                if (current.equals(expected)) return true;
            } else if (current.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) return "";
        String value = text;
        if (ignoreColors) value = ChatColor.stripColor(value);
        if (ignoreSpaces) value = value.replaceAll("\\s", "");
        return value;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private void sendDenied(Player player) {
        if (player == null || !getModuleConfig().getBoolean("settings.send-message", true)) return;
        long cooldown = Math.max(0L, getModuleConfig().getLong("settings.message-cooldown-ms", 500L));
        Long last = messageCooldowns.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown) return;
        messageCooldowns.put(player.getUniqueId(), now);

        String prefix = "";
        if (getModuleConfig().getBoolean("settings.use-prefix", true)) {
            prefix = getModuleConfig().getString("settings.prefix", "{core_prefix}");
            String corePrefix = plugin.getApi().getMessagePrefix();
            prefix = prefix.replace("{core_prefix}", corePrefix == null ? "" : corePrefix);
        }
        String message = getModuleConfig().getString("messages.denied",
                "&c副手槽只能放入带有 &f类型: 副武器 &c的物品。");
        player.sendMessage(color(prefix + message));
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
