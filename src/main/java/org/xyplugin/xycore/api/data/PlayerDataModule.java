package org.xyplugin.xycore.api.data;

import java.util.UUID;

/**
 * 一个插件拥有的玩家数据模块。
 *
 * @param <T> 模块在内存中的类型
 */
public interface PlayerDataModule<T> {

    /** 模块的稳定 ID，例如 xyjob、xyforge。 */
    String getId();

    /** 当前模块数据结构版本。 */
    int getVersion();

    /** 没有历史数据时创建默认对象。 */
    T createDefault(UUID playerId);

    /** 把持久化容器解析为内存对象。 */
    T read(UUID playerId, DataContainer container) throws Exception;

    /** 把内存对象写入持久化容器。 */
    void write(UUID playerId, T value, DataContainer container) throws Exception;

    /**
     * 把旧版本容器迁移到当前版本。默认不做迁移。
     */
    default void migrate(UUID playerId, DataContainer container, int oldVersion) throws Exception {
    }
}
