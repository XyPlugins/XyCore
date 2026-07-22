package org.xyplugin.xycore.internal.data;

import org.xyplugin.xycore.api.service.ServiceRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 默认的线程安全服务注册表。 */
public final class ServiceRegistryImpl implements ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T service) {
        if (type == null || service == null) throw new IllegalArgumentException("Service type/value cannot be null");
        services.put(type, service);
    }

    @Override
    public <T> void unregister(Class<T> type) {
        services.remove(type);
    }

    @Override
    public <T> java.util.Optional<T> get(Class<T> type) {
        Object value = services.get(type);
        return type.isInstance(value) ? java.util.Optional.of(type.cast(value)) : java.util.Optional.empty();
    }

    @Override
    public Collection<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableList(new ArrayList<>(services.keySet()));
    }
}
