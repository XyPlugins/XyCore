# AI 使用记录

## 0.3.18

本次维护由 AI 根据服主反馈“称号不佩戴后 AP 属性应该消失”辅助完成。

关键决策：

- 复查 XyTitle 后确认称号侧会通过 XyCore 删除 `xytitle:<uuid>` 属性源。
- 进一步检查 AttributePlus 3.3.3.x API，确认存在 `AttributeAPI.updateAttribute(LivingEntity)`。
- XyCore 的 AttributePlus 桥接在 `addSource` 与 `removeSource` 后主动调用 `updateAttribute`，让 AP 当前属性立即重新计算。
- 该刷新只发生在插件主动写入/删除属性源时，不增加周期任务或玩家扫描。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.17

本次维护由 AI 根据服主反馈“副武器放入副手后逻辑正常，但客户端残影仍会停留1-2秒”辅助完成。

关键决策：

- 判断残影来自 DragonCore `container_45` 与原版副手槽的客户端显示同步，不是服务端复制。
- OffhandLoreGuard 将单次下一 tick 同步改为可配置的短时间多次同步。
- 默认 `settings.sync-repeat-ticks: [1, 3, 6]`，只在触及副手槽、拖拽到副手槽或 F 键交换后触发，不影响普通背包点击。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.16

本次维护由 AI 根据服主反馈“测试期频繁更换 XyCore 会导致模块配置文件夹消失，需要反复重配”辅助完成。

关键决策：

- 确认根因是 `ModuleManager` 在模块配置为 `false` 时会调用 `deleteConfigFile()` 删除模块 yml。
- 改为禁用模块只卸载功能，不删除 `plugins/XyCore/modules/*.yml`。
- 保留 `AbstractCoreModule#deleteConfigFile()` 方法本身，作为未来显式清理命令或维护工具的备用能力，但不再自动调用。
- 更新默认配置注释和文档，避免后续 AI 误以为关闭模块应该删除配置。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.15

本次维护由 AI 根据服主反馈“合法副武器放入副手后，手上还会短暂显示一把，过一会才消失”辅助完成。

关键决策：

- 判断这是 DragonCore `container_45` 与原版副手槽同步产生的客户端残影，不是服务端真实复制。
- OffhandLoreGuard 改为只在触及副手槽、拖拽到副手槽或 F 键交换后，下一 tick 执行清理并主动 `updateInventory()`。
- 同时收窄监听范围，避免普通背包点击也触发额外同步。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.14

本次维护由 AI 根据服主反馈“DragonCore 脚本会提示但 container_45 仍会放入物品”辅助完成。

关键决策：

- 确认 `container_45` 属于原版副手容器槽，DragonCore SlotConfig 脚本无法可靠取消服务端背包移动。
- 新增 `OffhandLoreGuard` 模块在 Bukkit 服务端层拦截副手点击、拖拽与 F 键交换。
- 对 DragonCore 客户端已经显示放入的情况，模块在下一 tick 检查副手并把非法物品退回背包；背包满时按配置掉落在玩家脚下。
- 默认匹配 `&7类型: &f副武器`，用于阻止墨魄等非副武器获得副手属性。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.13

本次维护由 AI 根据服主确认的“MM 怪物掉落最好通过 XyCore 通用物品库桥接”方案辅助完成。

关键决策：

- 桥接放在 XyCore，而不是 XyItems，避免后续每个物品插件都单独接 MythicMobs。
- MythicMobs 掉落表使用 `provider:item` 格式，例如 `xyitems:chiyamopo`。
- XyCore 只负责把 MM 自定义掉落转发到 `ItemLibraryService#create`，具体物品仍由对应 provider 生成。
- 使用 compile-only stub 编译 MythicMobs 4.11 相关类，最终 JAR 不打入 MythicMobs 类。
- 未安装 MythicMobs 或缺少 `MythicDropLoadEvent` 时桥接自动跳过，不影响 Core 启动。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。

## 0.3.12

本次维护由 AI 根据服主最终确认的“前端玩家玩法提示统一、后台管理提示保留原插件名”规则辅助完成。

关键决策：

- 明确玩家玩法结果才使用 XyCore `messages.prefix`，例如“你获得了物品”“锻造成功”“你已开启杀戮”“获得称号”等。
- `/help`、权限不足、参数错误、reload、list/status/info 与后台日志保留本插件前缀，便于管理员查错。
- 修正此前粗暴按 `Player` 判断前缀的方向，避免玩家执行管理命令时也显示系统提示。
- XyCore 启动后输出检测到的统一玩家前缀附属插件，作为后台摘要，不影响运行性能。

验证记录：

- 已执行受影响插件 `compileJava` 编译探针，XyCore、XyItems、XyForgeCrafting、XyTitle、XySoulSpace、XyBattleHud、XyKillAura、XyChemdahShow、XyMythicItemGui 均通过。

## 0.3.11

本次维护由AI根据服主确认的“Xy系列玩家聊天前缀统一由XyCore管理”需求辅助完成。

关键决策：

- 新增 `XyCoreApi#getMessagePrefix()`，统一读取 `config.yml -> messages.prefix`。
- 该方法返回配置原值，不自动补空格；服主可通过配置决定前缀后是否有空格。
- 后续所有 Xy 系列插件，只要检测到 XyCore，就应优先使用该 API 作为玩家聊天消息前缀。
- 可独立运行的 Xy 插件必须保留本插件自有前缀兜底，避免未安装 XyCore 时无法给玩家发送正常提示。
- 控制台日志和后台输出继续保留各插件名，例如 `[XyTitle]` 或 `[XyForgeCrafting]`，不走统一玩家前缀。

验证记录：

- 本次构建验证随各插件统一前缀接入一起执行。

## 0.3.10

本次维护由AI在服主确认的XyForgeCrafting架构下辅助完成，修改范围仅限统一物品匹配API及其文档、版本和构建验证。

关键决策：

- 继续只支持Java 8与Paper/Spigot 1.12.2。
- 使用完整 `provider:item` ID和隐藏身份标签匹配，不使用名称或Lore。
- MythicMobs适配锁定服务器使用的4.11结构，不引入5.x依赖。
- 原版Material匹配前排除可被自定义提供器识别的物品，避免材料相同造成误扣。
- 扩展 `ItemProvider` 时采用默认方法，降低已有Xy系列插件升级Core后的二进制兼容风险。

验证记录：

- `gradlew.bat clean build --no-daemon` 成功。
- 新增测试验证原版铁锭匹配，并确认被自定义提供器识别的同材质物品不会命中原版ID。
- 已核对 `XyCore-0.3.10.jar` 的plugin.yml版本和JAR内容。
