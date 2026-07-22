package org.xyplugin.xycore.internal.item;

import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.item.ItemTagService;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 1.12.2 NMS NBTTagCompound 适配器。
 *
 * <p>不在编译期引用 CraftBukkit/NMS，避免 Paper API 工程无法构建。服务器运行
 * 时通过反射定位 1.12.2 的 CraftItemStack 和 NBTTagCompound；定位失败则安全降级
 * 为不可用，调用方应检查 {@link #isAvailable()}。</p>
 */
public final class NmsItemTagService implements ItemTagService {

    private final XyCorePlugin plugin;
    private final boolean available;
    private final Class<?> craftItemStack;
    private final Class<?> nbtCompound;
    private final Method asNmsCopy;
    private final Method asBukkitCopy;
    private final Method hasTag;
    private final Method getTag;
    private final Method setTag;
    private final Method hasKey;
    private final Method getString;
    private final Method setString;
    private final Method remove;

    public NmsItemTagService(XyCorePlugin plugin) {
        this.plugin = plugin;
        Class<?> craft = null;
        Class<?> nbt = null;
        Method nms = null;
        Method bukkit = null;
        Method has = null;
        Method get = null;
        Method set = null;
        Method contains = null;
        Method read = null;
        Method write = null;
        Method delete = null;
        boolean ok = false;
        try {
            craft = Class.forName("org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack");
            nbt = Class.forName("net.minecraft.server.v1_12_R1.NBTTagCompound");
            nms = craft.getMethod("asNMSCopy", ItemStack.class);
            bukkit = craft.getMethod("asBukkitCopy", Class.forName("net.minecraft.server.v1_12_R1.ItemStack"));
            Class<?> nmsItem = Class.forName("net.minecraft.server.v1_12_R1.ItemStack");
            has = nmsItem.getMethod("hasTag");
            get = nmsItem.getMethod("getTag");
            set = nmsItem.getMethod("setTag", nbt);
            contains = nbt.getMethod("hasKey", String.class);
            read = nbt.getMethod("getString", String.class);
            write = nbt.getMethod("setString", String.class, String.class);
            delete = nbt.getMethod("remove", String.class);
            ok = true;
        } catch (Exception failure) {
            plugin.getLogger().warning("无法初始化 1.12.2 NBT 适配器，物品自定义标签不可用: " + failure.getMessage());
        }
        craftItemStack = craft;
        nbtCompound = nbt;
        asNmsCopy = nms;
        asBukkitCopy = bukkit;
        hasTag = has;
        getTag = get;
        setTag = set;
        hasKey = contains;
        getString = read;
        setString = write;
        remove = delete;
        available = ok;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Optional<String> getString(ItemStack item, String key) {
        if (!available || item == null || key == null) return Optional.empty();
        try {
            Object nms = asNmsCopy.invoke(null, item);
            if (!(Boolean) hasTag.invoke(nms)) return Optional.empty();
            Object tag = getTag.invoke(nms);
            if (!(Boolean) hasKey.invoke(tag, fullKey(key))) return Optional.empty();
            return Optional.of(String.valueOf(getString.invoke(tag, fullKey(key))));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    @Override
    public ItemStack setString(ItemStack item, String key, String value) {
        if (!available || item == null || key == null || value == null) return item;
        try {
            Object nms = asNmsCopy.invoke(null, item);
            Object tag = Boolean.TRUE.equals(hasTag.invoke(nms)) ? getTag.invoke(nms) : nbtCompound.newInstance();
            setString.invoke(tag, fullKey(key), value);
            setTag.invoke(nms, tag);
            return (ItemStack) asBukkitCopy.invoke(null, nms);
        } catch (Exception failure) {
            return item;
        }
    }

    @Override
    public ItemStack remove(ItemStack item, String key) {
        if (!available || item == null || key == null) return item;
        try {
            Object nms = asNmsCopy.invoke(null, item);
            if (!(Boolean) hasTag.invoke(nms)) return item;
            Object tag = getTag.invoke(nms);
            remove.invoke(tag, fullKey(key));
            setTag.invoke(nms, tag);
            return (ItemStack) asBukkitCopy.invoke(null, nms);
        } catch (Exception failure) {
            return item;
        }
    }

    private String fullKey(String key) {
        return "xycore." + key.replace(' ', '_');
    }
}
