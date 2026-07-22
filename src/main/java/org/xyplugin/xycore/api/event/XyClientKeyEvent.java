package org.xyplugin.xycore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DragonCore 客户端按键按下/释放的通用事件。 */
public final class XyClientKeyEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String key;
    private final List<String> keys;
    private final boolean pressed;
    private boolean cancelled;

    public XyClientKeyEvent(Player player, String key, List<String> keys, boolean pressed) {
        super(player);
        this.key = key;
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        this.pressed = pressed;
    }

    public String getKey() {
        return key;
    }

    public List<String> getKeys() {
        return keys;
    }

    public boolean isPressed() {
        return pressed;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
