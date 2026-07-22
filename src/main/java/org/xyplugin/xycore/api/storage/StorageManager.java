package org.xyplugin.xycore.api.storage;

/** 对外公开的存储状态视图。具体 SQL/YML 实现隐藏在 Core 内部。 */
public interface StorageManager {

    String getBackendName();
}
