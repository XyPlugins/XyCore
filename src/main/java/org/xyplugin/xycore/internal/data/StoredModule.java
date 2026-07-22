package org.xyplugin.xycore.internal.data;

import org.xyplugin.xycore.api.data.DataContainer;

/** 存储后端返回的一个模块快照。 */
public final class StoredModule {

    private final int version;
    private final DataContainer data;

    public StoredModule(int version, DataContainer data) {
        this.version = version;
        this.data = data;
    }

    public int getVersion() {
        return version;
    }

    public DataContainer getData() {
        return data;
    }
}
