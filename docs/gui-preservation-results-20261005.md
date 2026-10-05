# GUI preservation results / 界面还原验收 — 2026-10-05

## English

Restored the dummy's vanilla player inventory background and aligned it to existing slots, without moving buttons or changing equipment interactions. Retained EntityControl's existing 3D entity cards; both installed versions show actual models.

KineticArmory detail/piece-bonus effect rows and MobAscension effect lists/pickers now have leading potion icons. MobAscension obtains actual effect names even in English while keeping IDs and existing controls. ContentStudio recipe editors expose their vanilla container backgrounds and its browser uses vanilla slot textures; TACZWorkshop uses those textures inside its existing material/result/workbench slots. CombatSystems restores the original four-by-four spawner model grid, original visible bottom actions and correct editing target after search.

| Repository | Actual built nodes | Runtime acceptance |
|---|---|---|
| EntityControl | Forge 1.20.1, NeoForge 1.21.1 | 3D modifier cards, effect panel, dummy inventory |
| KineticArmory | Forge 1.20.1, NeoForge 1.21.1 | Detail and piece-bonus potion rows |
| ContentStudio | Forge 1.20.1, NeoForge 1.21.1, NeoForge 26.1.2 | Four recipe editors and browser on 1.20.1/1.21.1; 26.1.2 GUI fixture not ported |
| CombatSystems | Forge 1.20.1 | Restored model grid and filtered editing target |
| MobAscension | Forge 1.20.1 | Effect labels and full effect picker, including English names |
| TACZWorkshop | Forge 1.20.1 | Existing recipe draft editor and vanilla slots |

All release JARs are in `D:/NEWMODS`. Six Forge artifacts are installed in the complete existing OTHERWORLD CLASH mods directory; three matching NeoForge artifacts are installed in the existing 1.21.1 profile. Each installed artifact's SHA256 matches the output file. Profiles, full existing mod folders and original 8GiB launch arguments were used; no games were downloaded. Only copied saves and detached unsaved GUI drafts were tested.

Evidence in this worktree's ignored `build/gui-preservation-check`:

- `fullpack/client4.out.log`: initial 36 captures (3D cards, dummy and armor effects), zero fixture failures.
- `fullpack/final-addons/client2.out.log`: 36 captures (recipes, Combat, Mob and TACZ), zero fixture failures.
- `fullpack/final-names/client2.out.log`: final 16 captures (filtered Combat, corrected Mob names/picker, recipe browser), zero fixture failures.
- `neoforge/client.out.log`: initial 36 captures; `neoforge/recipes.out.log`: final rebuilt recipe-only 16 captures; `neoforge/final-browser/client.out.log`: four browser captures. All reported zero fixture failures.
- Captures cover English and Simplified Chinese, 854×480 and 1536×864, automatic GUI scale; actual images were inspected, not just the logs. Forge resource packs retain their overrides of vanilla texture paths.
- `final-artifact-receipts.json` records the nine installed release SHA256 values. Production archives contain no temporary validation classes.

All recorded validation clients exited. Temporary fixture JARs were removed from both profiles; original options and the temporarily changed log-cleaner configuration were restored and hash-verified. One Core/Mixin startup deadlock is documented separately; Core source/JAR were not changed.

Limits: CombatSystems, MobAscension and TACZWorkshop remain Forge-only here. EntityControl/KineticArmory's 26.1.2 nodes are not enabled. Shared Core autocomplete popups need an effect-icon API extension (separate handoff); existing inputs were retained. No claim is made that these unsupported versions or every Core popup were validated. Local commits use XYAT, English above Chinese, without AI attribution; nothing was pushed or released.

## 简体中文

恢复假人编辑器的原版玩家背包背景，并与现有物品槽对齐；保留按钮位置与装备交互。EntityControl 的既有 3D 生物卡片没有换成文字列表，两个实际安装版本均能显示模型。

KineticArmory 详情/件数加成效果行及 MobAscension 效果列表/选择器添加行首药水图标。MobAscension 英文也显示实际效果名称，保留 ID 和控件。ContentStudio 配方编辑器显示原版容器背景，浏览器使用原版槽位贴图；TACZWorkshop 在原有材料/结果/工作台槽位中使用原版贴图。CombatSystems 还原四列四行刷怪笼模型网格、原有底部按钮，并修复搜索后的编辑目标。

实际发布包均在 `D:/NEWMODS`。完整 OTHERWORLD CLASH 安装六个 Forge 新包，现有 1.21.1 目录安装三个对应 NeoForge 新包；九个已安装包与输出文件 SHA256 全部一致。测试使用既有完整 mods、原有 8GiB 参数，未下载游戏；只使用存档副本与未保存页面草稿。

已检查中英文、854×480 与 1536×864、自动 GUI 缩放的实际截图。Forge 原版纹理路径仍遵从整合包资源包覆盖。三轮 Forge 验证分别生成 36、36、16 张截图；NeoForge 分别生成 36、16、4 张截图；夹具均报告零失败，实际画面另行检查。日志、截图及九个安装包哈希保留在本工作区的 `build/gui-preservation-check`。

记录的测试客户端均已退出；两个目录的临时夹具已移除，原选项及临时修改的日志配置已恢复并核对哈希。一次核心/Mixin 启动死锁已单独记录，本次未修改核心源码或 JAR。

版本范围以表格为准：ContentStudio 三版本构建通过，但 26.1.2 GUI 测试夹具尚未移植；EntityControl/KineticArmory 未启用 26.1.2，CombatSystems/MobAscension/TACZWorkshop 仍只有 Forge。核心自动补全弹窗的药水图标仍需公共 API 扩展，已有输入方式保留；未宣称未支持版本或全部弹窗已验证。所有提交使用 XYAT、英文在上中文在下、无 AI 署名；未推送或发布。
