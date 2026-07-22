package org.xyplugin.xycore.api.data;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 玩家会话和可插拔数据模块的统一管理器。 */
public interface PlayerDataManager {

    <T> void registerModule(PlayerDataModule<T> module);

    void unregisterModule(String moduleId);

    Collection<PlayerDataModule<?>> getModules();

    PlayerSession getSession(UUID playerId);

    boolean isReady(UUID playerId);

    CompletableFuture<PlayerSession> load(UUID playerId);

    CompletableFuture<Void> save(UUID playerId);

    CompletableFuture<Void> saveAndRemove(UUID playerId);

    CompletableFuture<Void> saveAll();

    int getActiveSessionCount();
}
