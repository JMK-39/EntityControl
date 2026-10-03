# EntityControl 多版本迁移验收（2026-10-03）

启用 Forge 1.20.1、NeoForge 1.21.1；26.1.2 仅保留节点属性和注释声明，核心测试版由 Claude 继续修复，不启用、不构建、不验收。

## 改动

Gradle 9.8.0、Stonecutter 0.9.8、ModDevGradle 2.0.148、Java 21 共用节点架构；本地库优先、按 loader/MC 匹配 KineticCore、新发布命名、两版本 buildAll 和 CI。保留迁移前六个文件的既有修改，原始补丁存于 .gradle/migration/preexisting.patch。

1.20.1 保留物品 NBT；1.21.1 使用原生组件解析、编辑、预览与装备应用，不转换旧物品 NBT。实体与世界存档仍用其原生 NBT。适配 Holder、SavedData、同步实体数据、伤害及渲染签名、Curios 9 预设读写。假人类别适配原生附魔实体谓词和亡灵药水规则。

## 验证

- `gradlew buildAll :1.21.1-neoforge:runtimeValidationJar --offline --console=plain`：退出 0，24 个任务，两个节点通过。架构检查与核心引用检查无问题；Forge 5 个 Mixin、NeoForge 6 个 Mixin 目标检查均为 0 问题。
- 两个发布 JAR 的全部类均为 major 65；Forge 184 类、NeoForge 188 类；两个 Mixin 配置，Forge JAVA_17、refmap、MixinConfigs 通过；NeoForge JAVA_21、正确节点元数据通过。KineticCore 依赖范围仍为 [26.10.3,)。
- Forge 基线 SHA256：80A3489B52A97156B9064C53A49F875CA564C546ADCC679059416CF8A51EA533。184 个原有类的有序 javap 指令与签名比较一致（忽略常量池编号）；8 个资源中只有 mods.toml 的版本由 archive-derived token 展开为 26.10.3，运行时版本等价。语言及其他原有资源一致。
- 按用户要求跳过 1.20.1 游戏启动；未替换用户 Forge 整合包 JAR。
- 使用现有 PCL2 和 1.21.1-NeoForge_21.1.252 客户端进入既有创造世界，保持原 Java/内存设置。2026-10-03 20:00:23 五项运行断言通过，failures=0：组件装备（动态附魔组件、NBT 拒绝、库存保存恢复、属性、假人物品标记）、Curios 合法装备组件预设、方块记录保存恢复、Smite/Bane/Impaling 类别谓词、亡灵中毒与再生规则。Jade 两个插件均完成注册。
- 独立 runtimeValidationJar 含测试入口及仅供测试的 Curios 装备标签；不进入发布 JAR，验收后移出游戏 mods。证据在忽略目录 .gradle/migration/runtime-evidence。
- 代码审查发现的亡灵药水规则已修复并复验；运行回归还发现 codec 返回新 NBT 容器造成库存丢失、附魔类别谓词不再读取 MobType，均先复现后修复。Curios 的无标签钻石测试被正常过滤，修正测试数据，未放宽生产装备规则。
- 一次运行期间出现 Zulu 26 的原生 glfw.dll EXCEPTION_ACCESS_VIOLATION；无 Java 模组异常堆栈，原因尚未确定，重启后上述验证通过。原 hs_err_pid88424.log 保留在客户端目录。尚未覆盖全量玩法、专用服务器与每个 GUI 交互。

## 产物

- D:/NEWMODS/entitycontrol-forge-1.20.1-26.10.3.jar；SHA256 C6020431DFC96E6229D2AFEA6276C2FF06584D9C52B3A5FE916418F1429A1780。
- D:/NEWMODS/entitycontrol-neoforge-1.21.1-26.10.3.jar；SHA256 FCC9770087636547268C80A0B43687AA5DD3B083E21A5E49DAACE1861DF4D851。

只做本地迁移分支提交，不推送、不发布。1.21.1 验证客户端保留发布 JAR；替换前的核心 JAR 保存在版本目录 codex-migration-backup/entitycontrol-validation-20261003。
