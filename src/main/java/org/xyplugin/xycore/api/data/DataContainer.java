package org.xyplugin.xycore.api.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件数据模块使用的轻量键值容器。
 *
 * <p>容器只保存可以被 YAML/JDBC 序列化的基础类型、列表和嵌套 Map。
 * XyJob、XyForge 等插件可以通过自己的数据对象把内容写入这里，Core
 * 负责选择实际存储后端。</p>
 */
public final class DataContainer {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public DataContainer() {
    }

    public DataContainer(Map<String, Object> source) {
        if (source != null) {
            values.putAll(source);
        }
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key, String defaultValue) {
        Object value = get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public int getInt(String key, int defaultValue) {
        Object value = get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        Object value = get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        Object value = get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = get(key);
        if (!(value instanceof List)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object entry : (List<Object>) value) {
            result.add(String.valueOf(entry));
        }
        return result;
    }

    public DataContainer set(String key, Object value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
        return this;
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
