package org.xyplugin.xycore.internal.data;

import org.xyplugin.xycore.api.data.DataContainer;

import java.util.UUID;

/** Core 内部的存储后端 SPI。 */
public interface StorageBackend extends AutoCloseable {

    String getName();

    StoredModule load(UUID playerId, String moduleId) throws Exception;

    void save(UUID playerId, String moduleId, int version, DataContainer data) throws Exception;

    @Override
    void close();
}
