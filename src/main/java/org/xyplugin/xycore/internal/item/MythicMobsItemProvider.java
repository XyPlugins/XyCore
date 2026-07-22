package org.xyplugin.xycore.internal.item;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.item.ItemProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * MythicMobs 4.x 物品适配器。
 *
 * <p>适配器使用反射是为了让 XyCore 在没有 MythicMobs 时仍可正常启动，且不把
 * MythicMobs 的付费 JAR 打包进 Core。当前接口对应 XyMythicItemGui 已使用的
 * ItemManager#getItems、getItem 和 MythicItem#generateItemStack API。</p>
 */
public final class MythicMobsItemProvider implements ItemProvider {

    private final XyCorePlugin plugin;
    private final ClassLoader classLoader;
    private final boolean available;

    public MythicMobsItemProvider(XyCorePlugin plugin) {
        this.plugin = plugin;
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        this.classLoader = mythicMobs == null ? getClass().getClassLoader() : mythicMobs.getClass().getClassLoader();
        this.available = mythicMobs != null && findClass("io.lumine.xikage.mythicmobs.MythicMobs") != null;
    }

    @Override
    public String getId() {
        return "mythicmobs";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Collection<String> getItemIds() {
        if (!available) return Collections.emptyList();
        try {
            Object manager = itemManager();
            Object value = invoke(manager, "getItems");
            if (!(value instanceof Collection)) return Collections.emptyList();
            List<String> ids = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                String id = String.valueOf(invoke(item, "getInternalName"));
                if (id != null && !"null".equals(id)) ids.add(id);
            }
            return Collections.unmodifiableList(ids);
        } catch (Exception failure) {
            plugin.getLogger().warning("读取 MythicMobs 物品列表失败: " + failure.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ItemStack> createItem(String itemId, int amount) {
        if (!available || itemId == null || amount <= 0) return Optional.empty();
        try {
            Object optional = invoke(itemManager(), "getItem", itemId);
            if (optional == null || !(Boolean) invoke(optional, "isPresent")) return Optional.empty();
            Object mythicItem = invoke(optional, "get");
            Object generated = invoke(mythicItem, "generateItemStack", amount);
            ItemStack stack = adapt(generated);
            if (stack == null) return Optional.empty();
            stack.setAmount(amount);
            return Optional.of(stack);
        } catch (Exception failure) {
            plugin.getLogger().warning("生成 MythicMobs 物品 " + itemId + " 失败: " + failure.getMessage());
            return Optional.empty();
        }
    }

    private Object itemManager() throws Exception {
        Class<?> mmClass = findClass("io.lumine.xikage.mythicmobs.MythicMobs");
        Object instance = mmClass.getMethod("inst").invoke(null);
        return invoke(instance, "getItemManager");
    }

    private ItemStack adapt(Object generated) throws Exception {
        if (generated instanceof ItemStack) return (ItemStack) generated;
        Class<?> adapter = findClass("io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter");
        if (adapter == null) return null;
        for (Method method : adapter.getMethods()) {
            if (!"adapt".equals(method.getName()) || method.getParameterTypes().length != 1) continue;
            try {
                Object result = method.invoke(null, generated);
                if (result instanceof ItemStack) return (ItemStack) result;
            } catch (IllegalArgumentException ignored) {
                // 可能是其它平台的 adapt 重载，继续检查。
            }
        }
        return null;
    }

    private Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
            try {
                return method.invoke(target, args);
            } catch (IllegalArgumentException ignored) {
                // 继续寻找兼容的重载。
            }
        }
        throw new NoSuchMethodException(name);
    }

    private Class<?> findClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignoredAgain) {
                return null;
            }
        }
    }
}
