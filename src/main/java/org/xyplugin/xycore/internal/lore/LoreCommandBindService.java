package org.xyplugin.xycore.internal.lore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.internal.module.AbstractCoreModule;

import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side lore matcher that executes preconfigured commands and attribute effects. */
public final class LoreCommandBindService extends AbstractCoreModule implements Listener {

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, AttributeTask> activeAttributeTasks = new ConcurrentHashMap<>();
    private volatile List<LoreCommandRule> rules = Collections.emptyList();
    private volatile Set<Action> defaultActions = allClickActions();

    public LoreCommandBindService(XyCorePlugin plugin) {
        super(plugin, "lore-command-bind", "LoreCommandBind", "modules/LoreCommandBind.yml");
    }

    @Override
    protected void beforeCreateConfig(File file) throws Exception {
        File legacy = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("lore-command-bind.file", "LoreCommandBind.yml"));
        if (!legacy.isFile()) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        plugin.getLogger().info("已迁移旧 LoreCommandBind.yml 到 modules/LoreCommandBind.yml。");
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
        cooldowns.clear();
        for (AttributeTask task : activeAttributeTasks.values()) {
            Bukkit.getScheduler().cancelTask(task.taskId);
            Player player = Bukkit.getPlayer(task.playerId);
            if (player != null) plugin.getApi().getAttributes().removeSource(player, task.source);
        }
        activeAttributeTasks.clear();
        rules = Collections.emptyList();
    }

    public int size() {
        return rules.size();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (getModuleConfig().getBoolean("settings.ignore-offhand", true)
                && event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return;

        Match match = findMatch(event.getPlayer(), item, meta, event.getAction());
        if (match == null) return;

        LoreCommandRule rule = match.rule;
        Player player = event.getPlayer();
        if (rule.cancelEvent) event.setCancelled(true);

        if (!rule.permission.isEmpty() && !player.hasPermission(rule.permission)) {
            send(player, firstNonEmpty(rule.denyMessage,
                    getModuleConfig().getString("messages.no-permission", "&c你没有权限使用这个物品。")));
            return;
        }
        LoreCondition failed = firstFailedCondition(player, rule);
        if (failed != null) {
            String message = firstNonEmpty(failed.denyMessage, rule.denyMessage,
                    getModuleConfig().getString("messages.condition-failed", "&c你没有满足这个物品的使用条件。"));
            send(player, message
                    .replace("{condition}", failed.id)
                    .replace("{actual}", resolvePlaceholders(player, failed.placeholder))
                    .replace("{expected}", resolvePlaceholders(player, failed.expected)));
            return;
        }

        long remaining = remaining(player.getUniqueId(), rule);
        if (remaining > 0L) {
            double seconds = Math.ceil(remaining / 100.0D) / 10.0D;
            send(player, getModuleConfig().getString("messages.cooldown",
                    "&c请等待 &e{seconds}&c 秒后再使用。").replace("{seconds}", String.valueOf(seconds)));
            return;
        }

        cooldowns.put(cooldownKey(player.getUniqueId(), rule.id), System.currentTimeMillis());
        boolean handled = applyAttributes(player, rule) || !rule.commands.isEmpty();
        for (String command : rule.commands) dispatch(player, rule, command, match.originalLore, item, meta);
        if (rule.consume && handled) consume(event, item);
    }

    private void loadRules() {
        defaultActions = parseActions(getModuleConfig().getStringList("settings.actions"),
                allClickActions());
        List<LoreCommandRule> loaded = new ArrayList<>();

        ConfigurationSection rulesSection = getModuleConfig().getConfigurationSection("rules");
        if (rulesSection == null) rulesSection = getModuleConfig().getConfigurationSection("binds");
        if (rulesSection != null) {
            for (String id : rulesSection.getKeys(false)) {
                ConfigurationSection section = rulesSection.getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", true)) continue;
                LoreCommandRule rule = parseRule(id, section);
                if (rule != null) loaded.add(rule);
            }
        } else {
            for (String id : getModuleConfig().getKeys(false)) {
                if (isReservedRootKey(id)) continue;
                ConfigurationSection section = getModuleConfig().getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", true)) continue;
                LoreCommandRule rule = parseRule(id, section);
                if (rule != null) loaded.add(rule);
            }
        }
        rules = Collections.unmodifiableList(loaded);
        cooldowns.clear();
        plugin.getLogger().info("LoreCommandBind 已加载 " + loaded.size() + " 条有效规则。");
    }

    private LoreCommandRule parseRule(String id, ConfigurationSection section) {
        String lore = readLoreMatcher(section);
        List<String> commands = readCommands(section);
        List<String> attributeLines = readAttributeLines(section);
        if (lore.trim().isEmpty()) {
            plugin.getLogger().warning("LoreCommandBind 规则 " + id + " 缺少 lore 匹配文本，已跳过。");
            return null;
        }
        if (commands.isEmpty() && attributeLines.isEmpty()) {
            plugin.getLogger().warning("LoreCommandBind 规则 " + id + " 没有命令或属性效果，已跳过。");
            return null;
        }

        long cooldownMillis = section.getLong("execute.cooldown-ms", section.getLong("cooldown-ms", -1L));
        if (cooldownMillis < 0L) {
            long seconds = section.getLong("execute.cooldown-seconds", section.getLong("cooldown", -1L));
            cooldownMillis = seconds >= 0L
                    ? seconds * 1000L
                    : getModuleConfig().getLong("settings.default-cooldown-ms", 500L);
        }

        long durationSeconds = section.getLong("attributes.duration-seconds", -1L);
        if (durationSeconds < 0L) durationSeconds = findLegacyAttributeDuration(section.getStringList("attribute"));

        return new LoreCommandRule(
                id,
                lore,
                parseMatchMode(readString(section, "match.mode", "match-mode", "match", "CONTAINS")),
                parseExecutor(readString(section, "execute.executor", "executor", "CONSOLE")),
                parseActions(section.getStringList("actions"), defaultActions),
                readString(section, "match.material", "material", "item", ""),
                section.getBoolean("match.require-display-name",
                        section.getBoolean("require-display-name",
                                getModuleConfig().getBoolean("settings.require-display-name", false))),
                commands,
                readString(section, "conditions.permission", "permission", ""),
                readString(section, "messages.deny", "deny-message", ""),
                section.getBoolean("execute.cancel-event", section.getBoolean("cancel-event", true)),
                cooldownMillis,
                section.getBoolean("execute.consume", section.getBoolean("consume", section.getBoolean("take", false))),
                readConditions(id, section),
                attributeLines,
                readString(section, "attributes.source", "attribute-source", "xycore:lore:{rule}"),
                durationSeconds <= 0L ? 0L : durationSeconds * 20L
        );
    }

    private Match findMatch(Player player, ItemStack item, ItemMeta meta, Action action) {
        for (LoreCommandRule rule : rules) {
            if (!rule.actions.contains(action)) continue;
            if (!matchesMaterial(rule.material, item.getType())) continue;
            if (rule.requireDisplayName && !meta.hasDisplayName()) continue;

            String expected = normalize(resolvePlaceholders(player, rule.lore));
            for (String line : meta.getLore()) {
                String actual = normalize(resolvePlaceholders(player, line));
                boolean matches = rule.matchMode == LoreCommandRule.MatchMode.EXACT
                        ? actual.equals(expected)
                        : actual.contains(expected);
                if (matches) return new Match(rule, line);
            }
        }
        return null;
    }

    private LoreCondition firstFailedCondition(Player player, LoreCommandRule rule) {
        for (LoreCondition condition : rule.conditions) {
            if (!passes(player, condition)) return condition;
        }
        return null;
    }

    private boolean passes(Player player, LoreCondition condition) {
        String actual = resolvePlaceholders(player, condition.placeholder);
        String expected = resolvePlaceholders(player, condition.expected);
        String operator = condition.operator.toLowerCase(Locale.ROOT);
        Double left = parseNumber(actual);
        Double right = parseNumber(expected);

        if (left != null && right != null) {
            if (">".equals(operator)) return left > right;
            if (">=".equals(operator) || "=>".equals(operator)) return left >= right;
            if ("<".equals(operator)) return left < right;
            if ("<=".equals(operator) || "=<".equals(operator)) return left <= right;
            if ("!=".equals(operator) || "<>".equals(operator)) return Double.compare(left, right) != 0;
            return Double.compare(left, right) == 0;
        }

        String normalizedActual = normalize(actual);
        String normalizedExpected = normalize(expected);
        if ("contains".equals(operator)) return normalizedActual.contains(normalizedExpected);
        if ("!contains".equals(operator) || "not-contains".equals(operator)) {
            return !normalizedActual.contains(normalizedExpected);
        }
        if ("!=".equals(operator) || "<>".equals(operator) || "not-equals".equals(operator)) {
            return !normalizedActual.equals(normalizedExpected);
        }
        return normalizedActual.equals(normalizedExpected);
    }

    private boolean applyAttributes(Player player, LoreCommandRule rule) {
        if (rule.attributeLines.isEmpty()) return false;
        String source = replaceBuiltIns(player, rule, rule.attributeSource, "", null, null);
        if (source.trim().isEmpty()) source = "xycore:lore:" + rule.id;
        boolean applied = plugin.getApi().getAttributes().addSource(player, source, rule.attributeLines);
        if (applied && rule.attributeDurationTicks > 0L) {
            String key = player.getUniqueId() + ":" + source;
            AttributeTask previous = activeAttributeTasks.remove(key);
            if (previous != null) Bukkit.getScheduler().cancelTask(previous.taskId);
            final UUID playerId = player.getUniqueId();
            final String finalSource = source;
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                activeAttributeTasks.remove(key);
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) plugin.getApi().getAttributes().removeSource(online, finalSource);
            }, rule.attributeDurationTicks).getTaskId();
            activeAttributeTasks.put(key, new AttributeTask(playerId, source, taskId));
        }
        return applied;
    }

    private void dispatch(Player player, LoreCommandRule rule, String configured,
                          String lore, ItemStack item, ItemMeta meta) {
        if (configured == null) return;
        String command = replaceBuiltIns(player, rule, configured, lore, item, meta).trim();
        LoreCommandRule.ExecutorMode mode = rule.executorMode;
        if (hasPrefix(command, "[player]")) {
            mode = LoreCommandRule.ExecutorMode.PLAYER;
            command = command.substring(8).trim();
        } else if (hasPrefix(command, "[console]")) {
            mode = LoreCommandRule.ExecutorMode.CONSOLE;
            command = command.substring(9).trim();
        } else if (hasPrefix(command, "[op]") || hasPrefix(command, "[temp_op]")) {
            int length = hasPrefix(command, "[op]") ? 4 : 9;
            mode = LoreCommandRule.ExecutorMode.TEMP_OP;
            command = command.substring(length).trim();
        } else if (command.regionMatches(true, 0, "player:", 0, 7)) {
            mode = LoreCommandRule.ExecutorMode.PLAYER;
            command = command.substring(7).trim();
        } else if (command.regionMatches(true, 0, "console:", 0, 8)) {
            mode = LoreCommandRule.ExecutorMode.CONSOLE;
            command = command.substring(8).trim();
        } else if (command.regionMatches(true, 0, "op:", 0, 3)) {
            mode = LoreCommandRule.ExecutorMode.TEMP_OP;
            command = command.substring(3).trim();
        }

        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return;

        if (mode == LoreCommandRule.ExecutorMode.CONSOLE) {
            if (isBlockedConsoleCommand(command)) {
                plugin.getLogger().warning("LoreCommandBind 拦截危险控制台命令，规则=" + rule.id + ", 命令=" + command);
                return;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return;
        }
        if (mode == LoreCommandRule.ExecutorMode.TEMP_OP) {
            if (!getModuleConfig().getBoolean("security.allow-temporary-op", false)) {
                plugin.getLogger().warning("LoreCommandBind 已拒绝临时 OP 命令，规则=" + rule.id + ", 命令=" + command);
                return;
            }
            performTemporaryOp(player, command);
            return;
        }
        player.performCommand(command);
    }

    private void performTemporaryOp(Player player, String command) {
        boolean wasOp = player.isOp();
        try {
            if (!wasOp) player.setOp(true);
            player.performCommand(command);
        } finally {
            if (!wasOp && player.isOnline()) player.setOp(false);
        }
    }

    private String replaceBuiltIns(Player player, LoreCommandRule rule, String value,
                                   String lore, ItemStack item, ItemMeta meta) {
        String display = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
        String material = item == null ? "" : item.getType().name();
        String result = value
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{rule}", rule.id)
                .replace("{bind}", rule.id)
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()))
                .replace("{lore}", ChatColor.stripColor(lore == null ? "" : lore))
                .replace("{display}", display)
                .replace("{item_type}", material);
        return resolvePlaceholders(player, result);
    }

    private String resolvePlaceholders(Player player, String value) {
        if (value == null) return "";
        org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (!getModuleConfig().getBoolean("settings.apply-placeholders", true)
                || papi == null || !papi.isEnabled()) return value;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            return String.valueOf(method.invoke(null, player, value));
        } catch (Exception ignored) {
            return value;
        }
    }

    private String normalize(String text) {
        String value = ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
        if (getModuleConfig().getBoolean("settings.strip-color", true)) value = ChatColor.stripColor(value);
        if (getModuleConfig().getBoolean("settings.trim", true)) value = value.trim();
        if (getModuleConfig().getBoolean("settings.ignore-case", false)) value = value.toLowerCase(Locale.ROOT);
        return value;
    }

    private boolean matchesMaterial(String configured, Material actual) {
        if (configured == null || configured.trim().isEmpty() || "*".equals(configured.trim())) return true;
        String value = configured.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        return actual.name().equalsIgnoreCase(value);
    }

    private boolean isBlockedConsoleCommand(String command) {
        String lower = command.toLowerCase(Locale.ROOT).trim();
        String root = lower.split("\\s+", 2)[0];
        for (String blocked : getModuleConfig().getStringList("security.blocked-console-prefixes")) {
            String candidate = blocked.toLowerCase(Locale.ROOT).trim();
            if (candidate.isEmpty()) continue;
            if (root.equals(candidate) || lower.startsWith(candidate + " ")) return true;
        }
        return false;
    }

    private long remaining(UUID playerId, LoreCommandRule rule) {
        Long last = cooldowns.get(cooldownKey(playerId, rule.id));
        if (last == null) return 0L;
        return Math.max(0L, rule.cooldownMillis - (System.currentTimeMillis() - last));
    }

    private String cooldownKey(UUID playerId, String ruleId) {
        return playerId + ":" + ruleId;
    }

    private void consume(PlayerInteractEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) event.getPlayer().getInventory().setItemInOffHand(null);
        else event.getPlayer().getInventory().setItemInMainHand(null);
    }

    private String readLoreMatcher(ConfigurationSection section) {
        ConfigurationSection match = section.getConfigurationSection("match");
        if (match != null) return match.getString("lore", "");
        if (section.isString("lore")) return section.getString("lore", "");
        String legacy = section.getString("match", "");
        if ("EXACT".equalsIgnoreCase(legacy) || "CONTAINS".equalsIgnoreCase(legacy)) return "";
        return legacy;
    }

    private List<String> readCommands(ConfigurationSection section) {
        List<String> commands = section.getStringList("execute.commands");
        if (commands.isEmpty()) commands = section.getStringList("commands");
        if (commands.isEmpty() && section.isString("execute.command")) {
            commands = Collections.singletonList(section.getString("execute.command"));
        }
        if (commands.isEmpty() && section.isString("command")) {
            commands = Collections.singletonList(section.getString("command"));
        }
        return commands == null ? Collections.emptyList() : commands;
    }

    private List<String> readAttributeLines(ConfigurationSection section) {
        List<String> lines = section.getStringList("attributes.lines");
        if (!lines.isEmpty()) return lines;
        List<String> legacy = section.getStringList("attribute");
        if (legacy.isEmpty()) return legacy;
        List<String> parsed = new ArrayList<>();
        for (String line : legacy) {
            String attribute = extractLegacyAttribute(line);
            if (!attribute.isEmpty()) parsed.add(attribute);
        }
        return parsed;
    }

    private String extractLegacyAttribute(String value) {
        if (value == null) return "";
        String result = value;
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "attribute=", 0, 10)) {
                result = trimmed.substring(10).trim();
                break;
            }
        }
        return result;
    }

    private long findLegacyAttributeDuration(List<String> lines) {
        long seconds = 0L;
        for (String line : lines) {
            if (line == null) continue;
            for (String part : line.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.regionMatches(true, 0, "duration=", 0, 9)) continue;
                try {
                    seconds = Math.max(seconds, Long.parseLong(trimmed.substring(9).trim()));
                } catch (NumberFormatException ignored) {
                    // Bad legacy duration is ignored and the attribute becomes permanent until removed by source.
                }
            }
        }
        return seconds;
    }

    private List<LoreCondition> readConditions(String ruleId, ConfigurationSection section) {
        List<LoreCondition> conditions = new ArrayList<>();
        ConfigurationSection placeholders = section.getConfigurationSection("conditions.placeholders");
        if (placeholders != null) {
            for (String id : placeholders.getKeys(false)) {
                ConfigurationSection condition = placeholders.getConfigurationSection(id);
                if (condition == null) continue;
                conditions.add(new LoreCondition(
                        id,
                        condition.getString("placeholder", ""),
                        condition.getString("operator", ">="),
                        condition.getString("value", condition.getString("compare", "")),
                        condition.getString("deny-message", "")
                ));
            }
        }
        if (section.isString("apilevel") || section.isString("needlevel") || section.isInt("needlevel")) {
            conditions.add(new LoreCondition(
                    ruleId + "_legacy_level",
                    section.getString("apilevel", ""),
                    ">=",
                    section.getString("needlevel", ""),
                    section.getString("unMessage", "")
            ));
        }
        return conditions;
    }

    private Set<Action> parseActions(List<String> values, Set<Action> fallback) {
        EnumSet<Action> actions = EnumSet.noneOf(Action.class);
        for (String value : values) {
            try {
                actions.add(Action.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                plugin.getLogger().warning("LoreCommandBind 忽略未知触发动作: " + value);
            }
        }
        if (actions.isEmpty()) actions.addAll(fallback);
        return actions;
    }

    private boolean isReservedRootKey(String key) {
        String value = key.toLowerCase(Locale.ROOT);
        return "settings".equals(value)
                || "messages".equals(value)
                || "security".equals(value)
                || "rules".equals(value)
                || "binds".equals(value);
    }

    private static EnumSet<Action> allClickActions() {
        return EnumSet.of(Action.LEFT_CLICK_AIR, Action.RIGHT_CLICK_AIR,
                Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK);
    }

    private LoreCommandRule.MatchMode parseMatchMode(String value) {
        try {
            return LoreCommandRule.MatchMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return LoreCommandRule.MatchMode.CONTAINS;
        }
    }

    private LoreCommandRule.ExecutorMode parseExecutor(String value) {
        try {
            return LoreCommandRule.ExecutorMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return LoreCommandRule.ExecutorMode.CONSOLE;
        }
    }

    private String readString(ConfigurationSection section, String first, String second, String fallback) {
        return readString(section, first, second, null, fallback);
    }

    private String readString(ConfigurationSection section, String first, String second, String third, String fallback) {
        if (section.isString(first)) return section.getString(first);
        if (second != null && section.isString(second)) return section.getString(second);
        if (third != null && section.isString(third)) return section.getString(third);
        return fallback;
    }

    private Double parseNumber(String value) {
        if (value == null) return null;
        String cleaned = ChatColor.stripColor(value).replace(",", "").replace("%", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean hasPrefix(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String firstNonEmpty(String first, String second) {
        return firstNonEmpty(first, second, "");
    }

    private String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.trim().isEmpty()) return first;
        if (second != null && !second.trim().isEmpty()) return second;
        return third == null ? "" : third;
    }

    private void send(Player player, String message) {
        if (message != null && !message.trim().isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private static final class Match {
        private final LoreCommandRule rule;
        private final String originalLore;

        private Match(LoreCommandRule rule, String originalLore) {
            this.rule = rule;
            this.originalLore = originalLore;
        }
    }

    private static final class AttributeTask {
        private final UUID playerId;
        private final String source;
        private final int taskId;

        private AttributeTask(UUID playerId, String source, int taskId) {
            this.playerId = playerId;
            this.source = source;
            this.taskId = taskId;
        }
    }
}
