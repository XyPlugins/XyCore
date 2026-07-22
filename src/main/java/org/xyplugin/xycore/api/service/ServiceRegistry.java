package org.xyplugin.xycore.api.service;

import java.util.Collection;
import java.util.Optional;

/** Xy 插件服务注册中心。 */
public interface ServiceRegistry {

    <T> void register(Class<T> type, T service);

    <T> void unregister(Class<T> type);

    <T> Optional<T> get(Class<T> type);

    Collection<Class<?>> getRegisteredTypes();
}
