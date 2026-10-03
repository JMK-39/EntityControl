# EntityControl 多版本迁移实施计划

Spec: ../specs/2026-10-03-multiversion-migration-design.md

全局约束：保留 Forge 行为和既有修改；1.21 原生组件；26 节点预留；不改核心；不启动 Forge 客户端；只做本地提交。

## Task 1: Forge 构建迁移

保存 .gradle/migration/preexisting.patch 和 entitycontrol-baseline.jar；建立独立迁移分支。以 ItemControl 和核心为模板更新 wrapper、Stonecutter、MDG、节点属性、依赖解析与检查，删除旧根 build.gradle。保留原有 mods.toml 和所有源代码。

Interfaces: 输出 Forge release JAR、共用检查与本地优先依赖脚本，供 Task 2 使用。

Verification: gradlew :1.20.1-forge:build --offline；产物检查；对比旧 JAR 类指令及资源。
Expected: 构建通过；class 65、JAVA_17、refmap、MixinConfigs、原 Core 版本范围保留；既有类无玩法指令差异。

## Task 2: NeoForge 1.21.1

启用 1.21.1 节点，添加正确 Jade/Curios 依赖。以实际编译和 Mixin 检查定位 API 差异，按 Stonecutter 条件保留 Forge 实现。物品装备解析/写入使用原生组件，实体 NBT 不转换成物品组件。新增业务回归验证先失败后修复，验证动态注册表组件及训练假人装备。

Interfaces: 使用 Task 1 共用节点脚本；输出 NeoForge release JAR 与测试结果。

Verification: gradlew buildAll --offline；原生组件、生成与重置、属性及假人回归；现有 PCL2 客户端启动与日志检查。
Expected: 两个节点构建及全部检查通过，新版无 Mixin/核心链接错误。

## Task 3: 验收及本地提交

更新自动/手动工作流为 buildAll 与新命名；预留 26.1.2 属性及注释节点。运行最终构建、产物和 Forge 基线检查，整体代码审查，修复关键问题并复验，记录 runtime 验证的范围与限制。仅提交 EntityControl，再处理 ContentStudio。

Verification: git diff --check；buildAll；Verify-ReleaseJar.ps1；迁移报告；git status。
Expected: 每项验收如实记录，分支本地提交，工作区干净；26 不声称可用。
