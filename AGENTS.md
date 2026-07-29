# XyCore AI / Codex 维护手册

本文件给后续维护 XyCore 的 AI 编码助手使用。修改代码前请先阅读 `README.md`、`AI_CHANGELOG.md`、目标模块默认配置，以及相关源码类。

## 项目边界

- 项目名：XyCore
- 目标环境：Paper / Spigot 1.12.2
- Java 目标：Java 8
- 构建命令：`gradlew.bat clean build`
- 主类：`org.xyplugin.xycore.XyCorePlugin`
- 模块基类：`org.xyplugin.xycore.internal.module.AbstractCoreModule`
- 模块开关：`src/main/resources/config.yml -> modules`
- 模块默认配置：`src/main/resources/modules/`

不要把现代 Minecraft API、Adventure RGB 文本、Folia 调度模型或 MythicMobs 5.x API 混进当前 Legacy 代码路径。

## 构建规则

- 保持 `sourceCompatibility` 和 `targetCompatibility` 为 Java 8。
- 运行时软依赖通过 `plugin.yml softdepend` 声明。
- 第三方插件 API 尽量用反射桥接，避免把 MythicMobs、HolographicDisplays、Multiverse-Core 等服务端插件打进产物。
- `gradlew.bat clean build` 必须通过后再交付 jar。
- 不提交 `build/`、`.gradle/`、日志或服务端运行数据。

## 文档规则

涉及用户可见行为时，同步更新：

- `README.md`：服主使用说明和配置示例。
- `AI_CHANGELOG.md`：给后续 AI 的实现意图、兼容原因和坑点。
- 对应模块默认配置：保留中文注释，旧服缺少新节点时必须有代码默认值。

Bukkit 1.12 的 `YamlConfiguration#save` 会丢失注释，不要为了写回配置而覆盖用户已有模块 yml。

## MythicSpawnerHologram

核心类：

- `org.xyplugin.xycore.internal.hologram.MythicSpawnerHologramModule`
- `org.xyplugin.xycore.internal.hologram.MythicMobsSpawnerBridge`
- `org.xyplugin.xycore.internal.hologram.HolographicDisplaysBridge`

关键约定：

- 运行时需要 MythicMobs 4.11.x 和 HolographicDisplays 2.x。
- HolographicDisplays 2.x API 包名是 `com.gmail.filoghost.holographicdisplays.api.*`。
- 每个全息缓存 TextLine 句柄，只更新发生变化的行，避免每秒删除重建。
- MythicMobs 与 HolographicDisplays 操作必须在 Bukkit 主线程。
- `/mm reload` 后延迟重建全息，避免使用旧刷新点引用。

龙核血条兼容：

- 龙核配置常见写法是 `match` 加 `contains: true`，会匹配 HolographicDisplays 文字盔甲架的 custom name。
- `display.dragoncore-healthbar-guard: true` 会把 `{mob_name}` 内部用零宽字符拆开，视觉不变，但原始字符串不再连续包含怪物名。
- `display.armorstand-marker-guard: true` 会尝试把 HD 文字盔甲架设为 Marker，减少被准星或客户端血条选中。
- `display.hide-while-mob-alive: true` 会在刷新点怪物存活时隐藏整组全息，死亡进入 Warmup/Cooldown 后再显示。

- `{respawn}` 在无存活怪物时必须优先读取 Warmup 剩余时间，其次读取 Cooldown 剩余时间，避免使用 `Cooldown: 1`、`Warmup: 60` 时全息显示为即将刷新。

世界名：

- `display.world-name-mode: alias` 时，`{world}` 优先显示 Multiverse-Core alias。
- `{world_alias}` 固定尝试读取 MV alias。
- `{world_raw}` 固定显示 Bukkit 原世界名。
- Multiverse-Core 通过反射读取，不添加编译依赖；`plugin.yml` 保留 softdepend。

## ItemNameDisplay

核心类：`org.xyplugin.xycore.internal.itemdisplay.ItemNameDisplayModule`

约定：

- 直接使用掉落物实体自定义名称，不创建盔甲架。
- 不创建周期扫描任务。
- `ItemSpawnEvent` 处理新掉落物，`ChunkLoadEvent` 处理加载区块里的已有掉落物。
- 使用 Bukkit Metadata 保存原名称和 XyCore 应用状态。
- 如果实体名称被其他插件再次修改，XyCore 必须放弃管理，避免重载时覆盖外部插件行为。

## ServerRules

核心类：`org.xyplugin.xycore.internal.rules.ServerRulesModule`

约定：

- 死亡不掉落、PVP 保护、永远白天、不下雨都属于 `server-rules` 模块。
- 配置采用一个 yml 下多个世界列表，不要重新拆成多个模块配置。
- 世界列表支持 `'*'`，YAML 中必须加引号。
- AlwaysDay 使用阈值重置时间，避免每秒硬拉天空导致材质包天空抖动。

## WorldProtect / WorldPermission

核心类：

- `org.xyplugin.xycore.internal.protect.WorldProtectModule`
- `org.xyplugin.xycore.internal.permission.WorldPermissionModule`

约定：

- 模块入口必须先判断 `!isEnabled()` 并直接放行，避免模块关闭后残留拦截。
- 世界键名按小写缓存，运行时用 Bukkit 原世界名匹配。
- 权限附件、提示冷却和默认规则都要能在 `/xycore reload` 后重建或清理。

## 交付检查

交付前至少确认：

- `gradlew.bat clean build` 成功。
- `src/main/resources/plugin.yml` 版本号正确。
- 新配置缺失时有安全默认值。
- README、AI_CHANGELOG、默认配置注释已同步。
- jar 中没有不应打入的服务端插件 API 或运行数据。
