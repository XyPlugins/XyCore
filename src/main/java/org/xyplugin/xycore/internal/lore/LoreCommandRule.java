package org.xyplugin.xycore.internal.lore;

import org.bukkit.event.block.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A validated and cached lore command binding rule. */
final class LoreCommandRule {

    enum MatchMode { EXACT, CONTAINS }
    enum ExecutorMode { PLAYER, CONSOLE, TEMP_OP }

    final String id;
    final String lore;
    final MatchMode matchMode;
    final ExecutorMode executorMode;
    final Set<Action> actions;
    final String material;
    final boolean requireDisplayName;
    final List<String> commands;
    final String permission;
    final String denyMessage;
    final boolean cancelEvent;
    final long cooldownMillis;
    final boolean consume;
    final List<LoreCondition> conditions;
    final List<String> attributeLines;
    final String attributeSource;
    final long attributeDurationTicks;

    LoreCommandRule(String id,
                    String lore,
                    MatchMode matchMode,
                    ExecutorMode executorMode,
                    Set<Action> actions,
                    String material,
                    boolean requireDisplayName,
                    List<String> commands,
                    String permission,
                    String denyMessage,
                    boolean cancelEvent,
                    long cooldownMillis,
                    boolean consume,
                    List<LoreCondition> conditions,
                    List<String> attributeLines,
                    String attributeSource,
                    long attributeDurationTicks) {
        this.id = id;
        this.lore = lore;
        this.matchMode = matchMode;
        this.executorMode = executorMode;
        this.actions = actions.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK))
                : Collections.unmodifiableSet(EnumSet.copyOf(actions));
        this.material = material == null ? "" : material.trim();
        this.requireDisplayName = requireDisplayName;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.permission = permission == null ? "" : permission.trim();
        this.denyMessage = denyMessage == null ? "" : denyMessage;
        this.cancelEvent = cancelEvent;
        this.cooldownMillis = Math.max(0L, cooldownMillis);
        this.consume = consume;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
        this.attributeLines = Collections.unmodifiableList(new ArrayList<>(attributeLines));
        this.attributeSource = attributeSource == null ? "" : attributeSource.trim();
        this.attributeDurationTicks = Math.max(0L, attributeDurationTicks);
    }
}
