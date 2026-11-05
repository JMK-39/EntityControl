## 26.10.26 — 2026-10-26
- The attribute page puts the operation, value and delete controls on the top row beside the search; the list runs to the bottom.
- 属性页的运算方式、数值与删除属性移到顶行搜索框右侧，列表一直延伸到底部。
- Explanations next to filled-in content are gone (spawn equipment, buff editor, block rules, entity pools, global break settings, spawner, spawn dimensions and categories, reset rules); only empty states keep a short guide. Random attribute ranges show just the default value.
- 去掉内容旁的说明文字（生成装备、状态效果、方块规则、刷怪池、全局破坏设置、刷怪笼、生成维度与分类、重置规则），只在空白状态保留简短指引；随机属性范围只显示默认值。
- Save buttons read Save.
- 保存按钮统一为“保存”。

## 26.10.24 — 2026-10-24
- The spawn equipment editor uses the standard 22 px item slots.
- 生成装备编辑改用 22 像素标准物品格。

## 2026-10-20 — Shared interface frames / 统一界面边框

- Cancel buttons read Back.
- “取消”按钮统一为“返回”。
- Chinese text on 1.21.1 and 26.1.2 no longer puts spaces around 数据组件 (for example 编辑数据组件 instead of 编辑 数据组件).
- 1.21.1 与 26.1.2 的中文不再在“数据组件”两侧留空格（例如“编辑数据组件”而不是“编辑 数据组件”）。
- Spawn equipment uses vanilla equipment slots: empty slots show the vanilla helmet, chestplate, leggings, boots, sword and shield outlines, the slot name is in the tooltip, left-clicking an empty slot opens the item search and right-clicking a slot opens replace / NBT / clear. The mob is shown in 3D between the armor and hand slots, wearing the configured equipment, like the vanilla inventory; the notes take a narrower column (on 1.21.1 and 26.1.2 they now describe the new clicks and say components instead of NBT). The dummy's empty slot outlines now also show on 26.1.2.
- 生成装备改用原版装备槽：空槽显示原版头盔、胸甲、护腿、靴子、剑和盾牌轮廓，槽位名称放在悬浮提示里；左键空槽打开物品搜索，右键槽位可替换、编辑 NBT 或清空。生物以 3D 形式显示在盔甲槽与手持槽之间并穿戴配置的装备，与原版背包一致；说明栏改窄（1.21.1 与 26.1.2 的说明也更新为新的点击方式，并称为数据组件）。假人空槽轮廓在 26.1.2 上也能正常显示。
- Entity lists keep 2 px between the last card column and the scrollbar and no longer reserve a fixed minimum width, so the right-hand forms are wider; page content starts higher and long header titles use the free space instead of scrolling. The mob pool fits five card columns, and redundant section titles (mob count, selected mob, attribute count, attribute name) were removed.
- 生物列表的最后一列与滚动条间距改为 2 像素，不再保留固定最小宽度，右侧表单更宽；页面内容整体上移，长标题优先使用空余空间而非滚动；生物池正好放下 5 列，去掉多余的分区标题（生物数量、所选生物名、属性数量、属性名）。
- Spawner Control gives its setting labels 76 instead of 52 pixels, so labels such as Fixed delay (s) and Speed multiplier show in full instead of scrolling.
- 刷怪笼控制的设置标签列由 52 像素加宽到 76 像素，“固定延迟”“速度倍率”等标签完整显示，不再滚动。
- The dummy screen no longer shows a light line through the Inventory label and the Env / HP Drop buttons: the equipment panel and the vanilla inventory background now meet below that row, on plain texture.
- 假人界面的“背包”标签与环境伤害、掉血按钮不再被一条浅色线穿过：装备面板与原版背包背景改在该行下方的纯色区域衔接。
- Pages retain one white outer frame. Neighbouring content regions use borderless surfaces and single gray-white dividers; controls, entity cards, interactive rows and dialogs keep their own outlines.
- 页面统一保留一层白色外框，相邻内容区使用无边框底色与单条灰白分隔线；控件、生物卡片、交互条目及弹窗保留各自的描边。
- Equipment previews retain their configured NBT and count on Forge. Equipment, accessory and block icons use native item tooltips, including modded tooltip components.
- Text lists, item and entity grids, and scrolling forms stop at their actual content bottom; drawing and hit testing share the same offset.
- Forge 装备预览保留配置的 NBT 与数量；装备、饰品及方块图标使用原生物品提示，保留模组自定义提示组件。
- 文字列表、物品与生物网格及滚动表单按实际内容末端停止，绘制与命中使用同一偏移，消除末行后的空白。

