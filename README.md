# XyCore 0.3.0

XyCore 是一款面向 `Paper 1.12.2 build 1620` 的 RPG/MMO 服务器底层核心插件。

它的定位不是把所有玩法都塞进 Core，而是给后续的 `XyJob`、`XyForge`、符文、职业、锻造、活动等插件提供统一、稳定、可复用的底层服务。

当前设计为只安装在 RPG 主服；Velocity 登录服只做中转，不需要安装 XyCore。

## Core 负责什么

- 玩家数据会话与模块数据容器，供 XyJob、XyForge 和后续 Xy 系列插件使用。
- YML、SQLite、MySQL/MariaDB 三种数据存储。
- JDBC 存储使用 HikariCP 数据库连接池。
- Vault 经济接口桥接。
- PlaceholderAPI 正式新版变量扩展与动态变量命名空间。
- AttributePlus 属性读取桥接与属性源直接写入/移除桥接。
- DragonCore GUI、按键、变量、客户端数据包桥接。
- 原版物品与 MythicMobs 物品库软桥接。
- 可开关的内置模块系统。

职业等级、职业经验、锻造配方、技能、专精徽章、符文雕刻等具体玩法，建议交给独立插件实现。XyCore 只提供底座、接口和公共能力。

## 快速使用

1. 将 `XyCore-0.3.0.jar` 放入 `plugins` 文件夹。
2. 启动服务器生成 `plugins/XyCore/config.yml`。
3. 在 `config.yml` 中开启需要的模块。
4. 使用 `/xycore reload` 或重启服务器。
5. 开启后的模块配置会生成在 `plugins/XyCore/modules/`。

第一次加载时，默认只生成主配置 `config.yml`。模块配置不会提前生成，只有模块开关打开后才会创建。

## 模块系统

主配置位于：

```text
plugins/XyCore/config.yml
```

模块总开关示例：

```yaml
modules:
  # LoreCommandBind：Lore 识别指令模块。
  # 作用：识别物品 Lore，触发服务端预设命令、冷却、消耗、条件和临时属性效果。
  # 开启后生成：plugins/XyCore/modules/LoreCommandBind.yml
  lore-command-bind: false

  # WorldProtect：世界保护模块。
  # 作用：按世界禁止方块破坏/放置、桶、展示框、画、盔甲架等行为。
  # 开启后生成：plugins/XyCore/modules/world-protect.yml
  world-protect: false

  # WorldPermission：世界权限管控模块。
  # 作用：按世界临时给予或收回权限，适合只在家园世界开放 essentials.tpa / essentials.sethome。
  # 开启后生成：plugins/XyCore/modules/world-permission.yml
  world-permission: false

  # Kit：礼包/新手包模块预留开关。
  # 当前 0.3.0 暂未实现，开启也不会生成模块配置。
  kit: false

  # Nickname：昵称模块预留开关。
  # 当前 0.3.0 暂未实现，后续可用于玩家昵称、称号显示等功能。
  nickname: false

  # Script：脚本模块预留开关。
  # 当前 0.3.0 暂未实现，后续可用于轻量脚本、事件监听、命令注入等扩展。
  script: false
```

模块配置生成规则：

- 模块为 `false` 时，不生成对应模块配置。
- 模块改为 `true` 后，使用 `/xycore reload` 或重启服务器，会生成对应模块配置。
- 已开启过的模块再次改为 `false` 后，执行 `/xycore reload` 会卸载模块并删除对应模块配置。
- 服务器正常关闭时只卸载模块，不会删除模块配置。

当前内置模块配置：

```text
plugins/XyCore/modules/LoreCommandBind.yml
plugins/XyCore/modules/world-protect.yml
plugins/XyCore/modules/world-permission.yml
```

可使用以下命令查看模块状态：

```text
/xycore modules
```

## LoreCommandBind 模块

`LoreCommandBind` 是 0.3.0 重写后的 Lore 识别指令模块。

通过物品 Lore 匹配规则，触发玩家指令、后台指令、冷却、消耗物品、条件判断等效果。

同时它修正了旧版本 LoreCommand 无法使用的问题，并加强了安全限制：

- 指令只从服务端配置中读取。
- 默认禁止临时 OP 执行。
- 默认过滤危险后台命令。
- 支持左键、右键、空气、方块等触发方式。
- 支持 PlaceholderAPI 条件。
- 支持通过 AttributePlus 写入临时属性源。

示例：

```yaml
rules:
  forge_menu:
    enabled: true
    match:
      lore: '&e右键打开锻造台'
      mode: CONTAINS
    actions:
      - RIGHT_CLICK_AIR
      - RIGHT_CLICK_BLOCK
    execute:
      executor: PLAYER
      commands:
        - 'xyforge open {player}'
      cooldown-ms: 500
      consume: false
```

支持的指令前缀：

```text
player:command
console:command
op:command
[player] command
[console] command
[op] command
```

`op:` 和 `[op]` 只有在配置中打开 `security.allow-temporary-op: true` 后才允许使用。正式服建议保持关闭，除非你完全信任配置文件里的所有规则。

旧版 `plugins/XyCore/LoreCommandBind.yml` 会在首次生成新配置时迁移到：

```text
plugins/XyCore/modules/LoreCommandBind.yml
```

