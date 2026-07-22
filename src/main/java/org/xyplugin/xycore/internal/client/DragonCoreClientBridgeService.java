package org.xyplugin.xycore.internal.client;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.client.ClientBridgeService;
import org.xyplugin.xycore.api.event.XyClientKeyEvent;
import org.xyplugin.xycore.api.event.XyClientPacketEvent;
import org.xyplugin.xycore.internal.placeholder.PlaceholderRegistryImpl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DragonCore 2.6.2.9 的反射式 GUI、按键和数据包桥接。 */
@SuppressWarnings("unchecked")
public final class DragonCoreClientBridgeService implements ClientBridgeService {

    private final XyCorePlugin plugin;
    private final PlaceholderRegistryImpl placeholders;
    private final ClassLoader loader;
    private final Listener bridgeListener = new Listener() { };
    private final Set<String> registeredKeys = new HashSet<>();
    private final Method sendOpenGui;
    private final Method sendUpdateGui;
    private final Method sendSyncPlaceholder;
    private final Method sendRunFunction;
    private final Method closeScreen;
    private final Method registerKey;
    private final Method unregisterKey;
    private final boolean available;

    public DragonCoreClientBridgeService(XyCorePlugin plugin, PlaceholderRegistryImpl placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        Plugin dragonCore = Bukkit.getPluginManager().getPlugin("DragonCore");
        loader = dragonCore == null ? getClass().getClassLoader() : dragonCore.getClass().getClassLoader();

        Method open = null;
        Method update = null;
        Method sync = null;
        Method run = null;
        Method close = null;
        Method addKey = null;
        Method removeKey = null;
        boolean ready = false;
        if (dragonCore != null && dragonCore.isEnabled()) {
            try {
                Class<?> sender = Class.forName("eos.moe.dragoncore.network.PacketSender", false, loader);
                Class<?> coreApi = Class.forName("eos.moe.dragoncore.api.CoreAPI", false, loader);
                Class<?> easyScreen = Class.forName("eos.moe.dragoncore.api.easygui.EasyScreen", false, loader);
                open = sender.getMethod("sendOpenGui", Player.class, String.class);
                update = sender.getMethod("sendUpdateGui", Player.class, YamlConfiguration.class);
                sync = sender.getMethod("sendSyncPlaceholder", Player.class, Map.class);
                run = sender.getMethod("sendRunFunction", Player.class, String.class, String.class, boolean.class);
                close = easyScreen.getMethod("closeScreen", Player.class);
                addKey = coreApi.getMethod("registerKey", String.class);
                removeKey = coreApi.getMethod("unregisterKey", String.class);
                ready = true;
            } catch (Exception failure) {
                plugin.getLogger().warning("DragonCore API 初始化失败: " + failure.getMessage());
            }
        }
        sendOpenGui = open;
        sendUpdateGui = update;
        sendSyncPlaceholder = sync;
        sendRunFunction = run;
        closeScreen = close;
        registerKey = addKey;
        unregisterKey = removeKey;
        available = ready;

        if (available) {
            registerEvents();
            for (String key : plugin.getConfig().getStringList("integrations.dragoncore.register-keys")) {
                registerKey(key);
            }
            plugin.getLogger().info("已连接 DragonCore GUI、按键和客户端数据包 API。");
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderName() {
        return available ? "DragonCore-2.6" : "unavailable";
    }

    @Override
    public boolean openGui(Player player, String guiId) {
        return invokeStatic(sendOpenGui, player, guiId);
    }

    @Override
    public boolean closeGui(Player player) {
        return invokeStatic(closeScreen, player);
    }

    @Override
    public boolean updateGui(Player player, Map<String, Object> values) {
        if (!available || player == null || values == null) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) yaml.set(entry.getKey(), entry.getValue());
        return invokeStatic(sendUpdateGui, player, yaml);
    }

    @Override
    public boolean syncPlaceholders(Player player, Map<String, String> values) {
        return invokeStatic(sendSyncPlaceholder, player, values);
    }

    @Override
    public boolean runFunction(Player player, String component, String function, boolean async) {
        return invokeStatic(sendRunFunction, player, component, function, async);
    }

    @Override
    public boolean registerKey(String key) {
        if (key == null || key.trim().isEmpty()) return false;
        if (invokeStatic(registerKey, key.trim())) {
            registeredKeys.add(key.trim());
            return true;
        }
        return false;
    }

    @Override
    public boolean unregisterKey(String key) {
        if (key == null || key.trim().isEmpty()) return false;
        if (invokeStatic(unregisterKey, key.trim())) {
            registeredKeys.remove(key.trim());
            return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
        for (String key : new ArrayList<>(registeredKeys)) unregisterKey(key);
    }

    private void registerEvents() {
        registerEvent("eos.moe.dragoncore.api.event.KeyPressEvent", event -> {
            Player player = (Player) invoke(event, "getPlayer");
            String key = String.valueOf(invoke(event, "getKey"));
            Object rawKeys = invoke(event, "getKeys");
            List<String> keys = rawKeys instanceof List
                    ? new ArrayList<>((List<String>) rawKeys)
                    : Collections.singletonList(key);
            XyClientKeyEvent bridge = new XyClientKeyEvent(player, key, keys, true);
            Bukkit.getPluginManager().callEvent(bridge);
            propagateCancellation(event, bridge.isCancelled());
        });
        registerEvent("eos.moe.dragoncore.api.event.KeyReleaseEvent", event -> {
            Player player = (Player) invoke(event, "getPlayer");
            String key = String.valueOf(invoke(event, "getKey"));
            XyClientKeyEvent bridge = new XyClientKeyEvent(player, key, Collections.singletonList(key), false);
            Bukkit.getPluginManager().callEvent(bridge);
            propagateCancellation(event, bridge.isCancelled());
        });
        registerEvent("eos.moe.dragoncore.api.gui.event.CustomPacketEvent", event -> {
            Player player = (Player) invoke(event, "getPlayer");
            String identifier = String.valueOf(invoke(event, "getIdentifier"));
            Object rawData = invoke(event, "getData");
            List<String> data = rawData instanceof List
                    ? new ArrayList<>((List<String>) rawData)
                    : Collections.<String>emptyList();
            XyClientPacketEvent bridge = new XyClientPacketEvent(player, identifier, data);
            Bukkit.getPluginManager().callEvent(bridge);
            propagateCancellation(event, bridge.isCancelled());
        });
        registerEvent("eos.moe.dragoncore.api.event.SyncPlaceholderEvent", event -> {
            Player player = (Player) invoke(event, "getPlayer");
            Object raw = invoke(event, "getPlaceholders");
            if (!(raw instanceof Map) || player == null) return;
            Map<String, String> values = (Map<String, String>) raw;
            values.put("xycore_uuid", placeholders.resolve(player, "xycore", "uuid"));
            values.put("xycore_data_loaded", placeholders.resolve(player, "xycore", "data_loaded"));
            values.put("xycore_storage", placeholders.resolve(player, "xycore", "storage"));
            values.put("xycore_version", placeholders.resolve(player, "xycore", "version"));
        });
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String className, BridgeExecutor executor) {
        try {
            Class<?> raw = Class.forName(className, false, loader);
            if (!Event.class.isAssignableFrom(raw)) return;
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) raw, bridgeListener,
                    EventPriority.NORMAL, (listener, event) -> {
                        try {
                            executor.execute(event);
                        } catch (Throwable failure) {
                            plugin.getLogger().warning("DragonCore 事件桥接失败: " + failure.getMessage());
                        }
                    }, plugin, false);
        } catch (Exception failure) {
            plugin.getLogger().warning("无法注册 DragonCore 事件 " + className + ": " + failure.getMessage());
        }
    }

    private Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Exception failure) {
            return null;
        }
    }

    private boolean invokeStatic(Method method, Object... args) {
        if (!available || method == null) return false;
        try {
            method.invoke(null, args);
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("DragonCore 调用失败: " + failure.getMessage());
            return false;
        }
    }

    private void propagateCancellation(Event source, boolean cancelled) {
        if (cancelled && source instanceof Cancellable) ((Cancellable) source).setCancelled(true);
    }

    private interface BridgeExecutor {
        void execute(Event event);
    }
}
