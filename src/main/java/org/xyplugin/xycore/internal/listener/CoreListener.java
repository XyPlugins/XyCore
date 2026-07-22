package org.xyplugin.xycore.internal.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.data.PlayerDataManager;

/** 绑定 Bukkit 玩家生命周期与 Core 数据生命周期。 */
public final class CoreListener implements Listener {

    private final XyCorePlugin plugin;
    private final PlayerDataManager data;

    public CoreListener(XyCorePlugin plugin, PlayerDataManager data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        data.load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        data.saveAndRemove(event.getPlayer().getUniqueId());
    }
}
