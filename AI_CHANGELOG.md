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
