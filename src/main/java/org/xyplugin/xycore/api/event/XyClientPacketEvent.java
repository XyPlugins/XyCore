package org.xyplugin.xycore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DragonCore GUI 自定义数据包的通用事件。 */
public final class XyClientPacketEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String identifier;
    private final List<String> data;
    private boolean cancelled;

    public XyClientPacketEvent(Player player, String identifier, List<String> data) {
        super(player);
        this.identifier = identifier;
        this.data = Collections.unmodifiableList(new ArrayList<>(data));
    }

    public String getIdentifier() {
        return identifier;
    }

    public List<String> getData() {
        return data;
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
