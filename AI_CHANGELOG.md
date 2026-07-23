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

- 不要把 DeathKeep、PVP、白天、天气逻辑塞回 WorldProtect；它们是独立模块。
- 用户偏好配置直观，模块配置键名使用 `DeathKeepModule:`、`PvpProtectModule:`、`AlwaysDayModule:`、`NoRainModule:` 这种直接列表形式。
- 如果改模块配置路径或键名，需要同步 README、AI_CHANGELOG 和默认配置文件。