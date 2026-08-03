# XyCore 更新说明

## 0.3.18 - 2026-08-04

- AttributePlus 属性源写入或删除后主动调用 `updateAttribute`，让 AP 立即重新计算玩家当前属性。
- 修复 XyTitle 取消佩戴称号并清理 `xytitle:<uuid>` source 后，AP 属性可能仍短暂保留的问题。
- 保持 AttributePlus 仍为软依赖；找不到兼容 `updateAttribute` 方法时会跳过主动刷新，不影响 Core 启动。

## 0.3.17 - 2026-08-04

- 优化 OffhandLoreGuard 对 DragonCore `container_45` 的客户端残影同步。
- 副手槽点击、拖拽或 F 键交换后，默认在 1、3、6 tick 各同步一次背包，进一步缩短合法副武器放入后的残影时间。
- 新增 `settings.sync-repeat-ticks` 配置，可按实服表现调整同步次数。

## 0.3.16 - 2026-08-04

- 调整模块配置保留策略：模块开关改为 `false` 后，只卸载模块，不再自动删除 `plugins/XyCore/modules/*.yml`。
- 解决测试期频繁替换 XyCore JAR 或临时关闭模块后，需要反复重新配置模块文件的问题。
- 更新默认 `config.yml`、README 和 AI 维护记录，明确模块配置需要服主手动删除才会清理。

## 0.3.15 - 2026-08-04

- 修复合法副武器放入 DragonCore `container_45` 后，客户端手上短暂显示一把残影武器的问题。
- OffhandLoreGuard 现在只在触及副手槽、拖拽到副手槽或 F 键交换后执行下一 tick 同步，避免对普通背包点击做额外刷新。

## 0.3.14 - 2026-08-03

- 新增 `OffhandLoreGuard` 副手槽Lore保护模块，可限制原版45号副手槽只能放入带指定Lore的副武器。
- 模块会拦截副手槽点击、拖拽、F键交换，并在 DragonCore `container_45` 已经放入时下一 tick 自动退回非法物品。
- 默认配置要求副手物品包含 `&7类型: &f副武器`，用于防止墨魄等物品放进副手后获得属性。
- 新增默认配置 `modules/offhand-lore-guard.yml`，提示走 XyCore 玩家前缀。

## 0.3.13 - 2026-08-03

- 新增 MythicMobs 掉落表完整物品ID桥接，MM `Drops` 可直接引用已注册到 XyCore 物品库的 `provider:item`。
- 默认支持 `xyitems:<物品ID>`，例如 `- xyitems:chiyamopo 1 0.05`，实际物品由 XyItems 正式 provider 生成，保留隐藏身份NBT。
- 桥接放在 XyCore，未来其他插件只要注册 ItemProvider，也能被 MythicMobs 掉落表引用。
- 新增配置 `integrations.mythicmobs-drop-bridge.enabled` 和 `providers` 白名单，未安装或版本不兼容的 MythicMobs 不影响 Core 启动。

## 0.3.12 - 2026-08-02

- 最终确认并固化 Xy 系列前缀语义：玩家玩法结果走 XyCore `messages.prefix`，管理/帮助/报错/后台日志保留各插件自己的前缀。
- `/xycore help/status/reload/modules/save/info/mms` 等管理反馈固定显示 XyCore 管理前缀，不会在服主把玩家前缀改成“系统”后混入系统提示。
- 启动后延迟检测 XyItems、XyForgeCrafting、XyChemdahShow 等附属插件，并在后台输出“玩家提示已统一输出 XyCore 前缀”的摘要。
- 更新维护手册，要求后续新插件新增聊天提示时按语义分流，而不是简单按发送者是否为玩家判断。

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
