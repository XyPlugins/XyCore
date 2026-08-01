# AI_CHANGELOG

这份文件给后续接手 XyCore 的 AI / Codex 使用，记录当前代码意图、配置约定和容易遗漏的兼容点。

## 项目定位

- 项目：XyCore
- 目标服务器：Paper 1.12.2 build 1620
- Java 目标版本：Java 8
- 构建命令：`gradlew.bat clean build`
- 主插件类：`org.xyplugin.xycore.XyCorePlugin`
- 内置模块基类：`org.xyplugin.xycore.internal.module.AbstractCoreModule`
- 模块开关位于：`src/main/resources/config.yml -> modules`
- 模块默认配置位于：`src/main/resources/modules/`

## v0.3.11 变更

### Xy 系列玩家消息统一前缀

- `XyCoreApi` 新增 `getMessagePrefix()`，作为全部 Xy 系列插件玩家聊天消息前缀的标准入口。
- 方法读取 `config.yml -> messages.prefix`，返回配置原值，不自动补空格或改颜色；空格也由服主在配置中决定。
- XyCore 自带 WorldProtect 的 `{core_prefix}` 也改为通过该 API 读取，确保 Core 内外统一。
- 后续开发新 Xy 插件时必须遵守：
  - 如果插件 `depend: [XyCore]`，玩家消息直接使用 `XyCore.get().getMessagePrefix()`。
  - 如果插件可以独立运行、只 `softdepend: [XyCore]`，检测到已启用 XyCore 时优先走 `getMessagePrefix()`，否则使用本插件自己的配置前缀。
  - 不要把控制台日志切到统一玩家前缀；后台日志保留各插件名方便排错。
- 本约定只针对发送给玩家或命令发送者的聊天消息，不改变称号聊天格式、DragonCore HUD文字、GUI标题和后台日志。

## v0.3.10 变更

### XyForgeCrafting物品匹配底座

- `ItemLibraryService` 新增 `matches(String namespacedId, ItemStack item)`。
- `ItemProvider` 新增默认 `matches`，默认通过稳定的 `identify` 结果比较提供器内部ID，避免破坏已有提供器。
- `VanillaItemProvider` 按1.12.2 `Material` 匹配；服务层在匹配 `minecraft:` 前先排除能被其他自定义提供器识别的物品。
- `MythicMobsItemProvider` 针对本服MythicMobs 4.11读取物品NBT的 `MYTHIC_TYPE`，相关构造器和Method只在桥接初始化时反射一次。
- 这套接口是XySoulSpace原子材料API和XyForgeCrafting背包扫描的共同规则，禁止各插件再按去色名称或Lore自行实现一套匹配。
- 目标仍固定为Paper/Spigot 1.12.2与Java 8，不添加现代Material或MythicMobs 5.x代码路径。

## v0.3.9 变更

### MythicMobs 刷新点粘贴命令

- 新增内置命令 `/xycore mms paste <刷新点ID>`，`/xy` 是 `/xycore` 的短别名。
- 该命令不依赖模块开关，只要求 MythicMobs 已启用且执行者是玩家。
- 实现类：`org.xyplugin.xycore.internal.mythic.MythicSpawnerCopyBridge`
- 通过反射调用 MythicMobs 4.11 的 `SpawnerManager#copySpawner(String, String, AbstractLocation)`，复制已加载刷新点到玩家脚下。
- 新刷新点 ID 由 XyCore 按源刷新点、世界和坐标自动生成；若重复则追加序号。
- 找不到源刷新点时返回“没有该刷新点位”。
- 数据仍由 MythicMobs 保存到自身 Spawners 配置，XyCore 不额外持久化刷新点。

### MythicSpawnerHologram 龙核血条兼容修复

- 背景：龙核怪物血条配置使用 `contains: true` 且 `match` 中包含怪物名，例如 `赤牙獠猪`。
- HolographicDisplays 文本行本质是带自定义名称的盔甲架；当全息第二行显示 `{mob_name}` 时，龙核会把这行文字识别为可显示血条的实体。
- 原先只在整行前加不可见字符不能解决 `contains`，因为原始文本仍连续包含完整怪物名。
- 当前实现中，`display.dragoncore-healthbar-guard: true` 会把 `{mob_name}` 内部插入零宽字符：
  - 视觉仍显示为完整怪物名。
  - 原始字符串不再连续包含 `match` 中的怪物名，从而避开龙核 `contains` 命中。
