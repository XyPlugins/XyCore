package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI 2.11 的最小编译契约。
 *
 * <p>本类属于 papiStub sourceSet，不会进入 XyCore JAR。服务器运行时会链接
 * PlaceholderAPI 插件提供的正式 PlaceholderExpansion 类。</p>
 */
public abstract class PlaceholderExpansion {

    public abstract String getIdentifier();

    public abstract String getAuthor();

    public abstract String getVersion();

    public boolean persist() {
        return false;
    }

    public boolean canRegister() {
        return true;
    }

    public boolean register() {
        return false;
    }

    public boolean unregister() {
        return false;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return null;
    }
}
