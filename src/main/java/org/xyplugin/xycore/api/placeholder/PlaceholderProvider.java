package org.xyplugin.xycore.api.placeholder;

import org.bukkit.entity.Player;

/** Core 内部的 PlaceholderAPI 变量提供器。 */
public interface PlaceholderProvider {

    String getNamespace();

    String resolve(Player player, String params);
}