- `display.armorstand-marker-guard: true` 会在创建 HD 全息后扫描附近对应文字盔甲架并尝试执行：
  - `ArmorStand#setMarker(true)`
  - `ArmorStand#setVisible(false)`
  - `ArmorStand#setGravity(false)`
- `display.hide-while-mob-alive: true` 会在刷新点怪物存活时删除整组全息，怪物死亡进入 Warmup/Cooldown 后再重建倒计时全息。
- `display.world-name-mode: alias` 让 `{world}` 优先显示 Multiverse-Core 世界 alias；未安装 MV 或 alias 为空时回退 Bukkit 原世界名。
- 新增变量：
  - `{world_alias}`：固定尝试读取 Multiverse-Core alias。
  - `{world_raw}`：固定显示 Bukkit 原世界名。
- `plugin.yml` 新增 `Multiverse-Core` softdepend，用于加载顺序；运行时仍通过反射读取，不引入编译依赖。
- `{respawn}` 在无存活怪物时优先显示 `isOnWarmup/getRemainingWarmupSeconds`，其次显示 `isOnCooldown/getRemainingCooldownSeconds`，用于配合 `Cooldown: 1`、`Warmup: 60` 这类近似死亡后复活的配置。
- 旧服已生成的 `modules/mythic-spawner-hologram.yml` 不会被自动覆盖，但上述新配置都有代码默认值，缺少节点时仍按新行为运行。

## v0.3.6 变更

### ItemNameDisplay 模块

- 新模块 id：`item-name-display`
- 实现类：`org.xyplugin.xycore.internal.itemdisplay.ItemNameDisplayModule`
- 默认配置：`src/main/resources/modules/item-name-display.yml`
- 主开关：

```yaml
modules:
  item-name-display: false
```

### 行为约定

- 默认 `display.custom-name-only: true`，只显示 ItemStack 自带 Display 名称的 RPG/MM 物品。
- `display.custom-name-only: false` 时，普通物品优先读取 `material-names`，未配置时显示 1.12.2 Material ID。
- `display.overwrite-existing-entity-name: false` 是安全默认值，不与其他掉落物插件争夺实体名称。
- 支持 `{name}` 与 `{material}`，支持传统 `&` 颜色和格式代码。
- 不要宣称支持 `&#RRGGBB`；Minecraft 1.12.2 客户端协议没有 RGB 文字颜色。
- 模块关闭或重载时，只恢复仍由 XyCore 管理且未被其他插件再次修改的名称。

### 性能与实现约定

- 直接调用掉落物实体的 `setCustomName` 与 `setCustomNameVisible`，不要改成盔甲架全息。
- 不创建周期任务，不维护不断增长的掉落物 Map，不按玩家或掉落物循环扫描世界实体。
- `ItemSpawnEvent` 只处理新物品；`ChunkLoadEvent` 只处理该区块中的物品。
- 模块启用、重载和关闭时允许一次性遍历当前已加载实体，用于应用或恢复名称。
- 使用 Bukkit Metadata 保存原名称、原可见状态和 XyCore 应用的名称，实体销毁时由 Bukkit 一同释放。
- 如果发现实体名称已不等于 XyCore 上次应用的名称，说明其他插件后来接管，XyCore 必须放弃管理且不能在重载时覆盖。
- 本模块不依赖 MythicMobs、HolographicDisplays、HolographicExtension、PlaceholderAPI 或 ProtocolLib。

## v0.3.5 变更

### MythicSpawnerHologram 模块

- 新模块 id：`mythic-spawner-hologram`
- 实现类：`org.xyplugin.xycore.internal.hologram.MythicSpawnerHologramModule`
- MythicMobs 桥接：`org.xyplugin.xycore.internal.hologram.MythicMobsSpawnerBridge`
- HolographicDisplays 桥接：`org.xyplugin.xycore.internal.hologram.HolographicDisplaysBridge`
- 默认配置：`src/main/resources/modules/mythic-spawner-hologram.yml`
- 主开关：

