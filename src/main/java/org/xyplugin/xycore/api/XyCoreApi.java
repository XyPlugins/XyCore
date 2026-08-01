package org.xyplugin.xycore.api;

import org.xyplugin.xycore.api.attribute.AttributeService;
import org.xyplugin.xycore.api.client.ClientBridgeService;
import org.xyplugin.xycore.api.data.PlayerDataManager;
import org.xyplugin.xycore.api.economy.EconomyService;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemTagService;
import org.xyplugin.xycore.api.placeholder.PlaceholderRegistry;
import org.xyplugin.xycore.api.service.ServiceRegistry;
import org.xyplugin.xycore.api.service.ReloadManager;
import org.xyplugin.xycore.api.storage.StorageManager;

/**
 * XyCore 对外暴露的总入口。
 *
 * <p>功能插件只应依赖这些接口，不应访问 XyCore 的 internal 包或直接操作
 * MythicMobs、Vault、AttributePlus 的内部对象。</p>
 */
public interface XyCoreApi {

    PlayerDataManager getPlayerData();

    StorageManager getStorage();

    ServiceRegistry getServices();

    EconomyService getEconomy();

    AttributeService getAttributes();

    ClientBridgeService getClientBridge();

    ItemLibraryService getItems();

    ItemTagService getItemTags();

    PlaceholderRegistry getPlaceholders();

    ReloadManager getReloads();

    /**
     * 获取 Xy 系列插件给玩家发送聊天消息时使用的统一前缀。
     *
     * <p>返回值来自 XyCore 主配置 {@code messages.prefix}，保持配置原样，
     * 调用方负责按自身文本工具转换颜色代码。</p>
     */
    String getMessagePrefix();

    String getVersion();
}
