## 26.10.4 — 2026-10-04

### English

- Long attribute/effect names, namespace labels and summaries scroll inside their own columns. The attribute editing footer and shared editor header keep controls within the available space, including long translations.
- Bound dummy labels before adjacent controls, keep the Curios title clear of Back, and limit component editor headings, hints and errors to the screen width.
- Hide duplicate vanilla container labels again after initialization so the dummy inventory caption is drawn only once.
- Require matching KineticCore 26.10.4+ to use the current screen-fitting tooltip implementation. Existing item-component/NBT version rules remain unchanged.

### 简体中文

- 属性与效果名称、命名空间及说明在各自列内滚动；属性编辑栏和通用编辑器顶栏按可用空间限制控件，适配超长翻译。
- 假人标签避开相邻控件，饰品标题避开返回按钮，组件编辑页的标题、说明与错误文字限制在屏幕范围内。
- 初始化后重新隐藏原版容器标签，避免假人物品栏标题重复绘制。
- 要求匹配的 KineticCore 26.10.4+，使用当前屏幕适配悬浮提示；保留既有组件/NBT 分版本规则。

---

2026年10月04日 — Language key validation / 语言键一致性检查

- Require identical authored English/Chinese keys and string values in source, version overrides and packaged resources; generated formatting keys are rejected during builds.

- 强制检查源码、版本覆盖与最终资源的中英文完整键名一致、值为字符串；构建禁止派生格式语言键。

---

2026年10月03日 20时11分 — 26.10.3

- Added NeoForge 1.21.1 support alongside Forge 1.20.1, using Java 21 and matching KineticCore 26.10.3+.
- The 1.21.1 equipment editor uses native item components and does not accept or convert legacy item NBT; entity/world data retain native NBT.
- Adapted Curios dummy presets and fixed equipment inventory restoration, enchantment creature predicates, and undead dummy potion behavior.
- Both builds and targeted NeoForge world checks passed; Forge game startup was skipped and full gameplay, every GUI, and dedicated servers have not been tested.
- The 26.1.2 node is reserved and disabled; it is not a supported release.

- 新增 NeoForge 1.21.1 支持，同时保留 Forge 1.20.1；使用 Java 21 和对应版本的 KineticCore 26.10.3+。
- 1.21.1 装备编辑器使用原生物品组件，不接受或转换旧物品 NBT；实体与世界数据仍使用原生 NBT。
- 适配 Curios 假人预设，修复装备库存恢复、附魔生物类别判定和亡灵假人药水行为。
- 两个版本构建及针对性的 NeoForge 世界检查通过；跳过 Forge 游戏启动，完整玩法、所有 GUI 与专用服务端尚未测试。
- 26.1.2 节点仅预留、未启用，不代表已支持。

---

2026年10月02日 13时53分

- Enabled KineticCore addon architecture validation during compilation.
- Verified the full build, final-JAR API references, and real development-client startup. No source-level warning suppressions were added.

- 在编译阶段接入 KineticCore 附属架构验证。
- 完整构建、最终 JAR API 引用检查及真实开发客户端启动验证通过，未添加源码级警告抑制。

---

历史记录（原记录未标注时间）

- Rebuilt the spawner, natural spawn, entity attribute, entity reset and block-break encounter editors with a unified layout: back button, compact buttons, drop-down menus and 3D mob cards.
- Merged the spawn rule, biome and category tabs and the spawn profile page into one natural spawn editor; the entity reset list and rule editor are now one page.
- Added editing for block-break encounter mob settings that previously had no GUI: spawn position, Y range, light, dimensions, biomes, allowed and blocked blocks, and custom name.
- Editors now ask for confirmation before leaving with unsaved changes.
- Cards, lists and item slots show state with borders: green for modified or enabled, red for invalid data, blue for hovered, orange for selected.
- Fixed missing translations in the spawner Jade tooltip and the Jade plugin settings.
- Replaced grey and other hard-to-read text colors.
- Fixed attribute list colors appearing only on the opening bracket; namespace labels, attribute names and values now retain their full colors.
- Modified attributes now appear at the top of the list and use a green outline, with hover and selection colors following the standard UI theme.

- 重做刷怪笼、自然生成、实体属性、实体重置和方块破坏遭遇编辑器的界面布局，统一为返回按钮、紧凑按钮、下拉菜单和 3D 生物卡片。
- 自然生成的生成规则、群系、分类三个页签和配置档案页合并为一个编辑器；实体重置的列表页和编辑页合并为一页。
- 方块破坏遭遇新增原先无法在界面中编辑的生物设置：生成位置、Y 范围、亮度、维度、群系、额外允许与禁止方块、自定义名称。
- 编辑器在有未保存修改时离开会先确认。
- 卡片、列表和物品格改用边框表示状态：绿色为已修改或已启用，红色为数据有问题，蓝色为悬停，橘黄色为选中。
- 修复刷怪笼 Jade 提示和 Jade 插件设置中缺失的翻译。
- 替换灰色等不易辨认的文字颜色。
- 修复属性列表只有左括号有颜色、后续文字颜色丢失的问题，恢复来源标签、属性名称和数值的完整配色。
- 已修改的属性优先置顶并显示绿色描边，悬停和选中颜色遵循统一界面主题规则。
