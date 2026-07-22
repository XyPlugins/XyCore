package org.xyplugin.xycore.api.service;

/** 可被 XyCore 安全配置重载调用的插件扩展。 */
public interface Reloadable {

    String getId();

    void reload() throws Exception;
}
