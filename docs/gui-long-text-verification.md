# GUI verification / 界面验证

## English

2026-10-04: bounded text changes in attribute/effect panels, shared editor header, dummy inventory, Curios and component editor. Shared Core APIs only; no new loader branches or language changes. Core dependency now 26.10.4.

- buildAll and the test-only runtimeValidationJar passed offline for both enabled nodes. Architecture/reference checks passed; Forge 5 and NeoForge 6 Mixins had zero problems. Authored source keys match (1196 each); NeoForge overrides match (13 each); packaged parity checks passed. There are no ordinary unit tests in this repository.
- The existing NeoForge21.1.252 client opened eight actual GUI states: global attributes, zombie attributes, effects without/with a modified row, dummy inventory, Curios, valid and invalid components. English/Chinese: 854×480 and 1536×864; GUI scale automatic. Extra long translated text was captured twice to inspect scrolling.
- The complete run reports ENTITY_GUI_PASS pages=8 captures=48 failures=0. Review found uncapped header actions and a duplicate vanilla inventory caption; these were fixed and five affected states recaptured (ENTITY_GUI_PASS pages=8 captures=30 failures=0). Markers record harness execution; screenshots were reviewed separately, including all final stress headers and dummy labels.
- The first harness launch ended at the dummy page because KineticGui.open rejects container pages. The fixture now creates the actual container host; this was a test entry error, not a product bug. Tests operate on local drafts and unadded client dummy entities, with no save or inventory-changing callbacks.
- All original option bytes were restored (SHA256 50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919), test client stopped normally and validation JAR moved out of mods. Matching production 26.10.4 JAR remains installed. Memory settings unchanged.
- HUD/world text and bounded numeric values were excluded. The empty Curios no-slots branch was reviewed in source; the installed Curios fixture provides a slot. No Forge game run was performed, per user instruction. The Core tall-tooltip limitation is tracked by ContentStudio/docs/tooltip-height-follow-up.md.

Local evidence: .gradle/gui-long-text-20261004/build-final.log, client-second.log, client-final.log and phase-numbered PNGs. Validation classes are absent from release JARs.

## 简体中文

2026-10-04：修复属性/效果面板、通用编辑器顶栏、假人物品栏、饰品及组件编辑器的文字边界。只使用核心 API，未新增加载器分支或修改语言键；核心依赖更新为 26.10.4。

- 两个启用节点的 buildAll 与测试专用 runtimeValidationJar 均通过离线构建；架构、引用检查通过；Forge 5 个、NeoForge 6 个 Mixin 零问题。中英文源码各1196键，NeoForge覆盖各13键，打包资源一致性检查通过；本仓库没有普通单元测试。
- 现有 NeoForge21.1.252 客户端打开8种真实状态：全局属性、僵尸属性、未修改/已修改效果、假人物品栏、饰品及有效/错误组件。分别检查中英文854×480、1536×864，GUI缩放自动；额外拍摄超长翻译的开始/滚动帧。
- 完整运行记录 ENTITY_GUI_PASS pages=8 captures=48 failures=0。截图发现顶栏按钮宽度无总预算、原版物品栏标签重复；修复后复测5种受影响状态，记录 ENTITY_GUI_PASS pages=8 captures=30 failures=0。日志只证明执行，最终顶栏和假人长文字均另行检查截图。
- 首次测试在假人页结束，因为 KineticGui.open 不接受容器页；测试现使用真实容器宿主，此为验证入口问题。测试仅使用本地草稿与未加入世界的客户端假人，不调用保存或修改库存的操作。
- 原 options.txt 已逐字节恢复，SHA256为50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919；测试客户端正常退出，测试JAR已移走，保留匹配的正式26.10.4产物，未更改内存设置。
- 世界/HUD文字、数值保持原样。饰品无槽位提示分支已检查源码，本机测试提供了槽位；按用户要求不启动Forge游戏。核心过高提示问题见ContentStudio/docs/tooltip-height-follow-up.md。

本机证据位于 .gradle/gui-long-text-20261004；正式JAR不含验证类。
