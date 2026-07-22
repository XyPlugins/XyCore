package org.xyplugin.xycore.api.client;

import org.bukkit.entity.Player;

import java.util.Map;

/** DragonCore 等客户端增强模组的通用服务接口。 */
public interface ClientBridgeService {

    boolean isAvailable();

    String getProviderName();

    boolean openGui(Player player, String guiId);

    boolean closeGui(Player player);

    boolean updateGui(Player player, Map<String, Object> values);

    boolean syncPlaceholders(Player player, Map<String, String> values);

    boolean runFunction(Player player, String component, String function, boolean async);

    boolean registerKey(String key);

    boolean unregisterKey(String key);

    void shutdown();
}