## 2026-10-16 — Standard entity cards and dimension windows / 标准生物卡片与维度窗口

### English

- Entity editors use the Core's 50 px model cards with 2 px gaps. Five columns leave space for the settings form; entity pools retain four columns. Existing settings-panel widths are preserved, and each card has the standard item slot background.
- Search fields use more of their available width. Delete reuses existing rule, biome, effect and equipment removal actions while text fields retain keyboard priority.
- Spawn dimension allowlists, blocklists and buff dimension restrictions open a compact text selection window with dimension IDs and translated names. Apply updates the local draft; Back cancels the selection.
- Editor frames and their content use 4 px margins. Short Back and Apply actions stay 40 px wide; list headings no longer repeat entry counts.

### 简体中文

- 生物编辑器统一使用核心 50 像素模型卡片，间隔 2 像素；五列网格为设置表单保留空间，生物池保持四列，原设置栏宽度不缩小，每张卡片使用标准物品格背景。
- 搜索框在可用范围内加宽；Delete 复用已有的规则、群系、效果与装备移除动作，文本框编辑优先。
- 生成规则的维度白名单、黑名单与效果的限定维度改用紧凑的纯文字多选窗口，显示维度 ID 与译名；应用只更新本地草稿，返回取消本次选择。
- 编辑器外框与内容留出 4 像素间距，返回、应用等短文字按钮保持 40 像素宽，列表标题不再重复数量。

## 2026-10-15 — Card backgrounds / 卡片背景

### English

- The global rule card and disabled mobs keep the light card background instead of turning black.

### 简体中文

- 全局规则卡片与已禁用的生物保持浅色卡片背景，不再变黑。

## 2026-10-13 — Attribute list rows / 属性列表行

### English

- The attribute list in the entity modifier shows whole rows only, so no edge of a hidden row appears just above the operation and value controls.

### 简体中文

- 实体修改器的属性列表只显示完整的行，操作与数值控件上方不再露出被遮住行的边缘。

## 2026-10-10 — Spawner compatibility and editor navigation / 刷怪笼兼容与编辑页导航

### English

- Fix a 26.1.2 startup failure caused by spawner save/load compatibility. Keep successful spawn counts, cooldowns and original spawner settings when saving and reopening worlds.
- Move the dummy accessory editor's Back button to the upper left, matching the other Entity Control editors.

### 简体中文

- 修复 26.1.2 刷怪笼存取接口兼容问题引起的启动失败；保存及重新进入存档后保留成功生成次数、冷却时间和刷怪笼原始参数。
- 假人饰品编辑界面的返回按钮移到左上角，与其他实体控制编辑界面保持一致。

## 2026-10-09 — Craftable testing dummies / 可合成的测试假人

### English

- Add a testing dummy item with its own icon. Craft it with a vertical column of hay block, armor stand, and hay block; right-click a block to place it.
- Sneak and left-click a dummy with an empty main hand to recover it directly into your hand, retaining equipment, accessories, attributes, and editor settings. Sneak and right-click continues to open the editor.
- Add placement and recovery instructions to the item tooltip and Jade overlay.
- Restore missing hover explanations in Entity Control configuration menus.

### 简体中文

- 新增带独立图标的测试假人物品；在工作台竖排放入“干草块、盔甲架、干草块”即可合成，手持后右键方块放置。
- 潜行时主手空手左键假人可直接回收到手中，并保留装备、饰品、属性及编辑设置；潜行空手右键仍打开编辑界面。
- 在物品悬浮提示与 Jade 显示中补充放置和回收说明。
- 修复实体控制配置菜单中部分配置项缺少悬浮说明的问题。

## 2026-10-09 — Adjustable damage number distance / 可调伤害数字显示距离

### English

