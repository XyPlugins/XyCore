# AI 使用记录

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