```yaml
modules:
  mythic-spawner-hologram: false
```

### 依赖与兼容约定

- 运行时需要 MythicMobs 4.11.x 和旧版 HolographicDisplays 2.x API。
- 已核对用户实际使用的定制版：
  `MythicMobs-4.11.0-da8c22c1-1.12.2-eventfix-cnhelp-final.jar`。
- 已核对用户当前提供的 HolographicDisplays：`2.2.6`。
- HolographicDisplays 使用旧包名：
  `com.gmail.filoghost.holographicdisplays.api.*`。
- 模块不依赖 HolographicExtension、PlaceholderAPI 或 ProtocolLib。
- 两个桥接都缓存反射 Method；不要改回每次刷新都查找 Method 的实现。
- 不把 MythicMobs/HolographicDisplays API JAR 打进 XyCore，保持软依赖隔离。
- `plugin.yml` 必须保留 `HolographicDisplays` softdepend，以保证加载顺序。

### 生命周期与性能约定

- 模块启用时扫描全部刷新点并创建全息。
- 默认每 20 ticks 更新一次状态文字，每 100 ticks 对比新增、移动和删除刷新点。
- 监听 MythicMobSpawnEvent，用实际生成怪物的显示名刷新第二行。
- 监听 MythicMobDeathEvent，通过 ActiveMob#getSpawner 关联刷新点，只记录玩家击杀者。
- 监听 MythicReloadedEvent，延迟 2 ticks 重建全息和刷新点引用。
- 所有 MM/HD 操作都必须保留在 Bukkit 主线程。
- 每个全息持有独立 TextLine 句柄，只更新内容发生变化的行；不要每秒 clearLines/appendTextLine。
- 模块关闭时必须注销动态事件、取消两个定时任务并删除全部由 XyCore 创建的全息。
- `{killer}` 当前只保存在内存中，服务器重启后回到 `no-killer-text`，不要写入模块配置以免破坏中文注释。

### 默认显示与变量

```yaml
display:
  lines:
    - '&7当前世界: &f{world}'
    - '&c{mob_name}'
    - '&e复活倒计时: &f{respawn}'
    - '&7上一任击杀者: &f{killer}'
```

- 支持 `{world}`、`{spawner}`、`{mob_id}`、`{mob_name}`、`{respawn}`、`{killer}`。
- `{mob_name}` 的优先级：`name-overrides` > 最近一次实际生成怪物显示名 > MM 怪物 Display > 内部 ID。
- 刷新点仍有关联怪物时 `{respawn}` 显示 `alive-text`；没有怪物时优先显示 Warmup 剩余时间，其次显示 Cooldown 剩余时间；否则显示 `ready-text`。
- 默认最多 8 行，间隔最小 20 ticks，防止配置错误制造高频更新。

## v0.3.3 变更

### ServerRules 合并

- 将 0.3.2 新增的四个轻量规则模块合并为一个模块：
  - `death-keep`
  - `pvp-protect`
  - `always-day`
  - `no-rain`
- 新模块 id：`server-rules`
- 新模块类：`org.xyplugin.xycore.internal.rules.ServerRulesModule`
- 新配置文件：`src/main/resources/modules/server-rules.yml`
- 主配置只保留：

```yaml
modules:
  server-rules: false
```

### ServerRules 配置约定

- 用户希望这类基础规则集中在一个“服务器规则”配置里，不要生成四个零散 yml。
- `server-rules.yml` 内部使用四个根节点世界列表；列表为空表示该规则不生效：

```yaml
death-keep:
  - world

pvp-protect:
  - spawn

always-day:
  - world

no-rain:
  - world
```

- 世界列表支持 `'*'` 匹配所有已加载世界。
- 注意 YAML 中 `*` 必须加引号写成 `'*'`，否则会被解析为 alias。
- 这版暂时不要把 `enabled`、`worlds`、时间阈值等高级配置暴露给服主；用户明确希望先只写世界列表。

### 0.3.2 兼容读取

