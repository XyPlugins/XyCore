# AI 使用记录

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
