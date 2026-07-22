package org.xyplugin.xycore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.xyplugin.xycore.api.data.PlayerSession;

/** 所有已注册玩家数据模块完成加载后触发。 */
public final class XyPlayerReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerSession session;

    public XyPlayerReadyEvent(Player player, PlayerSession session) {
        this.player = player;
        this.session = session;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerSession getSession() {
        return session;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