- `ModuleManager` 对 `server-rules` 做了 0.3.2 旧开关兼容：
  - 如果 `modules.server-rules` 不存在，但旧配置里 `death-keep`、`pvp-protect`、`always-day`、`no-rain` 任意一个为 true，则启用 `server-rules`。
- `ServerRulesModule` 会兼容读取旧配置文件：
  - `modules/DeathKeepModule.yml`
  - `modules/PvpProtectModule.yml`
  - `modules/AlwaysDayModule.yml`
  - `modules/NoRainModule.yml`
- 如果 `server-rules.yml` 对应根节点列表为空，则临时读取旧世界列表让规则继续生效。
- 不要自动写回 `server-rules.yml`，因为 Bukkit 1.12 的 `YamlConfiguration#save` 会丢失中文注释。
- 不要自动删除旧配置，交给服主确认后手动删除，避免升级时误删仍在参考的文件。

### AlwaysDay 天空变化修复

- 0.3.2 的 AlwaysDay 每 20 ticks 执行 `world.setTime(6000)`，在材质包天空下会显得一直变化/抽动。
- 0.3.3 改为阈值重置：
  - 默认每 200 ticks 检查一次。
  - 当世界时间达到 `11500` 后才设置回 `6000`。
  - 平时不频繁 setTime，因此天空不会一直被硬拉。
- 本次没有修改 `doDaylightCycle` gamerule，避免插件关闭后改变服务器持久世界规则。
  如果未来用户明确要求“绝对冻结天空”，再增加可选配置而不是默认启用。

## v0.3.2 变更

### WorldProtect 修复

- 修复 WorldProtect 模块关闭后仍可能残留拦截的问题。
- WorldProtect 每个事件入口都要先判断 `!isEnabled()` 并直接放行。
- `AbstractCoreModule.enable()` 在模块启用失败时会回滚 enabled 状态，并调用 `onDisable()` 清理，避免监听器残留。
- WorldProtect 默认提示冷却改为 `0ms`，每次拦截都提示。
- 已生成过配置的服务器，需要把 `plugins/XyCore/modules/world-protect.yml` 里的 `settings.message-cooldown-ms` 改成 `0`。

### 新模块配置约定

这些模块刻意采用“世界列表”配置，避免复杂的 `worlds.<world>.enabled` 结构。

```yaml
## 开启死亡不掉落的世界
DeathKeepModule:
  - world
  - dungeon_world
```

```yaml
## 禁止PVP的世界
PvpProtectModule:
  - world
  - dungeon_world
```

```yaml
## 永远白天的世界
AlwaysDayModule:
  - world
  - dungeon_world
```

```yaml
## 永远不下雨的世界
NoRainModule:
  - world
  - dungeon_world
```

列表支持 `*`，表示匹配所有已加载世界。

### 新模块实现

- `death-keep` -> `DeathKeepModule`
  - 监听 `PlayerDeathEvent`。
  - 匹配世界后调用 `setKeepInventory(true)`、`getDrops().clear()`、`setKeepLevel(true)`、`setDroppedExp(0)`，避免复制物品和经验掉落。
- `pvp-protect` -> `PvpProtectModule`
  - 监听 `EntityDamageByEntityEvent`。
  - 阻止玩家直接攻击玩家、投掷物来源玩家、驯服生物 owner 为玩家的 PVP 伤害。
- `always-day` -> `AlwaysDayModule`
  - 每 20 ticks 将匹配世界时间设为 `6000`。
  - 不修改 `doDaylightCycle` gamerule，避免永久改动服务器世界设置。
- `no-rain` -> `NoRainModule`
  - 监听 `WeatherChangeEvent` 和 `ThunderChangeEvent`，阻止雨天和雷暴开始。
  - 定时清理匹配世界的 storm/thunder 状态。

## 接手注意

- 不要把 DeathKeep、PVP、白天、天气逻辑塞回 WorldProtect；它们现在属于 `ServerRulesModule`。
- 用户偏好配置直观，但 0.3.3 起不要再为这些轻量规则生成四个独立 yml。
- ServerRules 的配置应保持“一个 yml，四个根节点世界列表”的结构。
- 如果改模块配置路径或键名，需要同步 README、AI_CHANGELOG 和默认配置文件。
