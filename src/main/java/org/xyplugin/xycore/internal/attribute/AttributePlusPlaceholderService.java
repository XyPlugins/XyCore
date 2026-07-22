package org.xyplugin.xycore.internal.attribute;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.attribute.AttributeService;
import org.xyplugin.xycore.api.attribute.AttributeValueMode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.File;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * AttributePlus 的稳定 PAPI 读取适配器。
 *
 * <p>Wiki 明确给出 %ap_<属性变量>:min/max/random% 格式，且 AttributePlus 硬依赖
 * PlaceholderAPI。因此 Core 通过反射调用 PAPI 的 setPlaceholders，不编译期锁定
 * AttributePlus 的 Kotlin 内部包名。属性源写入则按官方 AttributeAPI 契约在运行时
 * 定位 getAttrData、addSourceAttribute 和 takeSourceAttribute。</p>
 */
public final class AttributePlusPlaceholderService implements AttributeService {

    private final XyCorePlugin plugin;
    private final String template;
    private final Method setPlaceholders;
    private final boolean available;
    private final DirectApi directApi;

    public AttributePlusPlaceholderService(XyCorePlugin plugin) {
        this.plugin = plugin;
        this.template = plugin.getConfig().getString("integrations.attributeplus.placeholder-template",
                "%ap_{attribute}:max%");
        Method method = null;
        Plugin attributePlus = plugin.getServer().getPluginManager().getPlugin("AttributePlus");
        if (attributePlus != null && !attributePlus.isEnabled()) attributePlus = null;
        try {
            Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
            if (papi != null && !papi.isEnabled()) papi = null;
            ClassLoader loader = papi == null ? getClass().getClassLoader() : papi.getClass().getClassLoader();
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, loader);
            method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Exception failure) {
            plugin.getLogger().info("AttributePlus PAPI 读取适配器不可用: " + failure.getMessage());
        }
        setPlaceholders = method;
        directApi = attributePlus == null ? null : discoverDirectApi(attributePlus);
        available = attributePlus != null && (method != null || directApi != null);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderName() {
        if (!available) return "unavailable";
        return directApi == null ? "AttributePlus-PAPI" : "AttributePlus-API+PAPI";
    }

    @Override
    public String getRaw(LivingEntity entity, String attribute, AttributeValueMode mode) {
        if (setPlaceholders == null || !(entity instanceof Player) || attribute == null) return "";
        String suffix;
        switch (mode == null ? AttributeValueMode.MAX : mode) {
            case MIN:
                suffix = ":min";
                break;
            case RANDOM:
                suffix = ":random";
                break;
            case RAW:
                suffix = "";
                break;
            case MAX:
            default:
                suffix = ":max";
                break;
        }
        String placeholder = template
                .replace("{attribute}", attribute)
                .replace("{suffix}", suffix);
        // 默认模板已经包含 :max；自定义模板可以使用 {suffix}，避免重复后缀。
        if (!template.contains("{suffix}") && mode != AttributeValueMode.MAX) {
            placeholder = template
                    .replace("{attribute}", attribute)
                    .replace(":max", suffix);
        }
        try {
            return String.valueOf(setPlaceholders.invoke(null, (OfflinePlayer) entity, placeholder));
        } catch (Exception failure) {
            return "";
        }
    }

    @Override
    public OptionalDouble getValue(LivingEntity entity, String attribute, AttributeValueMode mode) {
        String raw = getRaw(entity, attribute, mode);
        if (raw == null || raw.trim().isEmpty()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException ignored) {
            // RAW 形式可能是 "最小值 - 最大值"，取最后一个可解析数字。
            String[] parts = raw.trim().split("\\s*-\\s*");
            for (int i = parts.length - 1; i >= 0; i--) {
                try {
                    return OptionalDouble.of(Double.parseDouble(parts[i].replace(",", "").trim()));
                } catch (NumberFormatException ignoredPart) {
                    // 继续尝试前一个片段。
                }
            }
            return OptionalDouble.empty();
        }
    }

