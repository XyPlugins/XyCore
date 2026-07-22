package org.xyplugin.xycore.internal.data;

import org.xyplugin.xycore.api.data.PlayerSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Core 内部的线程安全玩家会话实现。 */
public final class PlayerSessionImpl implements PlayerSession {

    private final UUID uniqueId;
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private final Set<String> dirty = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile boolean ready;

    public PlayerSessionImpl(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public void putData(String moduleId, Object value) {
        data.put(moduleId, value);
    }

    @Override
    public Object getData(String moduleId) {
        return data.get(moduleId);
    }

    @Override
    public <T> T getData(String moduleId, Class<T> type) {
        Object value = getData(moduleId);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public void markDirty(String moduleId) {
        dirty.add(moduleId);
    }

    public boolean isDirty(String moduleId) {
        return dirty.contains(moduleId);
    }

    public void clearDirty() {
        dirty.clear();
    }

    @Override
    public Set<String> getLoadedModules() {
        return Collections.unmodifiableSet(data.keySet());
    }
}