- Add an in-game Damage Number Distance setting, defaulting to 64 blocks with a 1–1024 block range. Saving applies immediately to floating and cumulative damage numbers.
- Keep damage-number distance separate from the dummy overhead statistics panel and use the target position consistently at distance boundaries.
- Add explanations to dummy attribute, equipment, creature-category and damage-behavior controls.

### 简体中文

- 游戏内新增“伤害数字显示距离”，默认 64 格，可调整范围 1–1024 格，保存后立即应用于普通飘字与累计伤害数字。
- 伤害数字距离独立于假人头顶统计面板；距离边界按目标位置统一判断。
- 为假人属性、饰品装备、生物类别与受伤行为控件补充操作说明。

## 2026-10-08 — Item preview slots / 物品预览格

### English

- Spawn-equipment previews use the standard item-slot background. Dummy accessory previews have clear gaps between their slots, with each icon kept clear of its slot border.
- Keep the dummy's built-in equipment and player inventory slot textures. The separate accessory grid shows full-size item icons with two-pixel gaps, using fewer rows and columns within the existing panel.
- Testing dummies have 16 Curios accessory slots by default; the extra slot count remains configurable.

### 简体中文

- 生成装备的物品预览使用统一的物品格背景。假人饰品预览格之间保留间距，图标与格子描边之间留出空隙。
- 假人内置装备栏和玩家背包保留原有槽位贴图；独立饰品网格在现有面板中减少行列，完整显示物品图标，并保留两像素间距。
- 测试假人默认提供 16 个 Curios 饰品槽位，额外槽位数量仍可配置。

## 2026-10-08 — Attribute value text / 属性数值文字

### English

- Attribute values scroll within their original value column instead of covering the attribute names.

### 简体中文

- 属性数值在原有数值列内滚动显示，不再覆盖属性名称。

## 26.10.6 — 2026-10-06

### English

- The dummy editor's panel keeps its own 176 px frame again: a gray fill had widened it past the frame, and the vanilla inventory below the equipment panel was cut off above its bottom border. The inventory is now drawn in full, including the hotbar's lower edge.
- The dummy Curios page draws its player inventory down to the texture's bottom border, so the hotbar slots keep their lower edge.
- In the potion buff list, the remove button (X) of a modified effect sits 2 px inside the row frame instead of touching its top and bottom lines.
- Screens keep the same layout in every language: buttons and switches, the settings label column, the attribute editor's value label and the namespace column of attribute and buff rows have fixed widths instead of following their text; longer text scrolls.

### 简体中文

- 假人编辑器的面板恢复为自身 176 像素的边框：之前的灰色底色把面板撑宽到边框之外，装备面板下方的原版背包也在底边框之前被截断。现在背包完整绘制，包括快捷栏的下边缘。
- 假人饰品页面的玩家背包绘制到贴图底边框为止，快捷栏格子不再缺少下边缘。
- 药水增益列表中，已修改效果的移除按钮（X）与行边框保持 2 像素，不再碰到上下边框线。
- 所有语言下界面排版相同：按钮与开关、设置标签列、属性编辑器的数值标签以及属性和药水增益行的命名空间列使用固定宽度，不再随文字变化；过长文字滚动显示。

## 26.10.5 — 2026-10-05

### English

- Lists (biomes, spawn rules, reset rules, picker lists) are plain striped rows packed without gaps instead of a stack of boxed rows, and the attribute list no longer draws a box around every row. Only the hovered row, the current choice (yellow), chosen or changed rows (green) and rows with errors (red) are outlined.
- In the "Add" pickers (for example adding several biomes), chosen entries are green, because they are a multi-selection; yellow marks only the current choice. Tab, scope, mode, profile and speed menus mark their current option yellow; filters and switches mark enabled options green.
- Saving or removing a reset rule answers only the player who edited; the rules are no longer sent to every online player.
- Restore the dummy editor's original vanilla inventory background below its equipment panel, preserving slot positions, buttons and interactions on every enabled Minecraft version.
- Align the vanilla inventory texture with its existing item slots and hotbar.
- Enabled Minecraft 26.1.2 / NeoForge 26.1.2.112 (Java 25); releases now cover Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Optional Jade 26.1 and Curios 15 on 26.1.2.
- Every version uses the same screens. On 1.21.1 and 26.1.2, equipment and drop component data (`[damage=5]`) is now edited in Core's NBT editor, the same editor Forge uses for NBT, instead of a separate page. Requires KineticCore 26.10.5+.
- The dummy, spawn rules, spawners, biome spawn tables, entity resets, attribute modifiers and block-break encounters work on 26.1.2. Existing dummy settings, inventory and Curios presets are preserved, and dummy damage labels remain visible.
- Fixed a NeoForge mod-loading warning screen on 26.1.2.

