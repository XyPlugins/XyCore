package org.xyplugin.xycore.internal.data;

import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.service.ReloadManager;
import org.xyplugin.xycore.api.service.Reloadable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 配置重载实现，错误被隔离并汇总给管理员。 */
public final class ReloadManagerImpl implements ReloadManager {

    private final XyCorePlugin plugin;
    private final Map<String, Reloadable> reloadables = new ConcurrentHashMap<>();

    public ReloadManagerImpl(XyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(Reloadable reloadable) {
        if (reloadable == null || reloadable.getId() == null) throw new IllegalArgumentException("Reloadable id is required");
        reloadables.put(reloadable.getId().toLowerCase(), reloadable);
    }

    @Override
    public void unregister(String id) {
        if (id != null) reloadables.remove(id.toLowerCase());
    }

    @Override
    public Collection<String> getIds() {
        return Collections.unmodifiableList(new ArrayList<>(reloadables.keySet()));
    }

    @Override
    public ReloadReport reload(String id) {
        Reloadable reloadable = id == null ? null : reloadables.get(id.toLowerCase());
        if (reloadable == null) return new ReloadReport(0, 1);
        try {
            reloadable.reload();
            return new ReloadReport(1, 0);
        } catch (Exception failure) {
            plugin.getLogger().warning("重载 " + id + " 失败: " + failure.getMessage());
            return new ReloadReport(0, 1);
        }
    }

    @Override
    public ReloadReport reloadAll() {
        int success = 0;
        int failure = 0;
        for (String id : new ArrayList<>(reloadables.keySet())) {
            ReloadReport report = reload(id);
            success += report.getSuccess();
            failure += report.getFailure();
        }
        return new ReloadReport(success, failure);
    }
}
