# XyCore 更新说明

## 0.3.11 - 2026-08-02

- 新增 `XyCoreApi#getMessagePrefix()`，作为 Xy 系列插件玩家聊天消息的统一前缀入口。
- 统一前缀直接读取 `config.yml -> messages.prefix`，保留配置中的颜色符号和空格，不在代码里自动增删。
- WorldProtect 内部 `{core_prefix}` 改为通过同一 API 读取，避免 Core 内外前缀来源不一致。
- 明确约定：后续 Xy 系列新插件检测到 XyCore 时，玩家聊天消息必须优先使用 XyCore 前缀；独立插件在没有 XyCore 时保留自己的前缀兜底。
- 控制台日志不纳入统一玩家前缀，继续保留各插件自己的后台输出名称。

## 0.3.10 - 2026-07-30

- 新增完整物品库ID匹配API `ItemLibraryService#matches`。
- 新增原版、XyItems与MythicMobs现有物品的统一匹配规则。
- 原版材料匹配排除已带自定义物品身份的堆叠，防止锻造错误扣除同材质RPG物品。
- MythicMobs 4.11通过缓存后的反射入口读取 `MYTHIC_TYPE`。
- 为XyForgeCrafting 1.0.1和XySoulSpace 1.1.1提供共同的材料身份底座。

旧版本记录继续保留在README的“版本记录”章节。
