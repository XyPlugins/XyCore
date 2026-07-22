package org.xyplugin.xycore.api.data;

import java.util.Set;
import java.util.UUID;

/** 玩家在主服上的数据会话。 */
public interface PlayerSession {

    UUID getUniqueId();

    boolean isReady();

    Object getData(String moduleId);

    <T> T getData(String moduleId, Class<T> type);

    void markDirty(String moduleId);

    Set<String> getLoadedModules();
}