    @Override
    public List<String> getItemAttributeLines(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyList();
        ItemMeta meta = item.getItemMeta();
        return meta == null || !meta.hasLore() ? Collections.emptyList() : new ArrayList<>(meta.getLore());
    }

    @Override
    public boolean addSource(LivingEntity entity, String source, List<String> attributeLines) {
        if (directApi == null || entity == null || source == null || attributeLines == null) return false;
        try {
            Object data = directApi.invoke(directApi.getAttrData, entity);
            directApi.invoke(directApi.addSourceAttribute, data, source, new ArrayList<>(attributeLines), false);
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("AttributePlus 增加属性源失败: " + failure.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeSource(LivingEntity entity, String source) {
        if (directApi == null || entity == null || source == null) return false;
        try {
            Object data = directApi.invoke(directApi.getAttrData, entity);
            directApi.invoke(directApi.takeSourceAttribute, data, source);
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("AttributePlus 删除属性源失败: " + failure.getMessage());
            return false;
        }
    }

    private DirectApi discoverDirectApi(Plugin attributePlus) {
        try {
            File pluginFile = new File(attributePlus.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!pluginFile.isFile()) return null;
            try (JarFile jar = new JarFile(pluginFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if ((!name.endsWith("/AttributeAPI.class") && !name.endsWith("/AttributeAPIKt.class"))
                            || name.contains("$")) continue;
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    Class<?> type = Class.forName(className, false, attributePlus.getClass().getClassLoader());
                    DirectApi api = DirectApi.create(type);
                    if (api != null) {
                        plugin.getLogger().info("已连接 AttributePlus 直接属性源 API: " + className);
                        return api;
                    }
                }
            }
        } catch (Throwable failure) {
            plugin.getLogger().warning("AttributePlus 直接 API 扫描失败: " + failure.getMessage());
        }
        plugin.getLogger().warning("未找到兼容的 AttributeAPI，属性读取可用但属性源写入已禁用。");
        return null;
    }

    private static final class DirectApi {
        private final Object target;
        private final Method getAttrData;
        private final Method addSourceAttribute;
        private final Method takeSourceAttribute;

        private DirectApi(Object target, Method getAttrData, Method addSourceAttribute,
                          Method takeSourceAttribute) {
            this.target = target;
            this.getAttrData = getAttrData;
            this.addSourceAttribute = addSourceAttribute;
            this.takeSourceAttribute = takeSourceAttribute;
        }

        private static DirectApi create(Class<?> type) {
            Method get = null;
            Method add = null;
            Method take = null;
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("getAttrData".equals(method.getName()) && parameters.length == 1
                        && parameters[0].isAssignableFrom(LivingEntity.class)) {
                    get = method;
                } else if ("addSourceAttribute".equals(method.getName()) && parameters.length == 4
                        && parameters[1] == String.class
                        && List.class.isAssignableFrom(parameters[2])
                        && (parameters[3] == boolean.class || parameters[3] == Boolean.class)) {
                    add = method;
                } else if ("takeSourceAttribute".equals(method.getName()) && parameters.length == 2
                        && parameters[1] == String.class) {
                    take = method;
                }
            }
            if (get == null || add == null || take == null) return null;
            try {
                Object target = null;
                if (!Modifier.isStatic(get.getModifiers()) || !Modifier.isStatic(add.getModifiers())
                        || !Modifier.isStatic(take.getModifiers())) {
                    target = type.getField("INSTANCE").get(null);
                }
                get.setAccessible(true);
                add.setAccessible(true);
                take.setAccessible(true);
                return new DirectApi(target, get, add, take);
            } catch (Exception ignored) {
                return null;
            }
        }

        private Object invoke(Method method, Object... args) throws Exception {
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, args);
        }
    }
}
