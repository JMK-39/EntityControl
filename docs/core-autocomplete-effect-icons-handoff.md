# Effect icons in shared autocomplete suggestions / 公共自动补全药水图标交接

## English

Addon-owned effect rows use `KineticGraphics.effectIcon` and can preserve their existing dimensions. Core-owned autocomplete popups currently cannot: `KineticSuggestion` exposes only value/translation, while `KineticAutoCompleteField` exposes no icon or suggestion-row renderer.

Affected existing callers: KineticArmory `PotionEditor`, `AttackEffectEditor`, `PotionImmunityEditor`, and the POTION condition dictionary. They already use `KineticSearch.potionDictionary`; replacing these inputs with another selector would change established interactions and is outside this fix.

Requested Core capability for its maintainer: optional effect-icon metadata/rendering on existing suggestions, retain the existing two-argument constructor and text-only fallback, and populate icons through `potionDictionary`. Keep popup geometry, keyboard navigation, search, selection and translated name/ID behavior; reserve icon space within each row. Implement once in Core for all three Minecraft versions. Validate invalid IDs, long English names, screen edges and existing addon callers.

Core is intentionally unmodified here because its maintainer is handling it separately. This is an explicit remaining capability gap, not a claim that every autocomplete popup already has icons.

## 简体中文

附属自己绘制的效果行可以用 `KineticGraphics.effectIcon` 补图标并保留尺寸；核心自动补全弹窗目前无法这样做：`KineticSuggestion` 只有值和翻译，`KineticAutoCompleteField` 没有图标或候选行绘制接口。

现有受影响入口：KineticArmory 的药水、攻击效果、药水免疫编辑器，以及 POTION 条件词典。这些入口已经使用 `KineticSearch.potionDictionary`；把输入框换成另一种选择器会改变既有操作，本次不这样处理。

请核心维护者为现有候选项提供可选效果图标元数据与绘制，保留双参数构造器和纯文字回退，由 `potionDictionary` 填入图标。保留弹窗布局、键盘导航、搜索、选择及翻译名称/ID，仅在行内给图标预留空间。三个版本由核心统一实现，验证无效 ID、超长英文、屏幕边缘及现有附属调用。

核心由另一位维护者单独处理，本次没有修改。这是明确待补的 API 能力，不代表所有自动补全弹窗已添加图标。
