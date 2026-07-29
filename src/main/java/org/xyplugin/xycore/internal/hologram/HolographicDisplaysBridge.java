package org.xyplugin.xycore.internal.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * HolographicDisplays 2.x 的轻量反射桥接。
 *
 * <p>只缓存创建、删除和 TextLine#setText，运行时不需要把第三方 API 打进 XyCore。</p>
 */
final class HolographicDisplaysBridge {

    private static final String API_CLASS =
            "com.gmail.filoghost.holographicdisplays.api.HologramsAPI";
    private static final String HOLOGRAM_CLASS =
            "com.gmail.filoghost.holographicdisplays.api.Hologram";
    private static final String TEXT_LINE_CLASS =
            "com.gmail.filoghost.holographicdisplays.api.line.TextLine";

    private final XyCorePlugin owner;
    private final Method createHologram;
    private final Method appendTextLine;
    private final Method deleteHologram;
    private final Method isDeleted;
    private final Method setLineText;

    HolographicDisplaysBridge(XyCorePlugin owner) throws ReflectiveOperationException {
        this.owner = owner;
        Plugin dependency = Bukkit.getPluginManager().getPlugin("HolographicDisplays");
        if (dependency == null || !dependency.isEnabled()) {
            throw new IllegalStateException("未安装或未启用 HolographicDisplays");
        }

        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> apiType = Class.forName(API_CLASS, true, loader);
        Class<?> hologramType = Class.forName(HOLOGRAM_CLASS, true, loader);
        Class<?> textLineType = Class.forName(TEXT_LINE_CLASS, true, loader);

        this.createHologram = findCreateMethod(apiType);
        this.appendTextLine = hologramType.getMethod("appendTextLine", String.class);
        this.deleteHologram = hologramType.getMethod("delete");
        this.isDeleted = hologramType.getMethod("isDeleted");
        this.setLineText = textLineType.getMethod("setText", String.class);
    }

    HologramHandle create(Location location, List<String> lines) throws ReflectiveOperationException {
        Object hologram = invoke(createHologram, null, owner, location);
        if (hologram == null) throw new IllegalStateException("HolographicDisplays 返回了空全息对象");

        List<Object> textLines = new ArrayList<>();
        try {
            for (String line : lines) {
                textLines.add(invoke(appendTextLine, hologram, line));
            }
            return new HologramHandle(hologram, textLines, new ArrayList<>(lines));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            try {
                invoke(deleteHologram, hologram);
            } catch (Exception ignored) {
                // 保留最初的创建异常。
            }
            throw failure;
        }
    }

    void update(HologramHandle handle, List<String> lines) throws ReflectiveOperationException {
        if (handle == null || lines.size() != handle.textLines.size()) {
            throw new IllegalArgumentException("全息行数发生变化，需要重新创建");
        }
        if (Boolean.TRUE.equals(invoke(isDeleted, handle.hologram))) {
            throw new IllegalStateException("全息已经被其他插件删除");
        }
        for (int index = 0; index < lines.size(); index++) {
            String next = lines.get(index);
            if (next.equals(handle.currentLines.get(index))) continue;
            invoke(setLineText, handle.textLines.get(index), next);
            handle.currentLines.set(index, next);
        }
    }

    void delete(HologramHandle handle) {
        if (handle == null) return;
        try {
            if (!Boolean.TRUE.equals(invoke(isDeleted, handle.hologram))) {
                invoke(deleteHologram, handle.hologram);
            }
        } catch (Exception failure) {
            owner.getLogger().warning("删除刷新点全息失败: " + rootMessage(failure));
        }
    }

    private Method findCreateMethod(Class<?> apiType) throws NoSuchMethodException {
        for (Method method : apiType.getMethods()) {
            if (!"createHologram".equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 2) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[0].isAssignableFrom(owner.getClass())
                    && parameters[1].isAssignableFrom(Location.class)) {
                return method;
            }
        }
        throw new NoSuchMethodException("HologramsAPI#createHologram(Plugin, Location)");
    }

    private Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw new IllegalStateException(rootMessage(cause == null ? failure : cause),
                    cause == null ? failure : cause);
        }
    }

    private String rootMessage(Throwable failure) {
        if (failure == null) return "未知错误";
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }

    static final class HologramHandle {
        private final Object hologram;
        private final List<Object> textLines;
        private final List<String> currentLines;

        private HologramHandle(Object hologram, List<Object> textLines, List<String> currentLines) {
            this.hologram = hologram;
            this.textLines = textLines;
            this.currentLines = currentLines;
        }
    }
}
