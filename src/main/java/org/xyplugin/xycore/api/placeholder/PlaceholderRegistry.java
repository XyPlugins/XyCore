package org.xyplugin.xycore.api.placeholder;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/** 统一注册 Xy 插件变量的注册表。 */
public interface PlaceholderRegistry {

    void register(PlaceholderProvider provider);

    void unregister(String namespace);

    Optional<PlaceholderProvider> get(String namespace);

    Collection<PlaceholderProvider> getProviders();

    String resolve(Player player, String namespace, String params);
}