### 简体中文

- 列表（群系、生成规则、重置规则、选择列表）改为无间隙的条纹纯文字行，不再是一排带边框的方块；属性列表不再给每一行画边框。只有光标所在行、当前选择（黄色）、已选或已修改的行（绿色）以及有错误的行（红色）带边框。
- "添加"选择列表（例如一次添加多个群系）中已选的项为绿色，因为这是多选；黄色只表示当前选择。标签页、范围、模式、配置档与速度菜单用黄色标出当前选项；筛选与开关菜单中开启的项为绿色。
- 保存或删除重置规则只回复编辑的玩家，不再把规则发送给所有在线玩家。
- 恢复假人编辑器装备面板下方的原版背包背景；所有已启用版本保留原有槽位、按钮位置和操作方式。
- 对齐原版背包贴图与现有物品槽、快捷栏的位置。
- 启用 Minecraft 26.1.2 / NeoForge 26.1.2.112（Java 25）；发布版本覆盖 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。26.1.2 可选 Jade 26.1 与 Curios 15。
- 所有版本使用相同界面。1.21.1 与 26.1.2 的装备与掉落物数据组件（`[damage=5]`）改用核心 NBT 编辑器编辑，与 Forge 编辑 NBT 的界面一致，不再使用单独页面。要求 KineticCore 26.10.5+。
- 假人、生成规则、刷怪笼、群系刷怪表、实体重置、属性修改与方块破坏遭遇可在 26.1.2 中使用，保留已有假人设置、物品栏与饰品预设，假人伤害标签正常显示。
- 修复 26.1.2 显示 NeoForge 模组加载警告界面的问题。

## 26.10.4 — 2026-10-04

### English

- Preserve continuous language-file colors across namespace/name and buff-summary placeholders, including the text inside brackets.

- Long attribute/effect names, namespace labels and summaries scroll inside their own columns. The attribute editing footer and shared editor header keep controls within the available space, including long translations.
- Bound dummy labels before adjacent controls, keep the Curios title clear of Back, and limit component editor headings, hints and errors to the screen width.
- Hide duplicate vanilla container labels again after initialization so the dummy inventory caption is drawn only once.
- Requires matching KineticCore 26.10.4+ for tooltips that fit the screen. Existing item-component/NBT version rules remain unchanged.

### 简体中文

- 连续解析命名空间、名称与效果说明占位符前后的语言颜色，保留括号内文字及数字样式。

- 属性与效果名称、命名空间及说明在各自列内滚动；属性编辑栏和通用编辑器顶栏按可用空间限制控件，适配超长翻译。
- 假人标签避开相邻控件，饰品标题避开返回按钮，组件编辑页的标题、说明与错误文字限制在屏幕范围内。
- 初始化后重新隐藏原版容器标签，避免假人物品栏标题重复绘制。
- 要求匹配的 KineticCore 26.10.4+，使用当前屏幕适配悬浮提示；保留既有组件/NBT 分版本规则。

---

2026年10月03日 20时11分 — 26.10.3

- Added NeoForge 1.21.1 support alongside Forge 1.20.1, using matching KineticCore 26.10.3+.
- The 1.21.1 equipment editor uses native item components and does not accept or convert legacy item NBT; entity/world data retain native NBT.
- Adapted Curios dummy presets and fixed equipment inventory restoration, enchantment creature predicates, and undead dummy potion behavior.
- Minecraft 26.1.2 is not yet supported.

- 新增 NeoForge 1.21.1 支持，同时保留 Forge 1.20.1；使用对应版本的 KineticCore 26.10.3+。
- 1.21.1 装备编辑器使用原生物品组件，不接受或转换旧物品 NBT；实体与世界数据仍使用原生 NBT。
- 适配 Curios 假人预设，修复装备库存恢复、附魔生物类别判定和亡灵假人药水行为。
- 尚不支持 Minecraft 26.1.2。

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
