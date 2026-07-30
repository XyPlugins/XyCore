# XyCore 更新说明

## 0.3.10 - 2026-07-30

- 新增完整物品库ID匹配API `ItemLibraryService#matches`。
- 新增原版、XyItems与MythicMobs现有物品的统一匹配规则。
- 原版材料匹配排除已带自定义物品身份的堆叠，防止锻造错误扣除同材质RPG物品。
- MythicMobs 4.11通过缓存后的反射入口读取 `MYTHIC_TYPE`。
- 为XyForgeCrafting 1.0.1和XySoulSpace 1.1.1提供共同的材料身份底座。

旧版本记录继续保留在README的“版本记录”章节。
