package org.xyplugin.xycore.api.attribute;

/** AttributePlus PAPI 变量的读取模式。 */
public enum AttributeValueMode {
    /** 读取 %ap_x:max% 的最大值。 */
    MAX,
    /** 读取 %ap_x:min% 的最小值。 */
    MIN,
    /** 读取 %ap_x:random% 的一次随机值。 */
    RANDOM,
    /** 读取默认的 "min - max" 文本。 */
    RAW
}