为了方便从 FishLore 迁移，如果配置里没有 `rules:` 或 `binds:`，也可以识别根节点规则，例如 `example1:`。

## WorldProtect 模块

`WorldProtect` 是世界保护模块

它可以按世界阻止：

- 方块破坏。
- 方块放置。
- 桶收回流体或生物。
- 桶放出流体或生物。
- 画、展示框等悬挂物放置。
- 画、展示框等悬挂物破坏。
- 展示框交互。
- 盔甲架放置、破坏、交互。
- 可选的耕地踩踏保护。

示例：

```yaml
worlds:
  world:
    enabled: true
    deny:
      block-break: true
      block-place: true
      bucket-fill: true
      bucket-empty: true
      hanging-break: true
      hanging-place: true
      item-frame-interact: true
      armor-stand-break: true
      armor-stand-place: true
      armor-stand-interact: true
```

全局绕过权限：

```text
xycore.worldprotect.bypass
```

每个世界也可以单独设置 `bypass-permission`。

默认情况下，WorldProtect 会使用主配置中的全局前缀：

```yaml
messages:
  prefix: '&7[&bXyCore&7]&r'
```

例如玩家在禁止破坏的世界中破坏方块时，默认提示为：

```text
[XyCore]该世界无法破坏方块
```

提示内容和前缀都可以在配置中自定义。

## WorldPermission 模块

`WorldPermission` 是世界权限管控模块。

它的核心思路不是拦截 `/tpa`、`/sethome` 这种指令，而是按世界临时给予玩家权限。这样可以和 EssentialsX、LuckPerms 等权限体系配合得更干净。

推荐用法：

```text
不要给玩家全局 essentials.tpa。
只在家园世界通过 WorldPermission 临时发放 essentials.tpa。
副本世界不发放这个权限，玩家自然无法使用 /tpa。
```

示例：

```yaml
worlds:
  home_world:
    enabled: true
    grant-permissions:
      - essentials.tpa
      - essentials.tpahere
      - essentials.tpaccept
      - essentials.tpdeny
      - essentials.sethome
      - essentials.home

  dungeon_world:
    enabled: true
    grant-permissions: []
```

玩家离开家园世界后，XyCore 会移除本模块发放的临时权限附件。

模块也支持 `deny-permissions`，用于特殊场景下把某个权限临时设置为 false。不过更推荐的结构依然是：不要全局给权限，只在允许的世界发放权限。

## 软依赖说明

以下插件都是软依赖：

```text
Vault
PlaceholderAPI
MythicMobs
AttributePlus
DragonCore
```

没有安装这些插件时，XyCore 不会因此无法启动。对应桥接会进入安全的不可用状态，调用时返回空实现或失败结果。

也就是说：

- 没有 PlaceholderAPI，Core 仍可启动。
- 没有 DragonCore，Core 仍可启动。
- 没有 Vault 或经济插件，Core 仍可启动，但经济功能不可用。
- 没有 AttributePlus，Core 仍可启动，但属性桥接不可用。
- 没有 MythicMobs，Core 仍可启动，但只能使用原版物品 Provider。

## 命令

```text
/xycore status
/xycore modules
/xycore reload
/xycore save [all|player]
/xycore info <player>
```

说明：

- `/xycore status`：查看 Core 基础状态。
- `/xycore modules`：查看内置模块状态。
- `/xycore reload`：重载 Core 配置和支持重载的 Xy 系列扩展。
- `/xycore save all`：保存所有在线玩家数据。
- `/xycore save <player>`：保存指定玩家数据。
- `/xycore info <player>`：查看指定玩家的 Core 数据摘要。

`/xycore reload` 不等于 Bukkit `/reload`，它只处理 XyCore 和已注册的可重载服务。

## 版本记录

### 0.3.0

- 新增内置模块管理器，支持模块启用、重载、关闭生命周期。
- 模块配置统一移动到 `plugins/XyCore/modules/`。
- 模块配置只在模块开启时生成。
- 模块关闭并执行 `/xycore reload` 时，会删除对应模块配置。
- 重写 LoreCommandBind，修复 0.2.x 中 LoreCommand 无法正常使用的问题。
- LoreCommandBind 新增左右键触发、冷却、消耗、PlaceholderAPI 条件、AttributePlus 临时属性源效果。
- LoreCommandBind 加强指令执行安全限制。
- 新增 WorldProtect 世界保护模块。
- 新增 WorldPermission 世界权限管控模块。
- 新增 `/xycore modules` 命令。

### 0.2.0

- 新增 DragonCore GUI、按键、变量、客户端数据包桥接。
- 新增 AttributePlus 直接属性源写入与移除桥接。
- 新增正式新版 PlaceholderAPI Expansion。
- 新增 HikariCP 数据库连接池。
- 新增第一版 LoreCommandBind。

### 0.1.0

- 新增玩家数据会话。
- 新增模块数据存储容器。
- 新增 YML、SQLite、MySQL/MariaDB 存储抽象。
- 新增 Vault 经济桥接。
- 新增基础物品接口。
- 新增 MythicMobs 软依赖物品桥接。

## 构建

源码内已经包含 Paper 1.12.2 与 HikariCP 等编译期所需本地依赖。

构建命令：

```text
gradlew.bat clean build
```

输出文件：

```text
build/libs/XyCore-0.3.0.jar
```
