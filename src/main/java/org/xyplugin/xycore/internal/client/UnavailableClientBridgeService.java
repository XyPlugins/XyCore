package org.xyplugin.xycore.internal.client;

import org.bukkit.entity.Player;
import org.xyplugin.xycore.api.client.ClientBridgeService;

import java.util.Map;

/** DragonCore 不存在时的安全空实现。 */
public final class UnavailableClientBridgeService implements ClientBridgeService {
    @Override public boolean isAvailable() { return false; }
    @Override public String getProviderName() { return "unavailable"; }
    @Override public boolean openGui(Player player, String guiId) { return false; }
    @Override public boolean closeGui(Player player) { return false; }
    @Override public boolean updateGui(Player player, Map<String, Object> values) { return false; }
    @Override public boolean syncPlaceholders(Player player, Map<String, String> values) { return false; }
    @Override public boolean runFunction(Player player, String component, String function, boolean async) { return false; }
    @Override public boolean registerKey(String key) { return false; }
    @Override public boolean unregisterKey(String key) { return false; }
    @Override public void shutdown() { }
}
