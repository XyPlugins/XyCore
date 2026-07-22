package org.xyplugin.xycore.internal.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.data.DataContainer;
import org.xyplugin.xycore.api.data.PlayerDataManager;
import org.xyplugin.xycore.api.data.PlayerDataModule;
import org.xyplugin.xycore.api.data.PlayerSession;
import org.xyplugin.xycore.api.event.XyPlayerReadyEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 玩家数据模块的异步生命周期管理器。 */
public final class PlayerDataManagerImpl implements PlayerDataManager {

    private final XyCorePlugin plugin;
    private final StorageManagerImpl storage;
    private final ExecutorService executor;
    private final Map<String, PlayerDataModule<?>> modules = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSessionImpl> sessions = new ConcurrentHashMap<>();

    public PlayerDataManagerImpl(XyCorePlugin plugin, StorageManagerImpl storage) {
        this.plugin = plugin;
        this.storage = storage;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.executor = Executors.newFixedThreadPool(Math.min(4, threads), runnable -> {
            Thread thread = new Thread(runnable, "XyCore-Data");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public <T> void registerModule(PlayerDataModule<T> module) {
        if (module == null || module.getId() == null || module.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("A data module must have a non-empty id");
        }
        String id = module.getId().toLowerCase();
        if (modules.putIfAbsent(id, module) != null) {
            throw new IllegalArgumentException("Data module already registered: " + id);
        }
        plugin.getLogger().info("已注册玩家数据模块: " + id + " v" + module.getVersion());
    }

    @Override
    public void unregisterModule(String moduleId) {
        if (moduleId != null) modules.remove(moduleId.toLowerCase());
    }

    @Override
    public Collection<PlayerDataModule<?>> getModules() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    @Override
    public PlayerSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    @Override
    public boolean isReady(UUID playerId) {
        PlayerSessionImpl session = sessions.get(playerId);
        return session != null && session.isReady();
    }

    @Override
    public CompletableFuture<PlayerSession> load(UUID playerId) {
        PlayerSessionImpl existing = sessions.get(playerId);
        if (existing != null && existing.isReady()) {
            return CompletableFuture.completedFuture(existing);
        }
        PlayerSessionImpl session = existing == null
                ? new PlayerSessionImpl(playerId)
                : existing;
        sessions.put(playerId, session);
        CompletableFuture<PlayerSession> future = CompletableFuture.supplyAsync(() -> {
            synchronized (session) {
                try {
                    for (PlayerDataModule<?> module : new ArrayList<>(modules.values())) {
                        loadModule(playerId, session, module);
                    }
                    session.setReady(true);
                    return session;
                } catch (Exception failure) {
                    sessions.remove(playerId, session);
                    throw new DataLoadException(playerId, failure);
                }
            }
        }, executor);
        return future.whenComplete((loaded, failure) -> {
            if (failure == null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.getPluginManager().callEvent(new XyPlayerReadyEvent(player, loaded)));
                }
            } else {
                handleLoadFailure(playerId, unwrap(failure));
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void loadModule(UUID playerId, PlayerSessionImpl session, PlayerDataModule module) throws Exception {
        String id = module.getId().toLowerCase();
        StoredModule stored = storage.load(playerId, id);
        DataContainer container = stored == null ? new DataContainer() : stored.getData();
        if (stored != null && stored.getVersion() < module.getVersion()) {
            module.migrate(playerId, container, stored.getVersion());
            session.markDirty(id);
        }
        Object value = stored == null
                ? module.createDefault(playerId)
                : module.read(playerId, container);
        if (stored == null) session.markDirty(id);
        session.putData(id, value);
    }

    @Override
    public CompletableFuture<Void> save(UUID playerId) {
        PlayerSessionImpl session = sessions.get(playerId);
        if (session == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> saveSession(playerId, session), executor);
    }

    @Override
    public CompletableFuture<Void> saveAndRemove(UUID playerId) {
        PlayerSessionImpl session = sessions.get(playerId);
        if (session == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            saveSession(playerId, session);
            sessions.remove(playerId, session);
        }, executor);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void saveSession(UUID playerId, PlayerSessionImpl session) {
        synchronized (session) {
            for (PlayerDataModule module : new ArrayList<>(modules.values())) {
                Object value = session.getData(module.getId().toLowerCase());
                if (value == null) continue;
                try {
                    DataContainer container = new DataContainer();
                    module.write(playerId, value, container);
                    storage.save(playerId, module.getId().toLowerCase(), module.getVersion(), container);
                } catch (Exception failure) {
                    plugin.getLogger().severe("保存玩家 " + playerId + " 的模块 " + module.getId()
                            + " 失败: " + failure.getMessage());
                }
            }
            session.clearDirty();
        }
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        Collection<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID playerId : new ArrayList<>(sessions.keySet())) futures.add(save(playerId));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    @Override
    public int getActiveSessionCount() {
        return sessions.size();
    }

    private void handleLoadFailure(UUID playerId, Throwable failure) {
        plugin.getLogger().severe("加载玩家 " + playerId + " 数据失败: " + failure.getMessage());
        if (!"kick".equalsIgnoreCase(plugin.getConfig().getString("player-data.load-failure", "kick"))) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            String message = plugin.getConfig().getString("player-data.kick-message", "&c玩家数据加载失败，请联系管理员。");
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(color(message)));
        }
    }

    private String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }

    public void shutdown() {
        saveAll().join();
        executor.shutdown();
    }

    private static final class DataLoadException extends RuntimeException {
        private DataLoadException(UUID playerId, Throwable cause) {
            super("Failed to load " + playerId, cause);
        }
    }
}
