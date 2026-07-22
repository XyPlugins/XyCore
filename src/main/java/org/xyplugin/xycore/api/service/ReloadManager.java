package org.xyplugin.xycore.api.service;

import java.util.Collection;

/** Xy 系列插件的配置重载注册表，不执行 Bukkit/Paper 的完整热重载。 */
public interface ReloadManager {

    void register(Reloadable reloadable);

    void unregister(String id);

    Collection<String> getIds();

    ReloadReport reload(String id);

    ReloadReport reloadAll();

    final class ReloadReport {
        private final int success;
        private final int failure;

        public ReloadReport(int success, int failure) {
            this.success = success;
            this.failure = failure;
        }

        public int getSuccess() {
            return success;
        }

        public int getFailure() {
            return failure;
        }
    }
}
