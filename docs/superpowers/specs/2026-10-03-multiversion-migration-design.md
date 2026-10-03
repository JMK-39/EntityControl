# EntityControl 多版本迁移设计

沿用用户已批准并在 ItemControl 验证的 KineticCore 架构：Gradle 9.8.0、Stonecutter 0.9.8、ModDevGradle 2.0.148，共用源码、按版本编译。先迁移 Forge 1.20.1 构建，再移植 NeoForge 1.21.1；26.1.2 仅预留，核心仍由 Claude 修复，不用测试版作为验收依据。

保留实体生成、属性与装备修改、实体重置、破坏生成、训练假人、Curios 和 Jade 功能。Forge 分支的玩法、配置和语言保持基线一致。1.20.1 物品用 NBT，1.21.1 物品用原生数据组件，不引入旧写法转换；实体及世界持久化继续使用其原生 NBT API。

依赖优先本地库，KineticCore 必须匹配加载器和 MC 版本。JAR 命名为 entitycontrol-<loader>-<minecraft>-<version>.jar，输出 D:/NEWMODS；buildAll 构建所有启用节点。Java 21，Forge Mixin 保持 JAVA_17，NeoForge 使用 JAVA_21。

验收包括离线构建、架构检查、Mixin 目标检查、核心引用检查、产物内容检查、Forge 基线指令及资源对比。1.20.1 不做游戏启动测试；1.21.1 使用现有 PCL2 和已有版本，不改内存、不单独下载客户端。各仓库单独完成、本地提交，不推送、不发布。保存原有六处修改后在当前工作目录创建迁移分支，不改动 KineticCore。
