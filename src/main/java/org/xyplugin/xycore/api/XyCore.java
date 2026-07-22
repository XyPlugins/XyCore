package org.xyplugin.xycore.api;

import org.xyplugin.xycore.XyCorePlugin;

/** 静态 API 入口，供其他 Xy 插件在运行时获取 Core。 */
public final class XyCore {

    private XyCore() {
    }

    /**
     * 获取已启用的 XyCore API。
     *
     * @throws IllegalStateException Core 尚未启用
     */
    public static XyCoreApi get() {
        XyCorePlugin plugin = XyCorePlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("XyCore is not enabled");
        }
        return plugin.getApi();
    }
}
